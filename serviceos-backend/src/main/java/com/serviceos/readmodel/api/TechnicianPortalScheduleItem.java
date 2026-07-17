package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/** Technician Portal 日程项（非敏感字段）。 */
public record TechnicianPortalScheduleItem(
        UUID appointmentId,
        UUID taskId,
        UUID workOrderId,
        UUID projectId,
        String type,
        String status,
        Instant windowStart,
        Instant windowEnd,
        String timezone
) {
}
