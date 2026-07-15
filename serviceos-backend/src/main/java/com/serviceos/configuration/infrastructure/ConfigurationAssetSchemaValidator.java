package com.serviceos.configuration.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationPublicationException;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluationException;
import com.serviceos.configuration.api.ExpressionEvaluator;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
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
                    expressions.validate(new ExpressionDefinition(
                            requiredWhen.path("language").asText(),
                            requiredWhen.path("source").asText()));
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
