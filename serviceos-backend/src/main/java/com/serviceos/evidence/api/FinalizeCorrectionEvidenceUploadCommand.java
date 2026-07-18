package com.serviceos.evidence.api;

import java.util.UUID;

public record FinalizeCorrectionEvidenceUploadCommand(
        UUID correctionCaseId,
        UUID correctionTaskId,
        UUID sourceTaskId,
        UUID slotId,
        UUID uploadSessionId,
        String actualSha256,
        String finalizeCommandId
) {
}
