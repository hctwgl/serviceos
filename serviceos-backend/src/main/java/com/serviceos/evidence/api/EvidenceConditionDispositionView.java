package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.UUID;

/** 条件变化处置的不可变公开视图。 */
public record EvidenceConditionDispositionView(
        UUID dispositionId,
        UUID taskId,
        UUID slotId,
        UUID resolutionId,
        String decision,
        String reasonCode,
        String reviewRef,
        String decidedBy,
        Instant decidedAt
) {
}
