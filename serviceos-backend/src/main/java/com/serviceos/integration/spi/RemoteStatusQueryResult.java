package com.serviceos.integration.spi;

/**
 * 远端查询结果。
 *
 * <p>{@link ConfirmedAccepted}/{@link ConfirmedRejected} 仅在协议提供可验证权威状态时使用；
 * 本地 stub 默认应返回 {@link StillUnknown} 或 {@link NotSupported}。</p>
 */
public sealed interface RemoteStatusQueryResult {
    record ConfirmedAccepted(String externalRef, String detail) implements RemoteStatusQueryResult {
        public ConfirmedAccepted {
            externalRef = required(externalRef, "externalRef");
            detail = detail == null ? "" : detail.trim();
        }
    }

    record ConfirmedRejected(String externalRef, String detail) implements RemoteStatusQueryResult {
        public ConfirmedRejected {
            externalRef = required(externalRef, "externalRef");
            detail = detail == null ? "" : detail.trim();
        }
    }

    record StillUnknown(String reasonCode, String detail) implements RemoteStatusQueryResult {
        public StillUnknown {
            reasonCode = required(reasonCode, "reasonCode");
            detail = detail == null ? "" : detail.trim();
        }
    }

    record NotSupported(String reasonCode, String detail) implements RemoteStatusQueryResult {
        public NotSupported {
            reasonCode = required(reasonCode, "reasonCode");
            detail = detail == null ? "" : detail.trim();
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
