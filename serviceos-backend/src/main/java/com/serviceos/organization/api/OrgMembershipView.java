package com.serviceos.organization.api;

import java.time.Instant;
import java.util.UUID;

public record OrgMembershipView(
        UUID id,
        UUID organizationId,
        UUID orgUnitId,
        UUID principalId,
        String membershipType,
        String status,
        Instant validFrom,
        Instant validTo,
        String sourceSystem,
        String sourceKey,
        Long sourceVersion,
        long version,
        String createdBy,
        Instant createdAt,
        String terminatedBy,
        Instant terminatedAt,
        String terminateReason
) {
}
