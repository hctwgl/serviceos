package com.serviceos.configuration.api;

import java.util.UUID;

/**
 * 配置包内已发布资产的不可变运行时快照。
 *
 * <p>消费者只能按精确 bundleId 与资产类型读取，不能查询“最新版本”。</p>
 */
public record ConfigurationAssetDefinition(
        UUID versionId,
        ConfigurationAssetType assetType,
        String assetKey,
        String semanticVersion,
        String schemaVersion,
        String definitionJson,
        String contentDigest
) {
}
