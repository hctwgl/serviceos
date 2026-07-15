package com.serviceos.evidence.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.workorder.api.WorkOrderExpressionContext;
import com.serviceos.workorder.api.WorkOrderExpressionContextQuery;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Task 的 Evidence resolution stream 唯一写路径。task.created 与 form.submitted 都调用本服务，
 * 在同一事务锁内比较单调事实版本并追加 generation，防止跨聚合事件乱序导致槽位回退。
 */
@Service
final class EvidenceResolutionGenerationService {
    private static final String OCCURRENCE = "default";

    private final WorkOrderExpressionContextQuery workOrders;
    private final ConfigurationService configurations;
    private final EvidenceTemplateResolver resolver;
    private final EvidenceSlotRepository repository;
    private final EvidenceItemRepository items;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    EvidenceResolutionGenerationService(
            WorkOrderExpressionContextQuery workOrders,
            ConfigurationService configurations,
            EvidenceTemplateResolver resolver,
            EvidenceSlotRepository repository,
            EvidenceItemRepository items,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.workOrders = workOrders;
        this.configurations = configurations;
        this.resolver = resolver;
        this.repository = repository;
        this.items = items;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    EvidenceResolutionApplyResult apply(
            String tenantId,
            TaskFulfillmentContext task,
            UUID sourceEventId,
            String sourceEventDigest,
            String factType,
            String factRef,
            int factRevision,
            Map<String, Object> formValues
    ) {
        repository.lockResolutionStream(tenantId, task.taskId());
        EvidenceResolutionState previous = repository.latestResolution(tenantId, task.taskId())
                .orElse(null);
        if (previous != null && factRevision <= previous.conditionFactRevision()) {
            return EvidenceResolutionApplyResult.stale();
        }

        List<ConfigurationAssetDefinition> templates = configurations.listBundleAssets(
                tenantId, task.configurationBundleId(), task.configurationBundleDigest(),
                ConfigurationAssetType.EVIDENCE);
        // 表单驱动条件在首个 VALIDATED submission 前没有权威事实。task.created 只记录等待，
        // 不能把“尚未提交”猜成 false；后续 form.submitted 会创建首个完整 generation。
        if ("TASK_CREATED".equals(factType) && templates.stream().anyMatch(this::requiresFormFacts)) {
            return EvidenceResolutionApplyResult.awaitingFormFacts();
        }
        Supplier<ExpressionContext> context = memoizedExpressionContext(tenantId, task, formValues);
        List<ResolvedEvidenceTemplate> resolvedTemplates = templates.stream()
                .map(template -> resolver.resolve(template, task.stageCode(), context))
                .toList();
        List<ResolvedEvidenceRequirement> requirements = resolvedTemplates.stream()
                .flatMap(result -> result.requirements().stream()).toList();
        List<ResolvedEvidenceCondition> conditions = resolvedTemplates.stream()
                .flatMap(result -> result.conditions().stream()).toList();

        // 固定资料模板以及没有 Evidence 资产的任务不需要 WorkOrder 条件事实。保持按需读取，
        // 避免非资料模块仅因发布 task.created 就被无关的 WorkOrder 前置条件阻断。
        ExpressionContext resolvedContext = conditions.isEmpty() ? null : context.get();
        String conditionInputJson = resolvedContext == null ? "{}" : write(resolvedContext);
        String conditionInputDigest = Sha256.digest(conditionInputJson);
        Map<String, Object> resolutionExplanation = new LinkedHashMap<>();
        resolutionExplanation.put("resolverVersion", EvidenceTemplateResolver.VERSION);
        resolutionExplanation.put("conditionFactType", factType);
        resolutionExplanation.put("conditionFactRef", factRef);
        resolutionExplanation.put("conditionFactRevision", factRevision);
        if (resolvedContext != null) {
            resolutionExplanation.put("conditionInput", resolvedContext);
        }
        resolutionExplanation.put("conditions", conditions);

        Map<RequirementKey, EvidenceResolutionMemberState> oldMembers = new HashMap<>();
        if (previous != null) {
            for (EvidenceResolutionMemberState member : previous.members()) {
                oldMembers.put(new RequirementKey(
                        member.templateVersionId(), member.requirementCode(), member.occurrenceKey()), member);
            }
        }

        Instant now = clock.instant();
        UUID resolutionId = UUID.randomUUID();
        int generationNo = previous == null ? 1 : previous.generationNo() + 1;
        List<EvidenceSlotView> newSlots = new ArrayList<>();
        List<EvidenceResolutionMember> members = new ArrayList<>();

        for (ResolvedEvidenceRequirement requirement : requirements) {
            RequirementKey key = new RequirementKey(
                    requirement.templateVersionId(), requirement.requirementCode(), OCCURRENCE);
            EvidenceResolutionMemberState old = oldMembers.remove(key);
            UUID activeSlotId;
            UUID previousSlotId = old == null ? null : old.lineageSlotId();
            String transition;
            int slotGeneration;
            if (old != null && old.conditionResult()) {
                activeSlotId = old.activeSlotId();
                transition = "UNCHANGED_ACTIVE";
                slotGeneration = old.slotGeneration();
            } else {
                activeSlotId = UUID.randomUUID();
                transition = "ACTIVATED";
                slotGeneration = old == null ? 1 : old.slotGeneration() + 1;
                newSlots.add(slot(resolutionId, task, requirement, activeSlotId,
                        slotGeneration, previousSlotId, generationNo, transition, now));
            }
            members.add(new EvidenceResolutionMember(
                    UUID.randomUUID(), resolutionId, task.taskId(), task.projectId(),
                    requirement.templateVersionId(), requirement.requirementCode(), OCCURRENCE,
                    true, activeSlotId, previousSlotId, transition, "NONE",
                    items.countCountingItems(tenantId, activeSlotId),
                    requirement.conditionInputDigest(), requirement.resolutionExplanationJson(), now));
        }

        for (ResolvedEvidenceCondition condition : conditions) {
            if (condition.result()) {
                continue;
            }
            RequirementKey key = new RequirementKey(
                    condition.templateVersionId(), condition.evidenceKey(), OCCURRENCE);
            EvidenceResolutionMemberState old = oldMembers.remove(key);
            UUID previousSlotId = old == null ? null : old.lineageSlotId();
            String transition = old != null && old.conditionResult()
                    ? "DEACTIVATED" : "UNCHANGED_INACTIVE";
            int counting = previousSlotId == null ? 0
                    : items.countCountingItems(tenantId, previousSlotId);
            // 未决处置属于首次 true→false 的精确 generation，后续 false→false 不能复制出多个
            // 人工处置任务。查询与完成门禁会跨历史 generation 查找该未决成员，直到显式关闭。
            String disposition = "DEACTIVATED".equals(transition) && counting > 0
                    ? "REVIEW_REQUIRED" : "NONE";
            members.add(new EvidenceResolutionMember(
                    UUID.randomUUID(), resolutionId, task.taskId(), task.projectId(),
                    condition.templateVersionId(), condition.evidenceKey(), OCCURRENCE,
                    false, null, previousSlotId, transition, disposition, counting,
                    conditionInputDigest, write(conditionExplanation(condition)), now));
        }

        int activeSlotCount = (int) members.stream().filter(EvidenceResolutionMember::conditionResult).count();
        resolutionExplanation.put("activeSlotCount", activeSlotCount);
        resolutionExplanation.put("generationNo", generationNo);
        EvidenceTaskResolution resolution = new EvidenceTaskResolution(
                resolutionId, tenantId, task.projectId(), task.taskId(),
                task.configurationBundleId(), task.configurationBundleDigest(), task.stageCode(),
                sourceEventId, sourceEventDigest, EvidenceTemplateResolver.VERSION,
                conditionInputDigest, write(resolutionExplanation), generationNo,
                factType, factRef, factRevision, previous == null ? null : previous.resolutionId(),
                now, newSlots, members);
        repository.insert(resolution);
        return new EvidenceResolutionApplyResult(
                "APPLIED", resolution, activeSlotCount,
                members.stream().filter(member -> "ACTIVATED".equals(member.transition())).count(),
                members.stream().filter(member -> "DEACTIVATED".equals(member.transition())).count(),
                members.stream().filter(member -> "REVIEW_REQUIRED".equals(member.requiredDisposition())).count());
    }

    Map<String, Object> readFormValues(String valuesJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(valuesJson);
        } catch (JacksonException exception) {
            throw new IllegalStateException("已验证 FormSubmission values 不是合法 JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalStateException("已验证 FormSubmission values 必须是 JSON object");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        root.propertyNames().forEach(key -> {
            JsonNode value = root.get(key);
            if (value == null || value.isNull()) {
                return;
            }
            if (value.isBoolean()) {
                values.put(key, value.asBoolean());
            } else if (value.isIntegralNumber()) {
                values.put(key, value.asLong());
            } else if (value.isNumber()) {
                values.put(key, value.decimalValue());
            } else if (value.isTextual()) {
                values.put(key, value.asText());
            } else {
                values.put(key, value);
            }
        });
        return Map.copyOf(values);
    }

