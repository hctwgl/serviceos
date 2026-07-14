package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 整改案例投影。 */
public record CorrectionCaseView(
        UUID correctionCaseId,
        UUID projectId,
        UUID taskId,
        UUID sourceReviewCaseId,
        UUID sourceReviewDecisionId,
        UUID sourceEvidenceSetSnapshotId,
        String sourceSnapshotContentDigest,
        List<String> reasonCodes,
        String status,
        String createdBy,
        Instant createdAt,
        UUID latestResubmissionSnapshotId,
        String closedBy,
        Instant closedAt,
        List<CorrectionResubmissionView> resubmissions
) {
}
