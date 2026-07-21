package com.serviceos.sla.infrastructure;

import com.serviceos.jooq.generated.tables.SlaInstance;
import com.serviceos.sla.application.SlaQueryRepository;
import com.serviceos.sla.application.SlaStoredInstance;
import com.serviceos.sla.application.SlaStoredMilestone;
import com.serviceos.sla.application.SlaStoredSegment;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.SlaClockSegment.SLA_CLOCK_SEGMENT;
import static com.serviceos.jooq.generated.tables.SlaInstance.SLA_INSTANCE;
import static com.serviceos.jooq.generated.tables.SlaMilestone.SLA_MILESTONE;

/**
 * SLA 查询的 jOOQ 实现（ADR-091）。与原 MyBatis 动态查询逐条等价：非全租户视角且无授权项目时
 * 退化为 AND FALSE，翻页游标为 (deadline_at, sla_instance_id) 行值比较。
 */
@Repository
final class JooqSlaQueryRepository implements SlaQueryRepository {
    private static final SlaInstance INSTANCE = SLA_INSTANCE;
    private static final List<SelectField<?>> INSTANCE_FIELDS = List.of(
            INSTANCE.SLA_INSTANCE_ID, INSTANCE.PROJECT_ID, INSTANCE.WORK_ORDER_ID,
            INSTANCE.TASK_ID, INSTANCE.SLA_REF, INSTANCE.POLICY_VERSION_ID,
            INSTANCE.POLICY_SEMANTIC_VERSION, INSTANCE.POLICY_CONTENT_DIGEST,
            INSTANCE.CLOCK_MODE, INSTANCE.TARGET_DURATION_SECONDS,
            INSTANCE.STARTED_AT, INSTANCE.DEADLINE_AT, INSTANCE.STATUS,
            INSTANCE.BREACHED_AT, INSTANCE.BREACH_DETECTED_AT, INSTANCE.COMPLETED_AT,
            INSTANCE.ELAPSED_SECONDS, INSTANCE.AGGREGATE_VERSION);

    private final DSLContext dsl;

    JooqSlaQueryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<SlaStoredInstance> findPage(
            String tenantId, boolean tenantWide, List<UUID> projectIds, UUID workOrderId, String status,
            Instant cursorDeadlineAt, UUID cursorId, int fetchSize
    ) {
        Condition condition = INSTANCE.TENANT_ID.eq(tenantId);
        if (!tenantWide) {
            // 非全租户视角且无任何授权项目时结果必须为空（AND FALSE），不得退化为全量。
            condition = condition.and(projectIds.isEmpty()
                    ? DSL.falseCondition()
                    : INSTANCE.PROJECT_ID.in(projectIds));
        }
        if (workOrderId != null) {
            condition = condition.and(INSTANCE.WORK_ORDER_ID.eq(workOrderId));
        }
        if (status != null) {
            condition = condition.and(INSTANCE.STATUS.eq(status));
        }
        if (cursorDeadlineAt != null) {
            condition = condition.and(
                    DSL.row(INSTANCE.DEADLINE_AT, INSTANCE.SLA_INSTANCE_ID).gt(cursorDeadlineAt, cursorId));
        }
        return dsl.select(INSTANCE_FIELDS)
                .from(INSTANCE)
                .where(condition)
                .orderBy(INSTANCE.DEADLINE_AT, INSTANCE.SLA_INSTANCE_ID)
                .limit(fetchSize)
                .fetch(JooqSlaQueryRepository::instance);
    }

    @Override
    public Optional<SlaStoredInstance> findById(String tenantId, UUID slaInstanceId) {
        return dsl.select(INSTANCE_FIELDS)
                .from(INSTANCE)
                .where(INSTANCE.TENANT_ID.eq(tenantId))
                .and(INSTANCE.SLA_INSTANCE_ID.eq(slaInstanceId))
                .fetchOptional()
                .map(JooqSlaQueryRepository::instance);
    }

