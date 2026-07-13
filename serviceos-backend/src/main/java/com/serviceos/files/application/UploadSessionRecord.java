package com.serviceos.files.application;

import java.time.Instant;
import java.util.UUID;

public record UploadSessionRecord(
        UUID sessionId,
        UUID fileId,
        String tenantId,
        String objectKey,
        String businessContextType,
        String businessContextId,
        String originalFileName,
        String declaredMimeType,
        long expectedSize,
        String expectedSha256,
        String status,
        Instant expiresAt,
        String finalizeRequestDigest,
        String finalizeCommandId,
        UUID finalizationToken,
        Instant finalizingStartedAt,
        String createdBy,
        Instant createdAt
) {
}
