package com.serviceos.authorization.domain;

import com.serviceos.authorization.api.RoleView;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 租户角色或平台模板聚合。 */
public record AuthRole(
        UUID id,
        String tenantId,
        String roleCode,
        String roleName,
        RoleKind roleKind,
        String roleStatus,
        String description,
        List<String> capabilityCodes,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum RoleKind { PLATFORM_TEMPLATE, TENANT }

    public AuthRole {
        Objects.requireNonNull(id, "id must not be null");
        tenantId = requireText(tenantId, "tenantId", 64);
        roleCode = requireText(roleCode, "roleCode", 120);
        roleName = requireText(roleName, "roleName", 200);
        roleKind = Objects.requireNonNull(roleKind, "roleKind must not be null");
        roleStatus = requireText(roleStatus, "roleStatus", 24);
        capabilityCodes = List.copyOf(Objects.requireNonNull(capabilityCodes, "capabilityCodes"));
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public RoleView toView() {
        return new RoleView(id, roleCode, roleName, roleKind.name(), roleStatus, description,
                capabilityCodes, version, createdAt, updatedAt);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