    @Override
    public List<SlaStoredSegment> findSegments(String tenantId, UUID slaInstanceId) {
        return dsl.select(
                        SLA_CLOCK_SEGMENT.SEGMENT_ID, SLA_CLOCK_SEGMENT.SEGMENT_NO,
                        SLA_CLOCK_SEGMENT.SEGMENT_TYPE, SLA_CLOCK_SEGMENT.STARTED_AT,
                        SLA_CLOCK_SEGMENT.ENDED_AT, SLA_CLOCK_SEGMENT.ELAPSED_SECONDS,
                        SLA_CLOCK_SEGMENT.START_EVENT_ID, SLA_CLOCK_SEGMENT.END_EVENT_ID)
                .from(SLA_CLOCK_SEGMENT)
                .where(SLA_CLOCK_SEGMENT.TENANT_ID.eq(tenantId))
                .and(SLA_CLOCK_SEGMENT.SLA_INSTANCE_ID.eq(slaInstanceId))
                .orderBy(SLA_CLOCK_SEGMENT.SEGMENT_NO)
                .fetch(row -> new SlaStoredSegment(
                        row.get(SLA_CLOCK_SEGMENT.SEGMENT_ID), row.get(SLA_CLOCK_SEGMENT.SEGMENT_NO),
                        row.get(SLA_CLOCK_SEGMENT.SEGMENT_TYPE), row.get(SLA_CLOCK_SEGMENT.STARTED_AT),
                        row.get(SLA_CLOCK_SEGMENT.ENDED_AT), row.get(SLA_CLOCK_SEGMENT.ELAPSED_SECONDS),
                        row.get(SLA_CLOCK_SEGMENT.START_EVENT_ID), row.get(SLA_CLOCK_SEGMENT.END_EVENT_ID)));
    }

    @Override
    public List<SlaStoredMilestone> findMilestones(String tenantId, UUID slaInstanceId) {
        return dsl.select(
                        SLA_MILESTONE.MILESTONE_ID, SLA_MILESTONE.MILESTONE_TYPE,
                        SLA_MILESTONE.SCHEDULED_AT, SLA_MILESTONE.STATUS,
                        SLA_MILESTONE.TRIGGERED_AT, SLA_MILESTONE.DETECTED_AT,
                        SLA_MILESTONE.TRIGGER_EVENT_ID)
                .from(SLA_MILESTONE)
                .where(SLA_MILESTONE.TENANT_ID.eq(tenantId))
                .and(SLA_MILESTONE.SLA_INSTANCE_ID.eq(slaInstanceId))
                .orderBy(SLA_MILESTONE.SCHEDULED_AT, SLA_MILESTONE.MILESTONE_ID)
                .fetch(row -> new SlaStoredMilestone(
                        row.get(SLA_MILESTONE.MILESTONE_ID), row.get(SLA_MILESTONE.MILESTONE_TYPE),
                        row.get(SLA_MILESTONE.SCHEDULED_AT), row.get(SLA_MILESTONE.STATUS),
                        row.get(SLA_MILESTONE.TRIGGERED_AT), row.get(SLA_MILESTONE.DETECTED_AT),
                        row.get(SLA_MILESTONE.TRIGGER_EVENT_ID)));
    }

    private static SlaStoredInstance instance(Record row) {
        return new SlaStoredInstance(
                row.get(INSTANCE.SLA_INSTANCE_ID), row.get(INSTANCE.PROJECT_ID),
                row.get(INSTANCE.WORK_ORDER_ID), row.get(INSTANCE.TASK_ID),
                row.get(INSTANCE.SLA_REF), row.get(INSTANCE.POLICY_VERSION_ID),
                row.get(INSTANCE.POLICY_SEMANTIC_VERSION), row.get(INSTANCE.POLICY_CONTENT_DIGEST),
                row.get(INSTANCE.CLOCK_MODE), row.get(INSTANCE.TARGET_DURATION_SECONDS),
                row.get(INSTANCE.STARTED_AT), row.get(INSTANCE.DEADLINE_AT),
                row.get(INSTANCE.STATUS), row.get(INSTANCE.BREACHED_AT),
                row.get(INSTANCE.BREACH_DETECTED_AT), row.get(INSTANCE.COMPLETED_AT),
                row.get(INSTANCE.ELAPSED_SECONDS), row.get(INSTANCE.AGGREGATE_VERSION));
    }
}
