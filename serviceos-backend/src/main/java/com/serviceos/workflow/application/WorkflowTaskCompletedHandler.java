package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskCompletedPayload;
import com.serviceos.task.api.TaskSchedulingService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TaskCompleted v1 的可靠本地消费者，将当前流程节点原子地推进到同阶段的唯一下一任务。
 *
 * <p>M18 的语义边界是“单一、无条件、同阶段、任务到任务”。网关、并行、跨阶段和结束节点
 * 必须由后续里程碑提供完整语义；本消费者遇到这些定义会失败关闭，避免提前写出错误状态。</p>
 */
@Service
final class WorkflowTaskCompletedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "workflow.task-completed.v1";

    private final JdbcClient jdbc;
    private final InboxService inbox;
    private final ConfigurationService configurations;
    private final WorkflowDefinitionParser parser;
    private final TaskSchedulingService tasks;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    WorkflowTaskCompletedHandler(
            JdbcClient jdbc,
            InboxService inbox,
            ConfigurationService configurations,
            WorkflowDefinitionParser parser,
            TaskSchedulingService tasks,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.inbox = inbox;
        this.configurations = configurations;
        this.parser = parser;
        this.tasks = tasks;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return "task.completed".equals(eventType) && schemaVersion == 1;
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        validateEnvelope(message);
        InboxDecision decision = inbox.begin(
                message.tenantId(), CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }

        TaskCompletedPayload completed = readPayload(message.payload());
        validatePayloadIdentity(message, completed);
        NodeRuntime current = lockCurrentNode(message.tenantId(), completed.workflowNodeInstanceId());
        validateFrozenContext(completed, current);

        var asset = configurations.requireAssetVersion(
                message.tenantId(), current.workflowDefinitionVersionId(),
                ConfigurationAssetType.WORKFLOW, current.workflowDefinitionDigest());
        var nextDefinition = parser.nextTask(asset, current.nodeId());
        if (!current.stageCode().equals(nextDefinition.stageCode())) {
            throw new IllegalArgumentException("cross-stage progression is not supported by M18");
        }

        int completedRows = jdbc.sql("""
                        UPDATE wfl_node_instance
                           SET status = 'COMPLETED', completion_event_id = :completionEventId,
                               completed_at = :completedAt, version = version + 1
                         WHERE tenant_id = :tenantId
                           AND workflow_node_instance_id = :nodeInstanceId
                           AND status = 'ACTIVE'
                        """)
                .param("completionEventId", message.eventId())
                .param("completedAt", java.sql.Timestamp.from(completed.completedAt()))
                .param("tenantId", message.tenantId())
                .param("nodeInstanceId", current.workflowNodeInstanceId())
                .update();
        if (completedRows != 1) {
            throw new IllegalStateException("workflow node is no longer active");
        }

        Instant activatedAt = clock.instant();
        UUID nextNodeInstanceId = UUID.randomUUID();
        ScheduledTaskView nextTask = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                message.tenantId(), current.projectId(), current.workOrderId(),
                current.workflowInstanceId(), current.stageInstanceId(), nextNodeInstanceId,
                nextDefinition.nodeId(), current.workflowDefinitionVersionId(),
                current.workflowDefinitionDigest(), nextDefinition.taskType(), nextDefinition.taskKind(),
                "work-order:" + current.workOrderId(), message.payloadDigest(), 100, activatedAt, 3,
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
                .param("nodeInstanceId", nextNodeInstanceId)
                .param("tenantId", message.tenantId())
                .param("workflowId", current.workflowInstanceId())
                .param("stageId", current.stageInstanceId())
                .param("workOrderId", current.workOrderId())
                .param("nodeId", nextDefinition.nodeId())
                .param("taskId", nextTask.taskId())
                .param("activationEventId", message.eventId())
                .param("activatedAt", java.sql.Timestamp.from(activatedAt))
                .update();

        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(current.workflowNodeInstanceId() + "|" + nextNodeInstanceId
                        + "|" + nextTask.taskId()));
    }

    private NodeRuntime lockCurrentNode(String tenantId, UUID nodeInstanceId) {
        return jdbc.sql("""
                        SELECT node.workflow_node_instance_id, node.workflow_instance_id,
                               node.stage_instance_id, node.work_order_id, node.node_id,
                               node.task_id, node.status AS node_status,
                               stage.stage_code, stage.status AS stage_status,
                               workflow.project_id, workflow.status AS workflow_status,
                               workflow.workflow_definition_version_id,
                               workflow.definition_digest AS workflow_definition_digest,
                               task.task_type, task.status AS task_status,
                               task.workflow_definition_version_id AS task_definition_version_id,
                               task.workflow_definition_digest AS task_definition_digest
                          FROM wfl_node_instance node
                          JOIN wfl_workflow_instance workflow
                            ON workflow.tenant_id = node.tenant_id
                           AND workflow.workflow_instance_id = node.workflow_instance_id
                          JOIN wfl_stage_instance stage
                            ON stage.tenant_id = node.tenant_id
                           AND stage.stage_instance_id = node.stage_instance_id
                          JOIN tsk_task task
                            ON task.tenant_id = node.tenant_id
                           AND task.task_id = node.task_id
                         WHERE node.tenant_id = :tenantId
                           AND node.workflow_node_instance_id = :nodeInstanceId
                         FOR UPDATE OF node
                        """)
                .param("tenantId", tenantId)
                .param("nodeInstanceId", nodeInstanceId)
                .query(NodeRuntime.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("workflow node instance does not exist"));
    }

    private static void validateFrozenContext(TaskCompletedPayload completed, NodeRuntime current) {
        if (!"ACTIVE".equals(current.nodeStatus())
                || !"ACTIVE".equals(current.stageStatus())
                || !"ACTIVE".equals(current.workflowStatus())
                || !"SUCCEEDED".equals(current.taskStatus())) {
            throw new IllegalStateException("TaskCompleted does not reference an active completed workflow task");
        }
        if (!completed.taskId().equals(current.taskId())
                || !completed.projectId().equals(current.projectId())
                || !completed.workOrderId().equals(current.workOrderId())
                || !completed.workflowInstanceId().equals(current.workflowInstanceId())
                || !completed.stageInstanceId().equals(current.stageInstanceId())
                || !completed.workflowNodeInstanceId().equals(current.workflowNodeInstanceId())
                || !completed.workflowNodeId().equals(current.nodeId())
                || !completed.taskType().equals(current.taskType())
                || !completed.workflowDefinitionVersionId().equals(current.workflowDefinitionVersionId())
                || !completed.workflowDefinitionDigest().equals(current.workflowDefinitionDigest())
                || !current.taskDefinitionVersionId().equals(current.workflowDefinitionVersionId())
                || !current.taskDefinitionDigest().equals(current.workflowDefinitionDigest())) {
            throw new IllegalArgumentException("TaskCompleted context does not match frozen workflow runtime");
        }
        String expectedResultDigest = Sha256.digest(
                completed.resultRef() == null ? "" : completed.resultRef());
        if (!expectedResultDigest.equals(completed.resultDigest())) {
            throw new IllegalArgumentException("TaskCompleted result digest does not match resultRef");
        }
    }

    private TaskCompletedPayload readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, TaskCompletedPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("TaskCompleted payload cannot be decoded", exception);
        }
    }

    private static void validateEnvelope(OutboxMessage message) {
        if (!"task".equals(message.module()) || !"Task".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported TaskCompleted envelope");
        }
    }

    private static void validatePayloadIdentity(OutboxMessage message, TaskCompletedPayload completed) {
        Objects.requireNonNull(completed.completedAt(), "TaskCompleted completedAt");
        if (!completed.taskId().toString().equals(message.aggregateId())) {
            throw new IllegalArgumentException("TaskCompleted aggregateId does not match payload");
        }
    }

    private record NodeRuntime(
            UUID workflowNodeInstanceId,
            UUID workflowInstanceId,
            UUID stageInstanceId,
            UUID workOrderId,
            String nodeId,
            UUID taskId,
            String nodeStatus,
            String stageCode,
            String stageStatus,
            UUID projectId,
            String workflowStatus,
            UUID workflowDefinitionVersionId,
            String workflowDefinitionDigest,
            String taskType,
            String taskStatus,
            UUID taskDefinitionVersionId,
            String taskDefinitionDigest
    ) {
    }
}
