package com.serviceos.organization.api;

import java.time.Instant;
import java.util.UUID;

public record OrganizationView(
        UUID id,
        String code,
        String name,
        String authorityMode,
        String status,
        String sourceSystem,
        String sourceKey,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
