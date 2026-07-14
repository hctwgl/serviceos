package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.UUID;

/** M25 跨模块改派握手事实；initiatedBy 用于消费者实时复核原命令主体授权。 */
public record ServiceAssignmentHandshakePayload(
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
        String initiatedBy,
        int protocolVersion,
        Instant occurredAt
) {
}
