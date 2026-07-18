package com.serviceos.configuration.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationPublicationException;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluationException;
import com.serviceos.configuration.api.ExpressionEvaluator;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 已发布配置资产的结构门禁。
 *
 * <p>Schema 与解释器版本必须显式登记。未知版本一律拒绝，避免旧节点把无法理解的配置
 * 当作普通 JSON 持久化。M268 起 WORKFLOW 增加条件边与 EXCLUSIVE_GATEWAY 出边的语义校验；
 * 完整 Workflow JSON Schema 结构门禁仍待夹具与历史定义对齐后启用。</p>
 */
@Component
public final class ConfigurationAssetSchemaValidator {
    private static final String FORM_SCHEMA_VERSION = "1.0.0";
    private static final String EVIDENCE_SCHEMA_VERSION = "1.0.0";
    private static final String SLA_SCHEMA_VERSION = "1.0.0";
    private static final String RULE_SCHEMA_VERSION = "1.0.0";
    private static final String DISPATCH_SCHEMA_VERSION = "1.0.0";
    private static final String NOTIFICATION_SCHEMA_VERSION = "1.0.0";
    private static final String ASSIGNEE_POLICY_SCHEMA_VERSION = "1.0.0";
    private static final String INTEGRATION_SCHEMA_VERSION = "1.0.0";
    private static final String PRICING_SCHEMA_VERSION = "1.0.0";
    private static final Set<ConfigurationAssetType> SCHEMA_GOVERNED_TYPES = Set.of(
            ConfigurationAssetType.FORM,
            ConfigurationAssetType.EVIDENCE,
            ConfigurationAssetType.SLA,
            ConfigurationAssetType.RULE,
            ConfigurationAssetType.DISPATCH,
            ConfigurationAssetType.NOTIFICATION,
            ConfigurationAssetType.ASSIGNEE_POLICY,
            ConfigurationAssetType.INTEGRATION,
            ConfigurationAssetType.PRICING);

    private final ObjectMapper objectMapper;
    private final Map<SchemaKey, JsonSchema> schemas;
    private final ExpressionEvaluator expressions;

    ConfigurationAssetSchemaValidator() {
        // networknt 1.x 使用 Jackson 2；与 Spring Boot 4 的 Jackson 3 HTTP 映射保持隔离。
        this(new ObjectMapper(), new ServiceOsExprV1Evaluator());
    }

    ConfigurationAssetSchemaValidator(ObjectMapper objectMapper) {
        this(objectMapper, new ServiceOsExprV1Evaluator());
    }

    ConfigurationAssetSchemaValidator(ObjectMapper objectMapper, ExpressionEvaluator expressions) {
        this.objectMapper = objectMapper;
        this.expressions = expressions;
        Map<SchemaKey, JsonSchema> loaded = new LinkedHashMap<>();
        loaded.put(new SchemaKey(ConfigurationAssetType.FORM, FORM_SCHEMA_VERSION),
                loadSchema("configuration-schemas/form-v1.schema.json"));
        loaded.put(new SchemaKey(ConfigurationAssetType.EVIDENCE, EVIDENCE_SCHEMA_VERSION),
                loadSchema("configuration-schemas/evidence-v1.schema.json"));
        loaded.put(new SchemaKey(ConfigurationAssetType.SLA, SLA_SCHEMA_VERSION),
                loadSchema("configuration-schemas/sla-v1.schema.json"));
        loaded.put(new SchemaKey(ConfigurationAssetType.RULE, RULE_SCHEMA_VERSION),
                loadSchema("configuration-schemas/rule-v1.schema.json"));
        loaded.put(new SchemaKey(ConfigurationAssetType.DISPATCH, DISPATCH_SCHEMA_VERSION),
                loadSchema("configuration-schemas/dispatch-v1.schema.json"));
        loaded.put(new SchemaKey(ConfigurationAssetType.NOTIFICATION, NOTIFICATION_SCHEMA_VERSION),
                loadSchema("configuration-schemas/notification-v1.schema.json"));
        loaded.put(new SchemaKey(ConfigurationAssetType.ASSIGNEE_POLICY, ASSIGNEE_POLICY_SCHEMA_VERSION),
                loadSchema("configuration-schemas/assignee-policy-v1.schema.json"));
        loaded.put(new SchemaKey(ConfigurationAssetType.INTEGRATION, INTEGRATION_SCHEMA_VERSION),
                loadSchema("configuration-schemas/integration-v1.schema.json"));
        loaded.put(new SchemaKey(ConfigurationAssetType.PRICING, PRICING_SCHEMA_VERSION),
                loadSchema("configuration-schemas/pricing-v1.schema.json"));
        this.schemas = Map.copyOf(loaded);
    }

