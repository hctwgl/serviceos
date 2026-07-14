package com.serviceos.dispatch.api;

import java.util.Objects;
import java.util.UUID;

/** ServiceAssignment 尚未切换前终止激活并释放 HELD 容量。 */
public record AbortServiceAssignmentActivationCommand(
        UUID sagaId,
        UUID serviceAssignmentId,
        long expectedSagaVersion,
        String reasonCode
) {
    public AbortServiceAssignmentActivationCommand {
        sagaId = Objects.requireNonNull(sagaId, "sagaId");
        serviceAssignmentId = Objects.requireNonNull(serviceAssignmentId, "serviceAssignmentId");
        if (expectedSagaVersion < 1) throw new IllegalArgumentException("expectedSagaVersion must be positive");
        if (reasonCode == null || !reasonCode.matches("[A-Z][A-Z0-9_]{1,99}")) {
            throw new IllegalArgumentException("reasonCode must be a stable uppercase code");
        }
    }
}
