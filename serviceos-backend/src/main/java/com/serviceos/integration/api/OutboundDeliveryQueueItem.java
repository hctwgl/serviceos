package com.serviceos.integration.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 外发交付队列安全摘要。
 *
 * <p>不含 payload/snapshot digest、对象存储引用、操作者、重放审批正文或 attempt 明细。</p>
 */
public record OutboundDeliveryQueueItem(
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
        String externalOrderCode,
        UUID executionTaskId,
        String status,
        UUID clientReviewCaseId,
        UUID reviewRouteId,
        long aggregateVersion,
        int attemptCount,
        Instant createdAt,
        Instant deliveredAt,
        Instant acknowledgedAt
) {
}
