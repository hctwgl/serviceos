package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.TechnicianActiveAssignmentQuery;
import com.serviceos.dispatch.api.TechnicianActiveAssignmentView;
import com.serviceos.jooq.generated.tables.DspServiceAssignment;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record9;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.DspServiceAssignment.DSP_SERVICE_ASSIGNMENT;

/**
 * 师傅 TECHNICIAN 责任列表：仅读 dispatch 拥有表，按 NETWORK 责任收敛网点范围。
 */
@Service
final class JooqTechnicianActiveAssignmentQuery implements TechnicianActiveAssignmentQuery {
    private final DSLContext dsl;

    JooqTechnicianActiveAssignmentQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianActiveAssignmentView> listActiveForTechnician(
            String tenantId, String networkId, Collection<String> assigneeIds
    ) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return List.of();
        }
        DspServiceAssignment t = DSP_SERVICE_ASSIGNMENT.as("t");
        return baseSelect(t)
                .where(t.TENANT_ID.eq(tenantId))
                .and(t.RESPONSIBILITY_LEVEL.eq("TECHNICIAN"))
                .and(t.STATUS.eq("ACTIVE"))
                .and(t.ASSIGNEE_ID.in(assigneeIds))
                .and(networkExists(t, networkId))
                .orderBy(t.EFFECTIVE_FROM.desc().nullsLast(), t.SERVICE_ASSIGNMENT_ID)
                .fetch(JooqTechnicianActiveAssignmentQuery::map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianActiveAssignmentView> listChangesSince(
            String tenantId,
            String networkId,
            Collection<String> assigneeIds,
            Instant since,
            UUID afterAssignmentId
    ) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return List.of();
        }
        Instant cursorTime = since == null ? Instant.EPOCH : since;
        UUID afterId = afterAssignmentId == null
                ? new UUID(0L, 0L)
                : afterAssignmentId;
        DspServiceAssignment t = DSP_SERVICE_ASSIGNMENT.as("t");
        // 游标语义与原 SQL 一致：ACTIVE 看 effective_from，ENDED 看 effective_to，
        // 同一时间值内用 service_assignment_id 保证稳定推进。
        Condition activeCursor = t.STATUS.eq("ACTIVE")
                .and(t.EFFECTIVE_FROM.gt(cursorTime)
                        .or(t.EFFECTIVE_FROM.eq(cursorTime)
                                .and(t.SERVICE_ASSIGNMENT_ID.gt(afterId))));
        Condition endedCursor = t.STATUS.eq("ENDED")
                .and(t.EFFECTIVE_TO.gt(cursorTime)
                        .or(t.EFFECTIVE_TO.eq(cursorTime)
                                .and(t.SERVICE_ASSIGNMENT_ID.gt(afterId))));
        return baseSelect(t)
                .where(t.TENANT_ID.eq(tenantId))
                .and(t.RESPONSIBILITY_LEVEL.eq("TECHNICIAN"))
                .and(t.STATUS.in("ACTIVE", "ENDED"))
                .and(t.ASSIGNEE_ID.in(assigneeIds))
                .and(networkExists(t, networkId))
                .and(activeCursor.or(endedCursor))
                .orderBy(DSL.case_(t.STATUS)
                                .when("ACTIVE", t.EFFECTIVE_FROM)
                                .otherwise(t.EFFECTIVE_TO),
                        t.SERVICE_ASSIGNMENT_ID)
                .fetch(JooqTechnicianActiveAssignmentQuery::map);
    }

    @Override
    @Transactional(readOnly = true)
    public int countEndedForTechnician(
            String tenantId, String networkId, Collection<String> assigneeIds
    ) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return 0;
        }
        DspServiceAssignment t = DSP_SERVICE_ASSIGNMENT.as("t");
        return dsl.fetchCount(t,
                t.TENANT_ID.eq(tenantId)
                        .and(t.RESPONSIBILITY_LEVEL.eq("TECHNICIAN"))
                        .and(t.STATUS.eq("ENDED"))
                        .and(t.ASSIGNEE_ID.in(assigneeIds))
                        .and(networkExists(t, networkId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> filterTaskIdsForNetwork(
            String tenantId, String networkId, Collection<UUID> candidateTaskIds
    ) {
        if (candidateTaskIds == null || candidateTaskIds.isEmpty()) {
            return List.of();
        }
        DspServiceAssignment n = DSP_SERVICE_ASSIGNMENT.as("n");
        return dsl.selectDistinct(n.TASK_ID)
                .from(n)
                .where(n.TENANT_ID.eq(tenantId))
                .and(n.RESPONSIBILITY_LEVEL.eq("NETWORK"))
                .and(n.ASSIGNEE_ID.eq(networkId))
                .and(n.STATUS.in("ACTIVE", "ENDED"))
                .and(n.TASK_ID.in(candidateTaskIds))
                .fetch(n.TASK_ID);
    }

    private org.jooq.SelectJoinStep<Record9<
            UUID, UUID, UUID, String, String, Instant, Instant, String, String>> baseSelect(
            DspServiceAssignment t) {
        return dsl.select(
                        t.SERVICE_ASSIGNMENT_ID,
                        t.WORK_ORDER_ID,
                        t.TASK_ID,
                        t.BUSINESS_TYPE,
                        t.STATUS,
                        t.EFFECTIVE_FROM,
                        t.EFFECTIVE_TO,
                        t.END_REASON_CODE,
                        t.ASSIGNEE_ID)
                .from(t);
    }

    /**
     * 同一 task 存在本网点（ACTIVE/ENDED）NETWORK 责任才纳入网点范围。
     */
    private Condition networkExists(DspServiceAssignment t, String networkId) {
        DspServiceAssignment n = DSP_SERVICE_ASSIGNMENT.as("n");
        return DSL.exists(dsl.selectOne()
                .from(n)
                .where(n.TENANT_ID.eq(t.TENANT_ID))
                .and(n.TASK_ID.eq(t.TASK_ID))
                .and(n.RESPONSIBILITY_LEVEL.eq("NETWORK"))
                .and(n.ASSIGNEE_ID.eq(networkId))
                .and(n.STATUS.in("ACTIVE", "ENDED")));
    }

    private static TechnicianActiveAssignmentView map(
            Record9<UUID, UUID, UUID, String, String, Instant, Instant, String, String> row) {
        return new TechnicianActiveAssignmentView(
                row.value1(), row.value2(), row.value3(), row.value4(), row.value5(),
                row.value6(), row.value7(), row.value8(), row.value9());
    }
}
