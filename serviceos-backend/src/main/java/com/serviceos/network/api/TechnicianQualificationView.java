package com.serviceos.network.api;

import java.time.Instant;
import java.util.UUID;

public record TechnicianQualificationView(
        UUID id,
        UUID technicianProfileId,
        String qualificationCode,
        String status,
        Instant validFrom,
        Instant validTo,
        String submittedBy,
        Instant submittedAt,
        String decidedBy,
        Instant decidedAt,
        String decisionReason,
        long version
) {}
