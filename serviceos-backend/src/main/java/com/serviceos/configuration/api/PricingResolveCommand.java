package com.serviceos.configuration.api;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 从冻结 Bundle 试算 PRICING。
 *
 * <p>只输出匹配行与合计，不落账、不创建结算单。</p>
 */
public record PricingResolveCommand(
        String tenantId,
        UUID bundleId,
        String expectedManifestDigest,
        String pricingKey,
        ExpressionContext expressionContext
) {
    public PricingResolveCommand {
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(bundleId, "bundleId");
        expectedManifestDigest = required(expectedManifestDigest, "expectedManifestDigest")
                .toLowerCase(Locale.ROOT);
        if (!expectedManifestDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedManifestDigest must be SHA-256 hex");
        }
        pricingKey = required(pricingKey, "pricingKey");
        Objects.requireNonNull(expressionContext, "expressionContext");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
