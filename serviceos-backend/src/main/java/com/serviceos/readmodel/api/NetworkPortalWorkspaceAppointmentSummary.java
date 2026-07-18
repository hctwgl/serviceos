package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/**
 * M227：Network Portal 工作区预约摘要；字段对齐 Admin
 * {@code WorkOrderWorkspaceAppointmentSummary}，故意不含 revisions/allowedActions/address。
 */
public record NetworkPortalWorkspaceAppointmentSummary(
        UUID appointmentId,
        UUID taskId,
        String type,
        String status,
        String assignedNetworkId,
        String technicianId,
        int currentRevisionNo,
        Instant windowStart,
        Instant windowEnd,
        String timezone,
        Integer estimatedDurationMinutes,
        long aggregateVersion,
        Instant createdAt
) {
}
