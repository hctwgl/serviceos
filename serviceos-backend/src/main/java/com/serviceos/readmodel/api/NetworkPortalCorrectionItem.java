package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Network Portal 整改队列安全摘要。
 * <p>
 * 字段语义对齐 {@code CorrectionCaseQueueItem}；不含 snapshot digest、操作者与豁免/关闭自由文本。
 */
public record NetworkPortalCorrectionItem(
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
    public NetworkPortalCorrectionItem {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
