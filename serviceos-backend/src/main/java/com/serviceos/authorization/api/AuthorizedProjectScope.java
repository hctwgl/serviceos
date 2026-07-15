package com.serviceos.authorization.api;

import java.util.Set;
import java.util.UUID;

/**
 * 当前主体对某能力的实时项目数据范围。tenantWide 与 projectIds 互斥，调用方不得自行扩大范围。
 */
public record AuthorizedProjectScope(boolean tenantWide, Set<UUID> projectIds, String scopeDigest) {
    public AuthorizedProjectScope {
        projectIds = Set.copyOf(projectIds);
        if (tenantWide == !projectIds.isEmpty()) {
            throw new IllegalArgumentException("tenantWide scope and explicit projects are mutually exclusive");
        }
        if (scopeDigest == null || scopeDigest.isBlank()) {
            throw new IllegalArgumentException("scopeDigest must not be blank");
        }
    }
}
