package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/**
 * M223：Network Portal 工作区 EvidenceSlot 摘要；字段对齐 Admin
 * {@code WorkOrderWorkspaceEvidenceSlotSummary}，故意不含 definition/explanation JSON。
 */
public record NetworkPortalWorkspaceEvidenceSlotSummary(
        UUID slotId,
        UUID taskId,
        UUID projectId,
        String templateKey,
        String templateVersion,
        String requirementCode,
        String occurrenceKey,
        String requirementName,
        String mediaType,
        boolean required,
        int minCount,
        Integer maxCount,
        String status,
        Instant resolvedAt,
        int slotGeneration,
        boolean active,
        String transition,
        String requiredDisposition
) {
}
