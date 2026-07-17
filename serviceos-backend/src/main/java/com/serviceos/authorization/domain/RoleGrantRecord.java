package com.serviceos.authorization.domain;

import com.serviceos.authorization.api.RoleGrantView;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** RoleGrant 聚合快照。 */
public record RoleGrantRecord(
        UUID id,
        String tenantId,
        String principalId,
        UUID roleId,
        String roleCode,
        String scopeType,
        String scopeRef,
        GrantStatus grantStatus,
        GrantEffect grantEffect,
        Instant validFrom,
        Instant validTo,
        String sourceCode,
        String approvalRef,
        String requestedBy,
        String requestReason,
        String approvedBy,
        Instant approvedAt,
        String rejectedBy,
        Instant rejectedAt,
        String rejectReason,
        Instant revokedAt,
        String revokedBy,
        String revokeReason,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum GrantStatus { PENDING_APPROVAL, ACTIVE, REJECTED, REVOKED }

    public enum GrantEffect { ALLOW, DENY }

    public RoleGrantRecord {
        Objects.requireNonNull(id, "id must not be null");
        tenantId = requireText(tenantId, "tenantId", 64);
        principalId = requireText(principalId, "principalId", 128);
        Objects.requireNonNull(roleId, "roleId must not be null");
        scopeType = requireText(scopeType, "scopeType", 32);
        scopeRef = requireText(scopeRef, "scopeRef", 128);
        grantStatus = Objects.requireNonNull(grantStatus, "grantStatus must not be null");
        grantEffect = Objects.requireNonNull(grantEffect, "grantEffect must not be null");
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public RoleGrantView toView() {
        return new RoleGrantView(id, principalId, roleId, roleCode, scopeType, scopeRef,
                grantStatus.name(), grantEffect.name(), validFrom, validTo, sourceCode,
                requestedBy, requestReason, approvedBy, approvedAt, rejectedBy, rejectedAt,
                rejectReason, revokedAt, revokedBy, revokeReason, version, createdAt, updatedAt);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
