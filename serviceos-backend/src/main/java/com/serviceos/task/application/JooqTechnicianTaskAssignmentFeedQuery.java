package com.serviceos.task.application;

import com.serviceos.jooq.generated.tables.TskTask;
import com.serviceos.jooq.generated.tables.TskTaskAssignment;
import com.serviceos.task.api.TechnicianTaskAssignmentFeedQuery;
import com.serviceos.task.api.TechnicianTaskAssignmentFeedView;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;
import static com.serviceos.jooq.generated.tables.TskTaskAssignment.TSK_TASK_ASSIGNMENT;

/**
 * TaskAssignment feed：仅读 task 拥有表；网点收敛由 readmodel 通过 dispatch NETWORK 责任完成。
 */
@Service
final class JooqTechnicianTaskAssignmentFeedQuery implements TechnicianTaskAssignmentFeedQuery {
    private final DSLContext dsl;

    JooqTechnicianTaskAssignmentFeedQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianTaskAssignmentFeedView> listActiveForPrincipal(String tenantId, String principalId) {
        return list(tenantId, principalId, "ACTIVE");
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianTaskAssignmentFeedView> listRevokedForPrincipal(String tenantId, String principalId) {
        return list(tenantId, principalId, "REVOKED");
    }

    private List<TechnicianTaskAssignmentFeedView> list(String tenantId, String principalId, String status) {
        TskTaskAssignment assignment = TSK_TASK_ASSIGNMENT.as("a");
        TskTask task = TSK_TASK.as("t");
        return dsl.select(assignment.TASK_ASSIGNMENT_ID, assignment.TASK_ID, task.WORK_ORDER_ID,
                        assignment.STATUS, assignment.EFFECTIVE_FROM, assignment.EFFECTIVE_TO,
                        assignment.REVOKE_REASON_CODE)
                .from(assignment)
                .join(task).on(task.TASK_ID.eq(assignment.TASK_ID))
                .where(assignment.TENANT_ID.eq(tenantId))
                .and(assignment.PRINCIPAL_ID.eq(principalId))
                .and(assignment.PRINCIPAL_TYPE.eq("USER"))
                .and(assignment.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                .and(assignment.STATUS.eq(status))
                .orderBy(assignment.EFFECTIVE_FROM.desc(), assignment.TASK_ASSIGNMENT_ID)
                .fetch(record -> mapView(record, assignment, task));
    }

    private static TechnicianTaskAssignmentFeedView mapView(
            Record record, TskTaskAssignment assignment, TskTask task) {
        return new TechnicianTaskAssignmentFeedView(
                record.get(assignment.TASK_ASSIGNMENT_ID),
                record.get(assignment.TASK_ID),
                record.get(task.WORK_ORDER_ID),
                record.get(assignment.STATUS),
                record.get(assignment.EFFECTIVE_FROM),
                record.get(assignment.EFFECTIVE_TO),
                record.get(assignment.REVOKE_REASON_CODE));
    }
}
