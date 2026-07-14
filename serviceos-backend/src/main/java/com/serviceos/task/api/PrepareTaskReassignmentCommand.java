package com.serviceos.task.api;

import java.util.Objects;
import java.util.UUID;

/** 原子建立改派 guard，并准备尚不可执行的新责任。 */
public record PrepareTaskReassignmentCommand(
        UUID taskId,
        long expectedVersion,
        String preparationKey,
        String newPrincipalId,
        String pendingServiceAssignmentId,
        String reasonCode
) {
    public PrepareTaskReassignmentCommand {
        taskId = Objects.requireNonNull(taskId, "taskId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        preparationKey = text(preparationKey, "preparationKey", 160);
        newPrincipalId = text(newPrincipalId, "newPrincipalId", 128);
        pendingServiceAssignmentId = text(pendingServiceAssignmentId, "pendingServiceAssignmentId", 160);
        if (reasonCode == null || !reasonCode.matches("[A-Z][A-Z0-9_]{1,99}")) {
            throw new IllegalArgumentException("reasonCode must be a stable uppercase code");
        }
    }

    private static String text(String value, String name, int max) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(name + " must contain at most " + max + " characters");
        }
        return normalized;
    }
}
