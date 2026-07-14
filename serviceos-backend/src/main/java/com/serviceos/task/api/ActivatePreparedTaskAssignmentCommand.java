package com.serviceos.task.api;

import java.util.Objects;
import java.util.UUID;

/** ServiceAssignment 已完成权威切换后，激活预备责任并解除 guard。 */
public record ActivatePreparedTaskAssignmentCommand(
        UUID taskId,
        UUID guardId,
        UUID preparedTaskAssignmentId,
        long expectedVersion,
        String activeServiceAssignmentId
) {
    public ActivatePreparedTaskAssignmentCommand {
        taskId = Objects.requireNonNull(taskId, "taskId");
        guardId = Objects.requireNonNull(guardId, "guardId");
        preparedTaskAssignmentId = Objects.requireNonNull(
                preparedTaskAssignmentId, "preparedTaskAssignmentId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        activeServiceAssignmentId = Objects.requireNonNull(
                activeServiceAssignmentId, "activeServiceAssignmentId").trim();
        if (activeServiceAssignmentId.isEmpty() || activeServiceAssignmentId.length() > 160) {
            throw new IllegalArgumentException(
                    "activeServiceAssignmentId must contain at most 160 characters");
        }
    }
}
