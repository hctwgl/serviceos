package com.serviceos.integration.spi;

import java.util.Objects;

/**
 * 连接器失败分类。
 *
 * <p>将协议/传输错误映射为管道可执行的终态策略：最终失败、UNKNOWN 或本地可重试。</p>
 */
public record ConnectorFailure(
        Classification classification,
        String errorCode
) {
    public enum Classification {
        /** 可证未产生远端副作用，Delivery 记 FAILED_FINAL。 */
        FINAL,
        /** 远端状态不可确认，Delivery 记 UNKNOWN，任务不得按 RETRYABLE 重发。 */
        UNKNOWN,
        /** 仅本地落账可安全重试（外部已明确接受）。 */
        LOCAL_RETRYABLE
    }

    public ConnectorFailure {
        Objects.requireNonNull(classification, "classification must not be null");
        errorCode = required(errorCode, "errorCode");
    }

    public static ConnectorFailure finalFailure(String errorCode) {
        return new ConnectorFailure(Classification.FINAL, errorCode);
    }

    public static ConnectorFailure unknown(String errorCode) {
        return new ConnectorFailure(Classification.UNKNOWN, errorCode);
    }

    public static ConnectorFailure localRetryable(String errorCode) {
        return new ConnectorFailure(Classification.LOCAL_RETRYABLE, errorCode);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
