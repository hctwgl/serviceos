package com.serviceos.evidence.api;

import java.util.UUID;

/**
 * 不含 Revision 图、文件引用与采集元数据的 EvidenceItem 安全摘要。
 *
 * <p>M426：可选 {@code latestRevisionId}/{@code latestMimeType} 仅作授权预览指针，
 * 不得携带 {@code fileObjectId}/digest/size/URL。</p>
 */
public record EvidenceItemSummaryView(
        UUID evidenceItemId,
        UUID taskId,
        UUID projectId,
        UUID evidenceSlotId,
        int itemOrdinal,
        String status,
        int revisionCount,
        Integer latestRevisionNumber,
        String latestRevisionStatus,
        UUID latestRevisionId,
        String latestMimeType
) {
}
