package com.serviceos.network.api;

import java.time.Instant;
import java.util.UUID;

public record NetworkMembershipView(
        UUID id,
        UUID serviceNetworkId,
        UUID principalId,
        String role,
        String status,
        Instant validFrom,
        Instant validTo,
        String invitedBy,
        Instant createdAt,
        String terminatedBy,
        Instant terminatedAt,
        String terminateReason,
        long version
) {}
