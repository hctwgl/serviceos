package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 审核案例投影。
 *
 * <p>M364：{@code taskId} 仍为源提交 Task；{@code reviewTaskId} 为独立审核 HUMAN Task
 * （可空：CLIENT Case 或迁移前历史行）。</p>
 */
public record ReviewCaseView(
        UUID reviewCaseId,
        UUID projectId,
        UUID taskId,
        UUID reviewTaskId,
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
    /** 兼容旧测试构造：无 reviewTaskId，默认 aggregateVersion=1。 */
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
                reviewCaseId, projectId, taskId, null, evidenceSetSnapshotId, snapshotContentDigest,
                scopeType, origin, policyVersion, status, createdBy, createdAt, decidedAt,
                sourceReviewCaseId, externalSubmissionRef, callbackBatchRef, mappingVersionId,
                reopenedFromReviewCaseId, reopenTriggerRef, decisions, 1L);
    }
}
