package com.serviceos.task.infrastructure;

import com.serviceos.task.api.TaskResponsibilityQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
final class JdbcTaskResponsibilityQuery implements TaskResponsibilityQuery {
    private final JdbcClient jdbc;

    JdbcTaskResponsibilityQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> findCorrectionCandidateUser(String tenantId, UUID taskId) {
        return jdbc.sql("""
                        SELECT assignment.principal_id
                          FROM tsk_task task
                          JOIN tsk_task_assignment assignment
                            ON assignment.tenant_id = task.tenant_id
                           AND assignment.task_id = task.task_id
                         WHERE task.tenant_id = :tenantId
                           AND task.task_id = :taskId
                           AND assignment.assignment_kind = 'RESPONSIBLE'
                           AND assignment.principal_type = 'USER'
                           AND (
                               assignment.status = 'ACTIVE'
                               OR (
                                   task.status = 'COMPLETED'
                                   AND assignment.status = 'EXPIRED'
                                   AND assignment.revoke_reason_code = 'TASK_COMPLETED'
                               )
                           )
                         ORDER BY assignment.effective_from DESC
                         LIMIT 1
                        """)
                .param("tenantId", tenantId)
                .param("taskId", taskId)
                .query(String.class)
                .optional();
    }
}
