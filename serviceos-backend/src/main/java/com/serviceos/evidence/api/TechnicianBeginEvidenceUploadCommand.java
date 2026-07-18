package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.UUID;

/** Technician 在线 Begin：主体、网点、receivedAt、offline 与上传者均由服务端权威生成。 */
public record TechnicianBeginEvidenceUploadCommand(
        UUID taskId,
        UUID slotId,
        UUID evidenceItemId,
        String originalFileName,
        String declaredMimeType,
        long expectedSize,
        String expectedSha256,
        String captureSource,
        Instant capturedAt
) {
}
