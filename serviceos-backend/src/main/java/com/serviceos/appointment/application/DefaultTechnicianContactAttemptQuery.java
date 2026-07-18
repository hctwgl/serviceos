package com.serviceos.appointment.application;

import com.serviceos.appointment.api.TechnicianContactAttemptQuery;
import com.serviceos.appointment.api.TechnicianContactAttemptView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Technician Portal 联系历史 fan-in，只读 Appointment 拥有表并执行固定白名单投影。 */
@Service
final class DefaultTechnicianContactAttemptQuery implements TechnicianContactAttemptQuery {
    private final JdbcClient jdbc;

    DefaultTechnicianContactAttemptQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianContactAttemptView> listForTasks(String tenantId, Collection<UUID> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        // 仅选择页面确需的非敏感事实；禁止 SELECT * 后再依赖 Java 层脱敏。
        return jdbc.sql("""
                        SELECT contact_attempt_id,
                               task_id,
                               channel,
                               started_at,
                               ended_at,
                               result_code,
                               next_contact_at,
                               created_at
                          FROM apt_contact_attempt
                         WHERE tenant_id = :tenantId
                           AND task_id IN (:taskIds)
                         ORDER BY started_at DESC, contact_attempt_id DESC
                        """)
                .param("tenantId", tenantId)
                .param("taskIds", List.copyOf(taskIds))
                .query((rs, rowNum) -> new TechnicianContactAttemptView(
                        rs.getObject("contact_attempt_id", UUID.class),
                        rs.getObject("task_id", UUID.class),
                        rs.getString("channel"),
                        toInstant(rs.getTimestamp("started_at")),
                        toInstant(rs.getTimestamp("ended_at")),
                        rs.getString("result_code"),
                        toInstant(rs.getTimestamp("next_contact_at")),
                        toInstant(rs.getTimestamp("created_at"))))
                .list();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
