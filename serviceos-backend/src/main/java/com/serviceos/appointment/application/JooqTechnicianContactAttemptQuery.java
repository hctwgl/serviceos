package com.serviceos.appointment.application;

import com.serviceos.appointment.api.TechnicianContactAttemptQuery;
import com.serviceos.appointment.api.TechnicianContactAttemptView;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.AptContactAttempt.APT_CONTACT_ATTEMPT;

/** Technician Portal 联系历史 fan-in，只读 Appointment 拥有表并执行固定白名单投影。 */
@Service
final class JooqTechnicianContactAttemptQuery implements TechnicianContactAttemptQuery {
    private final DSLContext dsl;

    JooqTechnicianContactAttemptQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianContactAttemptView> listForTasks(String tenantId, Collection<UUID> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        // 仅选择页面确需的非敏感事实；禁止 SELECT * 后再依赖 Java 层脱敏。
        return dsl.select(
                        APT_CONTACT_ATTEMPT.CONTACT_ATTEMPT_ID,
                        APT_CONTACT_ATTEMPT.TASK_ID,
                        APT_CONTACT_ATTEMPT.CHANNEL,
                        APT_CONTACT_ATTEMPT.STARTED_AT,
                        APT_CONTACT_ATTEMPT.ENDED_AT,
                        APT_CONTACT_ATTEMPT.RESULT_CODE,
                        APT_CONTACT_ATTEMPT.NEXT_CONTACT_AT,
                        APT_CONTACT_ATTEMPT.CREATED_AT)
                .from(APT_CONTACT_ATTEMPT)
                .where(APT_CONTACT_ATTEMPT.TENANT_ID.eq(tenantId))
                .and(APT_CONTACT_ATTEMPT.TASK_ID.in(taskIds))
                .orderBy(APT_CONTACT_ATTEMPT.STARTED_AT.desc(), APT_CONTACT_ATTEMPT.CONTACT_ATTEMPT_ID.desc())
                .fetch(row -> new TechnicianContactAttemptView(
                        row.get(APT_CONTACT_ATTEMPT.CONTACT_ATTEMPT_ID),
                        row.get(APT_CONTACT_ATTEMPT.TASK_ID),
                        row.get(APT_CONTACT_ATTEMPT.CHANNEL),
                        row.get(APT_CONTACT_ATTEMPT.STARTED_AT),
                        row.get(APT_CONTACT_ATTEMPT.ENDED_AT),
                        row.get(APT_CONTACT_ATTEMPT.RESULT_CODE),
                        row.get(APT_CONTACT_ATTEMPT.NEXT_CONTACT_AT),
                        row.get(APT_CONTACT_ATTEMPT.CREATED_AT)));
    }
}
