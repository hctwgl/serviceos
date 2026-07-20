package com.serviceos.task.application;

import com.serviceos.jooq.generated.tables.TskTask;
import com.serviceos.jooq.generated.tables.TskTaskAssignment;
import com.serviceos.task.api.HandlingTaskContextQuery;
import com.serviceos.task.api.HandlingTaskContextView;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;
import static com.serviceos.jooq.generated.tables.TskTaskAssignment.TSK_TASK_ASSIGNMENT;

/** Task 模块内部读取自己的表，避免 Evidence 为发现整改候选而跨模块查询 Task 持久化。 */
@Service
final class JooqHandlingTaskContextQuery implements HandlingTaskContextQuery {
    private static final TskTask TASK = TSK_TASK.as("task");
    private static final TskTaskAssignment CANDIDATE = TSK_TASK_ASSIGNMENT.as("candidate");
    private static final TskTaskAssignment RESPONSIBLE = TSK_TASK_ASSIGNMENT.as("responsible");

    private final DSLContext dsl;

    JooqHandlingTaskContextQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HandlingTaskContextView> listForActor(String tenantId, String actorId, String taskType) {
        return baseSelect(actorId)
                .where(TASK.TENANT_ID.eq(tenantId))
                .and(TASK.TASK_TYPE.eq(taskType))
                .and(TASK.TASK_KIND.eq("HUMAN"))
                .and(CANDIDATE.TASK_ASSIGNMENT_ID.isNotNull()
                        .or(RESPONSIBLE.TASK_ASSIGNMENT_ID.isNotNull()))
                .orderBy(TASK.UPDATED_AT.desc(), TASK.TASK_ID)
                .fetch(JooqHandlingTaskContextQuery::mapView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HandlingTaskContextView> findForActor(
            String tenantId, UUID taskId, String actorId, String taskType
    ) {
        return baseSelect(actorId)
                .where(TASK.TENANT_ID.eq(tenantId))
                .and(TASK.TASK_ID.eq(taskId))
                .and(TASK.TASK_TYPE.eq(taskType))
                .and(TASK.TASK_KIND.eq("HUMAN"))
                .fetchOptional(JooqHandlingTaskContextQuery::mapView);
    }

    private org.jooq.SelectOnConditionStep<org.jooq.Record8<
            UUID, String, String, String, String, Long, Boolean, Boolean>> baseSelect(String actorId) {
        return dsl.select(TASK.TASK_ID, TASK.TASK_TYPE, TASK.BUSINESS_KEY, TASK.STATUS,
                        TASK.CLAIMED_BY, TASK.VERSION,
                        CANDIDATE.TASK_ASSIGNMENT_ID.isNotNull().as("actor_candidate"),
                        RESPONSIBLE.TASK_ASSIGNMENT_ID.isNotNull().as("actor_responsible"))
                .from(TASK)
                .leftJoin(CANDIDATE)
                .on(CANDIDATE.TENANT_ID.eq(TASK.TENANT_ID))
                .and(CANDIDATE.TASK_ID.eq(TASK.TASK_ID))
                .and(CANDIDATE.ASSIGNMENT_KIND.eq("CANDIDATE"))
                .and(CANDIDATE.PRINCIPAL_TYPE.eq("USER"))
                .and(CANDIDATE.PRINCIPAL_ID.eq(actorId))
                .and(CANDIDATE.STATUS.eq("ACTIVE"))
                .leftJoin(RESPONSIBLE)
                .on(RESPONSIBLE.TENANT_ID.eq(TASK.TENANT_ID))
                .and(RESPONSIBLE.TASK_ID.eq(TASK.TASK_ID))
                .and(RESPONSIBLE.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                .and(RESPONSIBLE.PRINCIPAL_TYPE.eq("USER"))
                .and(RESPONSIBLE.PRINCIPAL_ID.eq(actorId))
                .and(RESPONSIBLE.STATUS.eq("ACTIVE"));
    }

    private static HandlingTaskContextView mapView(Record record) {
        return new HandlingTaskContextView(
                record.get(TASK.TASK_ID), record.get(TASK.TASK_TYPE),
                record.get(TASK.BUSINESS_KEY), record.get(TASK.STATUS),
                record.get(TASK.CLAIMED_BY), record.get(TASK.VERSION),
                record.get("actor_candidate", Boolean.class),
                record.get("actor_responsible", Boolean.class));
    }
}
