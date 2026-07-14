package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskCompletedPayload;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.workflow.api.StageActivatedPayload;
import com.serviceos.workflow.api.StageCompletedPayload;
import com.serviceos.workflow.api.WorkflowCompletedPayload;
import com.serviceos.workorder.api.FulfillWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * TaskCompleted v1 的可靠本地消费者，将当前流程节点原子地推进到唯一下一任务或 END。
 *
 * <p>M19 支持单一无条件的同阶段、跨阶段与 END；网关、并行和条件分支仍失败关闭。</p>
 */
@Service
final class WorkflowTaskCompletedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "workflow.task-completed.v1";

    private final JdbcClient jdbc;
    private final InboxService inbox;
    private final ConfigurationService configurations;
    private final WorkflowDefinitionParser parser;
    private final TaskSchedulingService tasks;
    private final OutboxAppender outbox;
    private final WorkOrderCommandService workOrders;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    WorkflowTaskCompletedHandler(
            JdbcClient jdbc,
            InboxService inbox,
            ConfigurationService configurations,
            WorkflowDefinitionParser parser,
            TaskSchedulingService tasks,
            OutboxAppender outbox,
            WorkOrderCommandService workOrders,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.inbox = inbox;
        this.configurations = configurations;
        this.parser = parser;
        this.tasks = tasks;
        this.outbox = outbox;
        this.workOrders = workOrders;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return "task.completed".equals(eventType) && (schemaVersion == 1 || schemaVersion == 2);
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
        var progression = parser.progression(asset, current.nodeId());

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
        boolean stageCompleted = progression.end()
                || !current.stageCode().equals(progression.stageCode());
        if (stageCompleted) {
            completeStage(message, current, activatedAt);
        }
        if (progression.end()) {
            completeWorkflow(message, current, activatedAt);
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(current.workflowNodeInstanceId() + "|END|" + progression.nodeId()));
            return;
        }

        UUID nextStageInstanceId = current.stageInstanceId();
        int nextStageSequence = current.stageSequenceNo();
        if (stageCompleted) {
            nextStageInstanceId = UUID.randomUUID();
            nextStageSequence = current.stageSequenceNo() + 1;
            activateStage(message, current, nextStageInstanceId, progression.stageCode(),
                    nextStageSequence, activatedAt);
        }
        UUID nextNodeInstanceId = UUID.randomUUID();
        ScheduledTaskView nextTask = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                message.tenantId(), current.projectId(), current.workOrderId(),
                current.workflowInstanceId(), nextStageInstanceId, nextNodeInstanceId,
                progression.nodeId(), current.workflowDefinitionVersionId(),
                current.workflowDefinitionDigest(), current.configurationBundleId(),
                current.configurationBundleDigest(), progression.stageCode(),
                progression.taskType(), progression.taskKind(),
                progression.formRef(),
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
                .param("stageId", nextStageInstanceId)
                .param("workOrderId", current.workOrderId())
                .param("nodeId", progression.nodeId())
                .param("taskId", nextTask.taskId())
                .param("activationEventId", message.eventId())
                .param("activatedAt", java.sql.Timestamp.from(activatedAt))
                .update();

        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(current.workflowNodeInstanceId() + "|" + nextNodeInstanceId
                        + "|" + nextTask.taskId()));
    }

    private void completeStage(OutboxMessage message, NodeRuntime current, Instant completedAt) {
        int updated = jdbc.sql("""
                        UPDATE wfl_stage_instance
                           SET status = 'COMPLETED', completed_at = :completedAt, version = version + 1
                         WHERE tenant_id = :tenantId AND stage_instance_id = :stageId
                           AND status = 'ACTIVE'
                        """)
                .param("completedAt", java.sql.Timestamp.from(completedAt))
                .param("tenantId", message.tenantId())
                .param("stageId", current.stageInstanceId())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("workflow stage is no longer active");
        }
        append(message, "stage.completed", "Stage", current.stageInstanceId(),
                current.stageVersion() + 1,
                new StageCompletedPayload(
                        current.stageInstanceId(), current.workflowInstanceId(), current.workOrderId(),
                        current.stageCode(), current.stageSequenceNo(), completedAt), completedAt);
    }

    private void activateStage(
            OutboxMessage message,
            NodeRuntime current,
            UUID stageId,
            String stageCode,
            int sequenceNo,
            Instant activatedAt
    ) {
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
                .param("workflowId", current.workflowInstanceId())
                .param("workOrderId", current.workOrderId())
                .param("stageCode", stageCode)
                .param("sequenceNo", sequenceNo)
                .param("activationEventId", message.eventId())
                .param("activatedAt", java.sql.Timestamp.from(activatedAt))
                .update();
        append(message, "stage.activated", "Stage", stageId, 1,
                new StageActivatedPayload(stageId, current.workflowInstanceId(), current.workOrderId(),
                        stageCode, sequenceNo, activatedAt), activatedAt);
    }

    private void completeWorkflow(OutboxMessage message, NodeRuntime current, Instant completedAt) {
        int updated = jdbc.sql("""
                        UPDATE wfl_workflow_instance
                           SET status = 'COMPLETED', completed_at = :completedAt, version = version + 1
                         WHERE tenant_id = :tenantId AND workflow_instance_id = :workflowId
                           AND status = 'ACTIVE'
                        """)
                .param("completedAt", java.sql.Timestamp.from(completedAt))
                .param("tenantId", message.tenantId())
                .param("workflowId", current.workflowInstanceId())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("workflow is no longer active");
        }
        append(message, "workflow.completed", "Workflow", current.workflowInstanceId(),
                current.workflowVersion() + 1,
                new WorkflowCompletedPayload(
                        current.workflowInstanceId(), current.projectId(), current.workOrderId(),
                        current.workflowDefinitionVersionId(), current.workflowDefinitionDigest(), completedAt),
                completedAt);
        List<String> completedStageCodes = jdbc.sql("""
                        SELECT stage_code
                          FROM wfl_stage_instance
                         WHERE tenant_id = :tenantId AND workflow_instance_id = :workflowId
                           AND status = 'COMPLETED'
                         ORDER BY sequence_no
                        """)
                .param("tenantId", message.tenantId())
                .param("workflowId", current.workflowInstanceId())
                .query(String.class)
                .list();
        workOrders.fulfill(new FulfillWorkOrderCommand(
                message.tenantId(), current.workOrderId(), current.workflowInstanceId(),
                message.eventId(), message.correlationId(), completedStageCodes));
    }

    private void append(
            OutboxMessage source,
            String eventType,
            String aggregateType,
            UUID aggregateId,
            long aggregateVersion,
            Object payload,
            Instant occurredAt
    ) {
        final String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("workflow progression event serialization failed", exception);
        }
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "workflow", eventType, 1,
                aggregateType, aggregateId.toString(), aggregateVersion, source.tenantId(),
                source.correlationId(), source.eventId().toString(), source.partitionKey(),
                json, Sha256.digest(json), occurredAt));
    }

    private NodeRuntime lockCurrentNode(String tenantId, UUID nodeInstanceId) {
        return jdbc.sql("""
                        SELECT node.workflow_node_instance_id, node.workflow_instance_id,
                               node.stage_instance_id, node.work_order_id, node.node_id,
                               node.task_id, node.status AS node_status,
                               stage.stage_code, stage.sequence_no AS stage_sequence_no,
                               stage.status AS stage_status, stage.version AS stage_version,
                               workflow.project_id, workflow.status AS workflow_status,
                               workflow.version AS workflow_version,
                               workflow.workflow_definition_version_id,
                               workflow.definition_digest AS workflow_definition_digest,
                               workflow.configuration_bundle_id,
                               workflow.configuration_bundle_digest,
                               task.task_type, task.task_kind, task.status AS task_status,
                               task.result_ref AS task_result_ref,
                               task.result_digest AS task_result_digest,
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
        boolean taskCompleted = switch (current.taskKind()) {
            case "AUTOMATED" -> "SUCCEEDED".equals(current.taskStatus());
            case "HUMAN" -> "COMPLETED".equals(current.taskStatus());
            default -> false;
        };
        if (!"ACTIVE".equals(current.nodeStatus())
                || !"ACTIVE".equals(current.stageStatus())
                || !"ACTIVE".equals(current.workflowStatus())
                || !taskCompleted) {
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
        if ("HUMAN".equals(current.taskKind())) {
            // 人工任务的摘要属于外部表单/业务结果内容，不能错误地当作 resultRef 字符串的摘要。
            // 命令事务已经把引用和摘要冻结到 Task，此处必须与持久化事实逐字段一致。
            if (!Objects.equals(completed.resultRef(), current.taskResultRef())
                    || !Objects.equals(completed.resultDigest(), current.taskResultDigest())) {
                throw new IllegalArgumentException("TaskCompleted result does not match frozen human task result");
            }
        } else {
            String expectedResultDigest = Sha256.digest(
                    completed.resultRef() == null ? "" : completed.resultRef());
            if (!expectedResultDigest.equals(completed.resultDigest())) {
                throw new IllegalArgumentException("TaskCompleted result digest does not match resultRef");
            }
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
            int stageSequenceNo,
            String stageStatus,
            long stageVersion,
            UUID projectId,
            String workflowStatus,
            long workflowVersion,
            UUID workflowDefinitionVersionId,
            String workflowDefinitionDigest,
            UUID configurationBundleId,
            String configurationBundleDigest,
            String taskType,
            String taskKind,
            String taskStatus,
            String taskResultRef,
            String taskResultDigest,
            UUID taskDefinitionVersionId,
            String taskDefinitionDigest
    ) {
    }
}
