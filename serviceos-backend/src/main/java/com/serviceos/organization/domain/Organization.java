package com.serviceos.organization.domain;

import com.serviceos.organization.api.OrganizationView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 企业组织聚合根。 */
public record Organization(
        UUID id,
        String tenantId,
        String code,
        String name,
        AuthorityMode authorityMode,
        Status status,
        String sourceSystem,
        String sourceKey,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum AuthorityMode { LOCAL, EXTERNAL_AUTHORITATIVE }
    public enum Status { ACTIVE, DISABLED }

    public Organization {
        Objects.requireNonNull(id, "id must not be null");
        tenantId = requireText(tenantId, "tenantId", 64);
        code = requireText(code, "code", 64);
        name = requireText(name, "name", 200);
        authorityMode = Objects.requireNonNull(authorityMode, "authorityMode must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public boolean externalAuthoritative() {
        return authorityMode == AuthorityMode.EXTERNAL_AUTHORITATIVE;
    }

    public void requireActive() {
        if (status != Status.ACTIVE) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "组织不存在或已停用");
        }
    }

    public OrganizationView toView() {
        return new OrganizationView(id, code, name, authorityMode.name(), status.name(),
                sourceSystem, sourceKey, version, createdAt, updatedAt);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
