package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Admin 用户目录行：主体事实 + 组织/角色摘要（缺 capability 时摘要为 null，不整页 403）。
 */
public record AdminUserDirectoryItem(
        UUID id,
        String type,
        String status,
        String displayName,
        String employeeNumber,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String organizationSummary,
        String roleSummary,
        Instant lastLoginAt
) {
    public AdminUserDirectoryItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
