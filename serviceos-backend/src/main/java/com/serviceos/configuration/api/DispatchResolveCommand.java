package com.serviceos.configuration.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 从冻结 Bundle 执行 DISPATCH 策略。 */
public record DispatchResolveCommand(
        String tenantId,
        UUID bundleId,
        String expectedManifestDigest,
        String policyKey,
        ExpressionContext expressionContext,
        List<DispatchCandidate> candidates
) {
    public DispatchResolveCommand {
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(bundleId, "bundleId");
        expectedManifestDigest = required(expectedManifestDigest, "expectedManifestDigest")
                .toLowerCase(java.util.Locale.ROOT);
        if (!expectedManifestDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedManifestDigest must be SHA-256 hex");
        }
        policyKey = required(policyKey, "policyKey");
        Objects.requireNonNull(expressionContext, "expressionContext");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
