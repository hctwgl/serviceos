package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** 可靠改派握手中的 TaskAssignment 状态事实。 */
public record TaskAssignmentChangedPayload(
        UUID taskId,
        UUID guardId,
        UUID taskAssignmentId,
        String preparationKey,
        String principalId,
        String status,
        String serviceAssignmentId,
        String reasonCode,
        Instant occurredAt
) {
}
