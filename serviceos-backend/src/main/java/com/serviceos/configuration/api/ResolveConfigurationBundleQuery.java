package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.Objects;

/** 新工单配置解析输入；项目代码来自已认证的外部连接配置，而不是请求体。 */
public record ResolveConfigurationBundleQuery(
        String tenantId,
        String projectCode,
        String brandCode,
        String serviceProductCode,
        String provinceCode,
        Instant effectiveAt,
        boolean preferCanary,
        String canaryRoutingKey
) {
    public ResolveConfigurationBundleQuery(
            String tenantId,
            String projectCode,
            String brandCode,
            String serviceProductCode,
            String provinceCode,
            Instant effectiveAt
    ) {
        this(tenantId, projectCode, brandCode, serviceProductCode, provinceCode, effectiveAt, false, null);
    }

    public ResolveConfigurationBundleQuery(
            String tenantId,
            String projectCode,
            String brandCode,
            String serviceProductCode,
            String provinceCode,
            Instant effectiveAt,
            boolean preferCanary
    ) {
        this(tenantId, projectCode, brandCode, serviceProductCode, provinceCode, effectiveAt, preferCanary, null);
    }

    public ResolveConfigurationBundleQuery {
        tenantId = PublishConfigurationAssetCommand.text(tenantId, "tenantId", 64);
        projectCode = PublishConfigurationAssetCommand.text(projectCode, "projectCode", 64);
        brandCode = PublishConfigurationAssetCommand.text(brandCode, "brandCode", 64);
        serviceProductCode = PublishConfigurationAssetCommand.text(
                serviceProductCode, "serviceProductCode", 96);
        provinceCode = PublishConfigurationAssetCommand.text(provinceCode, "provinceCode", 16);
        effectiveAt = Objects.requireNonNull(effectiveAt, "effectiveAt");
        if (canaryRoutingKey != null) {
            canaryRoutingKey = canaryRoutingKey.trim();
            if (canaryRoutingKey.isEmpty() || canaryRoutingKey.length() > 256) {
                throw new IllegalArgumentException("canaryRoutingKey must be 1..256 chars when provided");
            }
        }
    }
}
