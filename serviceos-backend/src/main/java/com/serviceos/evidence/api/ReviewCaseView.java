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
        List<ReviewDecisionView> decisions,
        long aggregateVersion
) {
    /** 兼容旧测试构造：默认 aggregateVersion=1。 */
    public ReviewCaseView(
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
        this(
                reviewCaseId, projectId, taskId, evidenceSetSnapshotId, snapshotContentDigest,
                scopeType, origin, policyVersion, status, createdBy, createdAt, decidedAt,
                sourceReviewCaseId, externalSubmissionRef, callbackBatchRef, mappingVersionId,
                reopenedFromReviewCaseId, reopenTriggerRef, decisions, 1L);
    }
}
