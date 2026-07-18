package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/**
 * M225：Network Portal 工作区整改补传摘要；字段对齐 Admin
 * {@code WorkOrderWorkspaceCorrectionResubmissionSummary}。
 */
public record NetworkPortalWorkspaceCorrectionResubmissionSummary(
        UUID correctionResubmissionId,
        int resubmissionOrdinal,
        UUID evidenceSetSnapshotId,
        Instant submittedAt
) {
}
