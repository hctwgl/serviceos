package com.serviceos.integration.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 批量 UNKNOWN 重放申请摘要。 */
public record BatchReplayRequestView(
        UUID batchId,
        String mode,
        String status,
        String reason,
        String approvalRef,
        String requestedBy,
        String decidedBy,
        String decision,
        String decisionNote,
        int maxItems,
        Instant createdAt,
        Instant decidedAt,
        List<BatchReplayItemView> items
) {
    public record BatchReplayItemView(
            UUID deliveryId,
            UUID projectId,
            String eligibility,
            String ineligibilityCode,
            Long expectedDeliveryVersion,
            String itemStatus,
            UUID singleReplayRequestId,
            String errorCode
    ) {
    }
}
