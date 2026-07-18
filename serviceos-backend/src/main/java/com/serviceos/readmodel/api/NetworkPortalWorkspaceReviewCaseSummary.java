package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * M229：Network Portal 工作区审核案例摘要；字段对齐 Admin
 * {@code WorkOrderWorkspaceReviewCaseSummary}，故意不含 createdBy/digest。
 */
public record NetworkPortalWorkspaceReviewCaseSummary(
        UUID reviewCaseId,
        UUID taskId,
        UUID projectId,
        UUID evidenceSetSnapshotId,
        String scopeType,
        String origin,
        String policyVersion,
        String status,
        Instant createdAt,
        Instant decidedAt,
        UUID sourceReviewCaseId,
        String externalSubmissionRef,
        String callbackBatchRef,
        String mappingVersionId,
        UUID reopenedFromReviewCaseId,
        String reopenTriggerRef,
        List<NetworkPortalWorkspaceReviewDecisionSummary> decisions
) {
    public NetworkPortalWorkspaceReviewCaseSummary {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }
}
