package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 审核案例投影。 */
public record ReviewCaseView(
        UUID reviewCaseId,
        UUID projectId,
        UUID taskId,
        UUID evidenceSetSnapshotId,
        String snapshotContentDigest,
        String scopeType,
        String origin,
        String policyVersion,
        String status,
        String createdBy,
        Instant createdAt,
        Instant decidedAt,
        UUID sourceReviewCaseId,
        String externalSubmissionRef,
        String callbackBatchRef,
        String mappingVersionId,
        UUID reopenedFromReviewCaseId,
        String reopenTriggerRef,
        List<ReviewDecisionView> decisions
) {
}
