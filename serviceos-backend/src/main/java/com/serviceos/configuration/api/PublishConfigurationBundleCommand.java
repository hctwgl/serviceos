package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 发布可供新工单解析的配置包。assetVersionIds 必须全部属于同一租户且已经发布。
 */
public record PublishConfigurationBundleCommand(
        String tenantId,
        UUID projectId,
        String bundleCode,
        String bundleVersion,
        String brandCode,
        String serviceProductCode,
        String provinceCode,
        Instant effectiveFrom,
        Instant effectiveUntil,
        List<UUID> assetVersionIds
) {
    public PublishConfigurationBundleCommand {
        tenantId = PublishConfigurationAssetCommand.text(tenantId, "tenantId", 64);
        projectId = Objects.requireNonNull(projectId, "projectId");
        bundleCode = PublishConfigurationAssetCommand.text(bundleCode, "bundleCode", 128);
        bundleVersion = PublishConfigurationAssetCommand.text(bundleVersion, "bundleVersion", 64);
        brandCode = PublishConfigurationAssetCommand.text(brandCode, "brandCode", 64);
        serviceProductCode = PublishConfigurationAssetCommand.text(
                serviceProductCode, "serviceProductCode", 96);
        provinceCode = normalizeOptional(provinceCode, 16);
        effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveUntil must be after effectiveFrom");
        }
        assetVersionIds = List.copyOf(Objects.requireNonNull(assetVersionIds, "assetVersionIds"));
        if (assetVersionIds.isEmpty() || assetVersionIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("assetVersionIds must contain at least one version");
        }
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("provinceCode exceeds max length " + maxLength);
        }
        return normalized;
    }
}
