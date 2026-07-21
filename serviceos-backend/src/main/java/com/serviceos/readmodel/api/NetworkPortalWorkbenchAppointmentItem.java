package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Network 工作台「今日预约」非 PII 摘要项。
 *
 * <p>不含客户姓名/地址；师傅显示名仅在具备 {@code technician.readOwnNetwork} 时填充。</p>
 */
public record NetworkPortalWorkbenchAppointmentItem(
        UUID appointmentId,
        UUID taskId,
        UUID workOrderId,
        String type,
        String status,
        Instant windowStart,
        Instant windowEnd,
        String timezone,
        String technicianId,
        String technicianDisplayName
) {
    public NetworkPortalWorkbenchAppointmentItem {
        Objects.requireNonNull(appointmentId, "appointmentId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(workOrderId, "workOrderId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
    }
}
