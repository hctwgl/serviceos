package com.serviceos.readmodel.application;

import com.serviceos.readmodel.api.WorkOrderTimelineItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WorkOrderTimelineRepository {
    void append(TimelineEntry entry);

    List<WorkOrderTimelineItem> findPage(
            String tenantId,
            UUID workOrderId,
            Instant beforeOccurredAt,
            UUID beforeEntryId,
            int fetchSize);

    Instant findLastProjectedAt(String tenantId, UUID workOrderId);

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
            Instant receivedAt
    ) {
    }
}
