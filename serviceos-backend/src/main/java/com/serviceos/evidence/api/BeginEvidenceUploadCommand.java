package com.serviceos.evidence.api;

import java.util.UUID;

/** Begin Evidence Upload 命令；tenant/project/uploader 不接受客户端权威值。 */
public record BeginEvidenceUploadCommand(
        UUID taskId,
        UUID slotId,
        UUID evidenceItemId,
        String originalFileName,
        String declaredMimeType,
        long expectedSize,
        String expectedSha256,
        String captureMetadataJson
) {
}
