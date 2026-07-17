package com.serviceos.identity.domain;

import com.serviceos.identity.api.SecurityPrincipalView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * ServiceOS 稳定操作主体。外部 issuer/subject 只存在于 IdentityLink，不能替代本聚合身份。
 */
public record SecurityPrincipal(
        UUID id,
        String tenantId,
        Type type,
        Status status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        String disabledBy,
        String disabledReason,
        String displayName,
        String employeeNumber,
        long profileVersion
) {
    public enum Type { USER, SERVICE }
    public enum Status { ACTIVE, DISABLED }

    public SecurityPrincipal {
        Objects.requireNonNull(id, "id must not be null");
        tenantId = requireText(tenantId, "tenantId", 64);
        type = Objects.requireNonNull(type, "type must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        if (version < 1 || profileVersion < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        displayName = requireText(displayName, "displayName", 200);
        employeeNumber = normalizeOptional(employeeNumber, 128, "employeeNumber");
        if (status == Status.ACTIVE && (disabledAt != null || disabledBy != null || disabledReason != null)) {
            throw new IllegalArgumentException("active principal cannot carry disabled metadata");
        }
        if (status == Status.DISABLED && (disabledAt == null || disabledBy == null || disabledReason == null)) {
            throw new IllegalArgumentException("disabled principal requires complete disabled metadata");
        }
    }

    public static SecurityPrincipal register(
            UUID id, String tenantId, Type type, String displayName, Instant now
    ) {
        return new SecurityPrincipal(id, tenantId, type, Status.ACTIVE, 1, now, now,
                null, null, null, displayName, null, 1);
    }

    public SecurityPrincipal requireActive() {
        if (status != Status.ACTIVE) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "主体已停用");
        }
        return this;
    }

    public SecurityPrincipalView toView() {
        return new SecurityPrincipalView(id, type.name(), status.name(), displayName, employeeNumber,
                version, createdAt, updatedAt);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String normalizeOptional(String value, int max, String field) {
        if (value == null) return null;
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
