package com.serviceos.configuration.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ConfigurationTaskTemplateItem;
import com.serviceos.configuration.api.ConfigurationTaskTemplateQuery;
import com.serviceos.identity.api.CurrentPrincipal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 从 WORKFLOW 已发布版本与在编草稿投影任务模板。
 *
 * <p>不发明第二套 TaskTemplate 写聚合；产品页据此回答“谁执行、用什么表单/资料/SLA、被哪些流程引用”。</p>
 */
@Service
final class DefaultConfigurationTaskTemplateQuery implements ConfigurationTaskTemplateQuery {
    private static final String CAPABILITY = "configuration.draft.write";
    private static final Set<String> TASK_NODE_TYPES = Set.of(
            "USER_TASK", "SERVICE_TASK", "REVIEW_TASK", "MANUAL_INTERVENTION");

    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final ObjectMapper objectMapper;

    DefaultConfigurationTaskTemplateQuery(
            JdbcClient jdbc,
            AuthorizationService authorization,
            ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConfigurationTaskTemplateItem> list(CurrentPrincipal principal, String correlationId) {
        authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        CAPABILITY, principal.tenantId(), "ConfigurationTaskTemplate", "*"),
                correlationId);

        Map<String, MutableTemplate> templates = new LinkedHashMap<>();
        for (WorkflowSource source : loadPublishedWorkflows(principal.tenantId())) {
            ingest(templates, source, true);
        }
        for (WorkflowSource source : loadDraftWorkflows(principal.tenantId())) {
            ingest(templates, source, false);
        }
        return templates.values().stream()
                .map(MutableTemplate::toItem)
                .sorted(Comparator.comparing(ConfigurationTaskTemplateItem::category)
                        .thenComparing(ConfigurationTaskTemplateItem::templateName))
                .toList();
    }

    private void ingest(Map<String, MutableTemplate> templates, WorkflowSource source, boolean published) {
        Map<String, Object> definition = parse(source.definitionJson());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.getOrDefault(
                "nodes", List.of());
        String workflowName = displayWorkflowName(source.assetKey(), definition);
        for (Map<String, Object> node : nodes) {
            String nodeType = stringVal(node.get("nodeType"));
            if (!TASK_NODE_TYPES.contains(nodeType)) {
                continue;
            }
            String taskType = firstNonBlank(stringVal(node.get("taskType")), stringVal(node.get("nodeId")));
            if (taskType == null || taskType.isBlank()) {
                continue;
            }
            String key = taskType.toUpperCase(Locale.ROOT);
            MutableTemplate template = templates.computeIfAbsent(key, ignored -> new MutableTemplate(key));
            template.merge(node, workflowName, published, source.updatedAt());
        }
    }

    private List<WorkflowSource> loadPublishedWorkflows(String tenantId) {
        return jdbc.sql("""
                SELECT asset_key, definition::text AS definition_json, published_at AS updated_at
                  FROM cfg_configuration_asset_version
                 WHERE tenant_id = :tenantId
                   AND asset_type = 'WORKFLOW'
                   AND status = 'PUBLISHED'
                 ORDER BY published_at DESC
                 LIMIT 200
                """)
                .param("tenantId", tenantId)
                .query((rs, n) -> new WorkflowSource(
                        rs.getString("asset_key"),
                        rs.getString("definition_json"),
                        toInstant(rs.getObject("updated_at", OffsetDateTime.class))))
                .list();
    }

    private List<WorkflowSource> loadDraftWorkflows(String tenantId) {
        return jdbc.sql("""
                SELECT asset_key, definition::text AS definition_json, updated_at
                  FROM cfg_configuration_asset_draft
                 WHERE tenant_id = :tenantId
                   AND asset_type = 'WORKFLOW'
                   AND status IN ('DRAFT', 'VALIDATED', 'APPROVED')
                 ORDER BY updated_at DESC
                 LIMIT 200
                """)
                .param("tenantId", tenantId)
                .query((rs, n) -> new WorkflowSource(
                        rs.getString("asset_key"),
                        rs.getString("definition_json"),
                        toInstant(rs.getObject("updated_at", OffsetDateTime.class))))
                .list();
    }

    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String displayWorkflowName(String assetKey, Map<String, Object> definition) {
        Object metadata = definition.get("metadata");
        if (metadata instanceof Map<?, ?> map && map.get("displayName") != null) {
            return String.valueOf(map.get("displayName"));
        }
        if (assetKey == null) {
            return "未命名流程";
        }
        if (assetKey.contains("home-charging") || assetKey.contains("survey")) {
            return "家充勘测安装流程";
        }
        if (assetKey.contains("repair")) {
            return "维修流程";
        }
        return assetKey;
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record WorkflowSource(String assetKey, String definitionJson, Instant updatedAt) {
    }

    private static final class MutableTemplate {
        private final String taskTypeCode;
        private String templateName;
        private String category;
        private String executionRoleLabel;
        private String assignmentStrategyLabel;
        private String formSummary;
        private String evidenceSummary;
        private String slaSummary;
        private boolean published;
        private Instant lastUpdatedAt;
        private final Set<String> workflowNames = new LinkedHashSet<>();
        private final List<String> gaps = new ArrayList<>();

        private MutableTemplate(String taskTypeCode) {
            this.taskTypeCode = taskTypeCode;
            this.category = categorize(taskTypeCode);
            this.templateName = defaultTemplateName(taskTypeCode);
            this.executionRoleLabel = defaultRole(taskTypeCode);
            this.gaps.add("分配策略与升级规则尚未形成独立任务模板资产；当前由流程节点与派单策略共同决定");
        }

        private void merge(
                Map<String, Object> node,
                String workflowName,
                boolean sourcePublished,
                Instant updatedAt
        ) {
            workflowNames.add(workflowName);
            published = published || sourcePublished;
            if (updatedAt != null && (lastUpdatedAt == null || updatedAt.isAfter(lastUpdatedAt))) {
                lastUpdatedAt = updatedAt;
            }
            String name = stringVal(node.get("name"));
            if (name != null && !name.isBlank()) {
                templateName = name.endsWith("任务") || name.endsWith("模板") ? name : name + "任务模板";
            }
            String formRef = stringVal(node.get("formRef"));
            if (formRef != null && !formRef.isBlank()) {
                formSummary = "已绑定表单 " + formRef;
            } else if (formSummary == null) {
                formSummary = "未绑定表单";
            }
            String evidenceRef = stringVal(node.get("evidenceRef"));
            if (evidenceRef != null && !evidenceRef.isBlank()) {
                evidenceSummary = "已绑定资料 " + evidenceRef;
            } else if (evidenceSummary == null) {
                evidenceSummary = "未绑定资料要求";
            }
            String slaRef = stringVal(node.get("slaRef"));
            if (slaRef != null && !slaRef.isBlank()) {
                slaSummary = "已绑定 SLA " + slaRef;
            } else if (slaSummary == null) {
                slaSummary = "未绑定 SLA";
            }
            String assigneePolicyRef = stringVal(node.get("assigneePolicyRef"));
            if (assigneePolicyRef != null && !assigneePolicyRef.isBlank()) {
                assignmentStrategyLabel = "分配策略 " + assigneePolicyRef;
            } else if (assignmentStrategyLabel == null) {
                assignmentStrategyLabel = "待配置分配策略";
            }
        }

        private ConfigurationTaskTemplateItem toItem() {
            return new ConfigurationTaskTemplateItem(
                    taskTypeCode,
                    templateName,
                    taskTypeCode,
                    category,
                    categoryLabel(category),
                    executionRoleLabel,
                    assignmentStrategyLabel,
                    formSummary,
                    evidenceSummary,
                    slaSummary,
                    published ? "PUBLISHED" : "DRAFT",
                    published ? "已发布" : "草稿",
                    workflowNames.size(),
                    List.copyOf(workflowNames),
                    lastUpdatedAt,
                    List.copyOf(gaps));
        }

        private static String categorize(String taskType) {
            String code = taskType.toUpperCase(Locale.ROOT);
            if (code.contains("INTAKE") || code.contains("ACCEPT")) return "INTAKE";
            if (code.contains("DISPATCH") || code.contains("ASSIGN")) return "DISPATCH";
            if (code.contains("APPOINT") || code.contains("CONTACT")) return "APPOINTMENT";
            if (code.contains("SURVEY")) return "SURVEY";
            if (code.contains("INSTALL")) return "INSTALL";
            if (code.contains("REVIEW") || code.contains("AUDIT")) return "REVIEW";
            if (code.contains("CORRECT")) return "CORRECTION";
            if (code.contains("FOLLOW") || code.contains("VISIT")) return "FOLLOW_UP";
            return "SYSTEM";
        }

        private static String categoryLabel(String category) {
            return switch (category) {
                case "INTAKE" -> "受理类";
                case "DISPATCH" -> "派单类";
                case "APPOINTMENT" -> "预约类";
                case "SURVEY" -> "勘测类";
                case "INSTALL" -> "安装类";
                case "REVIEW" -> "审核类";
                case "CORRECTION" -> "整改类";
                case "FOLLOW_UP" -> "回访类";
                default -> "系统任务";
            };
        }

        private static String defaultTemplateName(String taskType) {
            return switch (taskType.toUpperCase(Locale.ROOT)) {
                case "DISPATCH" -> "派单调度任务模板";
                case "SURVEY" -> "上门勘测任务模板";
                case "INSTALL" -> "上门安装任务模板";
                case "REVIEW" -> "资料审核任务模板";
                case "CORRECTION" -> "整改任务模板";
                default -> taskType + " 任务模板";
            };
        }

        private static String defaultRole(String taskType) {
            return switch (categorize(taskType)) {
                case "SURVEY", "INSTALL", "CORRECTION", "FOLLOW_UP" -> "服务师傅";
                case "DISPATCH", "APPOINTMENT", "INTAKE" -> "网点/平台运营";
                case "REVIEW" -> "审核人员";
                default -> "系统";
            };
        }
    }
}
