package com.serviceos.appointment.api;

import java.time.Instant;
import java.util.UUID;

/** fieldwork 模块执行 Visit 命令所需的最小预约权威上下文。 */
public record AppointmentVisitContext(
        UUID appointmentId,
        UUID projectId,
        UUID workOrderId,
        UUID taskId,
        String status,
        String assignedNetworkId,
        String technicianId,
        long aggregateVersion,
        UUID currentRevisionId,
        Instant windowStart,
        Instant windowEnd
) {
}
