package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 逻辑资料项及其不可变版本投影。 */
public record EvidenceItemView(
        UUID evidenceItemId,
        UUID taskId,
        UUID projectId,
        UUID evidenceSlotId,
        int itemOrdinal,
        String status,
        String createdBy,
        Instant createdAt,
        List<EvidenceRevisionView> revisions
) {
}
