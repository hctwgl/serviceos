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
import com.serviceos.workflow.api.ReviewGateWait;
import com.serviceos.workflow.api.SignalWorkflowWaitCommand;
import com.serviceos.workflow.api.StageActivatedPayload;
import com.serviceos.workflow.api.StageCompletedPayload;
import com.serviceos.workflow.api.WorkflowCompletedPayload;
import com.serviceos.workflow.api.WorkflowTimerFireService;
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
final class WorkflowTaskCompletedHandler
        implements OutboxMessageHandler, WorkflowWaitSignalService, WorkflowTimerFireService {
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
        Boolean multiComplete = completeMultiInstanceSlotIfPresent(
                message.tenantId(), current, activatedAt);
        if (Boolean.FALSE.equals(multiComplete)) {
            inbox.complete(
                    message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(current.workflowNodeInstanceId() + "|MI_WAIT|" + current.nodeId()));
            return;
        }

        var asset = configurations.requireAssetVersion(
                message.tenantId(), current.workflowDefinitionVersionId(),
                ConfigurationAssetType.WORKFLOW, current.workflowDefinitionDigest());
        ExpressionContext expressionContext = expressionContext(message.tenantId(), current);
        var progression = parser.progression(asset, current.nodeId(), expressionContext);
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

    /**
     * TIMER worker 到期唤醒：完成 WAITING 节点并推进后继。
     */
    @Transactional
    public void fireTimer(UUID timerSubscriptionId, String correlationId) {
        var timer = jdbc.sql("""
                        SELECT timer_subscription_id, tenant_id, project_id, workflow_instance_id,
                               workflow_node_instance_id, work_order_id, node_id, status
                          FROM wfl_timer_subscription
                         WHERE timer_subscription_id = :timerId
                         FOR UPDATE
                        """)
                .param("timerId", timerSubscriptionId)
                .query((rs, row) -> new TimerSubscription(
                        rs.getObject("timer_subscription_id", UUID.class),
                        rs.getString("tenant_id"),
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("workflow_instance_id", UUID.class),
                        rs.getObject("workflow_node_instance_id", UUID.class),
                        rs.getObject("work_order_id", UUID.class),
                        rs.getString("node_id"),
                        rs.getString("status")))
                .single();
        if ("FIRED".equals(timer.status())) {
            return;
        }
        if (!"CLAIMED".equals(timer.status()) && !"WAITING".equals(timer.status())) {
            throw new IllegalStateException("TIMER subscription not firable: " + timer.status());
        }
        Instant now = clock.instant();
        UUID fireEventId = UUID.nameUUIDFromBytes(("timer-fire:" + timerSubscriptionId).getBytes());
        int timerUpdated = jdbc.sql("""
                        UPDATE wfl_timer_subscription
                           SET status = 'FIRED', fire_event_id = :fireEventId, fired_at = :firedAt,
                               claim_owner = NULL, claim_until = NULL, version = version + 1
                         WHERE timer_subscription_id = :timerId
                           AND status IN ('WAITING', 'CLAIMED')
                        """)
                .param("fireEventId", fireEventId)
                .param("firedAt", java.sql.Timestamp.from(now))
                .param("timerId", timerSubscriptionId)
                .update();
        if (timerUpdated != 1) {
            throw new IllegalStateException("TIMER subscription fire lost race");
        }
        int nodeUpdated = jdbc.sql("""
                        UPDATE wfl_node_instance
                           SET status = 'COMPLETED', completion_event_id = :completionEventId,
                               completed_at = :completedAt, version = version + 1
                         WHERE tenant_id = :tenantId
                           AND workflow_node_instance_id = :nodeInstanceId
                           AND status = 'WAITING'
                        """)
                .param("completionEventId", fireEventId)
                .param("completedAt", java.sql.Timestamp.from(now))
                .param("tenantId", timer.tenantId())
                .param("nodeInstanceId", timer.workflowNodeInstanceId())
                .update();
        if (nodeUpdated != 1) {
            throw new IllegalStateException("TIMER node is no longer waiting");
        }
        NodeRuntime current = lockWaitNodeRuntime(timer.tenantId(), timer.workflowNodeInstanceId());
        var asset = configurations.requireAssetVersion(
                timer.tenantId(), current.workflowDefinitionVersionId(),
                ConfigurationAssetType.WORKFLOW, current.workflowDefinitionDigest());
        ExpressionContext expressionContext = expressionContext(timer.tenantId(), current);
        var progression = parser.progressionAfterTimer(asset, current.nodeId(), expressionContext);
        activateProgression(
                timer.tenantId(), fireEventId, correlationId,
                Sha256.digest(timerSubscriptionId.toString()), current, progression, now);
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
        if (progression.joinPending()) {
            boolean complete = recordParallelJoinArrival(
                    tenantId, current, progression, activationEventId, activatedAt);
            if (!complete) {
                return Sha256.digest(current.workflowNodeInstanceId() + "|JOIN_WAIT|"
                        + progression.nodeId() + "|" + progression.joinFromNodeId());
            }
            var asset = configurations.requireAssetVersion(
                    tenantId, current.workflowDefinitionVersionId(),
                    ConfigurationAssetType.WORKFLOW, current.workflowDefinitionDigest());
            ExpressionContext expressionContext = expressionContext(tenantId, current);
            var afterJoin = parser.progressionAfterJoin(asset, progression.nodeId(), expressionContext);
            return activateProgression(
                    tenantId, activationEventId, correlationId, payloadDigest,
                    current, afterJoin, activatedAt);
        }
        if (progression.fork()) {
            NodeRuntime branchCurrent = current;
            String branchStage = progression.stageCode();
            if (branchStage != null && !branchStage.equals(current.stageCode())) {
                completeStage(tenantId, activationEventId, correlationId, current, activatedAt);
                UUID stageId = UUID.randomUUID();
                int sequenceNo = current.stageSequenceNo() + 1;
                activateStage(
                        tenantId, activationEventId, correlationId, current,
                        stageId, branchStage, sequenceNo, activatedAt);
                branchCurrent = current.withActiveStage(stageId, branchStage, sequenceNo);
            }
            StringBuilder digest = new StringBuilder();
            for (var branch : progression.forkBranches()) {
                digest.append('|').append(activateProgression(
                        tenantId, activationEventId, correlationId, payloadDigest,
                        branchCurrent, branch, activatedAt));
            }
            return Sha256.digest(current.workflowNodeInstanceId() + "|FORK|" + progression.nodeId()
                    + digest);
        }
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
        if (progression.subProcess()) {
            return activateSubProcess(
                    tenantId, activationEventId, correlationId, current,
                    nextStageInstanceId, progression, activatedAt);
        }
        if (progression.waiting() || progression.timer()) {
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
            if (progression.waiting()) {
                String correlationKey = WorkflowCorrelationKeys.resolve(
                        progression.correlationKeyTemplate(),
                        tenantId,
                        current.projectId(),
                        current.workOrderId(),
                        current.workflowInstanceId());
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
                // M365：REVIEW_TASK 门闸激活时立即消费早期 APPROVED 信号，避免“先审后到闸”卡住。
                if (ReviewGateWait.WAIT_EVENT_TYPE.equals(progression.waitEventType())) {
                    EarlyReviewSignal early = findUnconsumedEarlyReviewSignal(
                            tenantId, current.workOrderId());
                    if (early != null) {
                        WorkflowWaitSignalResult woke = signal(new SignalWorkflowWaitCommand(
                                tenantId, ReviewGateWait.WAIT_EVENT_TYPE, correlationKey,
                                early.signalId(), early.correlationId()));
                        markEarlyReviewSignalConsumed(tenantId, current.workOrderId(), activatedAt);
                        return Sha256.digest(current.workflowNodeInstanceId() + "|WAIT_EARLY|"
                                + nextNodeInstanceId + "|" + woke.waitSubscriptionId());
                    }
                }
                return Sha256.digest(current.workflowNodeInstanceId() + "|WAIT|" + nextNodeInstanceId
                        + "|" + correlationKey);
            }
            Instant fireAt = activatedAt.plusSeconds(progression.durationSeconds());
            jdbc.sql("""
                            INSERT INTO wfl_timer_subscription (
                                timer_subscription_id, tenant_id, project_id, workflow_instance_id,
                                workflow_node_instance_id, work_order_id, node_id, duration_seconds,
                                fire_at, status, activation_event_id, version, activated_at
                            ) VALUES (
                                :timerId, :tenantId, :projectId, :workflowId,
                                :nodeInstanceId, :workOrderId, :nodeId, :durationSeconds,
                                :fireAt, 'WAITING', :activationEventId, 1, :activatedAt
                            )
                            """)
                    .param("timerId", UUID.randomUUID())
                    .param("tenantId", tenantId)
                    .param("projectId", current.projectId())
                    .param("workflowId", current.workflowInstanceId())
                    .param("nodeInstanceId", nextNodeInstanceId)
                    .param("workOrderId", current.workOrderId())
                    .param("nodeId", progression.nodeId())
                    .param("durationSeconds", progression.durationSeconds())
                    .param("fireAt", java.sql.Timestamp.from(fireAt))
                    .param("activationEventId", activationEventId)
                    .param("activatedAt", java.sql.Timestamp.from(activatedAt))
                    .update();
            return Sha256.digest(current.workflowNodeInstanceId() + "|TIMER|" + nextNodeInstanceId
                    + "|" + progression.durationSeconds());
        }

        if (progression.multiInstance()) {
            return activateMultiInstanceTasks(
                    tenantId, activationEventId, correlationId, payloadDigest, current,
                    nextStageInstanceId, progression, activatedAt);
        }

        ScheduledTaskView nextTask = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                tenantId, current.projectId(), current.workOrderId(),
                current.workflowInstanceId(), nextStageInstanceId, nextNodeInstanceId,
                progression.nodeId(), current.workflowDefinitionVersionId(),
                current.workflowDefinitionDigest(), current.configurationBundleId(),
                current.configurationBundleDigest(), progression.stageCode(),
                progression.taskType(), progression.taskKind(),
                progression.formRef(), progression.slaRef(), progression.assigneePolicyRef(),
                progression.dispatchPolicyRef(), progression.ruleRef(),
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

    private String activateMultiInstanceTasks(
            String tenantId,
            UUID activationEventId,
            String correlationId,
            String payloadDigest,
            NodeRuntime current,
            UUID stageInstanceId,
            WorkflowDefinitionParser.ProgressionDefinition progression,
            Instant activatedAt
    ) {
        UUID collectionId = UUID.randomUUID();
        int cardinality = progression.multiInstanceCardinality();
        jdbc.sql("""
                        INSERT INTO wfl_multi_instance (
                            multi_instance_id, tenant_id, workflow_instance_id, node_id,
                            expected_instances, completed_instances, status, activation_event_id,
                            version, opened_at
                        ) VALUES (
                            :collectionId, :tenantId, :workflowId, :nodeId,
                            :expected, 0, 'OPEN', :activationEventId, 1, :openedAt
                        )
                        """)
                .param("collectionId", collectionId)
                .param("tenantId", tenantId)
                .param("workflowId", current.workflowInstanceId())
                .param("nodeId", progression.nodeId())
                .param("expected", cardinality)
                .param("activationEventId", activationEventId)
                .param("openedAt", java.sql.Timestamp.from(activatedAt))
                .update();
        StringBuilder digest = new StringBuilder();
        for (int index = 0; index < cardinality; index++) {
            UUID nodeInstanceId = UUID.randomUUID();
            ScheduledTaskView task = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                    tenantId, current.projectId(), current.workOrderId(),
                    current.workflowInstanceId(), stageInstanceId, nodeInstanceId,
                    progression.nodeId(), current.workflowDefinitionVersionId(),
                    current.workflowDefinitionDigest(), current.configurationBundleId(),
                    current.configurationBundleDigest(), progression.stageCode(),
                    progression.taskType(), progression.taskKind(),
                    progression.formRef(), progression.slaRef(), progression.assigneePolicyRef(),
                    progression.dispatchPolicyRef(), progression.ruleRef(),
                    "work-order:" + current.workOrderId() + "|mi:" + index,
                    Sha256.digest(payloadDigest + "|" + index), 100, activatedAt, 3,
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
                    .param("nodeInstanceId", nodeInstanceId)
                    .param("tenantId", tenantId)
                    .param("workflowId", current.workflowInstanceId())
                    .param("stageId", stageInstanceId)
                    .param("workOrderId", current.workOrderId())
                    .param("nodeId", progression.nodeId())
                    .param("taskId", task.taskId())
                    .param("activationEventId", activationEventId)
                    .param("activatedAt", java.sql.Timestamp.from(activatedAt))
                    .update();
            jdbc.sql("""
                            INSERT INTO wfl_multi_instance_slot (
                                multi_instance_id, tenant_id, instance_index,
                                workflow_node_instance_id, task_id, status
                            ) VALUES (
                                :collectionId, :tenantId, :instanceIndex,
                                :nodeInstanceId, :taskId, 'ACTIVE'
                            )
                            """)
                    .param("collectionId", collectionId)
                    .param("tenantId", tenantId)
                    .param("instanceIndex", index)
                    .param("nodeInstanceId", nodeInstanceId)
                    .param("taskId", task.taskId())
                    .update();
            digest.append('|').append(nodeInstanceId).append(':').append(task.taskId());
        }
        return Sha256.digest(current.workflowNodeInstanceId() + "|MI|" + progression.nodeId()
                + "|" + cardinality + digest);
    }

    /**
     * @return null 非多实例；false 未到齐；true 已全部完成
     */
    private Boolean completeMultiInstanceSlotIfPresent(
            String tenantId,
            NodeRuntime current,
            Instant completedAt
    ) {
        UUID collectionId = jdbc.sql("""
                        SELECT multi_instance_id
                          FROM wfl_multi_instance_slot
                         WHERE tenant_id = :tenantId
                           AND workflow_node_instance_id = :nodeInstanceId
                           AND status = 'ACTIVE'
                         FOR UPDATE
                        """)
                .param("tenantId", tenantId)
                .param("nodeInstanceId", current.workflowNodeInstanceId())
                .query(UUID.class)
                .optional()
                .orElse(null);
        if (collectionId == null) {
            return null;
        }
        var slot = jdbc.sql("""
                        SELECT multi_instance_id, expected_instances, completed_instances
                          FROM wfl_multi_instance
                         WHERE multi_instance_id = :collectionId
                           AND status = 'OPEN'
                         FOR UPDATE
                        """)
                .param("collectionId", collectionId)
                .query((rs, row) -> new MultiInstanceSlotRow(
                        rs.getObject("multi_instance_id", UUID.class),
                        rs.getInt("expected_instances"),
                        rs.getInt("completed_instances")))
                .single();
        int slotUpdated = jdbc.sql("""
                        UPDATE wfl_multi_instance_slot
                           SET status = 'COMPLETED', completed_at = :completedAt
                         WHERE multi_instance_id = :collectionId
                           AND workflow_node_instance_id = :nodeInstanceId
                           AND status = 'ACTIVE'
                        """)
                .param("completedAt", java.sql.Timestamp.from(completedAt))
                .param("collectionId", slot.collectionId())
                .param("nodeInstanceId", current.workflowNodeInstanceId())
                .update();
        if (slotUpdated != 1) {
            throw new IllegalStateException("multi-instance slot already completed");
        }
        int completed = slot.completedInstances() + 1;
        int miUpdated = jdbc.sql("""
                        UPDATE wfl_multi_instance
                           SET completed_instances = :completed,
                               status = CASE WHEN :completed = expected_instances THEN 'COMPLETED' ELSE status END,
                               completed_at = CASE WHEN :completed = expected_instances THEN :completedAt ELSE completed_at END,
                               version = version + 1
                         WHERE multi_instance_id = :collectionId
                           AND status = 'OPEN'
                           AND completed_instances = :previous
                        """)
                .param("completed", completed)
                .param("completedAt", java.sql.Timestamp.from(completedAt))
                .param("collectionId", slot.collectionId())
                .param("previous", slot.completedInstances())
                .update();
        if (miUpdated != 1) {
            throw new IllegalStateException("multi-instance counter update lost race");
        }
        return completed == slot.expectedInstances();
    }

    private record MultiInstanceSlotRow(
            UUID collectionId,
            int expectedInstances,
            int completedInstances
    ) {
    }

    private String activateSubProcess(
            String tenantId,
            UUID activationEventId,
            String correlationId,
            NodeRuntime current,
            UUID stageInstanceId,
            WorkflowDefinitionParser.ProgressionDefinition progression,
            Instant activatedAt
    ) {
        UUID parentNodeInstanceId = UUID.randomUUID();
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
                .param("nodeInstanceId", parentNodeInstanceId)
                .param("tenantId", tenantId)
                .param("workflowId", current.workflowInstanceId())
                .param("stageId", stageInstanceId)
                .param("workOrderId", current.workOrderId())
                .param("nodeId", progression.nodeId())
                .param("activationEventId", activationEventId)
                .param("activatedAt", java.sql.Timestamp.from(activatedAt))
                .update();

        var childAsset = configurations.listBundleAssets(
                        tenantId, current.configurationBundleId(),
                        current.configurationBundleDigest(), ConfigurationAssetType.WORKFLOW)
                .stream()
                .filter(asset -> progression.subProcessRef().equals(asset.assetKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "SUB_PROCESS ref missing from frozen bundle: " + progression.subProcessRef()));
        var childBootstrap = parser.parse(childAsset);
        UUID childWorkflowId = UUID.randomUUID();
        UUID childStageId = UUID.randomUUID();
        UUID childNodeInstanceId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();

        jdbc.sql("""
                        INSERT INTO wfl_workflow_instance (
                            workflow_instance_id, tenant_id, project_id, work_order_id,
                            configuration_bundle_id, configuration_bundle_digest,
                            workflow_definition_version_id, workflow_key, workflow_version,
                            definition_digest, status, start_event_id, correlation_id, version,
                            started_at, instance_role
                        ) VALUES (
                            :workflowId, :tenantId, :projectId, :workOrderId,
                            :bundleId, :bundleDigest, :definitionVersionId, :workflowKey, :workflowVersion,
                            :definitionDigest, 'ACTIVE', :startEventId, :correlationId, 1,
                            :startedAt, 'SUBPROCESS'
                        )
                        """)
                .param("workflowId", childWorkflowId)
                .param("tenantId", tenantId)
                .param("projectId", current.projectId())
                .param("workOrderId", current.workOrderId())
                .param("bundleId", current.configurationBundleId())
                .param("bundleDigest", current.configurationBundleDigest())
                .param("definitionVersionId", childAsset.versionId())
                .param("workflowKey", childBootstrap.workflowKey())
                .param("workflowVersion", childBootstrap.workflowVersion())
                .param("definitionDigest", childAsset.contentDigest())
                .param("startEventId", activationEventId)
                .param("correlationId", correlationId)
                .param("startedAt", java.sql.Timestamp.from(activatedAt))
                .update();

        jdbc.sql("""
                        INSERT INTO wfl_subprocess_link (
                            subprocess_link_id, tenant_id, parent_workflow_instance_id,
                            parent_node_instance_id, parent_node_id, child_workflow_instance_id,
                            child_workflow_key, child_definition_version_id, child_definition_digest,
                            status, activation_event_id, version, started_at
                        ) VALUES (
                            :linkId, :tenantId, :parentWorkflowId, :parentNodeId, :parentNode,
                            :childWorkflowId, :childKey, :childVersionId, :childDigest,
                            'RUNNING', :activationEventId, 1, :startedAt
                        )
                        """)
                .param("linkId", linkId)
                .param("tenantId", tenantId)
                .param("parentWorkflowId", current.workflowInstanceId())
                .param("parentNodeId", parentNodeInstanceId)
                .param("parentNode", progression.nodeId())
                .param("childWorkflowId", childWorkflowId)
                .param("childKey", childBootstrap.workflowKey())
                .param("childVersionId", childAsset.versionId())
                .param("childDigest", childAsset.contentDigest())
                .param("activationEventId", activationEventId)
                .param("startedAt", java.sql.Timestamp.from(activatedAt))
                .update();

        jdbc.sql("""
                        INSERT INTO wfl_stage_instance (
                            stage_instance_id, tenant_id, workflow_instance_id, work_order_id,
                            stage_code, sequence_no, status, activation_event_id, version, activated_at
                        ) VALUES (
                            :stageId, :tenantId, :workflowId, :workOrderId,
                            :stageCode, 1, 'ACTIVE', :activationEventId, 1, :activatedAt
                        )
                        """)
                .param("stageId", childStageId)
                .param("tenantId", tenantId)
                .param("workflowId", childWorkflowId)
                .param("workOrderId", current.workOrderId())
                .param("stageCode", childBootstrap.firstStageCode())
                .param("activationEventId", activationEventId)
                .param("activatedAt", java.sql.Timestamp.from(activatedAt))
                .update();

        ScheduledTaskView childTask = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                tenantId, current.projectId(), current.workOrderId(),
                childWorkflowId, childStageId, childNodeInstanceId,
                childBootstrap.firstNodeId(), childAsset.versionId(), childAsset.contentDigest(),
                current.configurationBundleId(), current.configurationBundleDigest(),
                childBootstrap.firstStageCode(), childBootstrap.firstTaskType(),
                childBootstrap.firstTaskKind(), childBootstrap.firstFormRef(), childBootstrap.firstSlaRef(),
                childBootstrap.firstAssigneePolicyRef(), childBootstrap.firstDispatchPolicyRef(),
                childBootstrap.firstRuleRef(),
                "work-order:" + current.workOrderId(), Sha256.digest(linkId.toString()),
                100, activatedAt, 3, correlationId, activationEventId.toString()));
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
                .param("nodeInstanceId", childNodeInstanceId)
                .param("tenantId", tenantId)
                .param("workflowId", childWorkflowId)
                .param("stageId", childStageId)
                .param("workOrderId", current.workOrderId())
                .param("nodeId", childBootstrap.firstNodeId())
                .param("taskId", childTask.taskId())
                .param("activationEventId", activationEventId)
                .param("activatedAt", java.sql.Timestamp.from(activatedAt))
                .update();

        return Sha256.digest(current.workflowNodeInstanceId() + "|SUB|" + parentNodeInstanceId
                + "|" + childWorkflowId);
    }

    /**
     * 记录并行汇聚 token；返回是否已到齐。
     */
    private boolean recordParallelJoinArrival(
            String tenantId,
            NodeRuntime current,
            WorkflowDefinitionParser.ProgressionDefinition progression,
            UUID activationEventId,
            Instant arrivedAt
    ) {
        UUID proposedJoinId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO wfl_parallel_join (
                            parallel_join_id, tenant_id, workflow_instance_id, join_node_id,
                            expected_tokens, arrived_tokens, status, version, opened_at
                        )
                        SELECT :joinId, :tenantId, :workflowId, :joinNodeId,
                               :expected, 0, 'OPEN', 1, :openedAt
                         WHERE NOT EXISTS (
                            SELECT 1 FROM wfl_parallel_join
                             WHERE tenant_id = :tenantId
                               AND workflow_instance_id = :workflowId
                               AND join_node_id = :joinNodeId
                               AND status = 'OPEN'
                         )
                        """)
                .param("joinId", proposedJoinId)
                .param("tenantId", tenantId)
                .param("workflowId", current.workflowInstanceId())
                .param("joinNodeId", progression.nodeId())
                .param("expected", progression.expectedJoinTokens())
                .param("openedAt", java.sql.Timestamp.from(arrivedAt))
                .update();

        var join = jdbc.sql("""
                        SELECT parallel_join_id, expected_tokens, arrived_tokens, status
                          FROM wfl_parallel_join
                         WHERE tenant_id = :tenantId
                           AND workflow_instance_id = :workflowId
                           AND join_node_id = :joinNodeId
                           AND status = 'OPEN'
                         FOR UPDATE
                        """)
                .param("tenantId", tenantId)
                .param("workflowId", current.workflowInstanceId())
                .param("joinNodeId", progression.nodeId())
                .query((rs, row) -> new ParallelJoinRow(
                        rs.getObject("parallel_join_id", UUID.class),
                        rs.getInt("expected_tokens"),
                        rs.getInt("arrived_tokens"),
                        rs.getString("status")))
                .single();
        if (!"OPEN".equals(join.status())) {
            throw new IllegalStateException("PARALLEL join is not OPEN: " + progression.nodeId());
        }
        int inserted = jdbc.sql("""
                        INSERT INTO wfl_parallel_join_token (
                            parallel_join_id, from_node_id, source_node_instance_id,
                            activation_event_id, arrived_at
                        ) VALUES (
                            :joinId, :fromNodeId, :sourceNodeInstanceId,
                            :activationEventId, :arrivedAt
                        )
                        ON CONFLICT DO NOTHING
                        """)
                .param("joinId", join.joinId())
                .param("fromNodeId", progression.joinFromNodeId())
                .param("sourceNodeInstanceId", current.workflowNodeInstanceId())
                .param("activationEventId", activationEventId)
                .param("arrivedAt", java.sql.Timestamp.from(arrivedAt))
                .update();
        if (inserted != 1) {
            throw new IllegalArgumentException(
                    "PARALLEL join duplicate token from " + progression.joinFromNodeId());
        }
        int arrived = join.arrivedTokens() + 1;
        int updated = jdbc.sql("""
                        UPDATE wfl_parallel_join
                           SET arrived_tokens = :arrived,
                               status = CASE WHEN :arrived = expected_tokens THEN 'COMPLETED' ELSE status END,
                               completed_at = CASE WHEN :arrived = expected_tokens THEN :completedAt ELSE completed_at END,
                               version = version + 1
                         WHERE parallel_join_id = :joinId
                           AND status = 'OPEN'
                           AND arrived_tokens = :previous
                        """)
                .param("arrived", arrived)
                .param("completedAt", java.sql.Timestamp.from(arrivedAt))
                .param("joinId", join.joinId())
                .param("previous", join.arrivedTokens())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("PARALLEL join counter update lost race");
        }
        return arrived == join.expectedTokens();
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
        String instanceRole = jdbc.sql("""
                        SELECT instance_role FROM wfl_workflow_instance
                         WHERE tenant_id = :tenantId AND workflow_instance_id = :workflowId
                        """)
                .param("tenantId", tenantId)
                .param("workflowId", current.workflowInstanceId())
                .query(String.class)
                .single();
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
        if ("SUBPROCESS".equals(instanceRole)) {
            resumeParentAfterSubProcess(tenantId, activationEventId, correlationId, current, completedAt);
            return;
        }
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

    private void resumeParentAfterSubProcess(
            String tenantId,
            UUID activationEventId,
            String correlationId,
            NodeRuntime childCurrent,
            Instant completedAt
    ) {
        var link = jdbc.sql("""
                        SELECT subprocess_link_id, parent_workflow_instance_id, parent_node_instance_id,
                               parent_node_id
                          FROM wfl_subprocess_link
                         WHERE tenant_id = :tenantId
                           AND child_workflow_instance_id = :childWorkflowId
                           AND status = 'RUNNING'
                         FOR UPDATE
                        """)
                .param("tenantId", tenantId)
                .param("childWorkflowId", childCurrent.workflowInstanceId())
                .query((rs, row) -> new SubProcessLink(
                        rs.getObject("subprocess_link_id", UUID.class),
                        rs.getObject("parent_workflow_instance_id", UUID.class),
                        rs.getObject("parent_node_instance_id", UUID.class),
                        rs.getString("parent_node_id")))
                .single();
        // 父节点 completion_event_id 不得与子任务 completion 冲突（租户内唯一）。
        UUID parentCompletionEventId = UUID.nameUUIDFromBytes(
                ("subprocess-complete:" + link.linkId() + ":" + activationEventId).getBytes());
        int linkUpdated = jdbc.sql("""
                        UPDATE wfl_subprocess_link
                           SET status = 'COMPLETED', completion_event_id = :completionEventId,
                               completed_at = :completedAt, version = version + 1
                         WHERE subprocess_link_id = :linkId AND status = 'RUNNING'
                        """)
                .param("completionEventId", parentCompletionEventId)
                .param("completedAt", java.sql.Timestamp.from(completedAt))
                .param("linkId", link.linkId())
                .update();
        if (linkUpdated != 1) {
            throw new IllegalStateException("SUB_PROCESS link is no longer running");
        }
        int parentNodeUpdated = jdbc.sql("""
                        UPDATE wfl_node_instance
                           SET status = 'COMPLETED', completion_event_id = :completionEventId,
                               completed_at = :completedAt, version = version + 1
                         WHERE tenant_id = :tenantId
                           AND workflow_node_instance_id = :nodeInstanceId
                           AND status = 'WAITING'
                        """)
                .param("completionEventId", parentCompletionEventId)
                .param("completedAt", java.sql.Timestamp.from(completedAt))
                .param("tenantId", tenantId)
                .param("nodeInstanceId", link.parentNodeInstanceId())
                .update();
        if (parentNodeUpdated != 1) {
            throw new IllegalStateException("SUB_PROCESS parent node is no longer waiting");
        }
        NodeRuntime parent = lockWaitNodeRuntime(tenantId, link.parentNodeInstanceId());
        var parentAsset = configurations.requireAssetVersion(
                tenantId, parent.workflowDefinitionVersionId(),
                ConfigurationAssetType.WORKFLOW, parent.workflowDefinitionDigest());
        ExpressionContext expressionContext = expressionContext(tenantId, parent);
        var progression = parser.progressionAfterSubProcess(
                parentAsset, link.parentNodeId(), expressionContext);
        activateProgression(
                tenantId, parentCompletionEventId, correlationId,
                Sha256.digest(link.linkId().toString()), parent, progression, completedAt);
    }

    private record SubProcessLink(
            UUID linkId,
            UUID parentWorkflowInstanceId,
            UUID parentNodeInstanceId,
            String parentNodeId
    ) {
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

    private record TimerSubscription(
            UUID timerSubscriptionId,
            String tenantId,
            UUID projectId,
            UUID workflowInstanceId,
            UUID workflowNodeInstanceId,
            UUID workOrderId,
            String nodeId,
            String status
    ) {
    }

    private record ParallelJoinRow(
            UUID joinId,
            int expectedTokens,
            int arrivedTokens,
            String status
    ) {
    }

    private EarlyReviewSignal findUnconsumedEarlyReviewSignal(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                        SELECT signal_id, correlation_id
                          FROM wfl_review_gate_early_signal
                         WHERE tenant_id = :tenantId
                           AND work_order_id = :workOrderId
                           AND consumed_at IS NULL
                         FOR UPDATE
                        """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> new EarlyReviewSignal(
                        rs.getString("signal_id"), rs.getString("correlation_id")))
                .optional()
                .orElse(null);
    }

    private void markEarlyReviewSignalConsumed(String tenantId, UUID workOrderId, Instant consumedAt) {
        jdbc.sql("""
                        UPDATE wfl_review_gate_early_signal
                           SET consumed_at = :consumedAt
                         WHERE tenant_id = :tenantId
                           AND work_order_id = :workOrderId
                           AND consumed_at IS NULL
                        """)
                .param("consumedAt", java.sql.Timestamp.from(consumedAt))
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .update();
    }

    private record EarlyReviewSignal(String signalId, String correlationId) {
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
        NodeRuntime withActiveStage(UUID stageInstanceId, String stageCode, int stageSequenceNo) {
            return new NodeRuntime(
                    workflowNodeInstanceId, workflowInstanceId, stageInstanceId, workOrderId, nodeId,
                    taskId, nodeStatus, stageCode, stageSequenceNo, "ACTIVE", 1, projectId,
                    workflowStatus, workflowVersion, workflowDefinitionVersionId,
                    workflowDefinitionDigest, configurationBundleId, configurationBundleDigest,
                    taskType, taskKind, taskStatus, taskResultRef, taskResultDigest,
                    taskDefinitionVersionId, taskDefinitionDigest);
        }
    }
}
