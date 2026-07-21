package com.serviceos.readmodel.infrastructure;

import com.serviceos.jooq.generated.tables.RdmWorkOrderTimelineEntry;
import com.serviceos.readmodel.api.WorkOrderTimelineItem;
import com.serviceos.readmodel.application.WorkOrderTimelineRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.RdmWorkOrderTimelineEntry.RDM_WORK_ORDER_TIMELINE_ENTRY;

/**
 * 工单时间线投影的 jOOQ 实现。游标为 (occurred_at,timeline_entry_id) 行值比较，
 * 与原 MyBatis 查询逐条等价；append 幂等键为 (tenant_id,source_event_id,rebuild_generation)。
 */
@Repository
final class JooqWorkOrderTimelineRepository implements WorkOrderTimelineRepository {
    private static final RdmWorkOrderTimelineEntry T = RDM_WORK_ORDER_TIMELINE_ENTRY;

    private final DSLContext dsl;

    JooqWorkOrderTimelineRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void append(TimelineEntry entry) {
        if (!appendIfAbsent(entry)) {
            throw new IllegalStateException("时间线来源事件已存在但 Inbox 未识别为重放");
        }
    }

    @Override
    public boolean appendIfAbsent(TimelineEntry entry) {
        return dsl.insertInto(T)
                .set(T.TIMELINE_ENTRY_ID, entry.timelineEntryId())
                .set(T.TENANT_ID, entry.tenantId())
                .set(T.PROJECT_ID, entry.projectId())
                .set(T.WORK_ORDER_ID, entry.workOrderId())
                .set(T.SOURCE_EVENT_ID, entry.sourceEventId())
                .set(T.SOURCE_MODULE, entry.sourceModule())
                .set(T.EVENT_TYPE, entry.eventType())
                .set(T.SCHEMA_VERSION, entry.schemaVersion())
                .set(T.CATEGORY, entry.category())
                .set(T.RESOURCE_TYPE, entry.resourceType())
                .set(T.RESOURCE_ID, entry.resourceId())
                .set(T.RESOURCE_VERSION, entry.resourceVersion())
                .set(T.RESOURCE_CODE, entry.resourceCode())
                .set(T.OUTCOME_CODE, entry.outcomeCode())
                .set(T.ACTOR_ID, entry.actorId())
                .set(T.CORRELATION_ID, entry.correlationId())
                .set(T.DISPLAY_TEMPLATE_CODE, entry.displayTemplateCode())
                .set(T.DISPLAY_TEMPLATE_VERSION, entry.displayTemplateVersion())
                .set(T.OCCURRED_AT, entry.occurredAt())
                .set(T.RECEIVED_AT, entry.receivedAt())
                .set(T.REBUILD_GENERATION, entry.rebuildGeneration())
                .onConflict(T.TENANT_ID, T.SOURCE_EVENT_ID, T.REBUILD_GENERATION)
                .doNothing()
                .execute() == 1;
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
        Condition condition = T.TENANT_ID.eq(tenantId)
                .and(T.WORK_ORDER_ID.eq(workOrderId))
                .and(T.REBUILD_GENERATION.eq(rebuildGeneration));
        if (beforeOccurredAt != null) {
            condition = condition.and(
                    DSL.row(T.OCCURRED_AT, T.TIMELINE_ENTRY_ID).lt(beforeOccurredAt, beforeEntryId));
        }
        return dsl.select(
                        T.TIMELINE_ENTRY_ID, T.CATEGORY, T.EVENT_TYPE, T.SCHEMA_VERSION,
                        T.OCCURRED_AT, T.RECEIVED_AT, T.ACTOR_ID, T.RESOURCE_TYPE, T.RESOURCE_ID,
                        T.RESOURCE_VERSION, T.RESOURCE_CODE, T.OUTCOME_CODE, T.CORRELATION_ID,
                        T.DISPLAY_TEMPLATE_CODE, T.DISPLAY_TEMPLATE_VERSION)
                .from(T)
                .where(condition)
                .orderBy(T.OCCURRED_AT.desc(), T.TIMELINE_ENTRY_ID.desc())
                .limit(fetchSize)
                .fetch(JooqWorkOrderTimelineRepository::item);
    }

    @Override
    public Instant findLastProjectedAt(String tenantId, UUID workOrderId, int rebuildGeneration) {
        // max(received_at)：无条目时聚合行值为 NULL，与原 MyBatis 返回 null 语义一致。
        var maxReceivedAt = DSL.max(T.RECEIVED_AT);
        return dsl.select(maxReceivedAt)
                .from(T)
                .where(T.TENANT_ID.eq(tenantId))
                .and(T.WORK_ORDER_ID.eq(workOrderId))
                .and(T.REBUILD_GENERATION.eq(rebuildGeneration))
                .fetchOne(maxReceivedAt);
    }

    @Override
    public long countGeneration(int rebuildGeneration) {
        // count(*) 恒有一行；按 long 读取与旧 resultType="long" 语义一致。
        return dsl.selectCount()
                .from(T)
                .where(T.REBUILD_GENERATION.eq(rebuildGeneration))
                .fetchSingle(0, long.class);
    }

    @Override
    public long deleteGeneration(int rebuildGeneration) {
        return dsl.deleteFrom(T)
                .where(T.REBUILD_GENERATION.eq(rebuildGeneration))
                .execute();
    }

    private static WorkOrderTimelineItem item(Record row) {
        return new WorkOrderTimelineItem(
                row.get(T.TIMELINE_ENTRY_ID),
                row.get(T.CATEGORY),
                row.get(T.EVENT_TYPE),
                row.get(T.SCHEMA_VERSION),
                row.get(T.OCCURRED_AT),
                row.get(T.RECEIVED_AT),
                row.get(T.ACTOR_ID),
                row.get(T.RESOURCE_TYPE),
                row.get(T.RESOURCE_ID),
                row.get(T.RESOURCE_VERSION),
                row.get(T.RESOURCE_CODE),
                row.get(T.OUTCOME_CODE),
                row.get(T.CORRELATION_ID),
                row.get(T.DISPLAY_TEMPLATE_CODE),
                row.get(T.DISPLAY_TEMPLATE_VERSION));
    }
}
