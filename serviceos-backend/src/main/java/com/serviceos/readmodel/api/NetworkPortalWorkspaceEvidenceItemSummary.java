package com.serviceos.readmodel.api;

import java.util.UUID;

/**
 * M223：Network Portal 工作区 EvidenceItem 摘要；字段对齐 Admin
 * {@code WorkOrderWorkspaceEvidenceItemSummary}，故意不含 Revision 图/file/metadata。
 *
 * <p>M426：契约对齐增加 {@code latestRevisionId}/{@code latestMimeType}；本切片不交付 NP UI。</p>
 */
public record NetworkPortalWorkspaceEvidenceItemSummary(
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
