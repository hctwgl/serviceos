package com.serviceos.network.api;

import java.time.Instant;
import java.util.UUID;

public record ServiceNetworkView(
        UUID id,
        UUID partnerOrganizationId,
        String networkCode,
        String networkName,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant deactivatedAt,
        String deactivatedBy,
        String deactivateReason
) {}
