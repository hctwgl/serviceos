package com.serviceos.configuration.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 从冻结 Bundle 评估 NOTIFICATION 触发器并派发。
 *
 * <p>{@code eventId} 参与幂等键；{@code recipientsByRole} 由调用方提供已授权收件人快照。</p>
 */
public record NotificationResolveCommand(
        String tenantId,
        UUID bundleId,
        String expectedManifestDigest,
        String policyKey,
        String eventType,
        String eventId,
        ExpressionContext expressionContext,
        Map<String, List<String>> recipientsByRole,
        Map<String, String> templateVariables
) {
    public NotificationResolveCommand {
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(bundleId, "bundleId");
        expectedManifestDigest = required(expectedManifestDigest, "expectedManifestDigest")
                .toLowerCase(java.util.Locale.ROOT);
        if (!expectedManifestDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedManifestDigest must be SHA-256 hex");
        }
        policyKey = required(policyKey, "policyKey");
        eventType = required(eventType, "eventType");
        eventId = required(eventId, "eventId");
        Objects.requireNonNull(expressionContext, "expressionContext");
        if (recipientsByRole == null) {
            recipientsByRole = Map.of();
        } else {
            Map<String, List<String>> copy = new java.util.LinkedHashMap<>();
            recipientsByRole.forEach((role, principals) ->
                    copy.put(role, List.copyOf(principals == null ? List.of() : principals)));
            recipientsByRole = Map.copyOf(copy);
        }
        templateVariables = Map.copyOf(Objects.requireNonNullElse(templateVariables, Map.of()));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
