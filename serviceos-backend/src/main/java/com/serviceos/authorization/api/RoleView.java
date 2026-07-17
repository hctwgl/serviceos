package com.serviceos.authorization.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 角色模板或租户角色视图。 */
public record RoleView(
        UUID roleId,
        String roleCode,
        String roleName,
        String roleKind,
        String roleStatus,
        String description,
        List<String> capabilityCodes,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
