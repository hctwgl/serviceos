package com.serviceos.evidence.application;

import java.time.Instant;
import java.util.UUID;

public record EvidenceUploadBinding(
        UUID uploadSessionId,
        String tenantId,
        UUID projectId,
        UUID taskId,
        UUID slotId,
        UUID fileId,
        UUID evidenceItemId,
        String expectedSha256,
        String declaredMimeType,
        long expectedSizeBytes,
        String originalFileName,
        String captureMetadataJson,
        String status,
        String createdBy,
        Instant createdAt
) {
}
