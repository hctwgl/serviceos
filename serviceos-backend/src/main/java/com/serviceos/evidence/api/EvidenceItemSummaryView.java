package com.serviceos.evidence.api;

import java.util.UUID;

/** 不含 Revision、文件引用与采集元数据的 EvidenceItem 安全摘要。 */
public record EvidenceItemSummaryView(
        UUID evidenceItemId,
        UUID taskId,
        UUID projectId,
        UUID evidenceSlotId,
        int itemOrdinal,
        String status,
        int revisionCount,
        Integer latestRevisionNumber,
        String latestRevisionStatus
) {
}
