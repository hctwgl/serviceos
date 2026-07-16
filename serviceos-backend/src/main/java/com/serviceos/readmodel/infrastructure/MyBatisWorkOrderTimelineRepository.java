package com.serviceos.readmodel.infrastructure;

import com.serviceos.readmodel.api.WorkOrderTimelineItem;
import com.serviceos.readmodel.application.WorkOrderTimelineRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
final class MyBatisWorkOrderTimelineRepository implements WorkOrderTimelineRepository {
    private final WorkOrderTimelineMapper mapper;

    MyBatisWorkOrderTimelineRepository(WorkOrderTimelineMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(TimelineEntry entry) {
        if (!appendIfAbsent(entry)) {
            throw new IllegalStateException("时间线来源事件已存在但 Inbox 未识别为重放");
        }
    }

    @Override
    public boolean appendIfAbsent(TimelineEntry entry) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("timelineEntryId", entry.timelineEntryId());
        values.put("tenantId", entry.tenantId());
        values.put("projectId", entry.projectId());
        values.put("workOrderId", entry.workOrderId());
        values.put("sourceEventId", entry.sourceEventId());
        values.put("sourceModule", entry.sourceModule());
        values.put("eventType", entry.eventType());
        values.put("schemaVersion", entry.schemaVersion());
        values.put("category", entry.category());
        values.put("resourceType", entry.resourceType());
        values.put("resourceId", entry.resourceId());
        values.put("resourceVersion", entry.resourceVersion());
        values.put("resourceCode", entry.resourceCode());
        values.put("outcomeCode", entry.outcomeCode());
        values.put("actorId", entry.actorId());
        values.put("correlationId", entry.correlationId());
        values.put("displayTemplateCode", entry.displayTemplateCode());
        values.put("displayTemplateVersion", entry.displayTemplateVersion());
        values.put("occurredAt", entry.occurredAt());
        values.put("receivedAt", entry.receivedAt());
        values.put("rebuildGeneration", entry.rebuildGeneration());
        return mapper.append(values) == 1;
    }

    @Override
    public List<WorkOrderTimelineItem> findPage(
            String tenantId,
            UUID workOrderId,
            int rebuildGeneration,
            Instant beforeOccurredAt,
            UUID beforeEntryId,
            int fetchSize
    ) {
        return mapper.findPage(
                        tenantId, workOrderId, rebuildGeneration,
                        beforeOccurredAt, beforeEntryId, fetchSize)
                .stream()
                .map(MyBatisWorkOrderTimelineRepository::item)
                .toList();
    }

    @Override
    public Instant findLastProjectedAt(String tenantId, UUID workOrderId, int rebuildGeneration) {
        return mapper.findLastProjectedAt(tenantId, workOrderId, rebuildGeneration);
    }

    @Override
    public long countGeneration(int rebuildGeneration) {
        return mapper.countGeneration(rebuildGeneration);
    }

    @Override
    public long deleteGeneration(int rebuildGeneration) {
        return mapper.deleteGeneration(rebuildGeneration);
    }

    private static WorkOrderTimelineItem item(Map<String, Object> row) {
        return new WorkOrderTimelineItem(
                uuid(row, "id"),
                string(row, "category"),
                string(row, "eventType"),
                number(row, "schemaVersion").intValue(),
                instant(row, "occurredAt"),
                instant(row, "receivedAt"),
                string(row, "actorId"),
                string(row, "resourceType"),
                uuid(row, "resourceId"),
                number(row, "resourceVersion").longValue(),
                string(row, "resourceCode"),
                string(row, "outcomeCode"),
                string(row, "correlationId"),
                string(row, "displayTemplateCode"),
                number(row, "displayTemplateVersion").intValue());
    }

    private static String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private static Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return ((java.sql.Timestamp) value).toInstant();
    }
}
