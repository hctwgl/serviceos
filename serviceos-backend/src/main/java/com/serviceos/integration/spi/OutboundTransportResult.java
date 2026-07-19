package com.serviceos.integration.spi;

import java.util.Objects;

/**
 * 单次出站网络结果。
 *
 * <p>{@link Kind#NOT_SENT} 可证远端无副作用，允许 FAILED_FINAL；
 * {@link Kind#UNKNOWN} 不得按可重试失败处理。</p>
 */
public record OutboundTransportResult(
        Kind kind,
        Integer httpStatus,
        byte[] body,
        String errorCode,
        Throwable cause
) {
    public enum Kind {
        SENT,
        NOT_SENT,
        UNKNOWN
    }

    public OutboundTransportResult {
        Objects.requireNonNull(kind, "kind must not be null");
        if (kind == Kind.SENT) {
            Objects.requireNonNull(httpStatus, "httpStatus must not be null for SENT");
            Objects.requireNonNull(body, "body must not be null for SENT");
            body = body.clone();
            errorCode = null;
            cause = null;
        } else {
            httpStatus = null;
            body = null;
            errorCode = required(errorCode, "errorCode");
        }
    }

    public static OutboundTransportResult sent(int httpStatus, byte[] body) {
        return new OutboundTransportResult(Kind.SENT, httpStatus, body, null, null);
    }

    public static OutboundTransportResult notSent(String errorCode, Throwable cause) {
        return new OutboundTransportResult(Kind.NOT_SENT, null, null, errorCode, cause);
    }

    public static OutboundTransportResult unknown(String errorCode, Throwable cause) {
        return new OutboundTransportResult(Kind.UNKNOWN, null, null, errorCode, cause);
    }

    @Override
    public byte[] body() {
        return body == null ? null : body.clone();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
