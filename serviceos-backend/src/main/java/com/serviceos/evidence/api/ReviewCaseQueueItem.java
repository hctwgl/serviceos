package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 审核队列安全摘要；不含 snapshot digest、操作者与决定自由文本。 */
public record ReviewCaseQueueItem(
        UUID reviewCaseId,
        UUID projectId,
        UUID taskId,
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
        UUID latestDecisionId,
        String latestDecision,
        String latestDecisionSource,
        List<String> latestReasonCodes,
        Instant latestDecisionAt
) {
    public ReviewCaseQueueItem {
        latestReasonCodes = latestReasonCodes == null ? List.of() : List.copyOf(latestReasonCodes);
    }
}
