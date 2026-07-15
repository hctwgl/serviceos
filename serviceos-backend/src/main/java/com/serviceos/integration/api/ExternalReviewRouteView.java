package com.serviceos.integration.api;

import java.time.Instant;
import java.util.UUID;

/** BYD 外部订单到 CLIENT ReviewCase 的权威回调路由。 */
public record ExternalReviewRouteView(
        UUID reviewRouteId,
        UUID projectId,
        String connectorVersionId,
        String externalOrderCode,
        UUID reviewCaseId,
        String externalSubmissionRef,
        String callbackBatchRef,
        String mappingVersionId,
        String status,
        UUID canonicalMessageId,
        String createdBy,
        Instant createdAt,
        Instant completedAt
) {
}
