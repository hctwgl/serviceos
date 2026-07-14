package com.serviceos.appointment.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 当前预约投影及完整修订链。 */
public record AppointmentView(
        UUID appointmentId,
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
        List<AppointmentRevisionView> revisions,
        List<String> allowedActions
) {
    public AppointmentView {
        revisions = List.copyOf(revisions);
        allowedActions = List.copyOf(allowedActions);
    }
}
