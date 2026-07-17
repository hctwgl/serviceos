package com.serviceos.network.domain;

import com.serviceos.network.api.NetworkTechnicianMembershipView;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 师傅与网点的服务关系。 */
public record NetworkTechnicianMembership(
        UUID id,
        String tenantId,
        UUID serviceNetworkId,
        UUID technicianProfileId,
        Status status,
        Instant validFrom,
        Instant validTo,
        String createdBy,
        Instant createdAt,
        String terminatedBy,
        Instant terminatedAt,
        String terminateReason,
        long version
) {
    public enum Status { ACTIVE, TERMINATED }

    public NetworkTechnicianMembership {
        Objects.requireNonNull(id, "id must not be null");
        tenantId = requireText(tenantId, "tenantId", 64);
        Objects.requireNonNull(serviceNetworkId, "serviceNetworkId must not be null");
        Objects.requireNonNull(technicianProfileId, "technicianProfileId must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        createdBy = requireText(createdBy, "createdBy", 128);
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }

    public NetworkTechnicianMembershipView toView() {
        return new NetworkTechnicianMembershipView(id, serviceNetworkId, technicianProfileId, status.name(),
                validFrom, validTo, createdBy, createdAt, terminatedBy, terminatedAt, terminateReason, version);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
