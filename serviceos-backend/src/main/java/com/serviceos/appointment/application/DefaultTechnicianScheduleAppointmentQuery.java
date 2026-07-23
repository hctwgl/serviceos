package com.serviceos.appointment.application;

import com.serviceos.appointment.api.TechnicianScheduleAppointmentQuery;
import com.serviceos.appointment.api.TechnicianScheduleAppointmentView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 预约日程 fan-in：只读 appointment 拥有表，不返回地址等敏感正文。
 */
@Service
final class DefaultTechnicianScheduleAppointmentQuery implements TechnicianScheduleAppointmentQuery {
    private final JdbcClient jdbc;

    DefaultTechnicianScheduleAppointmentQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianScheduleAppointmentView> listForTasks(String tenantId, Collection<UUID> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT a.appointment_id,
                               a.project_id,
                               a.work_order_id,
                               a.task_id,
                               a.appointment_type,
                               a.status,
                               r.window_start,
                               r.window_end,
                               r.timezone
                          FROM apt_appointment a
                          JOIN apt_appointment_revision r
                            ON r.revision_id = a.current_revision_id
                         WHERE a.tenant_id = :tenantId
                           AND a.task_id IN (:taskIds)
                           -- 必须包含 IN_PROGRESS / COMPLETED：签到会把预约从 CONFIRMED 推进为
                           -- IN_PROGRESS、签退推进为 COMPLETED。完成任务的前置检查依赖任务详情里
                           -- 「已有预约安排」，若只保留 PROPOSED/CONFIRMED，则签到后预约会从任务详情
                           -- 消失，导致师傅签到后永远无法完成任务（必然死锁）。仅排除失败/放弃态。
                           AND a.status IN ('PROPOSED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED')
                         ORDER BY r.window_start, a.appointment_id
                        """)
                .param("tenantId", tenantId)
                .param("taskIds", List.copyOf(taskIds))
                .query((rs, rowNum) -> new TechnicianScheduleAppointmentView(
                        rs.getObject("appointment_id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("work_order_id", UUID.class),
                        rs.getObject("task_id", UUID.class),
                        rs.getString("appointment_type"),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("window_start")),
                        toInstant(rs.getTimestamp("window_end")),
                        rs.getString("timezone")))
                .list();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
