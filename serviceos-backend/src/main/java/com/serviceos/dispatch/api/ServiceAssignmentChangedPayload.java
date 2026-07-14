package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.UUID;

/** ServiceAssignment 激活生命周期事实。 */
public record ServiceAssignmentChangedPayload(
        UUID serviceAssignmentId,
        UUID sagaId,
        UUID workOrderId,
        UUID taskId,
        String responsibilityLevel,
        String assigneeId,
        String businessType,
        String status,
        UUID supersedesServiceAssignmentId,
        UUID capacityReservationId,
        UUID guardId,
        UUID preparedTaskAssignmentId,
        String reasonCode,
        Instant occurredAt
) {
}
