package com.serviceos.appointment.application;

import com.serviceos.appointment.api.AppointmentRevisionView;
import com.serviceos.appointment.api.AppointmentType;

import java.time.Instant;
import java.util.UUID;

/** Application 层使用的预约聚合快照；与 MyBatis 行结构隔离。 */
public record AppointmentAggregate(
        UUID appointmentId,
        String tenantId,
        UUID projectId,
        UUID workOrderId,
        UUID taskId,
        AppointmentType type,
        String status,
        String assignedNetworkId,
        String technicianId,
        long aggregateVersion,
        int currentRevisionNo,
        Instant createdAt,
        String createdBy,
        AppointmentRevisionView currentRevision
) {
}
