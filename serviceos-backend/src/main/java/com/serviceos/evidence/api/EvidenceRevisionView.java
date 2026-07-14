package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 一次不可变资料版本；不含 object key、URL 或完整敏感原图元数据。 */
public record EvidenceRevisionView(
        UUID evidenceRevisionId,
        UUID evidenceItemId,
        UUID evidenceSlotId,
        UUID taskId,
        UUID projectId,
        int revisionNumber,
        UUID fileObjectId,
        String contentDigest,
        String mimeType,
        long sizeBytes,
        String captureMetadataJson,
        String status,
        UUID sourceUploadSessionId,
        String finalizeCommandId,
        String createdBy,
        Instant createdAt,
        List<EvidenceValidationView> validations,
        String invalidatedBy,
        Instant invalidatedAt,
        String invalidationReasonCode,
        String invalidationApprovalRef
) {
}
