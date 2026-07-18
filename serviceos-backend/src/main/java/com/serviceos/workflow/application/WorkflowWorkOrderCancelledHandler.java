package com.serviceos.workflow.application;

import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CancelOpenWorkflowTasksCommand;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.workorder.api.WorkOrderCancelledPayload;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * workorder.cancelled 的可靠消费者：级联取消根/子流程运行时与开放任务。
 *
 * <p>事务边界：Inbox begin → 取消流程/节点/等待/定时器/并行汇聚/多实例 → 取消任务 → Inbox complete。
 * 幂等键为事件 ID；重放直接返回。</p>
 */
@Service
final class WorkflowWorkOrderCancelledHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "workflow.work-order-cancelled.v1";

    private final JdbcClient jdbc;
    private final InboxService inbox;
    private final TaskSchedulingService tasks;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    WorkflowWorkOrderCancelledHandler(
            JdbcClient jdbc,
            InboxService inbox,
            TaskSchedulingService tasks,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.inbox = inbox;
        this.tasks = tasks;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return "workorder.cancelled".equals(eventType) && schemaVersion == 1;
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        if (!"workorder".equals(message.module()) || !"WorkOrder".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported WorkOrderCancelled envelope");
        }
        InboxDecision decision = inbox.begin(
                message.tenantId(), CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }

        WorkOrderCancelledPayload cancelled = readPayload(message.payload());
        if (!cancelled.workOrderId().toString().equals(message.aggregateId())) {
            throw new IllegalArgumentException("WorkOrderCancelled aggregateId does not match payload");
        }

        Instant now = clock.instant();
        List<UUID> workflowIds = listOpenWorkflows(message.tenantId(), cancelled.workOrderId());
        if (workflowIds.isEmpty()) {
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest("no-open-workflows|" + cancelled.workOrderId()));
            return;
        }

        cancelWorkflowRuntime(message.tenantId(), workflowIds, now);
        int cancelledTasks = tasks.cancelOpenTasksForWorkflows(new CancelOpenWorkflowTasksCommand(
                message.tenantId(), workflowIds, cancelled.reasonCode(),
                message.eventId(), now, message.correlationId()));

        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(workflowIds + "|" + cancelledTasks));
    }

    private List<UUID> listOpenWorkflows(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                        SELECT workflow_instance_id
                          FROM wfl_workflow_instance
                         WHERE tenant_id = :tenantId
                           AND work_order_id = :workOrderId
                           AND status IN ('ACTIVE', 'SUSPENDED')
                        """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query(UUID.class)
                .list();
    }

    private void cancelWorkflowRuntime(String tenantId, List<UUID> workflowIds, Instant now) {
        jdbc.sql("""
                        UPDATE wfl_workflow_instance
                           SET status = 'CANCELLED', version = version + 1
                         WHERE tenant_id = :tenantId
                           AND workflow_instance_id IN (:workflowIds)
                           AND status IN ('ACTIVE', 'SUSPENDED')
                        """)
                .param("tenantId", tenantId)
                .param("workflowIds", workflowIds)
                .update();

        jdbc.sql("""
                        UPDATE wfl_stage_instance
                           SET status = 'CANCELLED', version = version + 1
                         WHERE tenant_id = :tenantId
                           AND workflow_instance_id IN (:workflowIds)
                           AND status IN ('PENDING', 'ACTIVE', 'BLOCKED')
                        """)
                .param("tenantId", tenantId)
                .param("workflowIds", workflowIds)
                .update();

        jdbc.sql("""
                        UPDATE wfl_node_instance
                           SET status = 'CANCELLED', version = version + 1
                         WHERE tenant_id = :tenantId
                           AND workflow_instance_id IN (:workflowIds)
                           AND status IN ('ACTIVE', 'WAITING')
                        """)
                .param("tenantId", tenantId)
                .param("workflowIds", workflowIds)
                .update();

        jdbc.sql("""
                        UPDATE wfl_wait_subscription
                           SET status = 'CANCELLED'
                         WHERE tenant_id = :tenantId
                           AND workflow_instance_id IN (:workflowIds)
                           AND status = 'WAITING'
                        """)
                .param("tenantId", tenantId)
                .param("workflowIds", workflowIds)
                .update();

        jdbc.sql("""
                        UPDATE wfl_timer_subscription
                           SET status = 'CANCELLED'
                         WHERE tenant_id = :tenantId
                           AND workflow_instance_id IN (:workflowIds)
                           AND status IN ('WAITING', 'CLAIMED')
                        """)
                .param("tenantId", tenantId)
                .param("workflowIds", workflowIds)
                .update();

        jdbc.sql("""
                        UPDATE wfl_parallel_join
                           SET status = 'CANCELLED'
                         WHERE tenant_id = :tenantId
                           AND workflow_instance_id IN (:workflowIds)
                           AND status = 'OPEN'
                        """)
                .param("tenantId", tenantId)
                .param("workflowIds", workflowIds)
                .update();

        jdbc.sql("""
                        UPDATE wfl_multi_instance
                           SET status = 'CANCELLED'
                         WHERE tenant_id = :tenantId
                           AND workflow_instance_id IN (:workflowIds)
                           AND status = 'OPEN'
                        """)
                .param("tenantId", tenantId)
                .param("workflowIds", workflowIds)
                .update();

        jdbc.sql("""
                        UPDATE wfl_multi_instance_slot
                           SET status = 'CANCELLED'
                         WHERE tenant_id = :tenantId
                           AND multi_instance_id IN (
                               SELECT multi_instance_id FROM wfl_multi_instance
                                WHERE tenant_id = :tenantId
                                  AND workflow_instance_id IN (:workflowIds)
                           )
                           AND status = 'ACTIVE'
                        """)
                .param("tenantId", tenantId)
                .param("workflowIds", workflowIds)
                .update();

        jdbc.sql("""
                        UPDATE wfl_subprocess_link
                           SET status = 'CANCELLED', version = version + 1
                         WHERE tenant_id = :tenantId
                           AND (parent_workflow_instance_id IN (:workflowIds)
                                OR child_workflow_instance_id IN (:workflowIds))
                           AND status = 'RUNNING'
                        """)
                .param("tenantId", tenantId)
                .param("workflowIds", workflowIds)
                .update();
    }

    private WorkOrderCancelledPayload readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, WorkOrderCancelledPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("WorkOrderCancelled payload cannot be decoded", exception);
        }
    }
}