    public void validate(PublishConfigurationAssetCommand command) {
        if (command.assetType() == ConfigurationAssetType.WORKFLOW) {
            validateWorkflowSemantics(parse(command.definitionJson()), command.assetKey());
            return;
        }
        if (!SCHEMA_GOVERNED_TYPES.contains(command.assetType())) {
            return;
        }
        JsonSchema schema = schemas.get(new SchemaKey(command.assetType(), command.schemaVersion()));
        if (schema == null) {
            throw new ConfigurationPublicationException(
                    "unsupported " + command.assetType() + " schemaVersion: " + command.schemaVersion());
        }

        JsonNode definition = parse(command.definitionJson());
        Set<ValidationMessage> errors = schema.validate(definition);
        if (!errors.isEmpty()) {
            String summary = errors.stream()
                    .sorted(Comparator.comparing(ValidationMessage::getMessage))
                    .limit(10)
                    .map(ValidationMessage::getMessage)
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("unknown schema violation");
            throw new ConfigurationPublicationException(
                    command.assetType() + " definition violates schema: " + summary);
        }

        // 命令身份与定义身份必须完全一致，防止同一内容被伪装成另一稳定资产或版本。
        String identityField = switch (command.assetType()) {
            case FORM -> "formKey";
            case EVIDENCE -> "templateKey";
            case SLA, DISPATCH, NOTIFICATION, ASSIGNEE_POLICY -> "policyKey";
            case RULE -> "ruleKey";
            case INTEGRATION -> "mappingKey";
            case PRICING -> "pricingKey";
            default -> throw new IllegalStateException("schema-governed asset type has no identity field");
        };
        if (!command.assetKey().equals(definition.path(identityField).asText())) {
            throw new ConfigurationPublicationException(
                    command.assetType() + " assetKey must equal definition " + identityField);
        }
        if (!command.semanticVersion().equals(definition.path("version").asText())) {
            throw new ConfigurationPublicationException(
                    command.assetType() + " semanticVersion must equal definition version");
        }
        if (command.assetType() == ConfigurationAssetType.EVIDENCE) {
            validateEvidenceSemantics(definition);
        } else if (command.assetType() == ConfigurationAssetType.FORM) {
            validateFormSemantics(definition);
        } else if (command.assetType() == ConfigurationAssetType.RULE) {
            validateRuleSemantics(definition);
        } else if (command.assetType() == ConfigurationAssetType.DISPATCH) {
            validateDispatchSemantics(definition);
        } else if (command.assetType() == ConfigurationAssetType.NOTIFICATION) {
            validateNotificationSemantics(definition);
        } else if (command.assetType() == ConfigurationAssetType.ASSIGNEE_POLICY) {
            validateAssigneePolicySemantics(definition);
        } else if (command.assetType() == ConfigurationAssetType.INTEGRATION) {
            validateIntegrationSemantics(definition);
        } else if (command.assetType() == ConfigurationAssetType.PRICING) {
            validatePricingSemantics(definition);
        }
    }

