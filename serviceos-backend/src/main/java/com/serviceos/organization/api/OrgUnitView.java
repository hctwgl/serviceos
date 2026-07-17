package com.serviceos.organization.api;

import java.time.Instant;
import java.util.UUID;

public record OrgUnitView(
        UUID id,
        UUID organizationId,
        UUID parentUnitId,
        String unitCode,
        String unitName,
        String status,
        String sourceSystem,
        String sourceKey,
        Long sourceVersion,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
