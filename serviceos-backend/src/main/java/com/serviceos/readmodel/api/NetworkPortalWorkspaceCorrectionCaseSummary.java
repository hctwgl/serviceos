package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * M225：Network Portal 工作区整改摘要；字段对齐 Admin
 * {@code WorkOrderWorkspaceCorrectionCaseSummary}，故意不含 createdBy/waiveNote/digest。
 */
public record NetworkPortalWorkspaceCorrectionCaseSummary(
        UUID correctionCaseId,
        UUID taskId,
        UUID projectId,
        UUID sourceReviewCaseId,
        UUID sourceReviewDecisionId,
        List<String> reasonCodes,
        UUID correctionTaskId,
        String status,
        Instant createdAt,
        UUID latestResubmissionSnapshotId,
        Instant closedAt,
        Instant waivedAt,
        List<NetworkPortalWorkspaceCorrectionResubmissionSummary> resubmissions
) {
    public NetworkPortalWorkspaceCorrectionCaseSummary {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        resubmissions = resubmissions == null ? List.of() : List.copyOf(resubmissions);
    }
}