    /**
     * EVIDENCE 对表单字段的引用只能在 Bundle 依赖闭包中证明。以 stage 作为当前 Schema 中唯一
     * 明确关联键；同一 stage 没有 FORM 或存在多个 FORM 时拒绝含 formValues 的资料条件，避免
     * 运行时猜测应该读取哪个表单版本。
     */
    void validateBundle(List<ConfigurationAssetDefinition> assets) {
        Map<String, List<JsonNode>> formsByStage = new LinkedHashMap<>();
        for (ConfigurationAssetDefinition asset : assets) {
            if (asset.assetType() == ConfigurationAssetType.FORM) {
                JsonNode form = parse(asset.definitionJson());
                formsByStage.computeIfAbsent(form.path("stage").asText(), ignored -> new java.util.ArrayList<>())
                        .add(form);
            }
        }
        for (ConfigurationAssetDefinition asset : assets) {
            if (asset.assetType() != ConfigurationAssetType.EVIDENCE) {
                continue;
            }
            JsonNode evidence = parse(asset.definitionJson());
            String stage = evidence.path("stage").asText();
            List<JsonNode> stageForms = formsByStage.getOrDefault(stage, List.of());
            for (JsonNode item : evidence.path("items")) {
                JsonNode requiredWhen = item.path("requiredWhen");
                if (!item.has("requiredWhen") || requiredWhen.isNull()) {
                    continue;
                }
                ExpressionDefinition expression = expression(requiredWhen);
                if (expression.source().contains("formValues")) {
                    if (stageForms.size() != 1) {
                        throw new ConfigurationPublicationException(
                                "EVIDENCE 表单条件要求同一 stage 恰好一个 FORM: "
                                        + stage + "; 实际=" + stageForms.size());
                    }
                    validateExpression(expression, fieldTypes(stageForms.getFirst()),
                            "EVIDENCE requiredWhen", item.path("evidenceKey").asText());
                } else {
                    validateExpression(expression, Map.of(),
                            "EVIDENCE requiredWhen", item.path("evidenceKey").asText());
                }
            }
        }
        validateWorkflowSlaReferences(assets);
        for (ConfigurationAssetDefinition asset : assets) {
            if (asset.assetType() == ConfigurationAssetType.WORKFLOW) {
                validateWorkflowSemantics(parse(asset.definitionJson()), asset.assetKey());
            }
        }
        validateSubProcessReferences(assets);
    }

    /** SUB_PROCESS.subProcessRef 必须在同一 Bundle 精确命中另一个 WORKFLOW assetKey。 */
    private void validateSubProcessReferences(List<ConfigurationAssetDefinition> assets) {
        Set<String> workflowKeys = new HashSet<>();
        for (ConfigurationAssetDefinition asset : assets) {
            if (asset.assetType() == ConfigurationAssetType.WORKFLOW) {
                workflowKeys.add(asset.assetKey());
            }
        }
        for (ConfigurationAssetDefinition asset : assets) {
            if (asset.assetType() != ConfigurationAssetType.WORKFLOW) {
                continue;
            }
            JsonNode workflow = parse(asset.definitionJson());
            for (JsonNode node : workflow.path("nodes")) {
                if (!"SUB_PROCESS".equals(node.path("nodeType").asText())) {
                    continue;
                }
                String ref = node.path("subProcessRef").asText();
                if (!workflowKeys.contains(ref)) {
                    throw new ConfigurationPublicationException(
                            "SUB_PROCESS subProcessRef 未在同一 Bundle 命中 WORKFLOW: " + ref);
                }
                if (ref.equals(asset.assetKey())) {
                    throw new ConfigurationPublicationException(
                            "SUB_PROCESS 不得引用自身 Workflow: " + ref);
                }
            }
        }
    }

