package com.serviceos.configuration.api;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 从冻结 Bundle 评估 RULE 决策。
 *
 * <p>{@code subjectType}/{@code stage} 必须与资产声明一致；运行时只输出决策，不写领域副作用。</p>
 */
public record RuleResolveCommand(
        String tenantId,
        UUID bundleId,
        String expectedManifestDigest,
        String ruleKey,
        String subjectType,
        String stage,
        ExpressionContext expressionContext
) {
    public RuleResolveCommand {
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(bundleId, "bundleId");
        expectedManifestDigest = required(expectedManifestDigest, "expectedManifestDigest")
                .toLowerCase(Locale.ROOT);
        if (!expectedManifestDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedManifestDigest must be SHA-256 hex");
        }
        ruleKey = required(ruleKey, "ruleKey");
        subjectType = required(subjectType, "subjectType");
        stage = required(stage, "stage");
        Objects.requireNonNull(expressionContext, "expressionContext");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
