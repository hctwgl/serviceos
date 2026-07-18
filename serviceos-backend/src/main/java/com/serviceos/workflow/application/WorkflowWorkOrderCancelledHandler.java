package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CancelOpenWorkflowTasksCommand;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.task.api.WorkflowTaskKind;
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
 * workorder.cancelled 的可靠消费者：级联取消运行时，并为已完成且声明补偿的节点创建补偿任务。
 *
 * <p>事务边界：Inbox begin → 取消流程/节点/等待/任务 → 按定义创建补偿请求与任务 → Inbox complete。
 * 补偿幂等键为 (tenant, cancel_event_id, source_node_instance_id)。</p>
 */
@Service
final class WorkflowWorkOrderCancelledHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "workflow.work-order-cancelled.v1";

    private final JdbcClient jdbc;
    private final InboxService inbox;
    private final TaskSchedulingService tasks;
    private final ConfigurationService configurations;
    private final WorkflowDefinitionParser parser;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    WorkflowWorkOrderCancelledHandler(
            JdbcClient jdbc,
            InboxService inbox,
            TaskSchedulingService tasks,
            ConfigurationService configurations,
            WorkflowDefinitionParser parser,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.inbox = inbox;
        this.tasks = tasks;
        this.configurations = configurations;
        this.parser = parser;
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
        // 先收集补偿候选（取消前仍可读取 COMPLETED 节点与 Bundle），再关闭开放运行时。
        List<CompensationCandidate> candidates = listCompensationCandidates(
                message.tenantId(), cancelled.workOrderId());

        if (!workflowIds.isEmpty()) {
            cancelWorkflowRuntime(message.tenantId(), workflowIds, now);
            tasks.cancelOpenTasksForWorkflows(new CancelOpenWorkflowTasksCommand(
                    message.tenantId(), workflowIds, cancelled.reasonCode(),
                    message.eventId(), now, message.correlationId()));
        }

        int compensationCount = 0;
        for (CompensationCandidate candidate : candidates) {
            if (scheduleCompensation(message, candidate, now)) {
                compensationCount++;
            }
        }

        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(workflowIds + "|" + compensationCount));
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

    private List<CompensationCandidate> listCompensationCandidates(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                        SELECT node.workflow_node_instance_id, node.node_id, node.workflow_instance_id,
                               workflow.project_id, workflow.work_order_id,
                               workflow.configuration_bundle_id, workflow.configuration_bundle_digest,
                               workflow.workflow_definition_version_id, workflow.definition_digest
                          FROM wfl_node_instance node
                          JOIN wfl_workflow_instance workflow
                            ON workflow.tenant_id = node.tenant_id
                           AND workflow.workflow_instance_id = node.workflow_instance_id
                         WHERE node.tenant_id = :tenantId
                           AND node.work_order_id = :workOrderId
                           AND node.status = 'COMPLETED'
                        """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> new CompensationCandidate(
                        rs.getObject("workflow_node_instance_id", UUID.class),
                        rs.getString("node_id"),
                        rs.getObject("workflow_instance_id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("work_order_id", UUID.class),
                        rs.getObject("configuration_bundle_id", UUID.class),
                        rs.getString("configuration_bundle_digest"),
                        rs.getObject("workflow_definition_version_id", UUID.class),
                        rs.getString("definition_digest")))
                .list();
    }

    private boolean scheduleCompensation(
            OutboxMessage message,
            CompensationCandidate candidate,
            Instant now
    ) {
        var asset = configurations.requireBundleAsset(
                message.tenantId(), candidate.bundleId(), candidate.bundleDigest(),
                ConfigurationAssetType.WORKFLOW);
        var compensation = parser.compensationForNode(asset, candidate.nodeId());
        if (compensation.isEmpty()) {
            return false;
        }
        var definition = compensation.get();

        boolean alreadyRequested = jdbc.sql("""
                        SELECT COUNT(1) FROM wfl_compensation_request
                         WHERE tenant_id = :tenantId
                           AND cancel_event_id = :cancelEventId
                           AND source_node_instance_id = :sourceNodeInstanceId
                        """)
                .param("tenantId", message.tenantId())
                .param("cancelEventId", message.eventId())
                .param("sourceNodeInstanceId", candidate.nodeInstanceId())
                .query(Integer.class)
                .single() > 0;
        if (alreadyRequested) {
            return false;
        }

        UUID stageId = UUID.randomUUID();
        UUID nodeInstanceId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        int nextSequence = jdbc.sql("""
                        SELECT COALESCE(MAX(sequence_no), 0) + 1
                          FROM wfl_stage_instance
                         WHERE tenant_id = :tenantId
                           AND workflow_instance_id = :workflowId
                        """)
                .param("tenantId", message.tenantId())
                .param("workflowId", candidate.workflowInstanceId())
                .query(Integer.class)
                .single();

        // 补偿阶段挂在已取消流程上，作为取消后仍需执行的配置化善后任务载体。
        jdbc.sql("""
                        INSERT INTO wfl_stage_instance (
                            stage_instance_id, tenant_id, workflow_instance_id, work_order_id,
                            stage_code, sequence_no, status, activation_event_id, version, activated_at
                        ) VALUES (
                            :stageId, :tenantId, :workflowId, :workOrderId,
                            :stageCode, :sequenceNo, 'ACTIVE', :activationEventId, 1, :activatedAt
                        )
                        """)
                .param("stageId", stageId)
                .param("tenantId", message.tenantId())
                .param("workflowId", candidate.workflowInstanceId())
                .param("workOrderId", candidate.workOrderId())
                .param("stageCode", definition.stageCode())
                .param("sequenceNo", nextSequence)
                .param("activationEventId", message.eventId())
                .param("activatedAt", java.sql.Timestamp.from(now))
                .update();

        String payloadDigest = Sha256.digest(
                "compensation|" + candidate.nodeInstanceId() + "|" + message.eventId());
        ScheduledTaskView task = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                message.tenantId(), candidate.projectId(), candidate.workOrderId(),
                candidate.workflowInstanceId(), stageId, nodeInstanceId,
                "COMPENSATE_" + candidate.nodeId(), candidate.definitionVersionId(),
                candidate.definitionDigest(), candidate.bundleId(), candidate.bundleDigest(),
                definition.stageCode(), definition.taskType(), WorkflowTaskKind.AUTOMATED,
                null, null,
                "compensation:" + candidate.nodeInstanceId() + ":" + message.eventId(),
                payloadDigest, 100, now, 3,
                message.correlationId(), message.eventId().toString()));

        jdbc.sql("""
                        INSERT INTO wfl_node_instance (
                            workflow_node_instance_id, tenant_id, workflow_instance_id,
                            stage_instance_id, work_order_id, node_id, task_id, status,
                            activation_event_id, version, activated_at
                        ) VALUES (
                            :nodeInstanceId, :tenantId, :workflowId,
                            :stageId, :workOrderId, :nodeId, :taskId, 'ACTIVE',
                            :activationEventId, 1, :activatedAt
                        )
                        """)
                .param("nodeInstanceId", nodeInstanceId)
                .param("tenantId", message.tenantId())
                .param("workflowId", candidate.workflowInstanceId())
                .param("stageId", stageId)
                .param("workOrderId", candidate.workOrderId())
                .param("nodeId", "COMPENSATE_" + candidate.nodeId())
                .param("taskId", task.taskId())
                .param("activationEventId", message.eventId())
                .param("activatedAt", java.sql.Timestamp.from(now))
                .update();

        jdbc.sql("""
                        INSERT INTO wfl_compensation_request (
                            compensation_request_id, tenant_id, workflow_instance_id, work_order_id,
                            source_node_instance_id, source_node_id, compensation_task_type,
                            compensation_stage_code, compensation_task_id, cancel_event_id,
                            status, version, requested_at
                        ) VALUES (
                            :requestId, :tenantId, :workflowId, :workOrderId,
                            :sourceNodeInstanceId, :sourceNodeId, :taskType,
                            :stageCode, :taskId, :cancelEventId,
                            'REQUESTED', 1, :requestedAt
                        )
                        """)
                .param("requestId", requestId)
                .param("tenantId", message.tenantId())
                .param("workflowId", candidate.workflowInstanceId())
                .param("workOrderId", candidate.workOrderId())
                .param("sourceNodeInstanceId", candidate.nodeInstanceId())
                .param("sourceNodeId", candidate.nodeId())
                .param("taskType", definition.taskType())
                .param("stageCode", definition.stageCode())
                .param("taskId", task.taskId())
                .param("cancelEventId", message.eventId())
                .param("requestedAt", java.sql.Timestamp.from(now))
                .update();
        return true;
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

    private record CompensationCandidate(
            UUID nodeInstanceId,
            String nodeId,
            UUID workflowInstanceId,
            UUID projectId,
            UUID workOrderId,
            UUID bundleId,
            String bundleDigest,
            UUID definitionVersionId,
            String definitionDigest
    ) {
    }
}