    /**
     * WORKFLOW 发布期语义：条件边必须是 SERVICEOS_EXPR_V1 对象；EXCLUSIVE_GATEWAY 至少两条
     * 带条件的出边。网关运行时求值由后续里程碑实现，此处只做失败关闭的静态门禁。
     */
    private void validateWorkflowSemantics(JsonNode workflow, String workflowKey) {
        Map<String, JsonNode> nodes = new LinkedHashMap<>();
        for (JsonNode node : workflow.path("nodes")) {
            String nodeId = node.path("nodeId").asText();
            if (nodeId == null || nodeId.isBlank()) {
                throw new ConfigurationPublicationException(
                        "WORKFLOW nodeId 不能为空: " + workflowKey);
            }
            if (nodes.putIfAbsent(nodeId, node) != null) {
                throw new ConfigurationPublicationException(
                        "WORKFLOW nodeId 必须唯一: " + nodeId);
            }
        }
        Map<String, List<JsonNode>> outgoing = new LinkedHashMap<>();
        Map<String, List<JsonNode>> incoming = new LinkedHashMap<>();
        for (JsonNode transition : workflow.path("transitions")) {
            String from = transition.path("from").asText();
            String to = transition.path("to").asText();
            if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
                throw new ConfigurationPublicationException(
                        "WORKFLOW transition 引用未知节点: " + from + " -> " + to);
            }
            validateTransitionCondition(transition, from);
            outgoing.computeIfAbsent(from, ignored -> new java.util.ArrayList<>()).add(transition);
            incoming.computeIfAbsent(to, ignored -> new java.util.ArrayList<>()).add(transition);
        }
        for (Map.Entry<String, JsonNode> entry : nodes.entrySet()) {
            String nodeType = entry.getValue().path("nodeType").asText();
            if ("EXCLUSIVE_GATEWAY".equals(nodeType)) {
                List<JsonNode> edges = outgoing.getOrDefault(entry.getKey(), List.of());
                if (edges.size() < 2) {
                    throw new ConfigurationPublicationException(
                            "EXCLUSIVE_GATEWAY 至少需要两条出边: " + entry.getKey());
                }
                for (JsonNode edge : edges) {
                    if (!present(edge, "condition")) {
                        throw new ConfigurationPublicationException(
                                "EXCLUSIVE_GATEWAY 出边必须带 condition: " + entry.getKey());
                    }
                }
            } else if ("WAIT_EVENT".equals(nodeType)) {
                validateWaitEventNode(entry.getValue(), entry.getKey(), outgoing);
            } else if ("TIMER".equals(nodeType)) {
                validateTimerNode(entry.getValue(), entry.getKey(), outgoing);
            } else if ("SUB_PROCESS".equals(nodeType)) {
                validateSubProcessNode(entry.getValue(), entry.getKey(), outgoing);
            } else if ("PARALLEL_GATEWAY".equals(nodeType)) {
                validateParallelGatewayNode(entry.getKey(), outgoing, incoming, nodes);
            } else if (Set.of("USER_TASK", "SERVICE_TASK", "REVIEW_TASK", "MANUAL_INTERVENTION")
                    .contains(nodeType)) {
                validateTaskMultiInstance(entry.getValue(), entry.getKey(), outgoing);
                validateTaskCompensation(entry.getValue(), entry.getKey());
            } else if (present(entry.getValue(), "compensation")) {
                throw new ConfigurationPublicationException(
                        "compensation 仅允许声明在任务节点上: " + entry.getKey());
            }
        }
    }

    private void validateTaskCompensation(JsonNode node, String nodeId) {
        if (!present(node, "compensation")) {
            return;
        }
        JsonNode taskType = node.path("compensation").path("taskType");
        if (!taskType.isTextual() || taskType.asText().isBlank()) {
            throw new ConfigurationPublicationException(
                    "compensation.taskType 不得为空: " + nodeId);
        }
    }

    private void validateTaskMultiInstance(
            JsonNode node,
            String nodeId,
            Map<String, List<JsonNode>> outgoing
    ) {
        if (!present(node, "multiInstance")) {
            return;
        }
        JsonNode cardinality = node.path("multiInstance").path("cardinality");
        if (!cardinality.isIntegralNumber() || cardinality.asInt() < 2 || cardinality.asInt() > 50) {
            throw new ConfigurationPublicationException(
                    "multiInstance.cardinality 必须在 2～50: " + nodeId);
        }
        List<JsonNode> edges = outgoing.getOrDefault(nodeId, List.of());
        if (edges.size() != 1 || present(edges.getFirst(), "condition")) {
            throw new ConfigurationPublicationException(
                    "多实例任务必须恰好一条无条件出边: " + nodeId);
        }
    }

    private void validateSubProcessNode(JsonNode node, String nodeId, Map<String, List<JsonNode>> outgoing) {
        if (!present(node, "subProcessRef")) {
            throw new ConfigurationPublicationException(
                    "SUB_PROCESS 必须声明 subProcessRef: " + nodeId);
        }
        if (!present(node, "stageCode")) {
            throw new ConfigurationPublicationException(
                    "SUB_PROCESS 必须声明 stageCode: " + nodeId);
        }
        List<JsonNode> edges = outgoing.getOrDefault(nodeId, List.of());
        if (edges.size() != 1 || present(edges.getFirst(), "condition")) {
            throw new ConfigurationPublicationException(
                    "SUB_PROCESS 必须恰好一条无条件出边: " + nodeId);
        }
    }

    private void validateTimerNode(JsonNode node, String nodeId, Map<String, List<JsonNode>> outgoing) {
        JsonNode duration = node.get("durationSeconds");
        if (duration == null || duration.isNull() || !duration.isIntegralNumber() || duration.asInt() < 1) {
            throw new ConfigurationPublicationException(
                    "TIMER 必须声明正整数 durationSeconds: " + nodeId);
        }
        if (!present(node, "stageCode")) {
            throw new ConfigurationPublicationException(
                    "TIMER 必须声明 stageCode: " + nodeId);
        }
        List<JsonNode> edges = outgoing.getOrDefault(nodeId, List.of());
        if (edges.size() != 1 || present(edges.getFirst(), "condition")) {
            throw new ConfigurationPublicationException(
                    "TIMER 必须恰好一条无条件出边: " + nodeId);
        }
    }

    private void validateParallelGatewayNode(
            String nodeId,
            Map<String, List<JsonNode>> outgoing,
            Map<String, List<JsonNode>> incoming,
            Map<String, JsonNode> nodes
    ) {
        List<JsonNode> outs = outgoing.getOrDefault(nodeId, List.of());
        List<JsonNode> ins = incoming.getOrDefault(nodeId, List.of());
        boolean fork = outs.size() >= 2;
        boolean join = ins.size() >= 2;
        if (fork && join) {
            throw new ConfigurationPublicationException(
                    "PARALLEL_GATEWAY 不能同时作为 fork 与 join: " + nodeId);
        }
        if (!fork && !join) {
            throw new ConfigurationPublicationException(
                    "PARALLEL_GATEWAY 必须是 fork(≥2 出边) 或 join(≥2 入边): " + nodeId);
        }
        if (fork) {
            for (JsonNode edge : outs) {
                if (present(edge, "condition")) {
                    throw new ConfigurationPublicationException(
                            "PARALLEL fork 出边必须无条件: " + nodeId);
                }
            }
            String sharedStage = null;
            for (JsonNode edge : outs) {
                JsonNode target = nodes.get(edge.path("to").asText());
                String targetType = target.path("nodeType").asText();
                if (!Set.of("USER_TASK", "SERVICE_TASK", "REVIEW_TASK", "MANUAL_INTERVENTION",
                        "WAIT_EVENT", "TIMER").contains(targetType)) {
                    throw new ConfigurationPublicationException(
                            "PARALLEL fork 目标必须是任务/WAIT_EVENT/TIMER: " + edge.path("to").asText());
                }
                String stage = target.path("stageCode").asText();
                if (stage == null || stage.isBlank()) {
                    throw new ConfigurationPublicationException(
                            "PARALLEL fork 目标必须声明 stageCode: " + edge.path("to").asText());
                }
                if (sharedStage == null) {
                    sharedStage = stage;
                } else if (!sharedStage.equals(stage)) {
                    throw new ConfigurationPublicationException(
                            "PARALLEL fork 分支必须共享 stageCode: " + nodeId);
                }
            }
        }
        if (join) {
            if (outs.size() != 1 || present(outs.getFirst(), "condition")) {
                throw new ConfigurationPublicationException(
                        "PARALLEL join 必须恰好一条无条件出边: " + nodeId);
            }
        }
    }

    private void validateWaitEventNode(
            JsonNode node,
            String nodeId,
            Map<String, List<JsonNode>> outgoing
    ) {
        if (!present(node, "waitEventType")) {
            throw new ConfigurationPublicationException(
                    "WAIT_EVENT 必须声明 waitEventType: " + nodeId);
        }
        if (!present(node, "correlationKeyTemplate")) {
            throw new ConfigurationPublicationException(
                    "WAIT_EVENT 必须声明 correlationKeyTemplate: " + nodeId);
        }
        if (!present(node, "stageCode")) {
            throw new ConfigurationPublicationException(
                    "WAIT_EVENT 必须声明 stageCode: " + nodeId);
        }
        String template = node.path("correlationKeyTemplate").asText();
        if (!template.contains("{workOrderId}")
                && !template.contains("{projectId}")
                && !template.contains("{workflowInstanceId}")
                && !template.contains("{tenantId}")) {
            throw new ConfigurationPublicationException(
                    "WAIT_EVENT correlationKeyTemplate 必须包含受支持占位符: " + nodeId);
        }
        List<JsonNode> edges = outgoing.getOrDefault(nodeId, List.of());
        if (edges.size() != 1 || present(edges.getFirst(), "condition")) {
            throw new ConfigurationPublicationException(
                    "WAIT_EVENT 必须恰好一条无条件出边: " + nodeId);
        }
    }

    private void validateTransitionCondition(JsonNode transition, String from) {
        if (!transition.has("condition") || transition.path("condition").isNull()) {
            return;
        }
        JsonNode condition = transition.path("condition");
        if (!condition.isObject()) {
            throw new ConfigurationPublicationException(
                    "WORKFLOW condition 必须是 SERVICEOS_EXPR_V1 对象: " + from);
        }
        validateExpression(expression(condition), Map.of(),
                "WORKFLOW transition condition", from);
    }

    /** Workflow 的 slaRef 必须在同一冻结 Bundle 中精确命中 SLA，并显式覆盖该节点 taskType。 */
    private void validateWorkflowSlaReferences(List<ConfigurationAssetDefinition> assets) {
        Map<String, List<JsonNode>> slaByKey = new LinkedHashMap<>();
        assets.stream().filter(asset -> asset.assetType() == ConfigurationAssetType.SLA).forEach(asset ->
                slaByKey.computeIfAbsent(asset.assetKey(), ignored -> new java.util.ArrayList<>())
                        .add(parse(asset.definitionJson())));
        for (ConfigurationAssetDefinition asset : assets) {
            if (asset.assetType() != ConfigurationAssetType.WORKFLOW) {
                continue;
            }
            JsonNode workflow = parse(asset.definitionJson());
            for (JsonNode node : workflow.path("nodes")) {
                if (!present(node, "slaRef")) {
                    continue;
                }
                String slaRef = node.path("slaRef").asText();
                List<JsonNode> matches = slaByKey.getOrDefault(slaRef, List.of());
                if (matches.size() != 1) {
                    throw new ConfigurationPublicationException(
                            "WORKFLOW slaRef 必须在同一 Bundle 精确命中一个 SLA: " + slaRef);
                }
                String taskType = node.path("taskType").asText();
                boolean applies = false;
                for (JsonNode configuredTaskType : matches.getFirst().path("taskTypes")) {
                    if (taskType.equals(configuredTaskType.asText())) {
                        applies = true;
                        break;
                    }
                }
                if (!applies) {
                    throw new ConfigurationPublicationException(
                            "WORKFLOW slaRef 未显式覆盖 taskType: " + slaRef + "/" + taskType);
                }
            }
        }
    }

    /**
     * JSON Schema 无法比较同一对象内的两个数值，也不能按业务键判断数组重复；这些仍属于
     * 确定性的发布期结构语义，不依赖尚未批准的条件表达式运行时。
     */
    private void validateEvidenceSemantics(JsonNode definition) {
        Set<String> evidenceKeys = new HashSet<>();
        for (JsonNode item : definition.path("items")) {
            String evidenceKey = item.path("evidenceKey").asText();
            if (!evidenceKeys.add(evidenceKey)) {
                throw new ConfigurationPublicationException(
                        "EVIDENCE evidenceKey must be unique: " + evidenceKey);
            }
            JsonNode capture = item.path("capture");
            JsonNode requiredWhen = item.path("requiredWhen");
            boolean conditional = item.has("requiredWhen") && !requiredWhen.isNull();
            if (conditional) {
                try {
                    expressions.validate(expression(requiredWhen));
                } catch (ExpressionEvaluationException exception) {
                    throw new ConfigurationPublicationException(
                            "EVIDENCE requiredWhen 表达式无效: " + evidenceKey
                                    + "; " + exception.getMessage());
                }
            }
            if (item.path("required").asBoolean()
                    && capture.has("minCount") && capture.path("minCount").asInt() == 0) {
                throw new ConfigurationPublicationException(
                        "EVIDENCE required item minCount must be greater than zero: " + evidenceKey);
            }
            // 条件命中后该要求会成为必填，因此显式 minCount=0 同样是自相矛盾配置。
            if (conditional && capture.has("minCount") && capture.path("minCount").asInt() == 0) {
                throw new ConfigurationPublicationException(
                        "EVIDENCE conditional item minCount must be greater than zero: " + evidenceKey);
            }
            if (capture.has("minCount") && capture.has("maxCount")
                    && capture.path("minCount").asInt() > capture.path("maxCount").asInt()) {
                throw new ConfigurationPublicationException(
                        "EVIDENCE capture minCount must not exceed maxCount: " + evidenceKey);
            }
        }
    }

    private void validateFormSemantics(JsonNode definition) {
        Map<String, String> types = fieldTypes(definition);
        for (JsonNode section : definition.path("sections")) {
            validateOptionalExpression(section, "visibility", types,
                    "FORM section visibility", section.path("sectionKey").asText());
            for (JsonNode field : section.path("fields")) {
                String fieldKey = field.path("fieldKey").asText();
                validateOptionalExpression(field, "requiredWhen", types,
                        "FORM requiredWhen", fieldKey);
                validateOptionalExpression(field, "visibleWhen", types,
                        "FORM visibleWhen", fieldKey);
                if (present(field, "editableWhen") || present(field, "defaultExpression")) {
                    throw new ConfigurationPublicationException(
                            "FORM 尚未接受 editableWhen/defaultExpression 运行时: " + fieldKey);
                }
                if (field.path("validators").isArray() && !field.path("validators").isEmpty()) {
                    throw new ConfigurationPublicationException(
                            "FORM validators 参数语义尚未接受: " + fieldKey);
                }
            }
        }
        for (JsonNode rule : definition.path("validationRules")) {
            String ruleKey = rule.path("ruleKey").asText();
            validateExpression(expression(rule.path("assert")), types,
                    "FORM validationRule", ruleKey);
        }
    }

    /** RULE：ruleCode 唯一，when 表达式必须通过 SERVICEOS_EXPR_V1 静态校验。 */
    private void validateRuleSemantics(JsonNode definition) {
        Set<String> codes = new HashSet<>();
        for (JsonNode item : definition.path("rules")) {
            String ruleCode = item.path("ruleCode").asText();
            if (!codes.add(ruleCode)) {
                throw new ConfigurationPublicationException("RULE ruleCode must be unique: " + ruleCode);
            }
            try {
                expressions.validate(expression(item.path("when")));
            } catch (ExpressionEvaluationException exception) {
                throw new ConfigurationPublicationException(
                        "RULE when 表达式无效: " + ruleCode + "; " + exception.getMessage());
            }
        }
    }

    /** DISPATCH：filterKey/factorKey 唯一，表达式静态校验。 */
    private void validateDispatchSemantics(JsonNode definition) {
        Set<String> filters = new HashSet<>();
        for (JsonNode filter : definition.path("hardFilters")) {
            String filterKey = filter.path("filterKey").asText() + "#" + filter.path("order").asInt();
            if (!filters.add(filterKey)) {
                throw new ConfigurationPublicationException(
                        "DISPATCH hardFilters order/filterKey 必须唯一: " + filterKey);
            }
            try {
                expressions.validate(expression(filter.path("expression")));
            } catch (ExpressionEvaluationException exception) {
                throw new ConfigurationPublicationException(
                        "DISPATCH hardFilter 表达式无效: " + filterKey + "; " + exception.getMessage());
            }
        }
        Set<String> factors = new HashSet<>();
        for (JsonNode factor : definition.path("scoring")) {
            String factorKey = factor.path("factorKey").asText();
            if (!factors.add(factorKey)) {
                throw new ConfigurationPublicationException(
                        "DISPATCH scoring factorKey must be unique: " + factorKey);
            }
            try {
                expressions.validate(expression(factor.path("expression")));
            } catch (ExpressionEvaluationException exception) {
                throw new ConfigurationPublicationException(
                        "DISPATCH scoring 表达式无效: " + factorKey + "; " + exception.getMessage());
            }
        }
    }

    private void validateNotificationSemantics(JsonNode definition) {
        Set<String> keys = new HashSet<>();
        for (JsonNode trigger : definition.path("triggers")) {
            String triggerKey = trigger.path("triggerKey").asText();
            if (!keys.add(triggerKey)) {
                throw new ConfigurationPublicationException(
                        "NOTIFICATION triggerKey must be unique: " + triggerKey);
            }
            try {
                expressions.validate(expression(trigger.path("when")));
            } catch (ExpressionEvaluationException exception) {
                throw new ConfigurationPublicationException(
                        "NOTIFICATION when 表达式无效: " + triggerKey + "; " + exception.getMessage());
            }
        }
    }

    private void validateAssigneePolicySemantics(JsonNode definition) {
        Set<String> keys = new HashSet<>();
        for (JsonNode strategy : definition.path("strategies")) {
            String strategyKey = strategy.path("strategyKey").asText();
            if (!keys.add(strategyKey)) {
                throw new ConfigurationPublicationException(
                        "ASSIGNEE_POLICY strategyKey must be unique: " + strategyKey);
            }
            try {
                expressions.validate(expression(strategy.path("when")));
            } catch (ExpressionEvaluationException exception) {
                throw new ConfigurationPublicationException(
                        "ASSIGNEE_POLICY when 表达式无效: " + strategyKey + "; " + exception.getMessage());
            }
        }
    }

    private void validateIntegrationSemantics(JsonNode definition) {
        Set<String> ids = new HashSet<>();
        for (JsonNode mapping : definition.path("fieldMappings")) {
            String mappingId = mapping.path("mappingId").asText();
            if (!ids.add(mappingId)) {
                throw new ConfigurationPublicationException(
                        "INTEGRATION mappingId must be unique: " + mappingId);
            }
        }
    }

    private void validatePricingSemantics(JsonNode definition) {
        Set<String> keys = new HashSet<>();
        for (JsonNode line : definition.path("lines")) {
            String lineKey = line.path("lineKey").asText();
            if (!keys.add(lineKey)) {
                throw new ConfigurationPublicationException(
                        "PRICING lineKey must be unique: " + lineKey);
            }
            try {
                expressions.validate(expression(line.path("when")));
            } catch (ExpressionEvaluationException exception) {
                throw new ConfigurationPublicationException(
                        "PRICING when 表达式无效: " + lineKey + "; " + exception.getMessage());
            }
        }
    }

    private Map<String, String> fieldTypes(JsonNode definition) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode section : definition.path("sections")) {
            for (JsonNode field : section.path("fields")) {
                String key = field.path("fieldKey").asText();
                if (result.putIfAbsent(key, field.path("dataType").asText()) != null) {
                    throw new ConfigurationPublicationException("FORM fieldKey 必须唯一: " + key);
                }
            }
        }
        return Map.copyOf(result);
    }

    private void validateOptionalExpression(
            JsonNode parent,
            String field,
            Map<String, String> types,
            String kind,
            String ownerKey
    ) {
        if (present(parent, field)) {
            validateExpression(expression(parent.path(field)), types, kind, ownerKey);
        }
    }

    private void validateExpression(
            ExpressionDefinition expression,
            Map<String, String> types,
            String kind,
            String ownerKey
    ) {
        try {
            expressions.validate(expression, types);
        } catch (ExpressionEvaluationException exception) {
            throw new ConfigurationPublicationException(
                    kind + " 表达式无效: " + ownerKey + "; " + exception.getMessage());
        }
    }

    private static ExpressionDefinition expression(JsonNode node) {
        return new ExpressionDefinition(
                node.path("language").asText(), node.path("source").asText());
    }

    private static boolean present(JsonNode parent, String field) {
        return parent.has(field) && !parent.path(field).isNull();
    }

    private JsonNode parse(String definitionJson) {
        try {
            return objectMapper.readTree(definitionJson);
        } catch (JsonProcessingException exception) {
            throw new ConfigurationPublicationException("configuration definition is not valid JSON");
        }
    }

    private JsonSchema loadSchema(String classpathLocation) {
        try (var input = new ClassPathResource(classpathLocation).getInputStream()) {
            JsonNode schemaNode = objectMapper.readTree(input);
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(schemaNode);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "cannot load configuration schema " + classpathLocation, exception);
        }
    }

    private record SchemaKey(ConfigurationAssetType assetType, String schemaVersion) {
    }
}
