package com.serviceos.configuration.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 从冻结 Bundle 解析 ASSIGNEE_POLICY 候选计划。
 *
 * <p>{@code principalsByRoleCode} 由调用方提供已授权的 USER 目录快照；运行时不临时读组织“当前值”。</p>
 */
public record AssigneePolicyResolveCommand(
        String tenantId,
        UUID bundleId,
        String expectedManifestDigest,
        String policyKey,
        ExpressionContext expressionContext,
        Map<String, List<String>> principalsByRoleCode
) {
    public AssigneePolicyResolveCommand {
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(bundleId, "bundleId");
        expectedManifestDigest = required(expectedManifestDigest, "expectedManifestDigest")
                .toLowerCase(java.util.Locale.ROOT);
        if (!expectedManifestDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedManifestDigest must be SHA-256 hex");
        }
        policyKey = required(policyKey, "policyKey");
        Objects.requireNonNull(expressionContext, "expressionContext");
        if (principalsByRoleCode == null) {
            principalsByRoleCode = Map.of();
        } else {
            Map<String, List<String>> copy = new java.util.LinkedHashMap<>();
            principalsByRoleCode.forEach((role, principals) ->
                    copy.put(role, List.copyOf(principals == null ? List.of() : principals)));
            principalsByRoleCode = Map.copyOf(copy);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