    private EvidenceSlotView slot(
            UUID resolutionId,
            TaskFulfillmentContext task,
            ResolvedEvidenceRequirement requirement,
            UUID slotId,
            int slotGeneration,
            UUID supersedesSlotId,
            int resolutionGeneration,
            String transition,
            Instant now
    ) {
        return new EvidenceSlotView(
                slotId, resolutionId, task.taskId(), task.projectId(),
                requirement.templateVersionId(), requirement.templateKey(),
                requirement.templateVersion(), requirement.templateDigest(),
                requirement.requirementCode(), OCCURRENCE, requirement.requirementName(),
                requirement.mediaType(), requirement.required(), requirement.minCount(),
                requirement.maxCount(), requirement.conditionInputDigest(),
                requirement.resolutionExplanationJson(), requirement.requirementDefinitionJson(),
                requirement.requirementDigest(), requirement.minCount() > 0 ? "MISSING" : "SATISFIED",
                now, slotGeneration, supersedesSlotId, resolutionId, resolutionGeneration,
                true, transition, "NONE");
    }

    private boolean requiresFormFacts(ConfigurationAssetDefinition template) {
        try {
            JsonNode definition = objectMapper.readTree(template.definitionJson());
            for (JsonNode item : definition.path("items")) {
                if (item.path("requiredWhen").path("source").asText().contains("formValues")) {
                    return true;
                }
            }
            return false;
        } catch (JacksonException exception) {
            throw new IllegalStateException("已发布 EvidenceTemplate 不是合法 JSON", exception);
        }
    }

