package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 师傅视角 ACTIVE/REVOKED TaskAssignment 摘要（跨模块可信只读）。
 */
public record TechnicianTaskAssignmentFeedView(
        UUID taskAssignmentId,
        UUID taskId,
        UUID workOrderId,
        String status,
        Instant effectiveFrom,
        Instant effectiveTo,
        String revokeReasonCode
) {
}
