package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ExpressionContext;
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
import com.serviceos.workflow.api.SignalWorkflowWaitCommand;
import com.serviceos.workflow.api.StageActivatedPayload;
import com.serviceos.workflow.api.StageCompletedPayload;
import com.serviceos.workflow.api.WorkflowCompletedPayload;
import com.serviceos.workflow.api.WorkflowWaitSignalResult;
import com.serviceos.workflow.api.WorkflowWaitSignalService;
import com.serviceos.workorder.api.FulfillWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderExpressionContext;
import com.serviceos.workorder.api.WorkOrderExpressionContextQuery;
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
 * TaskCompleted v1 的可靠本地消费者，并将 WAIT_EVENT 幂等唤醒推进到下一节点。
 *
 * <p>M19 支持线性推进；M269 支持 EXCLUSIVE_GATEWAY；M270 支持 WAIT_EVENT 挂起与信号唤醒。
 * 并行网关仍未实现。</p>
 */
@Service
final class WorkflowTaskCompletedHandler implements OutboxMessageHandler, WorkflowWaitSignalService {
    private static final String CONSUMER = "workflow.task-completed.v1";

    private final JdbcClient jdbc;
    private final InboxService inbox;
    private final ConfigurationService configurations;
    private final WorkflowDefinitionParser parser;
    private final TaskSchedulingService tasks;
    private final OutboxAppender outbox;
    private final WorkOrderCommandService workOrders;
    private final WorkOrderExpressionContextQuery workOrderExpressions;
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
            WorkOrderExpressionContextQuery workOrderExpressions,
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
        this.workOrderExpressions = workOrderExpressions;
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
        ExpressionContext expressionContext = expressionContext(message.tenantId(), current);
        var progression = parser.progression(asset, current.nodeId(), expressionContext);

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
        String progressionDigest = activateProgression(
                message.tenantId(),
                message.eventId(),
                message.correlationId(),
                message.payloadDigest(),
                current,
                progression,
                activatedAt);
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(), progressionDigest);
    }

    @Override
    @Transactional
    public WorkflowWaitSignalResult signal(SignalWorkflowWaitCommand command) {
        WaitSubscription wait = lockWaitingSubscription(
                command.tenantId(), command.waitEventType(), command.correlationKey());
        if (wait == null) {
            WaitSubscription completed = findCompletedSubscription(
                    command.tenantId(), command.waitEventType(), command.correlationKey());
            if (completed != null) {
                return new WorkflowWaitSignalResult(
                        completed.waitSubscriptionId(), completed.workflowInstanceId(),
                        completed.workOrderId(), true, "COMPLETED");
            }
            throw new IllegalArgumentException(
                    "WAIT_EVENT subscription not found for correlation key");
        }
        if (wait.wakeSignalId() != null) {
            throw new IllegalStateException("WAITING subscription already has wake signal");
        }
        Instant now = clock.instant();
        int waitUpdated = jdbc.sql("""
                        UPDATE wfl_wait_subscription
                           SET status = 'COMPLETED', wake_signal_id = :signalId,
                               completed_at = :completedAt, version = version + 1
                         WHERE tenant_id = :tenantId
                           AND wait_subscription_id = :waitId
                           AND status = 'WAITING'
                        """)
                .param("signalId", command.signalId())
                .param("completedAt", java.sql.Timestamp.from(now))
                .param("tenantId", command.tenantId())
                .param("waitId", wait.waitSubscriptionId())
                .update();
        if (waitUpdated != 1) {
            throw new IllegalStateException("WAIT_EVENT subscription is no longer waiting");
        }
        int nodeUpdated = jdbc.sql("""
                        UPDATE wfl_node_instance
                           SET status = 'COMPLETED', completion_event_id = :completionEventId,
                               completed_at = :completedAt, version = version + 1
                         WHERE tenant_id = :tenantId
                           AND workflow_node_instance_id = :nodeInstanceId
                           AND status = 'WAITING'
                        """)
                .param("completionEventId", UUID.nameUUIDFromBytes(
                        ("wait-signal:" + command.signalId()).getBytes()))
                .param("completedAt", java.sql.Timestamp.from(now))
                .param("tenantId", command.tenantId())
                .param("nodeInstanceId", wait.workflowNodeInstanceId())
                .update();
        if (nodeUpdated != 1) {
            throw new IllegalStateException("WAIT_EVENT node is no longer waiting");
        }
        NodeRuntime current = lockWaitNodeRuntime(command.tenantId(), wait.workflowNodeInstanceId());
        var asset = configurations.requireAssetVersion(
                command.tenantId(), current.workflowDefinitionVersionId(),
                ConfigurationAssetType.WORKFLOW, current.workflowDefinitionDigest());
        ExpressionContext expressionContext = expressionContext(command.tenantId(), current);
        var progression = parser.progressionAfterWait(asset, current.nodeId(), expressionContext);
        UUID activationEventId = UUID.nameUUIDFromBytes(
                ("wait-wake:" + command.signalId()).getBytes());
        activateProgression(
                command.tenantId(), activationEventId, command.correlationId(),
                Sha256.digest(command.signalId()), current, progression, now);
        return new WorkflowWaitSignalResult(
                wait.waitSubscriptionId(), wait.workflowInstanceId(), wait.workOrderId(),
                false, "COMPLETED");
    }

    /**
     * 根据推进定义激活 END / WAIT_EVENT / 下一任务。供 TaskCompleted 与 WAIT 唤醒共用。
     *
     * @return inbox 完成摘要
     */
    private String activateProgression(
            String tenantId,
            UUID activationEventId,
            String correlationId,
            String payloadDigest,
            NodeRuntime current,
            WorkflowDefinitionParser.ProgressionDefinition progression,
            Instant activatedAt
    ) {
        boolean stageCompleted = progression.end()
                || (progression.stageCode() != null
                && !current.stageCode().equals(progression.stageCode()));
        if (stageCompleted) {
            completeStage(tenantId, activationEventId, correlationId, current, activatedAt);
        }
        if (progression.end()) {
            completeWorkflow(tenantId, activationEventId, correlationId, current, activatedAt);
            return Sha256.digest(current.workflowNodeInstanceId() + "|END|" + progression.nodeId());
        }
        UUID nextStageInstanceId = current.stageInstanceId();
        int nextStageSequence = current.stageSequenceNo();
        if (stageCompleted) {
            nextStageInstanceId = UUID.randomUUID();
            nextStageSequence = current.stageSequenceNo() + 1;
            activateStage(
                    tenantId, activationEventId, correlationId, current,
                    nextStageInstanceId, progression.stageCode(), nextStageSequence, activatedAt);
        }
        UUID nextNodeInstanceId = UUID.randomUUID();
        if (progression.waiting()) {
            String correlationKey = WorkflowCorrelationKeys.resolve(
                    progression.correlationKeyTemplate(),
                    tenantId,
                    current.projectId(),
                    current.workOrderId(),
                    current.workflowInstanceId());
            jdbc.sql("""
                            INSERT INTO wfl_node_instance (
                                workflow_node_instance_id, tenant_id, workflow_instance_id,
                                stage_instance_id, work_order_id, node_id, task_id, status,
                                activation_event_id, version, activated_at
                            ) VALUES (
                                :nodeInstanceId, :tenantId, :workflowId,
                                :stageId, :workOrderId, :nodeId, NULL, 'WAITING',
                                :activationEventId, 1, :activatedAt
                            )
                            """)
                    .param("nodeInstanceId", nextNodeInstanceId)
                    .param("tenantId", tenantId)
                    .param("workflowId", current.workflowInstanceId())
                    .param("stageId", nextStageInstanceId)
                    .param("workOrderId", current.workOrderId())
                    .param("nodeId", progression.nodeId())
                    .param("activationEventId", activationEventId)
                    .param("activatedAt", java.sql.Timestamp.from(activatedAt))
                    .update();
            jdbc.sql("""
                            INSERT INTO wfl_wait_subscription (
                                wait_subscription_id, tenant_id, project_id, workflow_instance_id,
                                workflow_node_instance_id, work_order_id, node_id, wait_event_type,
                                correlation_key, status, activation_event_id, version, activated_at
                            ) VALUES (
                                :waitId, :tenantId, :projectId, :workflowId,
                                :nodeInstanceId, :workOrderId, :nodeId, :waitEventType,
                                :correlationKey, 'WAITING', :activationEventId, 1, :activatedAt
                            )
                            """)
                    .param("waitId", UUID.randomUUID())
                    .param("tenantId", tenantId)
                    .param("projectId", current.projectId())
                    .param("workflowId", current.workflowInstanceId())
                    .param("nodeInstanceId", nextNodeInstanceId)
                    .param("workOrderId", current.workOrderId())
                    .param("nodeId", progression.nodeId())
                    .param("waitEventType", progression.waitEventType())
                    .param("correlationKey", correlationKey)
                    .param("activationEventId", activationEventId)
                    .param("activatedAt", java.sql.Timestamp.from(activatedAt))
                    .update();
            return Sha256.digest(current.workflowNodeInstanceId() + "|WAIT|" + nextNodeInstanceId
                    + "|" + correlationKey);
        }

        ScheduledTaskView nextTask = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                tenantId, current.projectId(), current.workOrderId(),
                current.workflowInstanceId(), nextStageInstanceId, nextNodeInstanceId,
                progression.nodeId(), current.workflowDefinitionVersionId(),
                current.workflowDefinitionDigest(), current.configurationBundleId(),
                current.configurationBundleDigest(), progression.stageCode(),
                progression.taskType(), progression.taskKind(),
                progression.formRef(), progression.slaRef(),
                "work-order:" + current.workOrderId(), payloadDigest, 100, activatedAt, 3,
                correlationId, activationEventId.toString()));
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
                .param("tenantId", tenantId)
                .param("workflowId", current.workflowInstanceId())
                .param("stageId", nextStageInstanceId)
                .param("workOrderId", current.workOrderId())
                .param("nodeId", progression.nodeId())
                .param("taskId", nextTask.taskId())
                .param("activationEventId", activationEventId)
                .param("activatedAt", java.sql.Timestamp.from(activatedAt))
                .update();
        return Sha256.digest(current.workflowNodeInstanceId() + "|" + nextNodeInstanceId
                + "|" + nextTask.taskId());
    }

    private void completeStage(
            String tenantId,
            UUID activationEventId,
            String correlationId,
            NodeRuntime current,
            Instant completedAt
    ) {
        int updated = jdbc.sql("""
                        UPDATE wfl_stage_instance
                           SET status = 'COMPLETED', completed_at = :completedAt, version = version + 1
                         WHERE tenant_id = :tenantId AND stage_instance_id = :stageId
                           AND status = 'ACTIVE'
                        """)
                .param("completedAt", java.sql.Timestamp.from(completedAt))
                .param("tenantId", tenantId)
                .param("stageId", current.stageInstanceId())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("workflow stage is no longer active");
        }
        append(tenantId, activationEventId, correlationId, current.workOrderId().toString(),
                "stage.completed", "Stage", current.stageInstanceId(),
                current.stageVersion() + 1,
                new StageCompletedPayload(
                        current.stageInstanceId(), current.workflowInstanceId(), current.workOrderId(),
                        current.stageCode(), current.stageSequenceNo(), completedAt), completedAt);
    }

    private void activateStage(
            String tenantId,
            UUID activationEventId,
            String correlationId,
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
                .param("tenantId", tenantId)
                .param("workflowId", current.workflowInstanceId())
                .param("workOrderId", current.workOrderId())
                .param("stageCode", stageCode)
                .param("sequenceNo", sequenceNo)
                .param("activationEventId", activationEventId)
                .param("activatedAt", java.sql.Timestamp.from(activatedAt))
                .update();
        append(tenantId, activationEventId, correlationId, current.workOrderId().toString(),
                "stage.activated", "Stage", stageId, 1,
                new StageActivatedPayload(stageId, current.workflowInstanceId(), current.workOrderId(),
                        stageCode, sequenceNo, activatedAt), activatedAt);
    }

    private void completeWorkflow(
            String tenantId,
            UUID activationEventId,
            String correlationId,
            NodeRuntime current,
            Instant completedAt
    ) {
        int updated = jdbc.sql("""
                        UPDATE wfl_workflow_instance
                           SET status = 'COMPLETED', completed_at = :completedAt, version = version + 1
                         WHERE tenant_id = :tenantId AND workflow_instance_id = :workflowId
                           AND status = 'ACTIVE'
                        """)
                .param("completedAt", java.sql.Timestamp.from(completedAt))
                .param("tenantId", tenantId)
                .param("workflowId", current.workflowInstanceId())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("workflow is no longer active");
        }
        append(tenantId, activationEventId, correlationId, current.workOrderId().toString(),
                "workflow.completed", "Workflow", current.workflowInstanceId(),
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
                .param("tenantId", tenantId)
                .param("workflowId", current.workflowInstanceId())
                .query(String.class)
                .list();
        workOrders.fulfill(new FulfillWorkOrderCommand(
                tenantId, current.workOrderId(), current.workflowInstanceId(),
                activationEventId, correlationId, completedStageCodes));
    }

    private void append(
            String tenantId,
            UUID causationEventId,
            String correlationId,
            String partitionKey,
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
                aggregateType, aggregateId.toString(), aggregateVersion, tenantId,
                correlationId, causationEventId.toString(), partitionKey,
                json, Sha256.digest(json), occurredAt));
    }

    private WaitSubscription lockWaitingSubscription(
            String tenantId,
            String waitEventType,
            String correlationKey
    ) {
        return jdbc.sql("""
                        SELECT wait_subscription_id, tenant_id, project_id, workflow_instance_id,
                               workflow_node_instance_id, work_order_id, node_id, wait_event_type,
                               correlation_key, status, wake_signal_id
                          FROM wfl_wait_subscription
                         WHERE tenant_id = :tenantId
                           AND wait_event_type = :waitEventType
                           AND correlation_key = :correlationKey
                           AND status = 'WAITING'
                         FOR UPDATE
                        """)
                .param("tenantId", tenantId)
                .param("waitEventType", waitEventType)
                .param("correlationKey", correlationKey)
                .query(WaitSubscription.class)
                .optional()
                .orElse(null);
    }

    private WaitSubscription findCompletedSubscription(
            String tenantId,
            String waitEventType,
            String correlationKey
    ) {
        return jdbc.sql("""
                        SELECT wait_subscription_id, tenant_id, project_id, workflow_instance_id,
                               workflow_node_instance_id, work_order_id, node_id, wait_event_type,
                               correlation_key, status, wake_signal_id
                          FROM wfl_wait_subscription
                         WHERE tenant_id = :tenantId
                           AND wait_event_type = :waitEventType
                           AND correlation_key = :correlationKey
                           AND status = 'COMPLETED'
                         ORDER BY completed_at DESC
                         LIMIT 1
                        """)
                .param("tenantId", tenantId)
                .param("waitEventType", waitEventType)
                .param("correlationKey", correlationKey)
                .query(WaitSubscription.class)
                .optional()
                .orElse(null);
    }

    private NodeRuntime lockWaitNodeRuntime(String tenantId, UUID nodeInstanceId) {
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
                               CAST(NULL AS varchar) AS task_type,
                               CAST(NULL AS varchar) AS task_kind,
                               CAST(NULL AS varchar) AS task_status,
                               CAST(NULL AS varchar) AS task_result_ref,
                               CAST(NULL AS varchar) AS task_result_digest,
                               CAST(NULL AS uuid) AS task_definition_version_id,
                               CAST(NULL AS varchar) AS task_definition_digest
                          FROM wfl_node_instance node
                          JOIN wfl_workflow_instance workflow
                            ON workflow.tenant_id = node.tenant_id
                           AND workflow.workflow_instance_id = node.workflow_instance_id
                          JOIN wfl_stage_instance stage
                            ON stage.tenant_id = node.tenant_id
                           AND stage.stage_instance_id = node.stage_instance_id
                         WHERE node.tenant_id = :tenantId
                           AND node.workflow_node_instance_id = :nodeInstanceId
                         FOR UPDATE OF node
                        """)
                .param("tenantId", tenantId)
                .param("nodeInstanceId", nodeInstanceId)
                .query(NodeRuntime.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("workflow wait node does not exist"));
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

    /**
     * 网关条件只读取工单冻结事实与当前完成任务的 stage/taskType；不引入可变“最新配置”。
     */
    private ExpressionContext expressionContext(String tenantId, NodeRuntime current) {
        WorkOrderExpressionContext workOrder = workOrderExpressions.find(tenantId, current.workOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "workflow progression missing WorkOrder expression context: "
                                + current.workOrderId()));
        String taskType = current.taskType() == null ? "WAIT_EVENT" : current.taskType();
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext(
                        workOrder.clientCode(), workOrder.brandCode(), workOrder.serviceProductCode()),
                new ExpressionContext.RegionContext(
                        workOrder.provinceCode(), workOrder.cityCode(), workOrder.districtCode()),
                new ExpressionContext.TaskContext(current.stageCode(), taskType));
    }

    private record WaitSubscription(
            UUID waitSubscriptionId,
            String tenantId,
            UUID projectId,
            UUID workflowInstanceId,
            UUID workflowNodeInstanceId,
            UUID workOrderId,
            String nodeId,
            String waitEventType,
            String correlationKey,
            String status,
            String wakeSignalId
    ) {
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
