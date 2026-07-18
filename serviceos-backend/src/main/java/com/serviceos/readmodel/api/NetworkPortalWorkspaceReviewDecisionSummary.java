package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * M229：Network Portal 工作区审核决定摘要；字段对齐 Admin
 * {@code WorkOrderWorkspaceReviewDecisionSummary}，故意不含 note/approvalRef/decidedBy。
 */
public record NetworkPortalWorkspaceReviewDecisionSummary(
        UUID reviewDecisionId,
        int decisionOrdinal,
        String decision,
        String decisionSource,
        List<String> reasonCodes,
        Instant decidedAt
) {
    public NetworkPortalWorkspaceReviewDecisionSummary {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
