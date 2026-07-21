package com.serviceos.fieldwork.application;

import com.serviceos.fieldwork.api.TechnicianVisitHistoryQuery;
import com.serviceos.fieldwork.api.TechnicianVisitHistoryView;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.FldVisit.FLD_VISIT;

/** Technician Portal Visit fan-in，仅对白名单生命周期事实执行只读查询。 */
@Service
final class JooqTechnicianVisitHistoryQuery implements TechnicianVisitHistoryQuery {
    private final DSLContext dsl;

    JooqTechnicianVisitHistoryQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianVisitHistoryView> listForTasks(String tenantId, Collection<UUID> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        // GPS、距离、设备、note、operation/evidence refs 均不进入 SQL 结果集，避免二次脱敏遗漏。
        return dsl.select(
                        FLD_VISIT.VISIT_ID,
                        FLD_VISIT.TASK_ID,
                        FLD_VISIT.APPOINTMENT_ID,
                        FLD_VISIT.VISIT_SEQUENCE,
                        FLD_VISIT.STATUS,
                        FLD_VISIT.CHECK_IN_CAPTURED_AT,
                        FLD_VISIT.CHECK_IN_RECEIVED_AT,
                        FLD_VISIT.GEOFENCE_RESULT,
                        FLD_VISIT.POLICY_DECISION,
                        FLD_VISIT.CHECK_OUT_CAPTURED_AT,
                        FLD_VISIT.CHECK_OUT_RECEIVED_AT,
                        FLD_VISIT.RESULT_CODE,
                        FLD_VISIT.EXCEPTION_CODE,
                        FLD_VISIT.AGGREGATE_VERSION)
                .from(FLD_VISIT)
                .where(FLD_VISIT.TENANT_ID.eq(tenantId))
                .and(FLD_VISIT.TASK_ID.in(taskIds))
                .orderBy(FLD_VISIT.VISIT_SEQUENCE.desc(), FLD_VISIT.VISIT_ID.desc())
                .fetch(row -> new TechnicianVisitHistoryView(
                        row.get(FLD_VISIT.VISIT_ID),
                        row.get(FLD_VISIT.TASK_ID),
                        row.get(FLD_VISIT.APPOINTMENT_ID),
                        row.get(FLD_VISIT.VISIT_SEQUENCE),
                        row.get(FLD_VISIT.STATUS),
                        row.get(FLD_VISIT.CHECK_IN_CAPTURED_AT),
                        row.get(FLD_VISIT.CHECK_IN_RECEIVED_AT),
                        row.get(FLD_VISIT.GEOFENCE_RESULT),
                        row.get(FLD_VISIT.POLICY_DECISION),
                        row.get(FLD_VISIT.CHECK_OUT_CAPTURED_AT),
                        row.get(FLD_VISIT.CHECK_OUT_RECEIVED_AT),
                        row.get(FLD_VISIT.RESULT_CODE),
                        row.get(FLD_VISIT.EXCEPTION_CODE),
                        row.get(FLD_VISIT.AGGREGATE_VERSION)));
    }
}
