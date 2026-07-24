package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluation;
import com.serviceos.configuration.api.ExpressionEvaluationException;
import com.serviceos.configuration.api.ExpressionEvaluator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * SERVICEOS_EXPR_V1 布尔子集：强类型字面量、静态白名单路径、精确表单字段访问、
 * ==/!=/&&/||/! 与括号。不访问数据库、不调用函数、不解析未声明属性。
 */
@Component
final class ServiceOsExprV1Evaluator implements ExpressionEvaluator {
    private static final int MAX_SOURCE_LENGTH = 2_000;
    private static final int MAX_NESTING_DEPTH = 64;
    private static final int MAX_OPERATOR_COUNT = 256;
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "workOrder.clientCode", "workOrder.brandCode", "workOrder.serviceProductCode",
            "region.provinceCode", "region.cityCode", "region.districtCode",
            "task.stageCode", "task.taskType", "task.resultCode");

    @Override
    public ExpressionEvaluation evaluate(ExpressionDefinition expression, ExpressionContext context) {
        checkLength(expression);
        Parser parser = Parser.runtime(expression.source(), context);
        boolean result = parser.parseOr();
        parser.expectEnd();
        return new ExpressionEvaluation(result, parser.bindings(), expression);
    }

    @Override
    public void validate(ExpressionDefinition expression) {
        checkLength(expression);
        Parser parser = Parser.looseValidation(expression.source());
        parser.parseOr();
        parser.expectEnd();
    }

    @Override
    public void validate(ExpressionDefinition expression, Map<String, String> formFieldTypes) {
        checkLength(expression);
        Parser parser = Parser.strictValidation(expression.source(), formFieldTypes);
        parser.parseOr();
        parser.expectEnd();
    }

    private static void checkLength(ExpressionDefinition expression) {
        if (expression.source().length() > MAX_SOURCE_LENGTH) {
            throw new ExpressionEvaluationException(
                    "表达式长度超过 SERVICEOS_EXPR_V1 上限: " + expression.source().length());
        }
    }

    private static final class Parser {
        private final String input;
        private final ExpressionContext context;
        private final Map<String, ValueType> declaredFormTypes;
        private final ValidationMode validationMode;
        private final Map<String, Object> bindings = new LinkedHashMap<>();
        private int index;
        private int nestingDepth;
        private int operatorCount;

        private Parser(
                String input,
                ExpressionContext context,
                Map<String, ValueType> declaredFormTypes,
                ValidationMode validationMode
        ) {
            this.input = input;
            this.context = context;
            this.declaredFormTypes = declaredFormTypes;
            this.validationMode = validationMode;
        }

        static Parser runtime(String input, ExpressionContext context) {
            return new Parser(input, context, Map.of(), ValidationMode.RUNTIME);
        }

        static Parser looseValidation(String input) {
            return new Parser(input, null, Map.of(), ValidationMode.LOOSE);
        }

        static Parser strictValidation(String input, Map<String, String> formFieldTypes) {
            Map<String, ValueType> types = new LinkedHashMap<>();
            formFieldTypes.forEach((key, type) -> types.put(key, ValueType.fromFormType(key, type)));
            return new Parser(input, null, Map.copyOf(types), ValidationMode.STRICT);
        }

        Map<String, Object> bindings() {
            return Map.copyOf(bindings);
        }

        boolean parseOr() {
            boolean value = parseAnd();
            while (match("||")) {
                countOperator();
                boolean right = parseAnd();
                value = value || right;
            }
            return value;
        }

        boolean parseAnd() {
            boolean value = parseNot();
            while (match("&&")) {
                countOperator();
                boolean right = parseNot();
                value = value && right;
            }
            return value;
        }

        boolean parseNot() {
            if (match("!")) {
                countOperator();
                enterNesting();
                try {
                    return !parseNot();
                } finally {
                    leaveNesting();
                }
            }
            return parsePrimary();
        }

        boolean parsePrimary() {
            skipWhitespace();
            if (match("(")) {
                enterNesting();
                try {
                    boolean value = parseOr();
                    if (!match(")")) {
                        throw error("缺少右括号 ')' ");
                    }
                    return value;
                } finally {
                    leaveNesting();
                }
            }
            return parseComparison();
        }

        boolean parseComparison() {
            Value left = parseValue();
            skipWhitespace();
            if (match("==")) {
                countOperator();
                Value right = parseValue();
                return compareEquals(left, right);
            }
            if (match("!=")) {
                countOperator();
                Value right = parseValue();
                return !compareEquals(left, right);
            }
            if (left.type() == ValueType.BOOLEAN) {
                return validationMode == ValidationMode.RUNTIME
                        ? (Boolean) left.value() : true;
            }
            throw error("独立值必须是布尔字面量");
        }

        private boolean compareEquals(Value left, Value right) {
            if (left.type() != ValueType.UNKNOWN && right.type() != ValueType.UNKNOWN
                    && left.type() != right.type()) {
                throw new ExpressionEvaluationException(
                        "表达式比较两侧必须是相同类型");
            }
            if (validationMode != ValidationMode.RUNTIME) {
                return true;
            }
            return left.value().equals(right.value());
        }

        private Value parseValue() {
            skipWhitespace();
            if (matchKeyword("true")) {
                return new Value(Boolean.TRUE, ValueType.BOOLEAN);
            }
            if (matchKeyword("false")) {
                return new Value(Boolean.FALSE, ValueType.BOOLEAN);
            }
            if (peek() == '"') {
                return new Value(readString(), ValueType.STRING);
            }
            if (peek() == '-' || Character.isDigit(peek())) {
                return readNumber();
            }
            if (input.startsWith("formValues", index)) {
                return readFormValue();
            }
            return readPath();
        }

        private Value readPath() {
            skipWhitespace();
            int start = index;
            if (!isIdentifierStart(peek())) {
                throw error("此处需要白名单路径、字符串或布尔字面量");
            }
            index++;
            while (isIdentifierPart(peek()) || peek() == '.') {
                index++;
            }
            String path = input.substring(start, index).trim();
            if (!ALLOWED_PATHS.contains(path)) {
                throw new ExpressionEvaluationException("表达式路径不在白名单中: " + path);
            }
            if (validationMode != ValidationMode.RUNTIME) {
                return new Value("__STATIC_VALIDATION__", ValueType.STRING);
            }
            String value = resolvePath(path);
            bindings.putIfAbsent(path, value);
            return new Value(value, ValueType.STRING);
        }

        private Value readFormValue() {
            match("formValues");
            if (!match("[")) {
                throw error("formValues 后必须使用方括号字段访问");
            }
            skipWhitespace();
            String fieldKey = readString();
            if (!match("]")) {
                throw error("表单字段访问缺少右方括号 ']'");
            }
            if (validationMode == ValidationMode.LOOSE) {
                return new Value(null, ValueType.UNKNOWN);
            }
            if (validationMode == ValidationMode.STRICT) {
                ValueType type = declaredFormTypes.get(fieldKey);
                if (type == null) {
                    throw new ExpressionEvaluationException("表达式引用未声明的表单字段: " + fieldKey);
                }
                return new Value(null, type);
            }
            if (!context.formValues().containsKey(fieldKey)) {
                throw new ExpressionEvaluationException("表达式上下文缺少权威表单值: " + fieldKey);
            }
            Object raw = context.formValues().get(fieldKey);
            Value value = Value.runtime(fieldKey, raw);
            bindings.putIfAbsent("formValues[\"" + fieldKey + "\"]", value.value());
            return value;
        }

        private Value readNumber() {
            int start = index;
            if (peek() == '-') {
                index++;
            }
            if (!Character.isDigit(peek())) {
                throw error("数值字面量格式无效");
            }
            while (Character.isDigit(peek())) {
                index++;
            }
            boolean decimal = false;
            if (peek() == '.') {
                decimal = true;
                index++;
                if (!Character.isDigit(peek())) {
                    throw error("小数点后必须包含数字");
                }
                while (Character.isDigit(peek())) {
                    index++;
                }
            }
            BigDecimal number;
            try {
                number = new BigDecimal(input.substring(start, index));
            } catch (NumberFormatException exception) {
                throw error("数值字面量格式无效");
            }
            return new Value(number, decimal ? ValueType.DECIMAL : ValueType.INTEGER);
        }

        private String resolvePath(String path) {
            return switch (path) {
                case "workOrder.clientCode" -> require(context.workOrder().clientCode(), path);
                case "workOrder.brandCode" -> require(context.workOrder().brandCode(), path);
                case "workOrder.serviceProductCode" -> require(context.workOrder().serviceProductCode(), path);
                case "region.provinceCode" -> require(context.region().provinceCode(), path);
                case "region.cityCode" -> require(context.region().cityCode(), path);
                case "region.districtCode" -> require(context.region().districtCode(), path);
                case "task.stageCode" -> require(context.task().stageCode(), path);
                case "task.taskType" -> require(context.task().taskType(), path);
                case "task.resultCode" -> require(context.task().resultCode(), path);
                default -> throw new ExpressionEvaluationException("表达式路径不在白名单中: " + path);
            };
        }

        private static String require(String value, String path) {
            if (value == null || value.isBlank()) {
                throw new ExpressionEvaluationException("表达式上下文缺少权威值: " + path);
            }
            return value;
        }

        private String readString() {
            if (peek() != '"') {
                throw error("此处需要字符串字面量");
            }
            index++;
            StringBuilder builder = new StringBuilder();
            while (index < input.length() && peek() != '"') {
                char ch = input.charAt(index++);
                if (ch == '\\') {
                    if (index >= input.length()) {
                        throw error("字符串转义未结束");
                    }
                    char escaped = input.charAt(index++);
                    if (escaped != '"' && escaped != '\\') {
                        throw error("不支持的字符串转义: \\" + escaped);
                    }
                    builder.append(escaped);
                } else {
                    builder.append(ch);
                }
            }
            if (peek() != '"') {
                throw error("字符串字面量未结束");
            }
            index++;
            return builder.toString();
        }

        void expectEnd() {
            skipWhitespace();
            if (index < input.length()) {
                throw error("存在未解析的尾部输入");
            }
        }

        /**
         * 表达式来自已发布配置，但仍必须限制递归深度与操作符总数，防止异常配置在消费者
         * 事务中造成栈耗尽或不受控 CPU 消耗。达到上限时失败关闭，整笔 Inbox 事务回滚。
         */
        private void enterNesting() {
            nestingDepth++;
            if (nestingDepth > MAX_NESTING_DEPTH) {
                throw new ExpressionEvaluationException("表达式嵌套深度超过上限: " + MAX_NESTING_DEPTH);
            }
        }

        private void leaveNesting() {
            nestingDepth--;
        }

        private void countOperator() {
            operatorCount++;
            if (operatorCount > MAX_OPERATOR_COUNT) {
                throw new ExpressionEvaluationException("表达式操作符数量超过上限: " + MAX_OPERATOR_COUNT);
            }
        }

        private boolean match(String token) {
            skipWhitespace();
            if (!input.startsWith(token, index)) {
                return false;
            }
            index += token.length();
            return true;
        }

        private boolean matchKeyword(String keyword) {
            skipWhitespace();
            if (!input.regionMatches(index, keyword, 0, keyword.length())) {
                return false;
            }
            if (index + keyword.length() < input.length()
                    && isIdentifierPart(input.charAt(index + keyword.length()))) {
                return false;
            }
            index += keyword.length();
            return true;
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private char peek() {
            return index < input.length() ? input.charAt(index) : '\0';
        }

        private static boolean isIdentifierStart(char ch) {
            return Character.isLetter(ch) || ch == '_';
        }

        private static boolean isIdentifierPart(char ch) {
            return Character.isLetterOrDigit(ch) || ch == '_';
        }

        private ExpressionEvaluationException error(String message) {
            return new ExpressionEvaluationException(message + "，位置 " + index);
        }
    }

    private enum ValidationMode { RUNTIME, LOOSE, STRICT }

    private enum ValueType {
        BOOLEAN, STRING, INTEGER, DECIMAL, UNKNOWN;

        static ValueType fromFormType(String fieldKey, String dataType) {
            return switch (dataType) {
                case "BOOLEAN" -> BOOLEAN;
                case "INTEGER" -> INTEGER;
                case "DECIMAL" -> DECIMAL;
                case "STRING", "TEXT", "DATE", "DATETIME", "ENUM", "SIGNATURE", "FILE_REF" -> STRING;
                default -> throw new ExpressionEvaluationException(
                        "表单字段类型不能用于 SERVICEOS_EXPR_V1 标量比较: "
                                + fieldKey + " (" + dataType + ")");
            };
        }
    }

    private record Value(Object value, ValueType type) {
        static Value runtime(String fieldKey, Object raw) {
            if (raw == null) {
                throw new ExpressionEvaluationException("表达式上下文表单值不能为 null: " + fieldKey);
            }
            if (raw instanceof Boolean value) {
                return new Value(value, ValueType.BOOLEAN);
            }
            if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
                return new Value(new BigDecimal(raw.toString()), ValueType.INTEGER);
            }
            if (raw instanceof Number value) {
                return new Value(new BigDecimal(value.toString()), ValueType.DECIMAL);
            }
            if (raw instanceof String value) {
                return new Value(value, ValueType.STRING);
            }
            throw new ExpressionEvaluationException(
                    "表单字段不是可比较的标量值: " + fieldKey);
        }
    }
}
