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
    public Optional<String> findActiveResponsibleUser(String tenantId, UUID taskId) {
        return jdbc.sql("""
                        SELECT principal_id
                          FROM tsk_task_assignment
                         WHERE tenant_id = :tenantId
                           AND task_id = :taskId
                           AND assignment_kind = 'RESPONSIBLE'
                           AND principal_type = 'USER'
                           AND status = 'ACTIVE'
                         ORDER BY effective_from DESC
                         LIMIT 1
                        """)
                .param("tenantId", tenantId)
                .param("taskId", taskId)
                .query(String.class)
                .optional();
    }
}
