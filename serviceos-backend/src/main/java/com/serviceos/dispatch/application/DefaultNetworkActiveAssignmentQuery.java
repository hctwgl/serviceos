package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.NetworkActiveAssignmentQuery;
import com.serviceos.dispatch.api.NetworkActiveAssignmentView;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACTIVE NETWORK 责任列表：责任属于整张工单，并投影到该工单的各阶段 Task。
 *
 * <p>Dispatch 只读取自己的 ServiceAssignment 表；Task 明细通过公开
 * {@link TaskFulfillmentContextService} 获取，禁止跨模块查询 {@code tsk_*}。</p>
 */
@Service
final class DefaultNetworkActiveAssignmentQuery implements NetworkActiveAssignmentQuery {
    private final JdbcClient jdbc;
    private final TaskFulfillmentContextService tasks;

    DefaultNetworkActiveAssignmentQuery(
            JdbcClient jdbc,
            TaskFulfillmentContextService tasks
    ) {
        this.jdbc = jdbc;
        this.tasks = tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NetworkActiveAssignmentView> listActiveForNetwork(String tenantId, String networkId) {
        List<NetworkActiveAssignmentView> workOrders = jdbc.sql("""
                        WITH current_network AS (
                            SELECT DISTINCT ON (n.work_order_id)
                                   n.service_assignment_id,
                                   n.tenant_id,
                                   n.work_order_id,
                                   n.task_id,
                                   n.business_type,
                                   n.effective_from
                              FROM dsp_service_assignment n
                             WHERE n.tenant_id = :tenantId
                               AND n.responsibility_level = 'NETWORK'
                               AND n.assignee_id = :networkId
                               AND n.status = 'ACTIVE'
                             ORDER BY n.work_order_id,
                                      n.effective_from DESC NULLS LAST,
                                      n.created_at DESC,
                                      n.service_assignment_id DESC
                        )
                        SELECT n.service_assignment_id,
                               n.work_order_id,
                               n.task_id,
                               n.business_type,
                               n.effective_from,
                               technician.assignee_id AS technician_id
                          FROM current_network n
                          LEFT JOIN LATERAL (
                              SELECT t.assignee_id
                                FROM dsp_service_assignment t
                               WHERE t.tenant_id = n.tenant_id
                                 AND t.work_order_id = n.work_order_id
                                 AND t.responsibility_level = 'TECHNICIAN'
                                 AND t.status = 'ACTIVE'
                               ORDER BY (t.task_id = n.task_id) DESC,
                                        t.effective_from DESC NULLS LAST,
                                        t.created_at DESC,
                                        t.service_assignment_id DESC
                               LIMIT 1
                          ) technician ON true
                         ORDER BY n.effective_from DESC NULLS LAST, n.service_assignment_id
                        """)
                .param("tenantId", tenantId)
                .param("networkId", networkId)
                .query((rs, rowNum) -> new NetworkActiveAssignmentView(
                        rs.getObject("service_assignment_id", UUID.class),
                        rs.getObject("work_order_id", UUID.class),
                        rs.getObject("task_id", UUID.class),
                        rs.getString("business_type"),
                        toInstant(rs.getTimestamp("effective_from")),
                        rs.getString("technician_id")))
                .list();
        Map<UUID, NetworkActiveAssignmentView> projected = new LinkedHashMap<>();
        for (NetworkActiveAssignmentView responsibility : workOrders) {
            for (TaskFulfillmentContext task :
                    tasks.listForWorkOrder(tenantId, responsibility.workOrderId())) {
                projected.put(task.taskId(), new NetworkActiveAssignmentView(
                        responsibility.serviceAssignmentId(),
                        responsibility.workOrderId(),
                        task.taskId(),
                        responsibility.businessType(),
                        responsibility.effectiveFrom(),
                        responsibility.technicianId()));
            }
        }
        return List.copyOf(projected.values());
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
