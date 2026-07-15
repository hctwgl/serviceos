package com.serviceos.evidence.api;

import java.util.UUID;

/** 对最新解析代次中 REVIEW_REQUIRED 槽位作出显式人工处置。 */
public record ResolveEvidenceConditionChangeCommand(
        UUID taskId,
        UUID slotId,
        UUID expectedResolutionId,
        String decision,
        String reasonCode,
        String reviewRef
) {
}
