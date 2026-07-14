package com.serviceos.dispatch.api;

import java.util.Objects;
import java.util.UUID;

/** Task 已准备后，原子切换 ServiceAssignment 与容量责任。 */
public record ActivateServiceAssignmentCommand(
        UUID sagaId,
        UUID serviceAssignmentId,
        long expectedSagaVersion,
        String authorityAssignmentId,
        long authorityVersion,
        String fenceDecisionId,
        String fencePolicyVersion
) {
    public ActivateServiceAssignmentCommand {
        sagaId = Objects.requireNonNull(sagaId, "sagaId");
        serviceAssignmentId = Objects.requireNonNull(serviceAssignmentId, "serviceAssignmentId");
        if (expectedSagaVersion < 1) throw new IllegalArgumentException("expectedSagaVersion must be positive");
        authorityAssignmentId = text(authorityAssignmentId, "authorityAssignmentId");
        if (authorityVersion < 1) throw new IllegalArgumentException("authorityVersion must be positive");
        fenceDecisionId = text(fenceDecisionId, "fenceDecisionId");
        fencePolicyVersion = text(fencePolicyVersion, "fencePolicyVersion");
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > 160) {
            throw new IllegalArgumentException(name + " must contain at most 160 characters");
        }
        return normalized;
    }
}
