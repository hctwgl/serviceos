package com.serviceos.appointment.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Technician Portal 日程非敏感预约摘要。不含地址等敏感字段。
 */
public record TechnicianScheduleAppointmentView(
        UUID appointmentId,
        UUID projectId,
        UUID workOrderId,
        UUID taskId,
        String type,
        String status,
        Instant windowStart,
        Instant windowEnd,
        String timezone
) {
}
