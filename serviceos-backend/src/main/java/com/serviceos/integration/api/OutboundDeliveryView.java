package com.serviceos.integration.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 不可变外部交付意图及其可审计执行摘要。 */
public record OutboundDeliveryView(
        UUID deliveryId,
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
        String payloadDigest,
        String externalIdempotencyKey,
        UUID executionTaskId,
        String status,
        UUID clientReviewCaseId,
        UUID reviewRouteId,
        long aggregateVersion,
        Instant createdAt,
        Instant deliveredAt,
        Instant acknowledgedAt,
        List<DeliveryAttemptView> attempts,
        List<ExternalAcknowledgementView> acknowledgements,
        List<DeliveryReplayRequestView> replayRequests
) {
}
