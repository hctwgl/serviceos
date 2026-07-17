package com.serviceos.authorization.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DelegationView(
        UUID delegationId,
        String delegatorPrincipalId,
        String delegatePrincipalId,
        List<String> capabilityCodes,
        String scopeType,
        String scopeRef,
        Instant validFrom,
        Instant validTo,
        String reason,
        String delegationStatus,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant revokedAt,
        String revokedBy,
        String revokeReason
) {
}
