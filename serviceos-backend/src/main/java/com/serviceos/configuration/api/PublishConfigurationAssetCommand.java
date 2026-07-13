package com.serviceos.configuration.api;

import java.util.Locale;
import java.util.Objects;

/** 发布一个不可变配置资产版本。definitionJson 在数据库侧按 jsonb 再校验。 */
public record PublishConfigurationAssetCommand(
        String tenantId,
        ConfigurationAssetType assetType,
        String assetKey,
        String semanticVersion,
        String schemaVersion,
        String definitionJson,
        String contentDigest
) {
    public PublishConfigurationAssetCommand {
        tenantId = text(tenantId, "tenantId", 64);
        assetType = Objects.requireNonNull(assetType, "assetType");
        assetKey = text(assetKey, "assetKey", 128);
        semanticVersion = text(semanticVersion, "semanticVersion", 64);
        schemaVersion = text(schemaVersion, "schemaVersion", 64);
        definitionJson = text(definitionJson, "definitionJson", 1_000_000);
        contentDigest = digest(contentDigest);
    }

    static String text(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds max length " + maxLength);
        }
        return normalized;
    }

    static String digest(String value) {
        String normalized = text(value, "contentDigest", 64).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentDigest must be SHA-256 hex");
        }
        return normalized;
    }
}
