package com.serviceos.task.application;

import java.time.Instant;

public record TaskExecutionOutcome(Kind kind, String resultRef, String errorCode, Instant retryAt) {
    public enum Kind { SUCCESS, RETRYABLE_FAILURE, FINAL_FAILURE, UNKNOWN }

    public static TaskExecutionOutcome success(String resultRef) {
        return new TaskExecutionOutcome(Kind.SUCCESS, resultRef, null, null);
    }

    public static TaskExecutionOutcome retry(String errorCode, Instant retryAt) {
        return new TaskExecutionOutcome(Kind.RETRYABLE_FAILURE, null, errorCode, retryAt);
    }

    public static TaskExecutionOutcome finalFailure(String errorCode) {
        return new TaskExecutionOutcome(Kind.FINAL_FAILURE, null, errorCode, null);
    }

    public static TaskExecutionOutcome unknown(String errorCode) {
        return new TaskExecutionOutcome(Kind.UNKNOWN, null, errorCode, null);
    }
}
