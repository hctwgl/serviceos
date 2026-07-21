package com.serviceos.appointment.application;

import com.serviceos.appointment.api.TechnicianScheduleAppointmentQuery;
import com.serviceos.appointment.api.TechnicianScheduleAppointmentView;
import com.serviceos.jooq.generated.tables.AptAppointment;
import com.serviceos.jooq.generated.tables.AptAppointmentRevision;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.AptAppointment.APT_APPOINTMENT;
import static com.serviceos.jooq.generated.tables.AptAppointmentRevision.APT_APPOINTMENT_REVISION;

/**
 * 预约日程 fan-in：只读 appointment 拥有表，不返回地址等敏感正文。
 */
@Service
final class JooqTechnicianScheduleAppointmentQuery implements TechnicianScheduleAppointmentQuery {
    private final DSLContext dsl;

    JooqTechnicianScheduleAppointmentQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianScheduleAppointmentView> listForTasks(String tenantId, Collection<UUID> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        AptAppointment a = APT_APPOINTMENT.as("a");
        AptAppointmentRevision r = APT_APPOINTMENT_REVISION.as("r");
        return dsl.select(
                        a.APPOINTMENT_ID,
                        a.PROJECT_ID,
                        a.WORK_ORDER_ID,
                        a.TASK_ID,
                        a.APPOINTMENT_TYPE,
                        a.STATUS,
                        r.WINDOW_START,
                        r.WINDOW_END,
                        r.TIMEZONE)
                .from(a)
                .join(r).on(r.REVISION_ID.eq(a.CURRENT_REVISION_ID))
                .where(a.TENANT_ID.eq(tenantId))
                .and(a.TASK_ID.in(taskIds))
                .and(a.STATUS.in("PROPOSED", "CONFIRMED"))
                .orderBy(r.WINDOW_START, a.APPOINTMENT_ID)
                .fetch(row -> new TechnicianScheduleAppointmentView(
                        row.get(a.APPOINTMENT_ID),
                        row.get(a.PROJECT_ID),
                        row.get(a.WORK_ORDER_ID),
                        row.get(a.TASK_ID),
                        row.get(a.APPOINTMENT_TYPE),
                        row.get(a.STATUS),
                        row.get(r.WINDOW_START),
                        row.get(r.WINDOW_END),
                        row.get(r.TIMEZONE)));
    }
}
