package com.serviceos.dispatch.api;

import java.util.Objects;
import java.util.UUID;

/** 记录 Task 模块已建立 guard 与 PREPARED TaskAssignment。 */
public record ConfirmTaskAssignmentPreparedCommand(
        UUID sagaId,
        UUID serviceAssignmentId,
        UUID taskId,
        UUID guardId,
        UUID preparedTaskAssignmentId,
        long expectedSagaVersion
) {
    public ConfirmTaskAssignmentPreparedCommand {
        sagaId = Objects.requireNonNull(sagaId, "sagaId");
        serviceAssignmentId = Objects.requireNonNull(serviceAssignmentId, "serviceAssignmentId");
        taskId = Objects.requireNonNull(taskId, "taskId");
        guardId = Objects.requireNonNull(guardId, "guardId");
        preparedTaskAssignmentId = Objects.requireNonNull(
                preparedTaskAssignmentId, "preparedTaskAssignmentId");
        if (expectedSagaVersion < 1) throw new IllegalArgumentException("expectedSagaVersion must be positive");
    }
}
