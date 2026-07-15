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
 * 当作普通 JSON 持久化。表达式的静态类型和执行语义属于后续独立门禁；本类不猜测
 * {@code SERVICEOS_EXPR_V1} 的含义。</p>
 */
@Component
final class ConfigurationAssetSchemaValidator {
    private static final String FORM_SCHEMA_VERSION = "1.0.0";
    private static final String EVIDENCE_SCHEMA_VERSION = "1.0.0";
    private static final Set<ConfigurationAssetType> SCHEMA_GOVERNED_TYPES = Set.of(
            ConfigurationAssetType.FORM, ConfigurationAssetType.EVIDENCE);

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
        this.schemas = Map.of(
                new SchemaKey(ConfigurationAssetType.FORM, FORM_SCHEMA_VERSION),
                loadSchema("configuration-schemas/form-v1.schema.json"),
                new SchemaKey(ConfigurationAssetType.EVIDENCE, EVIDENCE_SCHEMA_VERSION),
                loadSchema("configuration-schemas/evidence-v1.schema.json"));
    }

    void validate(PublishConfigurationAssetCommand command) {
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
