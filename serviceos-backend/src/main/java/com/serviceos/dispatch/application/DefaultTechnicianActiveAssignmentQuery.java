package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.TechnicianActiveAssignmentQuery;
import com.serviceos.dispatch.api.TechnicianActiveAssignmentView;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 师傅 TECHNICIAN 责任列表：仅读 dispatch 拥有表，按 NETWORK 责任收敛网点范围。
 */
@Service
final class DefaultTechnicianActiveAssignmentQuery implements TechnicianActiveAssignmentQuery {
    private final JdbcClient jdbc;
    private final TaskFulfillmentContextService tasks;

    DefaultTechnicianActiveAssignmentQuery(
            JdbcClient jdbc,
            TaskFulfillmentContextService tasks
    ) {
        this.jdbc = jdbc;
        this.tasks = tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianActiveAssignmentView> listActiveForTechnician(
            String tenantId, String networkId, Collection<String> assigneeIds
    ) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT t.service_assignment_id,
                               t.work_order_id,
                               t.task_id,
                               t.business_type,
                               t.status,
                               t.effective_from,
                               t.effective_to,
                               t.end_reason_code,
                               t.assignee_id
                          FROM dsp_service_assignment t
                         WHERE t.tenant_id = :tenantId
                           AND t.responsibility_level = 'TECHNICIAN'
                           AND t.status = 'ACTIVE'
                           AND t.assignee_id IN (:assigneeIds)
                           AND EXISTS (
                               SELECT 1
                                 FROM dsp_service_assignment n
                                WHERE n.tenant_id = t.tenant_id
                                  AND n.task_id = t.task_id
                                  AND n.responsibility_level = 'NETWORK'
                                  AND n.assignee_id = :networkId
                                  AND n.status IN ('ACTIVE', 'ENDED')
                           )
                         ORDER BY t.effective_from DESC NULLS LAST, t.service_assignment_id
                        """)
                .param("tenantId", tenantId)
                .param("networkId", networkId)
                .param("assigneeIds", List.copyOf(assigneeIds))
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianActiveAssignmentView> listChangesSince(
            String tenantId,
            String networkId,
            Collection<String> assigneeIds,
            Instant since,
            UUID afterAssignmentId
    ) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return List.of();
        }
        Instant cursorTime = since == null ? Instant.EPOCH : since;
        UUID afterId = afterAssignmentId == null
                ? new UUID(0L, 0L)
                : afterAssignmentId;
        return jdbc.sql("""
                        SELECT t.service_assignment_id,
                               t.work_order_id,
                               t.task_id,
                               t.business_type,
                               t.status,
                               t.effective_from,
                               t.effective_to,
                               t.end_reason_code,
                               t.assignee_id
                          FROM dsp_service_assignment t
                         WHERE t.tenant_id = :tenantId
                           AND t.responsibility_level = 'TECHNICIAN'
                           AND t.status IN ('ACTIVE', 'ENDED')
                           AND t.assignee_id IN (:assigneeIds)
                           AND EXISTS (
                               SELECT 1
                                 FROM dsp_service_assignment n
                                WHERE n.tenant_id = t.tenant_id
                                  AND n.task_id = t.task_id
                                  AND n.responsibility_level = 'NETWORK'
                                  AND n.assignee_id = :networkId
                                  AND n.status IN ('ACTIVE', 'ENDED')
                           )
                           AND (
                                 (t.status = 'ACTIVE'
                                  AND (t.effective_from > :cursorTime
                                       OR (t.effective_from = :cursorTime
                                           AND t.service_assignment_id > :afterId)))
                              OR (t.status = 'ENDED'
                                  AND (t.effective_to > :cursorTime
                                       OR (t.effective_to = :cursorTime
                                           AND t.service_assignment_id > :afterId)))
                           )
                         ORDER BY CASE WHEN t.status = 'ACTIVE' THEN t.effective_from ELSE t.effective_to END,
                                  t.service_assignment_id
                        """)
                .param("tenantId", tenantId)
                .param("networkId", networkId)
                .param("assigneeIds", List.copyOf(assigneeIds))
                .param("cursorTime", Timestamp.from(cursorTime))
                .param("afterId", afterId)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public int countEndedForTechnician(
            String tenantId, String networkId, Collection<String> assigneeIds
    ) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return 0;
        }
        Integer count = jdbc.sql("""
                        SELECT COUNT(*)::int
                          FROM dsp_service_assignment t
                         WHERE t.tenant_id = :tenantId
                           AND t.responsibility_level = 'TECHNICIAN'
                           AND t.status = 'ENDED'
                           AND t.assignee_id IN (:assigneeIds)
                           AND EXISTS (
                               SELECT 1
                                 FROM dsp_service_assignment n
                                WHERE n.tenant_id = t.tenant_id
                                  AND n.task_id = t.task_id
                                  AND n.responsibility_level = 'NETWORK'
                                  AND n.assignee_id = :networkId
                                  AND n.status IN ('ACTIVE', 'ENDED')
                           )
                        """)
                .param("tenantId", tenantId)
                .param("networkId", networkId)
                .param("assigneeIds", List.copyOf(assigneeIds))
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> filterTaskIdsForNetwork(
            String tenantId, String networkId, Collection<UUID> candidateTaskIds
    ) {
        if (candidateTaskIds == null || candidateTaskIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, UUID> workOrderByTask = new LinkedHashMap<>();
        for (UUID taskId : candidateTaskIds) {
            tasks.find(tenantId, taskId)
                    .map(TaskFulfillmentContext::workOrderId)
                    .ifPresent(workOrderId -> workOrderByTask.put(taskId, workOrderId));
        }
        if (workOrderByTask.isEmpty()) {
            return List.of();
        }
        Set<UUID> allowedWorkOrders = Set.copyOf(jdbc.sql("""
                        SELECT DISTINCT n.work_order_id
                          FROM dsp_service_assignment n
                         WHERE n.tenant_id = :tenantId
                           AND n.responsibility_level = 'NETWORK'
                           AND n.assignee_id = :networkId
                           AND n.status IN ('ACTIVE', 'ENDED')
                           AND n.work_order_id IN (:workOrderIds)
                        """)
                .param("tenantId", tenantId)
                .param("networkId", networkId)
                .param("workOrderIds", List.copyOf(workOrderByTask.values()))
                .query((rs, rowNum) -> rs.getObject("work_order_id", UUID.class))
                .list());
        return workOrderByTask.entrySet().stream()
                .filter(entry -> allowedWorkOrders.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static TechnicianActiveAssignmentView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TechnicianActiveAssignmentView(
                rs.getObject("service_assignment_id", UUID.class),
                rs.getObject("work_order_id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getString("business_type"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("effective_from")),
                toInstant(rs.getTimestamp("effective_to")),
                rs.getString("end_reason_code"),
                rs.getString("assignee_id"));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
