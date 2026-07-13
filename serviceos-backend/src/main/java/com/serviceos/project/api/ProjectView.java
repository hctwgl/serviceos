package com.serviceos.project.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectView(
        UUID id,
        String tenantId,
        String code,
        String clientId,
        String name,
        LocalDate startsOn,
        LocalDate endsOn,
        String status,
        long version,
        Instant createdAt
) {
}
