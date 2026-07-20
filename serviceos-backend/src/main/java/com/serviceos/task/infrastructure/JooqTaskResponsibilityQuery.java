package com.serviceos.task.infrastructure;

import com.serviceos.jooq.generated.tables.TskTask;
import com.serviceos.jooq.generated.tables.TskTaskAssignment;
import com.serviceos.task.api.TaskResponsibilityQuery;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;
import static com.serviceos.jooq.generated.tables.TskTaskAssignment.TSK_TASK_ASSIGNMENT;

@Component
final class JooqTaskResponsibilityQuery implements TaskResponsibilityQuery {
    private final DSLContext dsl;

    JooqTaskResponsibilityQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<String> findCorrectionCandidateUser(String tenantId, UUID taskId) {
        TskTask task = TSK_TASK;
        TskTaskAssignment assignment = TSK_TASK_ASSIGNMENT;
        return dsl.select(assignment.PRINCIPAL_ID)
                .from(task)
                .join(assignment)
                .on(assignment.TENANT_ID.eq(task.TENANT_ID))
                .and(assignment.TASK_ID.eq(task.TASK_ID))
                .where(task.TENANT_ID.eq(tenantId))
                .and(task.TASK_ID.eq(taskId))
                .and(assignment.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                .and(assignment.PRINCIPAL_TYPE.eq("USER"))
                .and(assignment.STATUS.eq("ACTIVE")
                        .or(task.STATUS.eq("COMPLETED")
                                .and(assignment.STATUS.eq("EXPIRED"))
                                .and(assignment.REVOKE_REASON_CODE.eq("TASK_COMPLETED"))))
                .orderBy(assignment.EFFECTIVE_FROM.desc())
                .limit(1)
                .fetchOptional(assignment.PRINCIPAL_ID);
    }
}
