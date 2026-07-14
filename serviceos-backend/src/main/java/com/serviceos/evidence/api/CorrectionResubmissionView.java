package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.UUID;

/** 只追加的整改补传轮次投影。 */
public record CorrectionResubmissionView(
        UUID correctionResubmissionId,
        UUID correctionCaseId,
        int resubmissionOrdinal,
        UUID evidenceSetSnapshotId,
        String snapshotContentDigest,
        String submittedBy,
        Instant submittedAt
) {
}
