package com.serviceos.forms.application;

import com.serviceos.forms.api.FormValidationIssue;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M34 首个服务端校验器。只执行定义中无歧义的固定 required 与基础类型约束；
 * 表达式和 validator 参数契约尚未获批时必须失败关闭，不能猜测执行语义。
 */
@Component
final class FormValueValidator {
    static final String VERSION = "FORM_STRUCTURAL_V1";
    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .findAndAddModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    private final ObjectMapper objectMapper;

    FormValueValidator(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    ValidationResult validate(String definitionJson, String valuesJson) {
        JsonNode definition = parse(definitionJson, "Published FormVersion definition is invalid");
        JsonNode values = parse(valuesJson, "values must be valid JSON");
        if (!values.isObject()) throw new IllegalArgumentException("values must be a JSON object");
        rejectUnsupportedRuntime(definition);
        Map<String, JsonNode> fields = fields(definition);
        List<FormValidationIssue> errors = new ArrayList<>();
        values.propertyNames().forEach(key -> {
            if (!fields.containsKey(key)) {
                errors.add(issue(key, "FIELD_UNKNOWN", "Field is not declared by the locked FormVersion"));
            }
        });
        fields.forEach((key, field) -> validateField(key, field, values.get(key), errors));
        try {
            Object genericValues = CANONICAL_JSON.readValue(valuesJson, Object.class);
            String normalized = CANONICAL_JSON.writeValueAsString(genericValues);
            return new ValidationResult(normalized, errors.isEmpty() ? "VALIDATED" : "INVALID",
                    List.copyOf(errors), List.of());
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("values cannot be normalized", exception);
        }
    }

    private void rejectUnsupportedRuntime(JsonNode definition) {
        if (nonEmptyArray(definition.get("validationRules"))) unsupported();
        for (JsonNode section : definition.path("sections")) {
            if (present(section.get("visibility"))) unsupported();
            for (JsonNode field : section.path("fields")) {
                if (present(field.get("requiredWhen")) || present(field.get("visibleWhen"))
                        || present(field.get("editableWhen")) || present(field.get("defaultExpression"))
                        || nonEmptyArray(field.get("validators"))) unsupported();
            }
        }
    }

    private static void unsupported() {
        throw new BusinessProblem(ProblemCode.FORM_RUNTIME_UNSUPPORTED,
                "Locked FormVersion requires expression or validator semantics that are not approved");
    }

    private Map<String, JsonNode> fields(JsonNode definition) {
        Map<String, JsonNode> result = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (JsonNode section : definition.path("sections")) {
            for (JsonNode field : section.path("fields")) {
                String key = field.path("fieldKey").asText();
                if (result.putIfAbsent(key, field) != null) duplicates.add(key);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Published FormVersion contains duplicate fieldKey values");
        }
        return Map.copyOf(result);
    }

    private static void validateField(String key, JsonNode field, JsonNode value,
                                      List<FormValidationIssue> errors) {
        boolean required = field.path("required").asBoolean(false);
        if (value == null || value.isNull()) {
            if (required) errors.add(issue(key, "FIELD_REQUIRED", "Field is required"));
            return;
        }
        String type = field.path("dataType").asText();
        boolean valid = switch (type) {
            case "STRING", "TEXT", "ENUM", "SIGNATURE" -> value.isTextual();
            case "FILE_REF" -> value.isTextual() && !isPermanentUrl(value.asText());
            case "INTEGER" -> value.isIntegralNumber();
            case "DECIMAL" -> value.isNumber();
            case "BOOLEAN" -> value.isBoolean();
            case "DATE" -> value.isTextual() && validDate(value.asText());
            case "DATETIME" -> value.isTextual() && validDateTime(value.asText());
            case "MULTI_ENUM" -> textArray(value);
            case "ADDRESS", "GEOPOINT", "OBJECT" -> value.isObject();
            case "OBJECT_LIST" -> objectArray(value);
            default -> false;
        };
        if (!valid) errors.add(issue(key, "FIELD_TYPE_INVALID",
                "Field value does not match declared dataType " + type));
    }

    private JsonNode parse(String json, String message) {
        try { return objectMapper.readTree(json); }
        catch (JacksonException exception) { throw new IllegalArgumentException(message, exception); }
    }

    private static boolean present(JsonNode node) { return node != null && !node.isNull(); }
    private static boolean nonEmptyArray(JsonNode node) { return node != null && node.isArray() && !node.isEmpty(); }
    private static boolean textArray(JsonNode node) {
        if (!node.isArray()) return false;
        for (JsonNode value : node) if (!value.isTextual()) return false;
        return true;
    }
    private static boolean objectArray(JsonNode node) {
        if (!node.isArray()) return false;
        for (JsonNode value : node) if (!value.isObject()) return false;
        return true;
    }
    private static boolean validDate(String value) {
        try { LocalDate.parse(value); return true; } catch (RuntimeException ignored) { return false; }
    }
    private static boolean validDateTime(String value) {
        try { OffsetDateTime.parse(value); return true; } catch (RuntimeException ignored) { return false; }
    }
    private static boolean isPermanentUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }
    private static FormValidationIssue issue(String field, String code, String message) {
        return new FormValidationIssue(field, code, message);
    }

    record ValidationResult(String normalizedValuesJson, String status,
                            List<FormValidationIssue> errors, List<FormValidationIssue> warnings) { }
}
