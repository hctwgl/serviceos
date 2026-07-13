package com.serviceos.files.application;

import com.serviceos.files.api.StoredFileView;

import java.time.Instant;
import java.util.UUID;

public record StoredFileRecord(
        UUID fileId,
        String tenantId,
        UUID uploadSessionId,
        String objectKey,
        String originalFileName,
        String checksumSha256,
        long size,
        String declaredMimeType,
        String detectedMimeType,
        String lifecycleStatus,
        String quarantineReason,
        Instant createdAt,
        long version
) {
    public StoredFileView toView() {
        return new StoredFileView(
                fileId, tenantId, originalFileName, checksumSha256, size,
                declaredMimeType, detectedMimeType, lifecycleStatus,
                quarantineReason, createdAt, version);
    }
}
