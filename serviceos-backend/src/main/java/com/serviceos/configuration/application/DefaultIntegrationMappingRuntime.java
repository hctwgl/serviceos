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
 * M334 起支持 bounded DSL：{@code constantValue}/{@code defaultValue}/{@code enumMap}/
 * {@code condition}（PRESENT/EQUALS/NOT_EQUALS/IN/NOT_IN）。
 * mappingKey 零命中/多命中、必填缺失、未知 Transform/枚举、非法条件一律失败关闭。</p>
 */
@Service
public class DefaultIntegrationMappingRuntime implements IntegrationMappingRuntime {
    private static final Set<String> ALLOWED_TRANSFORMS = Set.of(
            "NONE", "TRIM", "UPPER", "LOWER", "DATE_ISO");
    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "PRESENT", "EQUALS", "NOT_EQUALS", "IN", "NOT_IN");
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
        return findDirectionAssetsForConnector(
                tenantId, bundleId, expectedManifestDigest, connectorCode, "INBOUND");
    }

    private Optional<ConfigurationAssetDefinition> findDirectionAssetsForConnector(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            String connectorCode,
            String direction
    ) {
        String safeConnector = requiredText(connectorCode, "connectorCode");
        String safeDirection = requiredText(direction, "direction");
        List<ConfigurationAssetDefinition> assets = configurations.listBundleAssets(
                tenantId, bundleId, expectedManifestDigest, ConfigurationAssetType.INTEGRATION);
        List<ConfigurationAssetDefinition> matches = assets.stream()
                .filter(asset -> {
                    MappingDefinition definition = parse(asset.definitionJson());
                    return safeDirection.equals(definition.direction())
                            && safeConnector.equals(definition.connectorCode());
                })
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Multiple " + safeDirection + " INTEGRATION mappings for connector in frozen bundle: "
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
            applyFieldMapping(
                    mapping, true, externalPayload, internal, explanations);
        }
        return new IntegrationMappingResult(
                definition.mappingKey(),
                asset.versionId(),
                asset.contentDigest(),
                definition.connectorCode(),
                definition.direction(),
                internal,
                Map.of(),
                explanations);
    }

    @Override
    public boolean hasOutboundMappingForConnector(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            String connectorCode
    ) {
        return findDirectionAssetsForConnector(
                tenantId, bundleId, expectedManifestDigest, connectorCode, "OUTBOUND").isPresent();
    }

    @Override
    public Optional<IntegrationMappingResult> applyOutboundForConnectorIfPresent(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            String connectorCode,
            Map<String, Object> internalPayload
    ) {
        Objects.requireNonNull(internalPayload, "internalPayload");
        return findDirectionAssetsForConnector(
                tenantId, bundleId, expectedManifestDigest, connectorCode, "OUTBOUND")
                .map(asset -> executeOutbound(asset, internalPayload));
    }

    private IntegrationMappingResult executeOutbound(
            ConfigurationAssetDefinition asset,
            Map<String, Object> internalPayload
    ) {
        MappingDefinition definition = parse(asset.definitionJson());
        if (!"OUTBOUND".equals(definition.direction())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "INTEGRATION mapping direction must be OUTBOUND for applyOutbound");
        }
        Map<String, Object> external = new LinkedHashMap<>();
        List<String> explanations = new ArrayList<>();
        for (FieldMapping mapping : definition.fieldMappings()) {
            applyFieldMapping(
                    mapping, false, internalPayload, external, explanations);
        }
        return new IntegrationMappingResult(
                definition.mappingKey(),
                asset.versionId(),
                asset.contentDigest(),
                definition.connectorCode(),
                definition.direction(),
                Map.of(),
                external,
                explanations);
    }

    /**
     * 单字段映射：condition → constant/default/path → transform → enumMap → 写入目标。
     *
     * @param inbound true 时 source=external、dest=internal；false 时相反
     */
    private static void applyFieldMapping(
            FieldMapping mapping,
            boolean inbound,
            Map<String, Object> sourceRoot,
            Map<String, Object> destRoot,
            List<String> explanations
    ) {
        if (mapping.condition() != null
                && !evaluateCondition(mapping.condition(), sourceRoot, mapping.mappingId())) {
            explanations.add(mapping.mappingId() + ": skipped by condition");
            return;
        }

        Object raw;
        String sourceLabel;
        if (mapping.constantValue() != null) {
            raw = mapping.constantValue();
            sourceLabel = "constant";
        } else {
            String sourcePath = inbound ? mapping.externalPath() : mapping.internalPath();
            raw = readPath(sourceRoot, sourcePath);
            if (isBlank(raw) && mapping.defaultValue() != null) {
                raw = mapping.defaultValue();
                sourceLabel = sourcePath + "+default";
            } else if (isBlank(raw)) {
                if (mapping.required()) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "Required INTEGRATION mapping missing: " + mapping.mappingId()
                                    + " path=" + sourcePath);
                }
                explanations.add(mapping.mappingId() + ": skipped optional empty");
                return;
            } else {
                sourceLabel = sourcePath;
            }
        }

        String transform = mapping.transform() == null ? "NONE" : mapping.transform();
        if (!ALLOWED_TRANSFORMS.contains(transform)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Unsupported INTEGRATION transform: " + transform);
        }
        Object transformed = applyTransform(raw, transform, mapping.mappingId());
        if (mapping.enumMap() != null && !mapping.enumMap().isEmpty()) {
            transformed = applyEnumMap(transformed, mapping.enumMap(), mapping.mappingId());
        }

        String destPath = inbound ? mapping.internalPath() : mapping.externalPath();
        if (destPath == null || destPath.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "INTEGRATION mapping missing destination path: " + mapping.mappingId());
        }
        writePath(destRoot, destPath, transformed);
        explanations.add(mapping.mappingId() + ": " + sourceLabel
                + " -> " + destPath + " [" + transform + "]");
    }

    private static boolean evaluateCondition(
            SourceCondition condition,
            Map<String, Object> sourceRoot,
            String mappingId
    ) {
        Object actual = readPath(sourceRoot, condition.sourcePath());
        return switch (condition.operator()) {
            case "PRESENT" -> !isBlank(actual);
            case "EQUALS" -> !isBlank(actual)
                    && String.valueOf(actual).trim().equals(String.valueOf(condition.value()).trim());
            case "NOT_EQUALS" -> isBlank(actual)
                    || !String.valueOf(actual).trim().equals(String.valueOf(condition.value()).trim());
            case "IN" -> !isBlank(actual) && containsScalar(condition.values(), actual);
            case "NOT_IN" -> isBlank(actual) || !containsScalar(condition.values(), actual);
            default -> throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Unsupported INTEGRATION condition operator for mapping " + mappingId
                            + ": " + condition.operator());
        };
    }

    private static boolean containsScalar(List<Object> values, Object actual) {
        String normalized = String.valueOf(actual).trim();
        for (Object candidate : values) {
            if (normalized.equals(String.valueOf(candidate).trim())) {
                return true;
            }
        }
        return false;
    }

    private static Object applyEnumMap(Object value, Map<String, String> enumMap, String mappingId) {
        String key = String.valueOf(value).trim();
        if (!enumMap.containsKey(key)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "INTEGRATION enumMap miss for mapping " + mappingId + ": " + key);
        }
        return enumMap.get(key);
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
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
                mappings.add(parseFieldMapping(field, direction));
            }
            return new MappingDefinition(mappingKey, connectorCode, direction, mappings);
        } catch (BusinessProblem problem) {
            throw problem;
        } catch (RuntimeException exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "INTEGRATION definitionJson is invalid: " + exception.getMessage());
        }
    }

    private static FieldMapping parseFieldMapping(Map<String, Object> field, String direction) {
        String mappingId = text(field.get("mappingId"), "mappingId");
        boolean required = field.get("required") instanceof Boolean b && b;
        String transform = field.get("transform") == null
                ? "NONE" : text(field.get("transform"), "transform");
        Object constantRaw = field.get("constantValue");
        Object defaultRaw = field.get("defaultValue");
        if (constantRaw != null && defaultRaw != null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "INTEGRATION mapping cannot combine constantValue and defaultValue: " + mappingId);
        }
        Map<String, String> enumMap = parseEnumMap(field.get("enumMap"), mappingId);
        if (constantRaw != null && enumMap != null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "INTEGRATION mapping cannot combine constantValue and enumMap: " + mappingId);
        }
        SourceCondition condition = parseCondition(field.get("condition"), mappingId);
        String externalPath = optionalPath(field.get("externalPath"), "externalPath");
        String internalPath = optionalPath(field.get("internalPath"), "internalPath");
        if (constantRaw != null) {
            if ("INBOUND".equals(direction)) {
                if (internalPath == null) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "INBOUND constant mapping requires internalPath: " + mappingId);
                }
            } else if ("OUTBOUND".equals(direction)) {
                if (externalPath == null) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "OUTBOUND constant mapping requires externalPath: " + mappingId);
                }
            }
        } else {
            if (externalPath == null || internalPath == null) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "Path mapping requires externalPath and internalPath: " + mappingId);
            }
        }
        return new FieldMapping(
                mappingId,
                externalPath,
                internalPath,
                required,
                transform,
                constantRaw,
                defaultRaw,
                enumMap,
                condition);
    }

    private static Map<String, String> parseEnumMap(Object raw, String mappingId) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "INTEGRATION enumMap must be a non-empty object: " + mappingId);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "INTEGRATION enumMap key invalid: " + mappingId);
            }
            if (!(entry.getValue() instanceof String value) || value.isBlank()) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "INTEGRATION enumMap value invalid: " + mappingId);
            }
            if (result.put(key.trim(), value.trim()) != null) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "INTEGRATION enumMap duplicate key: " + mappingId + "/" + key);
            }
        }
        return result;
    }

    private static SourceCondition parseCondition(Object raw, String mappingId) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "INTEGRATION condition must be an object: " + mappingId);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) map;
        String sourcePath = text(condition.get("sourcePath"), "sourcePath");
        String operator = text(condition.get("operator"), "operator");
        if (!ALLOWED_OPERATORS.contains(operator)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Unsupported INTEGRATION condition operator: " + operator);
        }
        Object value = condition.get("value");
        Object valuesRaw = condition.get("values");
        List<Object> values = null;
        if (valuesRaw != null) {
            if (!(valuesRaw instanceof List<?> list) || list.isEmpty()) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "INTEGRATION condition.values must be a non-empty array: " + mappingId);
            }
            values = new ArrayList<>(list);
        }
        switch (operator) {
            case "PRESENT" -> {
                if (value != null || values != null) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "PRESENT condition must not declare value/values: " + mappingId);
                }
            }
            case "EQUALS", "NOT_EQUALS" -> {
                if (value == null || values != null) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            operator + " condition requires value only: " + mappingId);
                }
            }
            case "IN", "NOT_IN" -> {
                if (values == null || value != null) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            operator + " condition requires values only: " + mappingId);
                }
            }
            default -> throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Unsupported INTEGRATION condition operator: " + operator);
        }
        return new SourceCondition(sourcePath, operator, value, values);
    }

    private static String optionalPath(Object value, String field) {
        if (value == null) {
            return null;
        }
        return text(value, field);
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
            String transform,
            Object constantValue,
            Object defaultValue,
            Map<String, String> enumMap,
            SourceCondition condition
    ) {
    }

    private record SourceCondition(
            String sourcePath,
            String operator,
            Object value,
            List<Object> values
    ) {
    }
}
