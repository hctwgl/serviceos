package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 只追加审核决定投影。 */
public record ReviewDecisionView(
        UUID reviewDecisionId,
        UUID reviewCaseId,
        int decisionOrdinal,
        String decision,
        String decisionSource,
        List<String> reasonCodes,
        String note,
        String approvalRef,
        String decidedBy,
        Instant decidedAt
) {
}
