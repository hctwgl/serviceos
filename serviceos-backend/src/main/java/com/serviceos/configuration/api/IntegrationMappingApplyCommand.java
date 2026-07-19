package com.serviceos.configuration.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 对冻结 Bundle 内 INTEGRATION Mapping 施加一次入站映射。
 *
 * <p>必须提供精确 bundleId 与 manifestDigest；零/多命中 mappingKey 失败关闭。</p>
 */
public record IntegrationMappingApplyCommand(
        String tenantId,
        UUID bundleId,
        String expectedManifestDigest,
        String mappingKey,
        Map<String, Object> externalPayload
) {
    public IntegrationMappingApplyCommand {
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(bundleId, "bundleId");
        expectedManifestDigest = required(expectedManifestDigest, "expectedManifestDigest")
                .toLowerCase(java.util.Locale.ROOT);
        if (!expectedManifestDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedManifestDigest must be SHA-256 hex");
        }
        mappingKey = required(mappingKey, "mappingKey");
        Objects.requireNonNull(externalPayload, "externalPayload");
        externalPayload = Map.copyOf(externalPayload);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
