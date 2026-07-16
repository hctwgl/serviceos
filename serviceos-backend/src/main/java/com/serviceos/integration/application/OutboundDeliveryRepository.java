package com.serviceos.integration.application;

import com.serviceos.integration.api.DeliveryTimelineContext;
import com.serviceos.integration.api.OutboundDeliveryView;
import com.serviceos.integration.api.DeliveryReplayRequestView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * OutboundDelivery 并发与状态迁移端口。
 *
 * <p>网络调用前必须先登记 attempt；未知结果只能终结为 UNKNOWN，不能重新打开。</p>
 */
public interface OutboundDeliveryRepository {
    Registration register(NewDelivery delivery);

    void attachExecutionTask(String tenantId, UUID deliveryId, UUID taskId, Instant updatedAt);

    Optional<DeliveryRecord> find(String tenantId, UUID deliveryId);

    /**
     * 时间线投影专用：只返回 delivery/project/workOrder 身份，不加载 attempt/ack/replay 图。
     */
    Optional<DeliveryTimelineContext> findTimelineContext(String tenantId, UUID deliveryId);

    Optional<DeliveryRecord> findBySourceReview(
            String tenantId, UUID sourceReviewCaseId, String businessMessageType);

    Optional<DeliveryReplayRequestView> findReplay(String tenantId, UUID replayRequestId);

    DeliveryReplayRequestView registerReplay(NewReplayRequest replayRequest);

    boolean isAuthorizedExecutionTask(String tenantId, UUID deliveryId, UUID taskId);

    AttemptStart startAttempt(
            String tenantId,
            UUID deliveryId,
            UUID taskId,
            UUID taskExecutionAttemptId,
            String nonce,
            LocalDate requestDate,
            String requestDigest,
            String credentialVersionId,
            Instant startedAt);

    void recordDelivered(
            String tenantId,
            UUID deliveryId,
            UUID taskExecutionAttemptId,
            int httpStatus,
            String responseObjectRef,
            String responseDigest,
            String resultCode,
            String acknowledgementReasonCode,
            Instant finishedAt);

    void recordRejected(
            String tenantId,
            UUID deliveryId,
            UUID taskExecutionAttemptId,
            int httpStatus,
            String responseObjectRef,
            String responseDigest,
            String resultCode,
            String acknowledgementReasonCode,
            Instant finishedAt);

    void recordUnknown(
            String tenantId,
            UUID deliveryId,
            UUID taskExecutionAttemptId,
            Integer httpStatus,
            String responseObjectRef,
            String responseDigest,
            String resultCode,
            Instant finishedAt);

    void recordFailedFinal(
            String tenantId,
            UUID deliveryId,
            UUID taskExecutionAttemptId,
            Integer httpStatus,
            String responseObjectRef,
            String responseDigest,
            String resultCode,
            Instant finishedAt);

    void failBeforeAttempt(
            String tenantId, UUID deliveryId, UUID taskId, String resultCode, Instant failedAt);

    void acknowledge(
            String tenantId,
            UUID deliveryId,
            UUID clientReviewCaseId,
            UUID reviewRouteId,
            Instant acknowledgedAt);

    boolean markSendingUnknownByTaskAttempt(
            String tenantId, UUID taskExecutionAttemptId, String resultCode, Instant finishedAt);

    record Registration(DeliveryRecord delivery, boolean created) {
    }

    record AttemptStart(UUID deliveryAttemptId, boolean created, String status) {
    }

    record DeliveryRecord(
            OutboundDeliveryView view,
            String operatorDisplayValue,
            String payloadObjectRef,
            String failurePolicyVersionId
    ) {
    }

    record NewDelivery(
            UUID deliveryId,
            String tenantId,
            UUID projectId,
            String connectorVersionId,
            String mappingVersionId,
            String businessMessageType,
            String businessKey,
            UUID sourceReviewCaseId,
            UUID sourceTaskId,
            UUID sourceWorkOrderId,
            UUID sourceSnapshotId,
            String sourceSnapshotDigest,
            String externalOrderCode,
            String operatorPrincipalId,
            String operatorDisplayValue,
            String payloadObjectRef,
            String payloadDigest,
            String externalIdempotencyKey,
            String failurePolicyVersionId,
            String createdBy,
            Instant createdAt
    ) {
    }

    record NewReplayRequest(
            UUID replayRequestId,
            UUID deliveryId,
            String tenantId,
            long expectedDeliveryVersion,
            String reason,
            String approvalRef,
            String requestedBy,
            UUID executionTaskId,
            Instant requestedAt
    ) {
    }
}
