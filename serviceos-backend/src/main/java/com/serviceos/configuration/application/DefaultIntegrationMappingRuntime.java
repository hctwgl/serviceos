package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.IntegrationMappingApplyCommand;
import com.serviceos.configuration.api.IntegrationMappingResult;
import com.serviceos.configuration.api.IntegrationMappingRuntime;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 冻结 Bundle INTEGRATION Mapping 执行器。
 *
 * <p>禁止任意脚本；Transform 仅 NONE/TRIM/UPPER/LOWER/DATE_ISO。
 * mappingKey 零命中/多命中、必填缺失、未知 Transform 一律失败关闭。</p>
 */
@Service
public class DefaultIntegrationMappingRuntime implements IntegrationMappingRuntime {
    private static final Set<String> ALLOWED_TRANSFORMS = Set.of(
            "NONE", "TRIM", "UPPER", "LOWER", "DATE_ISO");
    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_TIME_T = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    private final ConfigurationService configurations;
    private final ObjectMapper objectMapper;

    public DefaultIntegrationMappingRuntime(ConfigurationService configurations, ObjectMapper objectMapper) {
        this.configurations = configurations;
        this.objectMapper = objectMapper;
    }

    @Override
    public IntegrationMappingResult applyInbound(IntegrationMappingApplyCommand command) {
        Objects.requireNonNull(command, "command");
        List<ConfigurationAssetDefinition> assets = configurations.listBundleAssets(
                command.tenantId(), command.bundleId(), command.expectedManifestDigest(),
                ConfigurationAssetType.INTEGRATION);
        List<ConfigurationAssetDefinition> matches = assets.stream()
                .filter(asset -> command.mappingKey().equals(readMappingKey(asset)))
                .toList();
        if (matches.isEmpty()) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                    "INTEGRATION mappingKey not found in frozen bundle: " + command.mappingKey());
        }
        if (matches.size() > 1) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Multiple INTEGRATION assets share mappingKey in frozen bundle: " + command.mappingKey());
        }
        return executeInbound(matches.getFirst(), command.externalPayload());
    }

    @Override
    public boolean hasInboundMappingForConnector(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            String connectorCode
    ) {
        return findInboundAssetsForConnector(
                tenantId, bundleId, expectedManifestDigest, connectorCode).isPresent();
    }

    @Override
    public Optional<IntegrationMappingResult> applyInboundForConnectorIfPresent(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            String connectorCode,
            Map<String, Object> externalPayload
    ) {
        Objects.requireNonNull(externalPayload, "externalPayload");
        return findInboundAssetsForConnector(
                tenantId, bundleId, expectedManifestDigest, connectorCode)
                .map(asset -> executeInbound(asset, externalPayload));
    }

    private Optional<ConfigurationAssetDefinition> findInboundAssetsForConnector(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            String connectorCode
    ) {
        String safeConnector = requiredText(connectorCode, "connectorCode");
        List<ConfigurationAssetDefinition> assets = configurations.listBundleAssets(
                tenantId, bundleId, expectedManifestDigest, ConfigurationAssetType.INTEGRATION);
        List<ConfigurationAssetDefinition> matches = assets.stream()
                .filter(asset -> {
                    MappingDefinition definition = parse(asset.definitionJson());
                    return "INBOUND".equals(definition.direction())
                            && safeConnector.equals(definition.connectorCode());
                })
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Multiple INBOUND INTEGRATION mappings for connector in frozen bundle: "
                            + safeConnector);
        }
        return Optional.of(matches.getFirst());
    }

    private IntegrationMappingResult executeInbound(
            ConfigurationAssetDefinition asset,
            Map<String, Object> externalPayload
    ) {
        MappingDefinition definition = parse(asset.definitionJson());
        if (!"INBOUND".equals(definition.direction())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "INTEGRATION mapping direction must be INBOUND for applyInbound");
        }
        Map<String, Object> internal = new LinkedHashMap<>();
        List<String> explanations = new ArrayList<>();
        for (FieldMapping mapping : definition.fieldMappings()) {
            Object raw = readPath(externalPayload, mapping.externalPath());
            if (raw == null || (raw instanceof String s && s.isBlank())) {
                if (mapping.required()) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "Required INTEGRATION mapping missing: " + mapping.mappingId()
                                    + " path=" + mapping.externalPath());
                }
                explanations.add(mapping.mappingId() + ": skipped optional empty");
                continue;
            }
            String transform = mapping.transform() == null ? "NONE" : mapping.transform();
            if (!ALLOWED_TRANSFORMS.contains(transform)) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "Unsupported INTEGRATION transform: " + transform);
            }
            Object transformed = applyTransform(raw, transform, mapping.mappingId());
            writePath(internal, mapping.internalPath(), transformed);
            explanations.add(mapping.mappingId() + ": " + mapping.externalPath()
                    + " -> " + mapping.internalPath() + " [" + transform + "]");
        }
        return new IntegrationMappingResult(
                definition.mappingKey(),
                asset.versionId(),
                asset.contentDigest(),
                definition.connectorCode(),
                definition.direction(),
                internal,
                explanations);
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private String readMappingKey(ConfigurationAssetDefinition asset) {
        MappingDefinition definition = parse(asset.definitionJson());
        return definition.mappingKey();
    }

    private MappingDefinition parse(String definitionJson) {
        try {
            Map<String, Object> root = objectMapper.readValue(definitionJson, new TypeReference<>() { });
            String mappingKey = text(root.get("mappingKey"), "mappingKey");
            String connectorCode = text(root.get("connectorCode"), "connectorCode");
            String direction = text(root.get("direction"), "direction");
            Object mappingsRaw = root.get("fieldMappings");
            if (!(mappingsRaw instanceof List<?> list) || list.isEmpty()) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "INTEGRATION fieldMappings must be a non-empty array");
            }
            List<FieldMapping> mappings = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "INTEGRATION fieldMapping must be an object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> field = (Map<String, Object>) map;
                boolean required = field.get("required") instanceof Boolean b && b;
                String transform = field.get("transform") == null
                        ? "NONE" : text(field.get("transform"), "transform");
                mappings.add(new FieldMapping(
                        text(field.get("mappingId"), "mappingId"),
                        text(field.get("externalPath"), "externalPath"),
                        text(field.get("internalPath"), "internalPath"),
                        required,
                        transform));
            }
            return new MappingDefinition(mappingKey, connectorCode, direction, mappings);
        } catch (BusinessProblem problem) {
            throw problem;
        } catch (RuntimeException exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "INTEGRATION definitionJson is invalid: " + exception.getMessage());
        }
    }

    private static Object applyTransform(Object raw, String transform, String mappingId) {
        String asText = String.valueOf(raw);
        return switch (transform) {
            case "NONE" -> raw;
            case "TRIM" -> asText.trim();
            case "UPPER" -> asText.trim().toUpperCase(Locale.ROOT);
            case "LOWER" -> asText.trim().toLowerCase(Locale.ROOT);
            case "DATE_ISO" -> toIsoDate(asText.trim(), mappingId);
            default -> throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Unsupported INTEGRATION transform: " + transform);
        };
    }

    private static String toIsoDate(String value, String mappingId) {
        try {
            return LocalDate.parse(value, DATE).toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME_T).toLocalDate().toString();
        } catch (DateTimeParseException exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "DATE_ISO transform failed for mapping " + mappingId + ": " + value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object readPath(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static void writePath(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object nested = current.get(parts[i]);
            if (!(nested instanceof Map<?, ?>)) {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(parts[i], created);
                current = created;
            } else {
                current = (Map<String, Object>) nested;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    private static String text(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is invalid");
        }
        return text.trim();
    }

    private record MappingDefinition(
            String mappingKey,
            String connectorCode,
            String direction,
            List<FieldMapping> fieldMappings
    ) {
    }

    private record FieldMapping(
            String mappingId,
            String externalPath,
            String internalPath,
            boolean required,
            String transform
    ) {
    }
}
