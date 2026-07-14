package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Evidence 编排的受限上传会话；上传事实仍由 files 权威维护。 */
public record EvidenceUploadSessionView(
        UUID uploadSessionId,
        UUID fileId,
        UUID taskId,
        UUID evidenceSlotId,
        UUID evidenceItemId,
        String status,
        String uploadMethod,
        String uploadUrl,
        Map<String, String> requiredHeaders,
        Instant uploadAuthorizationExpiresAt,
        Instant sessionExpiresAt
) {
}
