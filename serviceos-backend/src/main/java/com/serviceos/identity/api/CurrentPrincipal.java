package com.serviceos.identity.api;

import java.util.Objects;
import java.util.Set;

/**
 * 已认证主体的服务端快照。能力仍需 authorization 模块结合实时授权版本和数据范围复核。
 */
public record CurrentPrincipal(
        String principalId,
        String tenantId,
        PrincipalType principalType,
        String clientId,
        Set<String> assertedCapabilities
) {
    public enum PrincipalType { USER, SERVICE }

    public CurrentPrincipal {
        principalId = requireText(principalId, "principalId");
        tenantId = requireText(tenantId, "tenantId");
        principalType = Objects.requireNonNull(principalType, "principalType must not be null");
        clientId = clientId == null ? "unknown" : clientId.trim();
        assertedCapabilities = assertedCapabilities == null ? Set.of() : Set.copyOf(assertedCapabilities);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
