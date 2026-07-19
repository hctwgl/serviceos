package com.serviceos.integration.api;

import java.util.UUID;

/** 批准或拒绝批量重放。decision=APPROVE|REJECT。 */
public record ApproveBatchReplayCommand(
        UUID batchId,
        String decision,
        String decisionNote,
        Integer maxItems
) {
}
