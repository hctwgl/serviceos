package com.serviceos.evidence.api;

import java.util.UUID;

/** Finalize Evidence Upload；Finalize 成功前不得创建 EvidenceRevision。 */
public record FinalizeEvidenceUploadCommand(
        UUID taskId,
        UUID slotId,
        UUID uploadSessionId,
        String actualSha256,
        String finalizeCommandId
) {
}
