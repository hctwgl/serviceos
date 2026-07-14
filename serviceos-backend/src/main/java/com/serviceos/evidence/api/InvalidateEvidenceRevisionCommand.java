package com.serviceos.evidence.api;

import java.util.UUID;

/** 授权作废 VALIDATED EvidenceRevision。 */
public record InvalidateEvidenceRevisionCommand(
        UUID evidenceRevisionId,
        String reasonCode,
        String approvalRef
) {
}
