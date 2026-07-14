package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.UUID;

/** ServiceAssignment 激活 saga 命令的首次冻结结果。 */
public record ServiceAssignmentReceipt(
        UUID serviceAssignmentId,
        UUID sagaId,
        UUID taskId,
        UUID capacityReservationId,
        String assignmentStatus,
        String sagaStage,
        long sagaVersion,
        Instant occurredAt
) {
}
