package com.serviceos.task.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 为 READY 人工任务冻结显式 USER 候选人快照。 */
public record AssignTaskCandidatesCommand(
        UUID taskId,
        long expectedVersion,
        List<String> candidatePrincipalIds,
        AssignmentSourceType sourceType,
        String sourceId
) {
    public AssignTaskCandidatesCommand {
        taskId = Objects.requireNonNull(taskId, "taskId");
        sourceType = Objects.requireNonNull(sourceType, "sourceType");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        if (candidatePrincipalIds == null || candidatePrincipalIds.isEmpty()
                || candidatePrincipalIds.size() > 100) {
            throw new IllegalArgumentException("candidatePrincipalIds must contain 1..100 users");
        }
        candidatePrincipalIds = candidatePrincipalIds.stream()
                .map(candidate -> required(candidate, "candidatePrincipalId", 128))
                .distinct().sorted().toList();
        if (candidatePrincipalIds.isEmpty()) {
            throw new IllegalArgumentException("candidatePrincipalIds must not be empty");
        }
        sourceId = required(sourceId, "sourceId", 160);
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
