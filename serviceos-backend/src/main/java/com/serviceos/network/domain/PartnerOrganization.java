package com.serviceos.network.domain;

import com.serviceos.network.api.PartnerOrganizationView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 合作组织聚合根。 */
public record PartnerOrganization(
        UUID id,
        String tenantId,
        String code,
        String name,
        Status status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status { ACTIVE, DISABLED }

    public PartnerOrganization {
        Objects.requireNonNull(id, "id must not be null");
        tenantId = requireText(tenantId, "tenantId", 64);
        code = requireText(code, "code", 64);
        name = requireText(name, "name", 200);
        status = Objects.requireNonNull(status, "status must not be null");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public void requireActive() {
        if (status != Status.ACTIVE) {
            throw new BusinessProblem(ProblemCode.NETWORK_AUTHORITY_CONFLICT, "合作组织已停用");
        }
    }

    public PartnerOrganizationView toView() {
        return new PartnerOrganizationView(id, code, name, status.name(), version, createdAt, updatedAt);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
