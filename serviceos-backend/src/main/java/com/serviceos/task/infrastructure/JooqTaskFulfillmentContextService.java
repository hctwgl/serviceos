package com.serviceos.task.infrastructure;

import com.serviceos.jooq.generated.tables.TskTask;
import com.serviceos.jooq.generated.tables.TskTaskAssignment;
import com.serviceos.jooq.generated.tables.TskTaskExecutionGuard;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;
import static com.serviceos.jooq.generated.tables.TskTaskAssignment.TSK_TASK_ASSIGNMENT;
import static com.serviceos.jooq.generated.tables.TskTaskExecutionGuard.TSK_TASK_EXECUTION_GUARD;

/** 将 Task 内部表投影为稳定公开上下文，调用方不能据此修改 Task。 */
@Service
final class JooqTaskFulfillmentContextService implements TaskFulfillmentContextService {
    private final DSLContext dsl;

    JooqTaskFulfillmentContextService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<TaskFulfillmentContext> find(String tenantId, UUID taskId) {
        TskTask t = TSK_TASK;
        TskTaskAssignment a = TSK_TASK_ASSIGNMENT.as("a");
        TskTaskExecutionGuard g = TSK_TASK_EXECUTION_GUARD.as("g");
        Field<Boolean> executionGuarded = DSL.exists(DSL.selectOne()
                        .from(g)
                        .where(g.TENANT_ID.eq(t.TENANT_ID))
                        .and(g.TASK_ID.eq(t.TASK_ID))
                        .and(g.STATUS.eq("ACTIVE")))
                .as("execution_guarded");
        return dsl.select(t.TASK_ID, t.PROJECT_ID, t.WORK_ORDER_ID, t.TASK_TYPE,
                        t.CONFIGURATION_BUNDLE_ID, t.CONFIGURATION_BUNDLE_DIGEST, t.STAGE_CODE,
                        t.TASK_KIND, t.FORM_REF, t.SLA_REF, t.ASSIGNEE_POLICY_REF,
                        t.DISPATCH_POLICY_REF, t.RULE_REF, t.STATUS, t.VERSION,
                        a.PRINCIPAL_ID, executionGuarded)
                .from(t)
                .leftJoin(a)
                .on(a.TENANT_ID.eq(t.TENANT_ID))
                .and(a.TASK_ID.eq(t.TASK_ID))
                .and(a.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                .and(a.STATUS.eq("ACTIVE"))
                .where(t.TENANT_ID.eq(tenantId))
                .and(t.TASK_ID.eq(taskId))
                .fetchOptional(record -> new TaskFulfillmentContext(
                        record.get(t.TASK_ID), record.get(t.PROJECT_ID), record.get(t.WORK_ORDER_ID),
                        record.get(t.CONFIGURATION_BUNDLE_ID), record.get(t.CONFIGURATION_BUNDLE_DIGEST),
                        record.get(t.STAGE_CODE), record.get(t.TASK_TYPE), record.get(t.TASK_KIND),
                        record.get(t.FORM_REF), record.get(t.SLA_REF), record.get(t.ASSIGNEE_POLICY_REF),
                        record.get(t.DISPATCH_POLICY_REF), record.get(t.RULE_REF), record.get(t.STATUS),
                        record.get(a.PRINCIPAL_ID), record.get(executionGuarded),
                        record.get(t.VERSION)));
    }
}
