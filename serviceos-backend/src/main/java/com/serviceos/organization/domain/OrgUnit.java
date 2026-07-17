package com.serviceos.organization.domain;

import com.serviceos.organization.api.OrgUnitView;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 组织单元节点。 */
public record OrgUnit(
        UUID id,
        String tenantId,
        UUID organizationId,
        UUID parentUnitId,
        String unitCode,
        String unitName,
        Status status,
        String sourceSystem,
        String sourceKey,
        Long sourceVersion,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status { ACTIVE, DISABLED }

    public OrgUnit {
        Objects.requireNonNull(id, "id must not be null");
        tenantId = requireText(tenantId, "tenantId", 64);
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        unitCode = requireText(unitCode, "unitCode", 64);
        unitName = requireText(unitName, "unitName", 200);
        status = Objects.requireNonNull(status, "status must not be null");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public OrgUnitView toView() {
        return new OrgUnitView(id, organizationId, parentUnitId, unitCode, unitName, status.name(),
                sourceSystem, sourceKey, sourceVersion, version, createdAt, updatedAt);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
