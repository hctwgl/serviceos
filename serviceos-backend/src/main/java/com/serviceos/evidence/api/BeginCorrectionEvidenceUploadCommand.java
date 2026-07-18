package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.UUID;

/** 已完成源 Task 的整改上传命令；独立 correction Task 提供实时责任，不改变源 Task 终态。 */
public record BeginCorrectionEvidenceUploadCommand(
        UUID correctionCaseId,
        UUID correctionTaskId,
        UUID sourceTaskId,
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
