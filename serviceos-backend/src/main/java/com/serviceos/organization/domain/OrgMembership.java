package com.serviceos.organization.domain;

import com.serviceos.organization.api.OrgMembershipView;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 组织任职记录。 */
public record OrgMembership(
        UUID id,
        String tenantId,
        UUID organizationId,
        UUID orgUnitId,
        UUID principalId,
        MembershipType membershipType,
        Status status,
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
    public enum MembershipType { PRIMARY, SECONDARY, MANAGER }
    public enum Status { ACTIVE, TERMINATED }

    public OrgMembership {
        Objects.requireNonNull(id, "id must not be null");
        tenantId = requireText(tenantId, "tenantId", 64);
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(orgUnitId, "orgUnitId must not be null");
        Objects.requireNonNull(principalId, "principalId must not be null");
        membershipType = Objects.requireNonNull(membershipType, "membershipType must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        createdBy = requireText(createdBy, "createdBy", 128);
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public OrgMembershipView toView() {
        return new OrgMembershipView(id, organizationId, orgUnitId, principalId, membershipType.name(),
                status.name(), validFrom, validTo, sourceSystem, sourceKey, sourceVersion, version,
                createdBy, createdAt, terminatedBy, terminatedAt, terminateReason);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
