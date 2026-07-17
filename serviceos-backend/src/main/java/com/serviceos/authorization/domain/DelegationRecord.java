package com.serviceos.authorization.domain;

import com.serviceos.authorization.api.DelegationView;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Delegation 聚合快照。 */
public record DelegationRecord(
        UUID id,
        String tenantId,
        String delegatorPrincipalId,
        String delegatePrincipalId,
        List<String> capabilityCodes,
        String scopeType,
        String scopeRef,
        Instant validFrom,
        Instant validTo,
        String reason,
        Status status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant revokedAt,
        String revokedBy,
        String revokeReason
) {
    public enum Status { ACTIVE, REVOKED }

    public DelegationRecord {
        Objects.requireNonNull(id, "id must not be null");
        tenantId = requireText(tenantId, "tenantId", 64);
        delegatorPrincipalId = requireText(delegatorPrincipalId, "delegatorPrincipalId", 128);
        delegatePrincipalId = requireText(delegatePrincipalId, "delegatePrincipalId", 128);
        capabilityCodes = List.copyOf(Objects.requireNonNull(capabilityCodes, "capabilityCodes"));
        if (capabilityCodes.isEmpty()) throw new IllegalArgumentException("capabilityCodes is invalid");
        scopeType = requireText(scopeType, "scopeType", 32);
        scopeRef = requireText(scopeRef, "scopeRef", 128);
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        reason = requireText(reason, "reason", 500);
        status = Objects.requireNonNull(status, "status must not be null");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public DelegationView toView() {
        return new DelegationView(id, delegatorPrincipalId, delegatePrincipalId, capabilityCodes,
                scopeType, scopeRef, validFrom, validTo, reason, status.name(), version,
                createdAt, updatedAt, revokedAt, revokedBy, revokeReason);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
