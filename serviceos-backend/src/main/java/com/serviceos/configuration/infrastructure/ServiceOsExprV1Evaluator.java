package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluation;
import com.serviceos.configuration.api.ExpressionEvaluationException;
import com.serviceos.configuration.api.ExpressionEvaluator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * SERVICEOS_EXPR_V1 布尔子集：字面量、白名单路径、==/!=/&&/||/! 与括号。
 * 不访问数据库、不调用函数、不解析未声明属性。
 */
@Component
final class ServiceOsExprV1Evaluator implements ExpressionEvaluator {
    private static final int MAX_SOURCE_LENGTH = 2_000;
    private static final int MAX_NESTING_DEPTH = 64;
    private static final int MAX_OPERATOR_COUNT = 256;
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "workOrder.clientCode", "workOrder.brandCode", "workOrder.serviceProductCode",
            "region.provinceCode", "region.cityCode", "region.districtCode",
            "task.stageCode", "task.taskType");

    @Override
    public ExpressionEvaluation evaluate(ExpressionDefinition expression, ExpressionContext context) {
        if (expression.source().length() > MAX_SOURCE_LENGTH) {
            throw new ExpressionEvaluationException(
                    "表达式长度超过 SERVICEOS_EXPR_V1 上限: " + expression.source().length());
        }
        Parser parser = new Parser(expression.source(), context);
        boolean result = parser.parseOr();
        parser.expectEnd();
        return new ExpressionEvaluation(result, parser.bindings(), expression);
    }

    private static final class Parser {
        private final String input;
        private final ExpressionContext context;
        private final Map<String, String> bindings = new LinkedHashMap<>();
        private int index;
        private int nestingDepth;
        private int operatorCount;

        private Parser(String input, ExpressionContext context) {
            this.input = input;
            this.context = context;
        }

        Map<String, String> bindings() {
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
            Object left = parseValue();
            skipWhitespace();
            if (match("==")) {
                countOperator();
                Object right = parseValue();
                return compareEquals(left, right);
            }
            if (match("!=")) {
                countOperator();
                Object right = parseValue();
                return !compareEquals(left, right);
            }
            if (left instanceof Boolean bool) {
                return bool;
            }
            throw error("独立值必须是布尔字面量");
        }

        private static boolean compareEquals(Object left, Object right) {
            if (left.getClass() != right.getClass()) {
                throw new ExpressionEvaluationException(
                        "表达式比较两侧必须是相同类型");
            }
            return left.equals(right);
        }

        private Object parseValue() {
            skipWhitespace();
            if (matchKeyword("true")) {
                return Boolean.TRUE;
            }
            if (matchKeyword("false")) {
                return Boolean.FALSE;
            }
            if (peek() == '"') {
                return readString();
            }
            return readPath();
        }

        private String readPath() {
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
            String value = resolvePath(path);
            bindings.putIfAbsent(path, value);
            return value;
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
}
