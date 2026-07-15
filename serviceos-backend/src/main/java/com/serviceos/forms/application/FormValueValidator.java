package com.serviceos.forms.application;

import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluationException;
import com.serviceos.configuration.api.ExpressionEvaluator;
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
 * 表单服务端校验器。固定 required、可见性、条件必填与跨字段布尔断言统一使用
 * SERVICEOS_EXPR_V1；尚未批准的可编辑、默认值和 validator 参数语义继续失败关闭。
 */
@Component
final class FormValueValidator {
    static final String VERSION = "FORM_STRUCTURAL_V1";
    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .findAndAddModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    private final ObjectMapper objectMapper;
    private final ExpressionEvaluator expressions;

    FormValueValidator(ObjectMapper objectMapper, ExpressionEvaluator expressions) {
        this.objectMapper = objectMapper;
        this.expressions = expressions;
    }

    ValidationResult validate(
            String definitionJson,
            String valuesJson,
            ExpressionContext baseContext
    ) {
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
        ExpressionContext context = baseContext == null
                ? null : baseContext.withFormValues(expressionValues(values));
        for (JsonNode section : definition.path("sections")) {
            boolean sectionVisible = evaluateOptional(
                    section.get("visibility"), context, errors,
                    section.path("sectionKey").asText(), "SECTION_VISIBILITY_INVALID");
            for (JsonNode field : section.path("fields")) {
                String key = field.path("fieldKey").asText();
                JsonNode value = values.get(key);
                boolean visible = sectionVisible && evaluateOptional(
                        field.get("visibleWhen"), context, errors, key, "FIELD_VISIBILITY_INVALID");
                if (!visible) {
                    if (value != null && !value.isNull()) {
                        errors.add(issue(key, "FIELD_NOT_VISIBLE", "不可提交当前条件下不可见的字段"));
                    }
                    continue;
                }
                boolean conditionallyRequired = field.has("requiredWhen")
                        && !field.path("requiredWhen").isNull()
                        && evaluateOptional(field.get("requiredWhen"), context, errors,
                        key, "FIELD_REQUIRED_CONDITION_INVALID");
                validateField(key, field, value,
                        field.path("required").asBoolean(false) || conditionallyRequired, errors);
            }
        }
        for (JsonNode rule : definition.path("validationRules")) {
            String ruleKey = rule.path("ruleKey").asText();
            if (!evaluateOptional(rule.get("assert"), context, errors,
                    ruleKey, "FORM_RULE_INPUT_INVALID")) {
                errors.add(issue(ruleKey, "FORM_RULE_FAILED",
                        rule.path("message").asText("表单跨字段规则未通过")));
            }
        }
        try {
            Object genericValues = CANONICAL_JSON.readValue(valuesJson, Object.class);
            String normalized = CANONICAL_JSON.writeValueAsString(genericValues);
            return new ValidationResult(normalized, errors.isEmpty() ? "VALIDATED" : "INVALID",
                    List.copyOf(errors), List.of());
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("values cannot be normalized", exception);
        }
    }

    /** 固定表单不读取无关工单事实；只有实际声明表达式时才要求完整权威上下文。 */
    boolean requiresExpressionContext(String definitionJson) {
        JsonNode definition = parse(definitionJson, "Published FormVersion definition is invalid");
        if (nonEmptyArray(definition.get("validationRules"))) {
            return true;
        }
        for (JsonNode section : definition.path("sections")) {
            if (present(section.get("visibility"))) {
                return true;
            }
            for (JsonNode field : section.path("fields")) {
                if (present(field.get("requiredWhen")) || present(field.get("visibleWhen"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void rejectUnsupportedRuntime(JsonNode definition) {
        for (JsonNode section : definition.path("sections")) {
            for (JsonNode field : section.path("fields")) {
                if (present(field.get("editableWhen")) || present(field.get("defaultExpression"))
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

    private static void validateField(String key, JsonNode field, JsonNode value, boolean required,
                                      List<FormValidationIssue> errors) {
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

    private boolean evaluateOptional(
            JsonNode expressionNode,
            ExpressionContext context,
            List<FormValidationIssue> errors,
            String ownerKey,
            String errorCode
    ) {
        if (expressionNode == null || expressionNode.isNull()) {
            return true;
        }
        if (context == null) {
            throw new IllegalStateException("表单表达式缺少权威求值上下文: " + ownerKey);
        }
        try {
            return expressions.evaluate(new ExpressionDefinition(
                    expressionNode.path("language").asText(),
                    expressionNode.path("source").asText()), context).result();
        } catch (ExpressionEvaluationException exception) {
            errors.add(issue(ownerKey, errorCode, exception.getMessage()));
            return false;
        }
    }

    /**
     * 表达式上下文只复制当前提交中的 JSON 标量。复合对象仍可作为表单值提交，但不能被
     * SERVICEOS_EXPR_V1 直接比较；解释器遇到复合值会失败关闭并生成可定位字段错误。
     */
    private static Map<String, Object> expressionValues(JsonNode values) {
        Map<String, Object> result = new HashMap<>();
        values.propertyNames().forEach(key -> {
            JsonNode value = values.get(key);
            if (value == null || value.isNull()) {
                return;
            } else if (value.isBoolean()) {
                result.put(key, value.asBoolean());
            } else if (value.isIntegralNumber()) {
                result.put(key, value.asLong());
            } else if (value.isNumber()) {
                result.put(key, value.decimalValue());
            } else if (value.isTextual()) {
                result.put(key, value.asText());
            } else {
                result.put(key, value);
            }
        });
        return Map.copyOf(result);
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
