package com.serviceos.network.domain;

import com.serviceos.network.api.ServiceNetworkView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** ServiceNetwork 聚合根；不得进入 organization OrgUnit closure。 */
public record ServiceNetwork(
        UUID id,
        String tenantId,
        UUID partnerOrganizationId,
        String networkCode,
        String networkName,
        Status status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant deactivatedAt,
        String deactivatedBy,
        String deactivateReason
) {
    public enum Status { ACTIVE, DEACTIVATED }

    public ServiceNetwork {
        Objects.requireNonNull(id, "id must not be null");
        tenantId = requireText(tenantId, "tenantId", 64);
        Objects.requireNonNull(partnerOrganizationId, "partnerOrganizationId must not be null");
        networkCode = requireText(networkCode, "networkCode", 64);
        networkName = requireText(networkName, "networkName", 200);
        status = Objects.requireNonNull(status, "status must not be null");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public void requireActive() {
        if (status != Status.ACTIVE) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "网点不存在或已清退");
        }
    }

    public ServiceNetworkView toView() {
        return new ServiceNetworkView(id, partnerOrganizationId, networkCode, networkName, status.name(),
                version, createdAt, updatedAt, deactivatedAt, deactivatedBy, deactivateReason);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
