package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.UUID;

/** Admin 人工初派完成后的双责任回执。 */
public record ManualServiceAssignmentReceipt(
        UUID taskId,
        UUID workOrderId,
        UUID networkServiceAssignmentId,
        UUID technicianServiceAssignmentId,
        String networkAssigneeId,
        String technicianAssigneeId,
        Instant occurredAt
) {
}
