package com.serviceos.network.domain;

import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 师傅档案，与 Principal 1:1 关联。 */
public record TechnicianProfile(
        UUID id,
        String tenantId,
        UUID principalId,
        String displayName,
        Status status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        String disabledBy,
        String disabledReason
) {
    public enum Status { ACTIVE, DISABLED }

    public TechnicianProfile {
        Objects.requireNonNull(id, "id must not be null");
        tenantId = requireText(tenantId, "tenantId", 64);
        Objects.requireNonNull(principalId, "principalId must not be null");
        displayName = requireText(displayName, "displayName", 200);
        status = Objects.requireNonNull(status, "status must not be null");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public void requireActive() {
        if (status != Status.ACTIVE) {
            throw new BusinessProblem(ProblemCode.NETWORK_TECHNICIAN_CONFLICT, "师傅档案已停用");
        }
    }

    public TechnicianProfileView toView() {
        return new TechnicianProfileView(id, principalId, displayName, status.name(), version,
                createdAt, updatedAt, disabledAt, disabledBy, disabledReason);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
