package com.serviceos.dispatch.infrastructure;

import com.serviceos.dispatch.api.ActiveServiceResponsibility;
import com.serviceos.dispatch.api.ServiceAssignmentSummary;
import com.serviceos.dispatch.application.ActiveServiceResponsibilityRepository;
import com.serviceos.jooq.generated.tables.DspServiceAssignment;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.DspServiceAssignment.DSP_SERVICE_ASSIGNMENT;

/** ACTIVE ServiceAssignment 只读投影（jOOQ 实现，取代原 MyBatis Mapper + XML）。 */
@Repository
final class JooqActiveServiceResponsibilityRepository
        implements ActiveServiceResponsibilityRepository {
    private final DSLContext dsl;

    JooqActiveServiceResponsibilityRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<ActiveServiceResponsibility> find(String tenantId, UUID taskId) {
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT;
        // max(...) FILTER (WHERE ...)：同一 task 每级责任至多一行 ACTIVE，聚合成单行投影。
        return dsl.select(
                        assignment.TASK_ID,
                        DSL.max(assignment.ASSIGNEE_ID)
                                .filterWhere(assignment.RESPONSIBILITY_LEVEL.eq("NETWORK")),
                        DSL.max(assignment.ASSIGNEE_ID)
                                .filterWhere(assignment.RESPONSIBILITY_LEVEL.eq("TECHNICIAN")))
                .from(assignment)
                .where(assignment.TENANT_ID.eq(tenantId))
                .and(assignment.TASK_ID.eq(taskId))
                .and(assignment.STATUS.eq("ACTIVE"))
                .groupBy(assignment.TASK_ID)
                .fetchOptional(row -> new ActiveServiceResponsibility(
                        row.value1(), row.value2(), row.value3()));
    }

    @Override
    public Optional<ServiceAssignmentSummary> findSummary(String tenantId, UUID taskId) {
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT;
        return dsl.select(
                        assignment.TASK_ID,
                        DSL.max(assignment.ASSIGNEE_ID)
                                .filterWhere(assignment.RESPONSIBILITY_LEVEL.eq("NETWORK")),
                        DSL.max(assignment.EFFECTIVE_FROM)
                                .filterWhere(assignment.RESPONSIBILITY_LEVEL.eq("NETWORK")),
                        DSL.max(assignment.REASSIGNMENT_REASON_CODE)
                                .filterWhere(assignment.RESPONSIBILITY_LEVEL.eq("NETWORK")),
                        DSL.max(assignment.ASSIGNEE_ID)
                                .filterWhere(assignment.RESPONSIBILITY_LEVEL.eq("TECHNICIAN")),
                        DSL.max(assignment.EFFECTIVE_FROM)
                                .filterWhere(assignment.RESPONSIBILITY_LEVEL.eq("TECHNICIAN")),
                        DSL.max(assignment.REASSIGNMENT_REASON_CODE)
                                .filterWhere(assignment.RESPONSIBILITY_LEVEL.eq("TECHNICIAN")))
                .from(assignment)
                .where(assignment.TENANT_ID.eq(tenantId))
                .and(assignment.TASK_ID.eq(taskId))
                .and(assignment.STATUS.eq("ACTIVE"))
                .groupBy(assignment.TASK_ID)
                .fetchOptional(row -> new ServiceAssignmentSummary(
                        row.value1(), row.value2(), row.value3(), row.value4(),
                        row.value5(), row.value6(), row.value7()));
    }
}
