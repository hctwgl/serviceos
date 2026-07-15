package com.serviceos.configuration.api;

/** 已发布配置中的 SERVICEOS_EXPR_V1 表达式定义。 */
public record ExpressionDefinition(String language, String source) {
    public static final String SERVICEOS_EXPR_V1 = "SERVICEOS_EXPR_V1";

    public ExpressionDefinition {
        if (language == null || !SERVICEOS_EXPR_V1.equals(language)) {
            throw new ExpressionEvaluationException(
                    "不支持的表达式语言: " + language);
        }
        if (source == null || source.isBlank()) {
            throw new ExpressionEvaluationException("表达式 source 不能为空");
        }
        source = source.trim();
    }
}
