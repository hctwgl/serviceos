package com.serviceos.network.api;

import java.time.Instant;
import java.util.UUID;

public record NetworkTechnicianMembershipView(
        UUID id,
        UUID serviceNetworkId,
        UUID technicianProfileId,
        String status,
        Instant validFrom,
        Instant validTo,
        String createdBy,
        Instant createdAt,
        String terminatedBy,
        Instant terminatedAt,
        String terminateReason,
        long version
) {}
