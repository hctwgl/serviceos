package com.serviceos.configuration.api;

import java.util.List;
import java.util.UUID;

/**
 * 配置包内已发布资产的不可变运行时快照。
 *
 * <p>消费者只能按精确 bundleId 与资产类型读取，不能查询“最新版本”。
 * {@code supportedClientKinds} 为空表示未声明定向目标（全部生产师傅端，M356 默认语义）。</p>
 */
public record ConfigurationAssetDefinition(
        UUID versionId,
        ConfigurationAssetType assetType,
        String assetKey,
        String semanticVersion,
        String schemaVersion,
        String definitionJson,
        String contentDigest,
        List<String> supportedClientKinds
) {
    public ConfigurationAssetDefinition {
        supportedClientKinds = supportedClientKinds == null
                ? List.of() : List.copyOf(supportedClientKinds);
    }

    /** 未声明定向目标的兼容构造（测试与旧调用方）。 */
    public ConfigurationAssetDefinition(
            UUID versionId,
            ConfigurationAssetType assetType,
            String assetKey,
            String semanticVersion,
            String schemaVersion,
            String definitionJson,
            String contentDigest
    ) {
        this(versionId, assetType, assetKey, semanticVersion, schemaVersion,
                definitionJson, contentDigest, List.of());
    }
}
