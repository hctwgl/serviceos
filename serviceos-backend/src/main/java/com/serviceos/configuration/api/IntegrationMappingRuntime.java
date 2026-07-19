package com.serviceos.configuration.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * INTEGRATION Mapping 运行时。
 *
 * <p>只从工单冻结 Bundle 读取 Mapping；Transform 白名单；失败关闭并可解释。</p>
 */
public interface IntegrationMappingRuntime {
    IntegrationMappingResult applyInbound(IntegrationMappingApplyCommand command);

    /**
     * 冻结 Bundle 是否配置了该 connector 的唯一 INBOUND Mapping。
     *
     * <p>零命中 false；恰好一命中 true；多命中失败关闭。</p>
     */
    boolean hasInboundMappingForConnector(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            String connectorCode);

    /**
     * 按 connectorCode 在冻结 Bundle 中唯一选择 INBOUND Mapping 并应用。
     *
     * <p>零命中返回 empty（Bundle 未配置 INTEGRATION 时兼容旧路径）；多命中失败关闭。</p>
     */
    Optional<IntegrationMappingResult> applyInboundForConnectorIfPresent(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            String connectorCode,
            Map<String, Object> externalPayload);
}
