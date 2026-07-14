package com.serviceos.dispatch.api;

import java.util.Objects;
import java.util.UUID;

/** TaskAssignment 已激活后完成 Dispatch saga。 */
public record CompleteServiceAssignmentActivationCommand(
        UUID sagaId,
        UUID serviceAssignmentId,
        UUID preparedTaskAssignmentId,
        long expectedSagaVersion
) {
    public CompleteServiceAssignmentActivationCommand {
        sagaId = Objects.requireNonNull(sagaId, "sagaId");
        serviceAssignmentId = Objects.requireNonNull(serviceAssignmentId, "serviceAssignmentId");
        preparedTaskAssignmentId = Objects.requireNonNull(
                preparedTaskAssignmentId, "preparedTaskAssignmentId");
        if (expectedSagaVersion < 1) throw new IllegalArgumentException("expectedSagaVersion must be positive");
    }
}
