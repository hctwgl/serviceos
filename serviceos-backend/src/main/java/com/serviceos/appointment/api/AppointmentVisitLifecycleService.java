package com.serviceos.appointment.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Visit 与 Appointment 跨模块一致性端口；调用方事务必须同时覆盖 Visit 事实与预约状态。 */
public interface AppointmentVisitLifecycleService {
    Optional<AppointmentVisitContext> find(String tenantId, UUID appointmentId);

    long advance(
            String tenantId, UUID appointmentId, long expectedVersion,
            String expectedStatus, String newStatus, String commandCode,
            String actorId, String reasonCode, Instant occurredAt);
}
