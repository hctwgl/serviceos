package com.serviceos.task.spi;

import java.time.Instant;
import java.util.Objects;

/**
 * 受控失败分类。只有 RETRYABLE 才可安排下一次执行；UNKNOWN 与 FINAL 都转人工接管。
 */
public final class TaskExecutionException extends Exception {
    public enum Kind { RETRYABLE, FINAL, UNKNOWN }

    private final Kind kind;
    private final String errorCode;
    private final Instant retryAt;

    private TaskExecutionException(Kind kind, String errorCode, Instant retryAt, Throwable cause) {
        super(errorCode, cause);
        this.kind = Objects.requireNonNull(kind);
        this.errorCode = requireText(errorCode, "errorCode");
        this.retryAt = retryAt;
        if (kind == Kind.RETRYABLE && retryAt == null) {
            throw new IllegalArgumentException("retryAt is required for RETRYABLE failure");
        }
        if (kind != Kind.RETRYABLE && retryAt != null) {
            throw new IllegalArgumentException("retryAt is only valid for RETRYABLE failure");
        }
    }

    public static TaskExecutionException retryable(String errorCode, Instant retryAt, Throwable cause) {
        return new TaskExecutionException(Kind.RETRYABLE, errorCode, retryAt, cause);
    }

    public static TaskExecutionException finalFailure(String errorCode, Throwable cause) {
        return new TaskExecutionException(Kind.FINAL, errorCode, null, cause);
    }

    public static TaskExecutionException unknown(String errorCode, Throwable cause) {
        return new TaskExecutionException(Kind.UNKNOWN, errorCode, null, cause);
    }

    public Kind kind() {
        return kind;
    }

    public String errorCode() {
        return errorCode;
    }

    public Instant retryAt() {
        return retryAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
