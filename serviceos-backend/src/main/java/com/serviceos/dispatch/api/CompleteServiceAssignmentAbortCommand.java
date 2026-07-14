package com.serviceos.dispatch.api;

import java.util.Objects;
import java.util.UUID;

/** Task 已撤销 PREPARED 责任后，确认跨模块终止闭环完成。 */
public record CompleteServiceAssignmentAbortCommand(
        UUID sagaId,
        UUID serviceAssignmentId,
        UUID preparedTaskAssignmentId,
        long expectedSagaVersion
) {
    public CompleteServiceAssignmentAbortCommand {
        sagaId = Objects.requireNonNull(sagaId, "sagaId");
        serviceAssignmentId = Objects.requireNonNull(serviceAssignmentId, "serviceAssignmentId");
        preparedTaskAssignmentId = Objects.requireNonNull(
                preparedTaskAssignmentId, "preparedTaskAssignmentId");
        if (expectedSagaVersion < 1) {
            throw new IllegalArgumentException("expectedSagaVersion must be positive");
        }
    }
}
