package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.UUID;

/** Technician 整改资料 Begin；源 Task、整改 Task 与 Slot 均由服务端资源关系派生。 */
public record TechnicianBeginCorrectionEvidenceUploadCommand(
        UUID evidenceItemId,
        String originalFileName,
        String declaredMimeType,
        long expectedSize,
        String expectedSha256,
        String captureSource,
        Instant capturedAt
) {
}
