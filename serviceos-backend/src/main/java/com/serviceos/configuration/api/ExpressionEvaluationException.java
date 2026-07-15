package com.serviceos.configuration.api;

/** 表达式语法、类型或白名单违规；调用方必须失败关闭。 */
public final class ExpressionEvaluationException extends RuntimeException {
    public ExpressionEvaluationException(String message) {
        super(message);
    }
}
