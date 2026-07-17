package com.serviceos.integration.application;

import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.ExternalReviewRouteView;
import com.serviceos.integration.api.InboundEnvelopeQueueItem;
import com.serviceos.integration.api.InboundEnvelopeView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 入站消息持久化端口。
 *
 * <p>注册方法必须用数据库唯一键处理并发；相同 transport/business key 的重复请求不得先读后写。</p>
 */
public interface InboundMessageRepository {
    EnvelopeRegistration registerEnvelope(NewInboundEnvelope envelope);

    Optional<InboundEnvelopeRecord> findEnvelope(String tenantId, UUID envelopeId);

    boolean rejectEnvelope(
            String tenantId,
            UUID envelopeId,
            UUID projectId,
            String canonicalPayloadDigest,
            String mappingVersionId,
            String resultCode,
            Instant completedAt
    );

    CanonicalRegistration registerCanonical(NewCanonicalMessage message);

    void completeCanonical(
            String tenantId,
            UUID canonicalMessageId,
            String resultCode,
            String resultType,
            String resultId,
            Instant processedAt
    );

    void completeEnvelope(
            String tenantId,
            UUID envelopeId,
            UUID projectId,
            String canonicalPayloadDigest,
            String mappingVersionId,
            UUID canonicalMessageId,
            String resultCode,
            String resultType,
            String resultId,
            Instant completedAt
    );

    Optional<CanonicalMessageRecord> findCanonical(String tenantId, UUID canonicalMessageId);

    Optional<CanonicalMessageRecord> findCanonicalByBusinessKey(
            String tenantId, String connectorVersionId, String messageType, String businessKey);

    /** 由 integration 自己的领域结果引用反查原始入站业务键，避免跨模块读取工单表。 */
    Optional<CanonicalMessageRecord> findCanonicalByResult(
            String tenantId,
            String connectorVersionId,
            String messageType,
            String resultType,
            String resultId);

    /** 只返回成功映射到指定 WorkOrder 的 Envelope；批次审核回调不在本查询中猜测工单归属。 */
    List<InboundEnvelopeRecord> listEnvelopesByWorkOrder(
            String tenantId, UUID projectId, UUID workOrderId, int limit);

    /**
     * 授权入站队列分页。调用方必须已解析实时项目范围；本方法只执行范围化 SQL，
     * 且始终排除 project_id 为空的 Envelope。
     */
    List<InboundEnvelopeQueueItem> findQueuePage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            String processingStatus,
            String messageType,
            String resultType,
            String resultId,
            UUID canonicalMessageId,
            Instant cursorReceivedAt,
            UUID cursorId,
            int fetchSize);

    ExternalReviewRouteRegistration registerExternalReviewRoute(NewExternalReviewRoute route);

    Optional<ExternalReviewRouteView> findActiveExternalReviewRoute(
            String tenantId, String connectorVersionId, String externalOrderCode);

    Optional<ExternalReviewRouteView> findExternalReviewRoute(String tenantId, UUID reviewRouteId);

    void completeExternalReviewRoute(
            String tenantId, UUID reviewRouteId, UUID canonicalMessageId, Instant completedAt);

    InboundItemResult insertItemResult(InboundItemResult result);

    List<InboundItemResult> findItemResults(String tenantId, UUID inboundEnvelopeId);

    void completeBatchEnvelope(
            String tenantId,
            UUID envelopeId,
            UUID projectId,
            String canonicalPayloadDigest,
            String mappingVersionId,
            String resultCode,
            String resultId,
            Instant completedAt);

    record EnvelopeRegistration(InboundEnvelopeRecord envelope, boolean created) {
    }

    record CanonicalRegistration(CanonicalMessageRecord message, boolean created) {
    }

    record ExternalReviewRouteRegistration(ExternalReviewRouteView route, boolean created) {
    }

    record NewInboundEnvelope(
            UUID inboundEnvelopeId,
            String tenantId,
            String connectorVersionId,
            String messageType,
            String transportDedupKey,
            String externalMessageId,
            Instant receivedAt,
            String rawPayloadObjectRef,
            String rawPayloadDigest,
            String correlationId
    ) {
    }

    record NewCanonicalMessage(
            UUID canonicalMessageId,
            String tenantId,
            UUID projectId,
            String connectorVersionId,
            String messageType,
            String businessKey,
            String payloadObjectRef,
            String payloadDigest,
            String mappingVersionId,
            UUID sourceEnvelopeId,
            Instant createdAt
    ) {
    }

    record NewExternalReviewRoute(
            UUID reviewRouteId,
            String tenantId,
            UUID projectId,
            String connectorVersionId,
            String externalOrderCode,
            UUID reviewCaseId,
            String externalSubmissionRef,
            String callbackBatchRef,
            String mappingVersionId,
            String createdBy,
            Instant createdAt
    ) {
    }

    record InboundEnvelopeRecord(
            InboundEnvelopeView view,
            String rawPayloadObjectRef,
            String transportDedupKey
    ) {
    }

    record CanonicalMessageRecord(
            CanonicalMessageView view,
            String payloadObjectRef,
            UUID sourceEnvelopeId
    ) {
    }

    record InboundItemResult(
            UUID inboundEnvelopeId,
            String itemKey,
            UUID canonicalMessageId,
            String processingResult,
            String resultCode,
            String resultType,
            String resultId,
            Instant completedAt
    ) {
    }
}
