package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** prepare/activate/abort 的首次冻结结果。 */
public record TaskReassignmentReceipt(
        UUID taskId,
        UUID guardId,
        UUID preparedTaskAssignmentId,
        String principalId,
        String status,
        long taskVersion,
        Instant occurredAt
) {
}
