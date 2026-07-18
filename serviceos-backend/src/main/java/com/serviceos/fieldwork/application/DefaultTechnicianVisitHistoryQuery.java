package com.serviceos.fieldwork.application;

import com.serviceos.fieldwork.api.TechnicianVisitHistoryQuery;
import com.serviceos.fieldwork.api.TechnicianVisitHistoryView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Technician Portal Visit fan-in，仅对白名单生命周期事实执行只读查询。 */
@Service
final class DefaultTechnicianVisitHistoryQuery implements TechnicianVisitHistoryQuery {
    private final JdbcClient jdbc;

    DefaultTechnicianVisitHistoryQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianVisitHistoryView> listForTasks(String tenantId, Collection<UUID> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        // GPS、距离、设备、note、operation/evidence refs 均不进入 SQL 结果集，避免二次脱敏遗漏。
        return jdbc.sql("""
                        SELECT visit_id,
                               task_id,
                               appointment_id,
                               visit_sequence,
                               status,
                               check_in_captured_at,
                               check_in_received_at,
                               geofence_result,
                               policy_decision,
                               check_out_captured_at,
                               check_out_received_at,
                               result_code,
                               exception_code,
                               aggregate_version
                          FROM fld_visit
                         WHERE tenant_id = :tenantId
                           AND task_id IN (:taskIds)
                         ORDER BY visit_sequence DESC, visit_id DESC
                        """)
                .param("tenantId", tenantId)
                .param("taskIds", List.copyOf(taskIds))
                .query((rs, rowNum) -> new TechnicianVisitHistoryView(
                        rs.getObject("visit_id", UUID.class),
                        rs.getObject("task_id", UUID.class),
                        rs.getObject("appointment_id", UUID.class),
                        rs.getInt("visit_sequence"),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("check_in_captured_at")),
                        toInstant(rs.getTimestamp("check_in_received_at")),
                        rs.getString("geofence_result"),
                        rs.getString("policy_decision"),
                        toInstant(rs.getTimestamp("check_out_captured_at")),
                        toInstant(rs.getTimestamp("check_out_received_at")),
                        rs.getString("result_code"),
                        rs.getString("exception_code"),
                        rs.getLong("aggregate_version")))
                .list();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
