package com.serviceos.configuration.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * INTEGRATION Mapping 运行时。
 *
 * <p>只从工单冻结 Bundle 读取 Mapping；Transform 白名单；失败关闭并可解释。</p>
 *
 * <p>M339：INBOUND 选择唯一键为 {@code (connectorCode, direction, messageType)}，
 * 以便同一 Bundle 共存 CREATE/UPDATE/CANCEL Mapping。OUTBOUND 仍为
 * {@code (connectorCode, direction)}。</p>
 */
public interface IntegrationMappingRuntime {
    IntegrationMappingResult applyInbound(IntegrationMappingApplyCommand command);

    /**
     * 冻结 Bundle 是否配置了该 connector + messageType 的唯一 INBOUND Mapping。
     *
     * <p>零命中 false；恰好一命中 true；多命中失败关闭。</p>
     */
    boolean hasInboundMappingForConnector(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            String connectorCode,
            String messageType);

    /**
     * 按 connectorCode + messageType 在冻结 Bundle 中唯一选择 INBOUND Mapping 并应用。
     *
     * <p>零命中返回 empty；多命中失败关闭。</p>
     */
    Optional<IntegrationMappingResult> applyInboundForConnectorIfPresent(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            String connectorCode,
            String messageType,
            Map<String, Object> externalPayload);

    /**
     * 冻结 Bundle 是否配置了该 connector 的唯一 OUTBOUND Mapping。
     *
     * <p>零命中 false；恰好一命中 true；多命中失败关闭。</p>
     */
    boolean hasOutboundMappingForConnector(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            String connectorCode);

    /**
     * 按 connectorCode 在冻结 Bundle 中唯一选择 OUTBOUND Mapping 并应用。
     *
     * <p>零命中返回 empty，由提审创建路径失败关闭（M331 起不再回退 Profile 硬编码 payload）；
     * 多命中失败关闭。输出 OEM 字段在 {@link IntegrationMappingResult#externalFields()}。</p>
     */
    Optional<IntegrationMappingResult> applyOutboundForConnectorIfPresent(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            String connectorCode,
            Map<String, Object> internalPayload);
}
