package com.serviceos.readmodel.application;

import com.serviceos.readmodel.api.WorkOrderTimelineItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WorkOrderTimelineRepository {
    /** 实时 Inbox 路径：冲突视为异常（Inbox 应已拦截重放）。 */
    void append(TimelineEntry entry);

    /** 重建路径：同 generation 幂等，冲突时返回 false。 */
    boolean appendIfAbsent(TimelineEntry entry);

    List<WorkOrderTimelineItem> findPage(
            String tenantId,
            UUID workOrderId,
            int rebuildGeneration,
            Instant beforeOccurredAt,
            UUID beforeEntryId,
            int fetchSize);

    Instant findLastProjectedAt(String tenantId, UUID workOrderId, int rebuildGeneration);

    long countGeneration(int rebuildGeneration);

    record TimelineEntry(
            UUID timelineEntryId,
            String tenantId,
            UUID projectId,
            UUID workOrderId,
            UUID sourceEventId,
            String sourceModule,
            String eventType,
            int schemaVersion,
            String category,
            String resourceType,
            UUID resourceId,
            long resourceVersion,
            String resourceCode,
            String outcomeCode,
            String actorId,
            String correlationId,
            String displayTemplateCode,
            int displayTemplateVersion,
            Instant occurredAt,
            Instant receivedAt,
            int rebuildGeneration
    ) {
    }
}