    private Map<String, Object> conditionExplanation(ResolvedEvidenceCondition condition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "CONDITIONAL");
        result.put("result", false);
        result.put("resolverVersion", EvidenceTemplateResolver.VERSION);
        result.put("expression", condition.expression());
        result.put("bindings", condition.bindings());
        result.put("evidenceKey", condition.evidenceKey());
        return result;
    }

    private ExpressionContext expressionContext(
            String tenantId,
            TaskFulfillmentContext task,
            Map<String, Object> formValues
    ) {
        WorkOrderExpressionContext workOrder = workOrders.find(tenantId, task.workOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "条件重解析缺少权威 WorkOrder: " + task.workOrderId()));
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext(
                        workOrder.clientCode(), workOrder.brandCode(), workOrder.serviceProductCode()),
                new ExpressionContext.RegionContext(
                        workOrder.provinceCode(), workOrder.cityCode(), workOrder.districtCode()),
                new ExpressionContext.TaskContext(task.stageCode(), task.taskType()), formValues);
    }

    /**
     * 同一 generation 的所有条件必须共享同一份权威上下文；延迟加载同时保证固定模板不会
     * 产生无业务必要的跨模块读取。AtomicReference 仅用于 Supplier 可能被多个模板调用的缓存，
     * generation 本身仍由数据库 advisory lock 串行化。
     */
    private Supplier<ExpressionContext> memoizedExpressionContext(
            String tenantId,
            TaskFulfillmentContext task,
            Map<String, Object> formValues
    ) {
        AtomicReference<ExpressionContext> cached = new AtomicReference<>();
        return () -> {
            ExpressionContext current = cached.get();
            if (current != null) {
                return current;
            }
            ExpressionContext loaded = expressionContext(tenantId, task, formValues);
            cached.compareAndSet(null, loaded);
            return cached.get();
        };
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Evidence resolution generation 无法序列化", exception);
        }
    }

    private record RequirementKey(UUID templateVersionId, String requirementCode, String occurrenceKey) {
    }
}
