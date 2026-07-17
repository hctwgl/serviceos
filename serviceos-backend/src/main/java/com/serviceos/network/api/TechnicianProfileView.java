package com.serviceos.network.api;

import java.time.Instant;
import java.util.UUID;

public record TechnicianProfileView(
        UUID id,
        UUID principalId,
        String displayName,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        String disabledBy,
        String disabledReason
) {}
