package com.serviceos.network.api;

import java.time.Instant;
import java.util.UUID;

public record ClearanceWorkItemView(
        UUID id,
        String subjectType,
        UUID serviceNetworkId,
        UUID technicianProfileId,
        String status,
        String reason,
        int openTaskCount,
        int openAppointmentCount,
        int openVisitCount,
        int activeAssignmentCount,
        int offlinePackageCount,
        String createdBy,
        Instant createdAt,
        String correlationId
) {}
