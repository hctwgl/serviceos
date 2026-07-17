package com.serviceos.task.application;

import com.serviceos.task.api.TechnicianTaskAssignmentFeedQuery;
import com.serviceos.task.api.TechnicianTaskAssignmentFeedView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * TaskAssignment feed：仅读 task 拥有表；网点收敛由 readmodel 通过 dispatch NETWORK 责任完成。
 */
@Service
final class DefaultTechnicianTaskAssignmentFeedQuery implements TechnicianTaskAssignmentFeedQuery {
    private final JdbcClient jdbc;

    DefaultTechnicianTaskAssignmentFeedQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
        return jdbc.sql("""
                        SELECT a.task_assignment_id,
                               a.task_id,
                               t.work_order_id,
                               a.status,
                               a.effective_from,
                               a.effective_to,
                               a.revoke_reason_code
                          FROM tsk_task_assignment a
                          JOIN tsk_task t ON t.task_id = a.task_id
                         WHERE a.tenant_id = :tenantId
                           AND a.principal_id = :principalId
                           AND a.principal_type = 'USER'
                           AND a.assignment_kind = 'RESPONSIBLE'
                           AND a.status = :status
                         ORDER BY a.effective_from DESC, a.task_assignment_id
                        """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("status", status)
                .query((rs, rowNum) -> new TechnicianTaskAssignmentFeedView(
                        rs.getObject("task_assignment_id", UUID.class),
                        rs.getObject("task_id", UUID.class),
                        rs.getObject("work_order_id", UUID.class),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("effective_from")),
                        toInstant(rs.getTimestamp("effective_to")),
                        rs.getString("revoke_reason_code")))
                .list();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
