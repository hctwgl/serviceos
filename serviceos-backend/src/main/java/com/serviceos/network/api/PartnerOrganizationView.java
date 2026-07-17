package com.serviceos.network.api;

import java.time.Instant;
import java.util.UUID;

public record PartnerOrganizationView(
        UUID id,
        String code,
        String name,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {}
