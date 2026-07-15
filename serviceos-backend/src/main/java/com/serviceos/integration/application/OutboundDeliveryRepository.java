package com.serviceos.integration.application;

import com.serviceos.integration.api.OutboundDeliveryView;

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

    Optional<DeliveryRecord> findBySourceReview(
            String tenantId, UUID sourceReviewCaseId, String businessMessageType);

    AttemptStart startAttempt(
            String tenantId,
            UUID deliveryId,
            UUID taskExecutionAttemptId,
            int attemptNo,
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

    void failPending(String tenantId, UUID deliveryId, String resultCode, Instant failedAt);

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
}
