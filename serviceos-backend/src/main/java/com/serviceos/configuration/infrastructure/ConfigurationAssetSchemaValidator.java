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
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
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

    private final ObjectMapper objectMapper;
    private final Map<SchemaKey, JsonSchema> schemas;

    ConfigurationAssetSchemaValidator() {
        // networknt 1.x 使用 Jackson 2；与 Spring Boot 4 的 Jackson 3 HTTP 映射保持隔离。
        this(new ObjectMapper());
    }

    ConfigurationAssetSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemas = Map.of(
                new SchemaKey(ConfigurationAssetType.FORM, FORM_SCHEMA_VERSION),
                loadSchema("configuration-schemas/form-v1.schema.json"));
    }

    void validate(PublishConfigurationAssetCommand command) {
        if (command.assetType() != ConfigurationAssetType.FORM) {
            return;
        }
        JsonSchema schema = schemas.get(new SchemaKey(command.assetType(), command.schemaVersion()));
        if (schema == null) {
            throw new ConfigurationPublicationException(
                    "unsupported FORM schemaVersion: " + command.schemaVersion());
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
            throw new ConfigurationPublicationException("FORM definition violates schema: " + summary);
        }

        // 命令身份与定义身份必须完全一致，防止同一内容被伪装成另一稳定资产或版本。
        if (!command.assetKey().equals(definition.path("formKey").asText())) {
            throw new ConfigurationPublicationException("FORM assetKey must equal definition formKey");
        }
        if (!command.semanticVersion().equals(definition.path("version").asText())) {
            throw new ConfigurationPublicationException(
                    "FORM semanticVersion must equal definition version");
        }
    }

    private JsonNode parse(String definitionJson) {
        try {
            return objectMapper.readTree(definitionJson);
        } catch (JsonProcessingException exception) {
            throw new ConfigurationPublicationException("FORM definition is not valid JSON");
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
