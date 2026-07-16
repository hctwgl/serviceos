package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 整改队列安全摘要；不含 snapshot digest、操作者与豁免/关闭自由文本。 */
public record CorrectionCaseQueueItem(
        UUID correctionCaseId,
        UUID projectId,
        UUID taskId,
        UUID sourceReviewCaseId,
        UUID sourceReviewDecisionId,
        List<String> reasonCodes,
        UUID correctionTaskId,
        String status,
        Instant createdAt,
        UUID latestResubmissionSnapshotId,
        Instant closedAt,
        Instant waivedAt,
        int resubmissionCount
) {
    public CorrectionCaseQueueItem {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
