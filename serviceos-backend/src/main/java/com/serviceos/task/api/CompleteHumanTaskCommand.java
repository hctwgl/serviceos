package com.serviceos.task.api;

import java.util.UUID;

/** 当前执行人提交具备稳定引用与摘要的人工任务结果。 */
public record CompleteHumanTaskCommand(
        UUID taskId, long expectedVersion, String resultRef, String resultDigest
) {
    public CompleteHumanTaskCommand {
        taskId = java.util.Objects.requireNonNull(taskId, "taskId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        resultRef = required(resultRef, "resultRef", 500);
        resultDigest = required(resultDigest, "resultDigest", 64);
        if (!resultDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("resultDigest must be a SHA-256 hex digest");
        }
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds max length " + max);
        return normalized;
    }
}
