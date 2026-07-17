package com.serviceos.network.domain;

import com.serviceos.network.api.NetworkMembershipView;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 网点人员成员关系。 */
public record NetworkMembership(
        UUID id,
        String tenantId,
        UUID serviceNetworkId,
        UUID principalId,
        Role role,
        Status status,
        Instant validFrom,
        Instant validTo,
        String invitedBy,
        Instant createdAt,
        String terminatedBy,
        Instant terminatedAt,
        String terminateReason,
        long version
) {
    public enum Role { MANAGER, STAFF }
    public enum Status { ACTIVE, TERMINATED }

    public NetworkMembership {
        Objects.requireNonNull(id, "id must not be null");
        tenantId = requireText(tenantId, "tenantId", 64);
        Objects.requireNonNull(serviceNetworkId, "serviceNetworkId must not be null");
        Objects.requireNonNull(principalId, "principalId must not be null");
        role = Objects.requireNonNull(role, "role must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        invitedBy = requireText(invitedBy, "invitedBy", 128);
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }

    public NetworkMembershipView toView() {
        return new NetworkMembershipView(id, serviceNetworkId, principalId, role.name(), status.name(),
                validFrom, validTo, invitedBy, createdAt, terminatedBy, terminatedAt, terminateReason, version);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
