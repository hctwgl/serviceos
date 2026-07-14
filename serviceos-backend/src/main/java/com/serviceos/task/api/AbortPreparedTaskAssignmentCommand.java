package com.serviceos.task.api;

import java.util.Objects;
import java.util.UUID;

/** ServiceAssignment 切换前放弃预备责任，保留旧责任并解除 guard。 */
public record AbortPreparedTaskAssignmentCommand(
        UUID taskId,
        UUID guardId,
        UUID preparedTaskAssignmentId,
        long expectedVersion,
        String reasonCode
) {
    public AbortPreparedTaskAssignmentCommand {
        taskId = Objects.requireNonNull(taskId, "taskId");
        guardId = Objects.requireNonNull(guardId, "guardId");
        preparedTaskAssignmentId = Objects.requireNonNull(
                preparedTaskAssignmentId, "preparedTaskAssignmentId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        if (reasonCode == null || !reasonCode.matches("[A-Z][A-Z0-9_]{1,99}")) {
            throw new IllegalArgumentException("reasonCode must be a stable uppercase code");
        }
    }
}
