package com.serviceos.evidence.application;

import java.time.Instant;
import java.util.UUID;

/** 条件变更人工处置只追加事实；KEEP 与 INVALIDATE 都必须引用审核依据。 */
public record EvidenceConditionDisposition(
        UUID dispositionId,
        String tenantId,
        UUID projectId,
        UUID taskId,
        UUID resolutionId,
        UUID memberId,
        UUID slotId,
        String decision,
        String reasonCode,
        String reviewRef,
        String decidedBy,
        Instant decidedAt,
        String requestDigest
) {
}
