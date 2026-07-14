package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 不可变资料集合快照投影。 */
public record EvidenceSetSnapshotView(
        UUID evidenceSetSnapshotId,
        UUID taskId,
        UUID projectId,
        UUID resolutionId,
        String purpose,
        int memberCount,
        String contentDigest,
        String eligibilitySummaryJson,
        String createdBy,
        Instant createdAt,
        List<EvidenceSetSnapshotMemberView> members
) {
}
