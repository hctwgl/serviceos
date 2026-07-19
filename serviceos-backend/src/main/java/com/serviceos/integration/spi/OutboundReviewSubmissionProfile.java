package com.serviceos.integration.spi;

import java.util.UUID;

/**
 * 出站提审创建面配置档案。
 *
 * <p>将 connectorVersion / taskType / 业务键 / lineage 认领从通用
 * {@code OutboundDeliveryService} 中移出，禁止在通用服务内按车企分支。
 * OEM Payload 形状由冻结 Bundle 的 OUTBOUND INTEGRATION Mapping 生成，不再由本接口硬编码。</p>
 */
public interface OutboundReviewSubmissionProfile {
    ConnectorIdentity identity();

    String callbackMappingVersion();

    String businessMessageType();

    String taskType();

    String failurePolicy();

    String clientPolicy();

    String payloadStorageSegment();

    /** 是否认领该入站建单 lineage（connectorVersion + create message type）。 */
    boolean supportsInboundLineage(String inboundConnectorVersionId, String inboundMessageType);

    String extractExternalOrderCode(String inboundBusinessKey);

    String submitBusinessKey(String externalOrderCode, String snapshotContentDigest);

    String clientSubmissionRef(UUID deliveryId);

    String callbackBatchRef(UUID deliveryId);
}
