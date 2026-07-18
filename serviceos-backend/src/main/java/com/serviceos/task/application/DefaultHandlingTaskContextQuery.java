package com.serviceos.task.application;

import com.serviceos.task.api.HandlingTaskContextQuery;
import com.serviceos.task.api.HandlingTaskContextView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Task 模块内部读取自己的表，避免 Evidence 为发现整改候选而跨模块查询 Task 持久化。 */
@Service
final class DefaultHandlingTaskContextQuery implements HandlingTaskContextQuery {
    private final JdbcClient jdbc;

    DefaultHandlingTaskContextQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HandlingTaskContextView> listForActor(String tenantId, String actorId, String taskType) {
        return jdbc.sql(baseSql() + """
                         WHERE task.tenant_id = :tenantId AND task.task_type = :taskType
                           AND task.task_kind = 'HUMAN'
                           AND (candidate.task_assignment_id IS NOT NULL OR responsible.task_assignment_id IS NOT NULL)
                         ORDER BY task.updated_at DESC, task.task_id
                        """)
                .param("tenantId", tenantId).param("actorId", actorId).param("taskType", taskType)
                .query((rs, rowNum) -> new HandlingTaskContextView(
                        rs.getObject("task_id", UUID.class), rs.getString("task_type"),
                        rs.getString("business_key"), rs.getString("status"),
                        rs.getString("claimed_by"), rs.getLong("version"),
                        rs.getBoolean("actor_candidate"), rs.getBoolean("actor_responsible")))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HandlingTaskContextView> findForActor(
            String tenantId, UUID taskId, String actorId, String taskType
    ) {
        return jdbc.sql(baseSql() + """
                         WHERE task.tenant_id = :tenantId AND task.task_id = :taskId
                           AND task.task_type = :taskType AND task.task_kind = 'HUMAN'
                        """)
                .param("tenantId", tenantId).param("taskId", taskId)
                .param("actorId", actorId).param("taskType", taskType)
                .query((rs, rowNum) -> new HandlingTaskContextView(
                        rs.getObject("task_id", UUID.class), rs.getString("task_type"),
                        rs.getString("business_key"), rs.getString("status"),
                        rs.getString("claimed_by"), rs.getLong("version"),
                        rs.getBoolean("actor_candidate"), rs.getBoolean("actor_responsible")))
                .optional();
    }

    private static String baseSql() {
        return """
                SELECT task.task_id, task.task_type, task.business_key, task.status,
                       task.claimed_by, task.version,
                       candidate.task_assignment_id IS NOT NULL AS actor_candidate,
                       responsible.task_assignment_id IS NOT NULL AS actor_responsible
                  FROM tsk_task task
                  LEFT JOIN tsk_task_assignment candidate
                    ON candidate.tenant_id = task.tenant_id AND candidate.task_id = task.task_id
                   AND candidate.assignment_kind = 'CANDIDATE' AND candidate.principal_type = 'USER'
                   AND candidate.principal_id = :actorId AND candidate.status = 'ACTIVE'
                  LEFT JOIN tsk_task_assignment responsible
                    ON responsible.tenant_id = task.tenant_id AND responsible.task_id = task.task_id
                   AND responsible.assignment_kind = 'RESPONSIBLE' AND responsible.principal_type = 'USER'
                   AND responsible.principal_id = :actorId AND responsible.status = 'ACTIVE'
                """;
    }
}
