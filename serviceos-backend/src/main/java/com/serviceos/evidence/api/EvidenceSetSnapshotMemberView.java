package com.serviceos.evidence.api;

import java.util.UUID;

/** Snapshot 冻结成员投影；不含文件 URL 或完整 CaptureMetadata。 */
public record EvidenceSetSnapshotMemberView(
        UUID memberId,
        UUID evidenceSlotId,
        UUID evidenceItemId,
        UUID evidenceRevisionId,
        int revisionNumber,
        String revisionStatus,
        String contentDigest,
        String validationDigest,
        int memberOrdinal
) {
}
