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
        Instant effectiveAt
) {
    public ResolveConfigurationBundleQuery {
        tenantId = PublishConfigurationAssetCommand.text(tenantId, "tenantId", 64);
        projectCode = PublishConfigurationAssetCommand.text(projectCode, "projectCode", 64);
        brandCode = PublishConfigurationAssetCommand.text(brandCode, "brandCode", 64);
        serviceProductCode = PublishConfigurationAssetCommand.text(
                serviceProductCode, "serviceProductCode", 96);
        provinceCode = PublishConfigurationAssetCommand.text(provinceCode, "provinceCode", 16);
        effectiveAt = Objects.requireNonNull(effectiveAt, "effectiveAt");
    }
}
