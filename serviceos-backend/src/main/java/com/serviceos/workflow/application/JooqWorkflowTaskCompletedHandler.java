package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.jooq.generated.tables.TskTask;
import com.serviceos.jooq.generated.tables.WflMultiInstance;
import com.serviceos.jooq.generated.tables.WflMultiInstanceSlot;
import com.serviceos.jooq.generated.tables.WflNodeInstance;
import com.serviceos.jooq.generated.tables.WflParallelJoin;
import com.serviceos.jooq.generated.tables.WflReviewGateEarlySignal;
import com.serviceos.jooq.generated.tables.WflStageInstance;
import com.serviceos.jooq.generated.tables.WflSubprocessLink;
import com.serviceos.jooq.generated.tables.WflTimerSubscription;
import com.serviceos.jooq.generated.tables.WflWaitSubscription;
import com.serviceos.jooq.generated.tables.WflWorkflowInstance;
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
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WflMultiInstance.WFL_MULTI_INSTANCE;
import static com.serviceos.jooq.generated.tables.WflMultiInstanceSlot.WFL_MULTI_INSTANCE_SLOT;
import static com.serviceos.jooq.generated.tables.WflNodeInstance.WFL_NODE_INSTANCE;
import static com.serviceos.jooq.generated.tables.WflParallelJoin.WFL_PARALLEL_JOIN;
import static com.serviceos.jooq.generated.tables.WflParallelJoinToken.WFL_PARALLEL_JOIN_TOKEN;
import static com.serviceos.jooq.generated.tables.WflReviewGateEarlySignal.WFL_REVIEW_GATE_EARLY_SIGNAL;
import static com.serviceos.jooq.generated.tables.WflStageInstance.WFL_STAGE_INSTANCE;
import static com.serviceos.jooq.generated.tables.WflSubprocessLink.WFL_SUBPROCESS_LINK;
import static com.serviceos.jooq.generated.tables.WflTimerSubscription.WFL_TIMER_SUBSCRIPTION;
import static com.serviceos.jooq.generated.tables.WflWaitSubscription.WFL_WAIT_SUBSCRIPTION;
import static com.serviceos.jooq.generated.tables.WflWorkflowInstance.WFL_WORKFLOW_INSTANCE;
import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;

/**
 * TaskCompleted v1 的可靠本地消费者（jOOQ 实现），并将 WAIT_EVENT 幂等唤醒推进到下一节点。
 *
 * <p>M19 支持线性推进；M269 支持 EXCLUSIVE_GATEWAY；M270 支持 WAIT_EVENT 挂起与信号唤醒。
 * 并行网关仍未实现。</p>
 */
@Service
final class JooqWorkflowTaskCompletedHandler
        implements OutboxMessageHandler, WorkflowWaitSignalService, WorkflowTimerFireService {
    private static final String CONSUMER = "workflow.task-completed.v1";

    private final DSLContext dsl;
    private final InboxService inbox;
    private final ConfigurationService configurations;
    private final WorkflowDefinitionParser parser;
    private final TaskSchedulingService tasks;
    private final OutboxAppender outbox;
    private final WorkOrderCommandService workOrders;
    private final WorkOrderExpressionContextQuery workOrderExpressions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqWorkflowTaskCompletedHandler(
            DSLContext dsl,
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
        this.dsl = dsl;
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

        // 条件更新带原状态（ACTIVE），影响行数不为 1 即节点已被并发推进。
        int completedRows = dsl.update(WFL_NODE_INSTANCE)
                .set(WFL_NODE_INSTANCE.STATUS, "COMPLETED")
                .set(WFL_NODE_INSTANCE.COMPLETION_EVENT_ID, message.eventId())
                .set(WFL_NODE_INSTANCE.COMPLETED_AT, completed.completedAt())
                .set(WFL_NODE_INSTANCE.VERSION, WFL_NODE_INSTANCE.VERSION.plus(1))
                .where(WFL_NODE_INSTANCE.TENANT_ID.eq(message.tenantId()))
                .and(WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID.eq(current.workflowNodeInstanceId()))
                .and(WFL_NODE_INSTANCE.STATUS.eq("ACTIVE"))
                .execute();
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
        WflTimerSubscription timerTable = WFL_TIMER_SUBSCRIPTION;
        var timer = dsl.select(
                        timerTable.TIMER_SUBSCRIPTION_ID, timerTable.TENANT_ID, timerTable.PROJECT_ID,
                        timerTable.WORKFLOW_INSTANCE_ID, timerTable.WORKFLOW_NODE_INSTANCE_ID,
                        timerTable.WORK_ORDER_ID, timerTable.NODE_ID, timerTable.STATUS)
                .from(timerTable)
                .where(timerTable.TIMER_SUBSCRIPTION_ID.eq(timerSubscriptionId))
                .forUpdate()
                .fetchSingle(Records.mapping(TimerSubscription::new));
        if ("FIRED".equals(timer.status())) {
            return;
        }
        if (!"CLAIMED".equals(timer.status()) && !"WAITING".equals(timer.status())) {
            throw new IllegalStateException("TIMER subscription not firable: " + timer.status());
        }
        Instant now = clock.instant();
        UUID fireEventId = UUID.nameUUIDFromBytes(("timer-fire:" + timerSubscriptionId).getBytes());
        int timerUpdated = dsl.update(timerTable)
                .set(timerTable.STATUS, "FIRED")
                .set(timerTable.FIRE_EVENT_ID, fireEventId)
                .set(timerTable.FIRED_AT, now)
                .setNull(timerTable.CLAIM_OWNER)
                .setNull(timerTable.CLAIM_UNTIL)
                .set(timerTable.VERSION, timerTable.VERSION.plus(1))
                .where(timerTable.TIMER_SUBSCRIPTION_ID.eq(timerSubscriptionId))
                .and(timerTable.STATUS.in("WAITING", "CLAIMED"))
                .execute();
        if (timerUpdated != 1) {
            throw new IllegalStateException("TIMER subscription fire lost race");
        }
        int nodeUpdated = dsl.update(WFL_NODE_INSTANCE)
                .set(WFL_NODE_INSTANCE.STATUS, "COMPLETED")
                .set(WFL_NODE_INSTANCE.COMPLETION_EVENT_ID, fireEventId)
                .set(WFL_NODE_INSTANCE.COMPLETED_AT, now)
                .set(WFL_NODE_INSTANCE.VERSION, WFL_NODE_INSTANCE.VERSION.plus(1))
                .where(WFL_NODE_INSTANCE.TENANT_ID.eq(timer.tenantId()))
                .and(WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID.eq(timer.workflowNodeInstanceId()))
                .and(WFL_NODE_INSTANCE.STATUS.eq("WAITING"))
                .execute();
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
        int waitUpdated = dsl.update(WFL_WAIT_SUBSCRIPTION)
                .set(WFL_WAIT_SUBSCRIPTION.STATUS, "COMPLETED")
                .set(WFL_WAIT_SUBSCRIPTION.WAKE_SIGNAL_ID, command.signalId())
                .set(WFL_WAIT_SUBSCRIPTION.COMPLETED_AT, now)
                .set(WFL_WAIT_SUBSCRIPTION.VERSION, WFL_WAIT_SUBSCRIPTION.VERSION.plus(1))
                .where(WFL_WAIT_SUBSCRIPTION.TENANT_ID.eq(command.tenantId()))
                .and(WFL_WAIT_SUBSCRIPTION.WAIT_SUBSCRIPTION_ID.eq(wait.waitSubscriptionId()))
                .and(WFL_WAIT_SUBSCRIPTION.STATUS.eq("WAITING"))
                .execute();
        if (waitUpdated != 1) {
            throw new IllegalStateException("WAIT_EVENT subscription is no longer waiting");
        }
        int nodeUpdated = dsl.update(WFL_NODE_INSTANCE)
                .set(WFL_NODE_INSTANCE.STATUS, "COMPLETED")
                .set(WFL_NODE_INSTANCE.COMPLETION_EVENT_ID, UUID.nameUUIDFromBytes(
                        ("wait-signal:" + command.signalId()).getBytes()))
                .set(WFL_NODE_INSTANCE.COMPLETED_AT, now)
                .set(WFL_NODE_INSTANCE.VERSION, WFL_NODE_INSTANCE.VERSION.plus(1))
                .where(WFL_NODE_INSTANCE.TENANT_ID.eq(command.tenantId()))
                .and(WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID.eq(wait.workflowNodeInstanceId()))
                .and(WFL_NODE_INSTANCE.STATUS.eq("WAITING"))
                .execute();
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
            dsl.insertInto(WFL_NODE_INSTANCE)
                    .set(WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID, nextNodeInstanceId)
                    .set(WFL_NODE_INSTANCE.TENANT_ID, tenantId)
                    .set(WFL_NODE_INSTANCE.WORKFLOW_INSTANCE_ID, current.workflowInstanceId())
                    .set(WFL_NODE_INSTANCE.STAGE_INSTANCE_ID, nextStageInstanceId)
                    .set(WFL_NODE_INSTANCE.WORK_ORDER_ID, current.workOrderId())
                    .set(WFL_NODE_INSTANCE.NODE_ID, progression.nodeId())
                    .set(WFL_NODE_INSTANCE.STATUS, "WAITING")
                    .set(WFL_NODE_INSTANCE.ACTIVATION_EVENT_ID, activationEventId)
                    .set(WFL_NODE_INSTANCE.VERSION, 1L)
                    .set(WFL_NODE_INSTANCE.ACTIVATED_AT, activatedAt)
                    .execute();
            if (progression.waiting()) {
                String correlationKey = WorkflowCorrelationKeys.resolve(
                        progression.correlationKeyTemplate(),
                        tenantId,
                        current.projectId(),
                        current.workOrderId(),
                        current.workflowInstanceId());
                dsl.insertInto(WFL_WAIT_SUBSCRIPTION)
                        .set(WFL_WAIT_SUBSCRIPTION.WAIT_SUBSCRIPTION_ID, UUID.randomUUID())
                        .set(WFL_WAIT_SUBSCRIPTION.TENANT_ID, tenantId)
                        .set(WFL_WAIT_SUBSCRIPTION.PROJECT_ID, current.projectId())
                        .set(WFL_WAIT_SUBSCRIPTION.WORKFLOW_INSTANCE_ID, current.workflowInstanceId())
                        .set(WFL_WAIT_SUBSCRIPTION.WORKFLOW_NODE_INSTANCE_ID, nextNodeInstanceId)
                        .set(WFL_WAIT_SUBSCRIPTION.WORK_ORDER_ID, current.workOrderId())
                        .set(WFL_WAIT_SUBSCRIPTION.NODE_ID, progression.nodeId())
                        .set(WFL_WAIT_SUBSCRIPTION.WAIT_EVENT_TYPE, progression.waitEventType())
                        .set(WFL_WAIT_SUBSCRIPTION.CORRELATION_KEY, correlationKey)
                        .set(WFL_WAIT_SUBSCRIPTION.STATUS, "WAITING")
                        .set(WFL_WAIT_SUBSCRIPTION.ACTIVATION_EVENT_ID, activationEventId)
                        .set(WFL_WAIT_SUBSCRIPTION.VERSION, 1L)
                        .set(WFL_WAIT_SUBSCRIPTION.ACTIVATED_AT, activatedAt)
                        .execute();
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
            dsl.insertInto(WFL_TIMER_SUBSCRIPTION)
                    .set(WFL_TIMER_SUBSCRIPTION.TIMER_SUBSCRIPTION_ID, UUID.randomUUID())
                    .set(WFL_TIMER_SUBSCRIPTION.TENANT_ID, tenantId)
                    .set(WFL_TIMER_SUBSCRIPTION.PROJECT_ID, current.projectId())
                    .set(WFL_TIMER_SUBSCRIPTION.WORKFLOW_INSTANCE_ID, current.workflowInstanceId())
                    .set(WFL_TIMER_SUBSCRIPTION.WORKFLOW_NODE_INSTANCE_ID, nextNodeInstanceId)
                    .set(WFL_TIMER_SUBSCRIPTION.WORK_ORDER_ID, current.workOrderId())
                    .set(WFL_TIMER_SUBSCRIPTION.NODE_ID, progression.nodeId())
                    .set(WFL_TIMER_SUBSCRIPTION.DURATION_SECONDS, progression.durationSeconds())
                    .set(WFL_TIMER_SUBSCRIPTION.FIRE_AT, fireAt)
                    .set(WFL_TIMER_SUBSCRIPTION.STATUS, "WAITING")
                    .set(WFL_TIMER_SUBSCRIPTION.ACTIVATION_EVENT_ID, activationEventId)
                    .set(WFL_TIMER_SUBSCRIPTION.VERSION, 1L)
                    .set(WFL_TIMER_SUBSCRIPTION.ACTIVATED_AT, activatedAt)
                    .execute();
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
        dsl.insertInto(WFL_NODE_INSTANCE)
                .set(WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID, nextNodeInstanceId)
                .set(WFL_NODE_INSTANCE.TENANT_ID, tenantId)
                .set(WFL_NODE_INSTANCE.WORKFLOW_INSTANCE_ID, current.workflowInstanceId())
                .set(WFL_NODE_INSTANCE.STAGE_INSTANCE_ID, nextStageInstanceId)
                .set(WFL_NODE_INSTANCE.WORK_ORDER_ID, current.workOrderId())
                .set(WFL_NODE_INSTANCE.NODE_ID, progression.nodeId())
                .set(WFL_NODE_INSTANCE.TASK_ID, nextTask.taskId())
                .set(WFL_NODE_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_NODE_INSTANCE.ACTIVATION_EVENT_ID, activationEventId)
                .set(WFL_NODE_INSTANCE.VERSION, 1L)
                .set(WFL_NODE_INSTANCE.ACTIVATED_AT, activatedAt)
                .execute();
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
        dsl.insertInto(WFL_MULTI_INSTANCE)
                .set(WFL_MULTI_INSTANCE.MULTI_INSTANCE_ID, collectionId)
                .set(WFL_MULTI_INSTANCE.TENANT_ID, tenantId)
                .set(WFL_MULTI_INSTANCE.WORKFLOW_INSTANCE_ID, current.workflowInstanceId())
                .set(WFL_MULTI_INSTANCE.NODE_ID, progression.nodeId())
                .set(WFL_MULTI_INSTANCE.EXPECTED_INSTANCES, cardinality)
                .set(WFL_MULTI_INSTANCE.COMPLETED_INSTANCES, 0)
                .set(WFL_MULTI_INSTANCE.STATUS, "OPEN")
                .set(WFL_MULTI_INSTANCE.ACTIVATION_EVENT_ID, activationEventId)
                .set(WFL_MULTI_INSTANCE.VERSION, 1L)
                .set(WFL_MULTI_INSTANCE.OPENED_AT, activatedAt)
                .execute();
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
            dsl.insertInto(WFL_NODE_INSTANCE)
                    .set(WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID, nodeInstanceId)
                    .set(WFL_NODE_INSTANCE.TENANT_ID, tenantId)
                    .set(WFL_NODE_INSTANCE.WORKFLOW_INSTANCE_ID, current.workflowInstanceId())
                    .set(WFL_NODE_INSTANCE.STAGE_INSTANCE_ID, stageInstanceId)
                    .set(WFL_NODE_INSTANCE.WORK_ORDER_ID, current.workOrderId())
                    .set(WFL_NODE_INSTANCE.NODE_ID, progression.nodeId())
                    .set(WFL_NODE_INSTANCE.TASK_ID, task.taskId())
                    .set(WFL_NODE_INSTANCE.STATUS, "ACTIVE")
                    .set(WFL_NODE_INSTANCE.ACTIVATION_EVENT_ID, activationEventId)
                    .set(WFL_NODE_INSTANCE.VERSION, 1L)
                    .set(WFL_NODE_INSTANCE.ACTIVATED_AT, activatedAt)
                    .execute();
            dsl.insertInto(WFL_MULTI_INSTANCE_SLOT)
                    .set(WFL_MULTI_INSTANCE_SLOT.MULTI_INSTANCE_ID, collectionId)
                    .set(WFL_MULTI_INSTANCE_SLOT.TENANT_ID, tenantId)
                    .set(WFL_MULTI_INSTANCE_SLOT.INSTANCE_INDEX, index)
                    .set(WFL_MULTI_INSTANCE_SLOT.WORKFLOW_NODE_INSTANCE_ID, nodeInstanceId)
                    .set(WFL_MULTI_INSTANCE_SLOT.TASK_ID, task.taskId())
                    .set(WFL_MULTI_INSTANCE_SLOT.STATUS, "ACTIVE")
                    .execute();
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
        WflMultiInstanceSlot slotTable = WFL_MULTI_INSTANCE_SLOT;
        WflMultiInstance multiInstance = WFL_MULTI_INSTANCE;
        UUID collectionId = dsl.select(slotTable.MULTI_INSTANCE_ID)
                .from(slotTable)
                .where(slotTable.TENANT_ID.eq(tenantId))
                .and(slotTable.WORKFLOW_NODE_INSTANCE_ID.eq(current.workflowNodeInstanceId()))
                .and(slotTable.STATUS.eq("ACTIVE"))
                .forUpdate()
                .fetchOptional(slotTable.MULTI_INSTANCE_ID)
                .orElse(null);
        if (collectionId == null) {
            return null;
        }
        var slot = dsl.select(
                        multiInstance.MULTI_INSTANCE_ID,
                        multiInstance.EXPECTED_INSTANCES,
                        multiInstance.COMPLETED_INSTANCES)
                .from(multiInstance)
                .where(multiInstance.MULTI_INSTANCE_ID.eq(collectionId))
                .and(multiInstance.STATUS.eq("OPEN"))
                .forUpdate()
                .fetchSingle(Records.mapping(MultiInstanceSlotRow::new));
        int slotUpdated = dsl.update(slotTable)
                .set(slotTable.STATUS, "COMPLETED")
                .set(slotTable.COMPLETED_AT, completedAt)
                .where(slotTable.MULTI_INSTANCE_ID.eq(slot.collectionId()))
                .and(slotTable.WORKFLOW_NODE_INSTANCE_ID.eq(current.workflowNodeInstanceId()))
                .and(slotTable.STATUS.eq("ACTIVE"))
                .execute();
        if (slotUpdated != 1) {
            throw new IllegalStateException("multi-instance slot already completed");
        }
        int completed = slot.completedInstances() + 1;
        // 计数迁移带原计数条件（completed_instances = :previous），到齐才翻 COMPLETED；影响行数校验防并发丢更。
        int miUpdated = dsl.update(multiInstance)
                .set(multiInstance.COMPLETED_INSTANCES, completed)
                .set(multiInstance.STATUS, DSL.when(multiInstance.EXPECTED_INSTANCES.eq(completed), "COMPLETED")
                        .otherwise(multiInstance.STATUS))
                .set(multiInstance.COMPLETED_AT, DSL.when(multiInstance.EXPECTED_INSTANCES.eq(completed), completedAt)
                        .otherwise(multiInstance.COMPLETED_AT))
                .set(multiInstance.VERSION, multiInstance.VERSION.plus(1))
                .where(multiInstance.MULTI_INSTANCE_ID.eq(slot.collectionId()))
                .and(multiInstance.STATUS.eq("OPEN"))
                .and(multiInstance.COMPLETED_INSTANCES.eq(slot.completedInstances()))
                .execute();
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
        dsl.insertInto(WFL_NODE_INSTANCE)
                .set(WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID, parentNodeInstanceId)
                .set(WFL_NODE_INSTANCE.TENANT_ID, tenantId)
                .set(WFL_NODE_INSTANCE.WORKFLOW_INSTANCE_ID, current.workflowInstanceId())
                .set(WFL_NODE_INSTANCE.STAGE_INSTANCE_ID, stageInstanceId)
                .set(WFL_NODE_INSTANCE.WORK_ORDER_ID, current.workOrderId())
                .set(WFL_NODE_INSTANCE.NODE_ID, progression.nodeId())
                .set(WFL_NODE_INSTANCE.STATUS, "WAITING")
                .set(WFL_NODE_INSTANCE.ACTIVATION_EVENT_ID, activationEventId)
                .set(WFL_NODE_INSTANCE.VERSION, 1L)
                .set(WFL_NODE_INSTANCE.ACTIVATED_AT, activatedAt)
                .execute();

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

        dsl.insertInto(WFL_WORKFLOW_INSTANCE)
                .set(WFL_WORKFLOW_INSTANCE.WORKFLOW_INSTANCE_ID, childWorkflowId)
                .set(WFL_WORKFLOW_INSTANCE.TENANT_ID, tenantId)
                .set(WFL_WORKFLOW_INSTANCE.PROJECT_ID, current.projectId())
                .set(WFL_WORKFLOW_INSTANCE.WORK_ORDER_ID, current.workOrderId())
                .set(WFL_WORKFLOW_INSTANCE.CONFIGURATION_BUNDLE_ID, current.configurationBundleId())
                .set(WFL_WORKFLOW_INSTANCE.CONFIGURATION_BUNDLE_DIGEST, current.configurationBundleDigest())
                .set(WFL_WORKFLOW_INSTANCE.WORKFLOW_DEFINITION_VERSION_ID, childAsset.versionId())
                .set(WFL_WORKFLOW_INSTANCE.WORKFLOW_KEY, childBootstrap.workflowKey())
                .set(WFL_WORKFLOW_INSTANCE.WORKFLOW_VERSION, childBootstrap.workflowVersion())
                .set(WFL_WORKFLOW_INSTANCE.DEFINITION_DIGEST, childAsset.contentDigest())
                .set(WFL_WORKFLOW_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_WORKFLOW_INSTANCE.START_EVENT_ID, activationEventId)
                .set(WFL_WORKFLOW_INSTANCE.CORRELATION_ID, correlationId)
                .set(WFL_WORKFLOW_INSTANCE.VERSION, 1L)
                .set(WFL_WORKFLOW_INSTANCE.STARTED_AT, activatedAt)
                .set(WFL_WORKFLOW_INSTANCE.INSTANCE_ROLE, "SUBPROCESS")
                .execute();

        dsl.insertInto(WFL_SUBPROCESS_LINK)
                .set(WFL_SUBPROCESS_LINK.SUBPROCESS_LINK_ID, linkId)
                .set(WFL_SUBPROCESS_LINK.TENANT_ID, tenantId)
                .set(WFL_SUBPROCESS_LINK.PARENT_WORKFLOW_INSTANCE_ID, current.workflowInstanceId())
                .set(WFL_SUBPROCESS_LINK.PARENT_NODE_INSTANCE_ID, parentNodeInstanceId)
                .set(WFL_SUBPROCESS_LINK.PARENT_NODE_ID, progression.nodeId())
                .set(WFL_SUBPROCESS_LINK.CHILD_WORKFLOW_INSTANCE_ID, childWorkflowId)
                .set(WFL_SUBPROCESS_LINK.CHILD_WORKFLOW_KEY, childBootstrap.workflowKey())
                .set(WFL_SUBPROCESS_LINK.CHILD_DEFINITION_VERSION_ID, childAsset.versionId())
                .set(WFL_SUBPROCESS_LINK.CHILD_DEFINITION_DIGEST, childAsset.contentDigest())
                .set(WFL_SUBPROCESS_LINK.STATUS, "RUNNING")
                .set(WFL_SUBPROCESS_LINK.ACTIVATION_EVENT_ID, activationEventId)
                .set(WFL_SUBPROCESS_LINK.VERSION, 1L)
                .set(WFL_SUBPROCESS_LINK.STARTED_AT, activatedAt)
                .execute();

        dsl.insertInto(WFL_STAGE_INSTANCE)
                .set(WFL_STAGE_INSTANCE.STAGE_INSTANCE_ID, childStageId)
                .set(WFL_STAGE_INSTANCE.TENANT_ID, tenantId)
                .set(WFL_STAGE_INSTANCE.WORKFLOW_INSTANCE_ID, childWorkflowId)
                .set(WFL_STAGE_INSTANCE.WORK_ORDER_ID, current.workOrderId())
                .set(WFL_STAGE_INSTANCE.STAGE_CODE, childBootstrap.firstStageCode())
                .set(WFL_STAGE_INSTANCE.SEQUENCE_NO, 1)
                .set(WFL_STAGE_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_STAGE_INSTANCE.ACTIVATION_EVENT_ID, activationEventId)
                .set(WFL_STAGE_INSTANCE.VERSION, 1L)
                .set(WFL_STAGE_INSTANCE.ACTIVATED_AT, activatedAt)
                .execute();

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
        dsl.insertInto(WFL_NODE_INSTANCE)
                .set(WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID, childNodeInstanceId)
                .set(WFL_NODE_INSTANCE.TENANT_ID, tenantId)
                .set(WFL_NODE_INSTANCE.WORKFLOW_INSTANCE_ID, childWorkflowId)
                .set(WFL_NODE_INSTANCE.STAGE_INSTANCE_ID, childStageId)
                .set(WFL_NODE_INSTANCE.WORK_ORDER_ID, current.workOrderId())
                .set(WFL_NODE_INSTANCE.NODE_ID, childBootstrap.firstNodeId())
                .set(WFL_NODE_INSTANCE.TASK_ID, childTask.taskId())
                .set(WFL_NODE_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_NODE_INSTANCE.ACTIVATION_EVENT_ID, activationEventId)
                .set(WFL_NODE_INSTANCE.VERSION, 1L)
                .set(WFL_NODE_INSTANCE.ACTIVATED_AT, activatedAt)
                .execute();

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
        WflParallelJoin joinTable = WFL_PARALLEL_JOIN;
        UUID proposedJoinId = UUID.randomUUID();
        // INSERT ... SELECT ... WHERE NOT EXISTS：首个到达分支创建 join 行，并发后续分支不再插入。
        dsl.insertInto(joinTable)
                .columns(
                        joinTable.PARALLEL_JOIN_ID, joinTable.TENANT_ID, joinTable.WORKFLOW_INSTANCE_ID,
                        joinTable.JOIN_NODE_ID, joinTable.EXPECTED_TOKENS, joinTable.ARRIVED_TOKENS,
                        joinTable.STATUS, joinTable.VERSION, joinTable.OPENED_AT)
                .select(dsl.select(
                                DSL.val(proposedJoinId),
                                DSL.val(tenantId),
                                DSL.val(current.workflowInstanceId()),
                                DSL.val(progression.nodeId()),
                                DSL.val(progression.expectedJoinTokens()),
                                DSL.val(0),
                                DSL.val("OPEN"),
                                DSL.val(1L),
                                DSL.val(arrivedAt))
                        .whereNotExists(dsl.selectOne()
                                .from(joinTable)
                                .where(joinTable.TENANT_ID.eq(tenantId))
                                .and(joinTable.WORKFLOW_INSTANCE_ID.eq(current.workflowInstanceId()))
                                .and(joinTable.JOIN_NODE_ID.eq(progression.nodeId()))
                                .and(joinTable.STATUS.eq("OPEN"))))
                .execute();

        var join = dsl.select(
                        joinTable.PARALLEL_JOIN_ID, joinTable.EXPECTED_TOKENS,
                        joinTable.ARRIVED_TOKENS, joinTable.STATUS)
                .from(joinTable)
                .where(joinTable.TENANT_ID.eq(tenantId))
                .and(joinTable.WORKFLOW_INSTANCE_ID.eq(current.workflowInstanceId()))
                .and(joinTable.JOIN_NODE_ID.eq(progression.nodeId()))
                .and(joinTable.STATUS.eq("OPEN"))
                .forUpdate()
                .fetchSingle(Records.mapping(ParallelJoinRow::new));
        if (!"OPEN".equals(join.status())) {
            throw new IllegalStateException("PARALLEL join is not OPEN: " + progression.nodeId());
        }
        int inserted = dsl.insertInto(WFL_PARALLEL_JOIN_TOKEN)
                .set(WFL_PARALLEL_JOIN_TOKEN.PARALLEL_JOIN_ID, join.joinId())
                .set(WFL_PARALLEL_JOIN_TOKEN.FROM_NODE_ID, progression.joinFromNodeId())
                .set(WFL_PARALLEL_JOIN_TOKEN.SOURCE_NODE_INSTANCE_ID, current.workflowNodeInstanceId())
                .set(WFL_PARALLEL_JOIN_TOKEN.ACTIVATION_EVENT_ID, activationEventId)
                .set(WFL_PARALLEL_JOIN_TOKEN.ARRIVED_AT, arrivedAt)
                .onConflict()
                .doNothing()
                .execute();
        if (inserted != 1) {
            throw new IllegalArgumentException(
                    "PARALLEL join duplicate token from " + progression.joinFromNodeId());
        }
        int arrived = join.arrivedTokens() + 1;
        // 计数迁移带原计数条件（arrived_tokens = :previous），到齐才翻 COMPLETED；影响行数校验防并发丢更。
        int updated = dsl.update(joinTable)
                .set(joinTable.ARRIVED_TOKENS, arrived)
                .set(joinTable.STATUS, DSL.when(joinTable.EXPECTED_TOKENS.eq(arrived), "COMPLETED")
                        .otherwise(joinTable.STATUS))
                .set(joinTable.COMPLETED_AT, DSL.when(joinTable.EXPECTED_TOKENS.eq(arrived), arrivedAt)
                        .otherwise(joinTable.COMPLETED_AT))
                .set(joinTable.VERSION, joinTable.VERSION.plus(1))
                .where(joinTable.PARALLEL_JOIN_ID.eq(join.joinId()))
                .and(joinTable.STATUS.eq("OPEN"))
                .and(joinTable.ARRIVED_TOKENS.eq(join.arrivedTokens()))
                .execute();
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
        int updated = dsl.update(WFL_STAGE_INSTANCE)
                .set(WFL_STAGE_INSTANCE.STATUS, "COMPLETED")
                .set(WFL_STAGE_INSTANCE.COMPLETED_AT, completedAt)
                .set(WFL_STAGE_INSTANCE.VERSION, WFL_STAGE_INSTANCE.VERSION.plus(1))
                .where(WFL_STAGE_INSTANCE.TENANT_ID.eq(tenantId))
                .and(WFL_STAGE_INSTANCE.STAGE_INSTANCE_ID.eq(current.stageInstanceId()))
                .and(WFL_STAGE_INSTANCE.STATUS.eq("ACTIVE"))
                .execute();
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
        dsl.insertInto(WFL_STAGE_INSTANCE)
                .set(WFL_STAGE_INSTANCE.STAGE_INSTANCE_ID, stageId)
                .set(WFL_STAGE_INSTANCE.TENANT_ID, tenantId)
                .set(WFL_STAGE_INSTANCE.WORKFLOW_INSTANCE_ID, current.workflowInstanceId())
                .set(WFL_STAGE_INSTANCE.WORK_ORDER_ID, current.workOrderId())
                .set(WFL_STAGE_INSTANCE.STAGE_CODE, stageCode)
                .set(WFL_STAGE_INSTANCE.SEQUENCE_NO, sequenceNo)
                .set(WFL_STAGE_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_STAGE_INSTANCE.ACTIVATION_EVENT_ID, activationEventId)
                .set(WFL_STAGE_INSTANCE.VERSION, 1L)
                .set(WFL_STAGE_INSTANCE.ACTIVATED_AT, activatedAt)
                .execute();
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
        String instanceRole = dsl.select(WFL_WORKFLOW_INSTANCE.INSTANCE_ROLE)
                .from(WFL_WORKFLOW_INSTANCE)
                .where(WFL_WORKFLOW_INSTANCE.TENANT_ID.eq(tenantId))
                .and(WFL_WORKFLOW_INSTANCE.WORKFLOW_INSTANCE_ID.eq(current.workflowInstanceId()))
                .fetchSingleInto(String.class);
        int updated = dsl.update(WFL_WORKFLOW_INSTANCE)
                .set(WFL_WORKFLOW_INSTANCE.STATUS, "COMPLETED")
                .set(WFL_WORKFLOW_INSTANCE.COMPLETED_AT, completedAt)
                .set(WFL_WORKFLOW_INSTANCE.VERSION, WFL_WORKFLOW_INSTANCE.VERSION.plus(1))
                .where(WFL_WORKFLOW_INSTANCE.TENANT_ID.eq(tenantId))
                .and(WFL_WORKFLOW_INSTANCE.WORKFLOW_INSTANCE_ID.eq(current.workflowInstanceId()))
                .and(WFL_WORKFLOW_INSTANCE.STATUS.eq("ACTIVE"))
                .execute();
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
        List<String> completedStageCodes = dsl.select(WFL_STAGE_INSTANCE.STAGE_CODE)
                .from(WFL_STAGE_INSTANCE)
                .where(WFL_STAGE_INSTANCE.TENANT_ID.eq(tenantId))
                .and(WFL_STAGE_INSTANCE.WORKFLOW_INSTANCE_ID.eq(current.workflowInstanceId()))
                .and(WFL_STAGE_INSTANCE.STATUS.eq("COMPLETED"))
                .orderBy(WFL_STAGE_INSTANCE.SEQUENCE_NO)
                .fetch(WFL_STAGE_INSTANCE.STAGE_CODE);
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
        WflSubprocessLink linkTable = WFL_SUBPROCESS_LINK;
        var link = dsl.select(
                        linkTable.SUBPROCESS_LINK_ID, linkTable.PARENT_WORKFLOW_INSTANCE_ID,
                        linkTable.PARENT_NODE_INSTANCE_ID, linkTable.PARENT_NODE_ID)
                .from(linkTable)
                .where(linkTable.TENANT_ID.eq(tenantId))
                .and(linkTable.CHILD_WORKFLOW_INSTANCE_ID.eq(childCurrent.workflowInstanceId()))
                .and(linkTable.STATUS.eq("RUNNING"))
                .forUpdate()
                .fetchSingle(Records.mapping(SubProcessLink::new));
        // 父节点 completion_event_id 不得与子任务 completion 冲突（租户内唯一）。
        UUID parentCompletionEventId = UUID.nameUUIDFromBytes(
                ("subprocess-complete:" + link.linkId() + ":" + activationEventId).getBytes());
        int linkUpdated = dsl.update(linkTable)
                .set(linkTable.STATUS, "COMPLETED")
                .set(linkTable.COMPLETION_EVENT_ID, parentCompletionEventId)
                .set(linkTable.COMPLETED_AT, completedAt)
                .set(linkTable.VERSION, linkTable.VERSION.plus(1))
                .where(linkTable.SUBPROCESS_LINK_ID.eq(link.linkId()))
                .and(linkTable.STATUS.eq("RUNNING"))
                .execute();
        if (linkUpdated != 1) {
            throw new IllegalStateException("SUB_PROCESS link is no longer running");
        }
        int parentNodeUpdated = dsl.update(WFL_NODE_INSTANCE)
                .set(WFL_NODE_INSTANCE.STATUS, "COMPLETED")
                .set(WFL_NODE_INSTANCE.COMPLETION_EVENT_ID, parentCompletionEventId)
                .set(WFL_NODE_INSTANCE.COMPLETED_AT, completedAt)
                .set(WFL_NODE_INSTANCE.VERSION, WFL_NODE_INSTANCE.VERSION.plus(1))
                .where(WFL_NODE_INSTANCE.TENANT_ID.eq(tenantId))
                .and(WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID.eq(link.parentNodeInstanceId()))
                .and(WFL_NODE_INSTANCE.STATUS.eq("WAITING"))
                .execute();
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
        WflWaitSubscription waitTable = WFL_WAIT_SUBSCRIPTION;
        return dsl.select(
                        waitTable.WAIT_SUBSCRIPTION_ID, waitTable.TENANT_ID, waitTable.PROJECT_ID,
                        waitTable.WORKFLOW_INSTANCE_ID, waitTable.WORKFLOW_NODE_INSTANCE_ID,
                        waitTable.WORK_ORDER_ID, waitTable.NODE_ID, waitTable.WAIT_EVENT_TYPE,
                        waitTable.CORRELATION_KEY, waitTable.STATUS, waitTable.WAKE_SIGNAL_ID)
                .from(waitTable)
                .where(waitTable.TENANT_ID.eq(tenantId))
                .and(waitTable.WAIT_EVENT_TYPE.eq(waitEventType))
                .and(waitTable.CORRELATION_KEY.eq(correlationKey))
                .and(waitTable.STATUS.eq("WAITING"))
                .forUpdate()
                .fetchOptional(Records.mapping(WaitSubscription::new))
                .orElse(null);
    }

    private WaitSubscription findCompletedSubscription(
            String tenantId,
            String waitEventType,
            String correlationKey
    ) {
        WflWaitSubscription waitTable = WFL_WAIT_SUBSCRIPTION;
        return dsl.select(
                        waitTable.WAIT_SUBSCRIPTION_ID, waitTable.TENANT_ID, waitTable.PROJECT_ID,
                        waitTable.WORKFLOW_INSTANCE_ID, waitTable.WORKFLOW_NODE_INSTANCE_ID,
                        waitTable.WORK_ORDER_ID, waitTable.NODE_ID, waitTable.WAIT_EVENT_TYPE,
                        waitTable.CORRELATION_KEY, waitTable.STATUS, waitTable.WAKE_SIGNAL_ID)
                .from(waitTable)
                .where(waitTable.TENANT_ID.eq(tenantId))
                .and(waitTable.WAIT_EVENT_TYPE.eq(waitEventType))
                .and(waitTable.CORRELATION_KEY.eq(correlationKey))
                .and(waitTable.STATUS.eq("COMPLETED"))
                .orderBy(waitTable.COMPLETED_AT.desc())
                .limit(1)
                .fetchOptional(Records.mapping(WaitSubscription::new))
                .orElse(null);
    }

    private NodeRuntime lockWaitNodeRuntime(String tenantId, UUID nodeInstanceId) {
        WflNodeInstance node = WFL_NODE_INSTANCE;
        WflWorkflowInstance workflow = WFL_WORKFLOW_INSTANCE;
        WflStageInstance stage = WFL_STAGE_INSTANCE;
        Field<String> nodeStatus = node.STATUS.as("node_status");
        Field<Integer> stageSequenceNo = stage.SEQUENCE_NO.as("stage_sequence_no");
        Field<String> stageStatus = stage.STATUS.as("stage_status");
        Field<Long> stageVersion = stage.VERSION.as("stage_version");
        Field<String> workflowStatus = workflow.STATUS.as("workflow_status");
        Field<Long> workflowVersion = workflow.VERSION.as("workflow_version");
        Field<String> workflowDefinitionDigest = workflow.DEFINITION_DIGEST.as("workflow_definition_digest");
        // WAITING 节点无关联任务：task_* 列固定为 NULL（CAST(NULL AS ...)），保持与任务态查询同一投影。
        Field<String> taskType = DSL.castNull(String.class).as("task_type");
        Field<String> taskKind = DSL.castNull(String.class).as("task_kind");
        Field<String> taskStatus = DSL.castNull(String.class).as("task_status");
        Field<String> taskResultRef = DSL.castNull(String.class).as("task_result_ref");
        Field<String> taskResultDigest = DSL.castNull(String.class).as("task_result_digest");
        Field<UUID> taskDefinitionVersionId = DSL.castNull(UUID.class).as("task_definition_version_id");
        Field<String> taskDefinitionDigest = DSL.castNull(String.class).as("task_definition_digest");
        return dsl.select(
                        node.WORKFLOW_NODE_INSTANCE_ID, node.WORKFLOW_INSTANCE_ID,
                        node.STAGE_INSTANCE_ID, node.WORK_ORDER_ID, node.NODE_ID,
                        node.TASK_ID, nodeStatus,
                        stage.STAGE_CODE, stageSequenceNo,
                        stageStatus, stageVersion,
                        workflow.PROJECT_ID, workflowStatus,
                        workflowVersion,
                        workflow.WORKFLOW_DEFINITION_VERSION_ID,
                        workflowDefinitionDigest,
                        workflow.CONFIGURATION_BUNDLE_ID,
                        workflow.CONFIGURATION_BUNDLE_DIGEST,
                        taskType, taskKind, taskStatus,
                        taskResultRef, taskResultDigest,
                        taskDefinitionVersionId, taskDefinitionDigest)
                .from(node)
                .join(workflow)
                .on(workflow.TENANT_ID.eq(node.TENANT_ID))
                .and(workflow.WORKFLOW_INSTANCE_ID.eq(node.WORKFLOW_INSTANCE_ID))
                .join(stage)
                .on(stage.TENANT_ID.eq(node.TENANT_ID))
                .and(stage.STAGE_INSTANCE_ID.eq(node.STAGE_INSTANCE_ID))
                .where(node.TENANT_ID.eq(tenantId))
                .and(node.WORKFLOW_NODE_INSTANCE_ID.eq(nodeInstanceId))
                .forUpdate()
                .of(node)
                .fetchOptional(record -> new NodeRuntime(
                        record.get(node.WORKFLOW_NODE_INSTANCE_ID),
                        record.get(node.WORKFLOW_INSTANCE_ID),
                        record.get(node.STAGE_INSTANCE_ID),
                        record.get(node.WORK_ORDER_ID),
                        record.get(node.NODE_ID),
                        record.get(node.TASK_ID),
                        record.get(nodeStatus),
                        record.get(stage.STAGE_CODE),
                        record.get(stageSequenceNo),
                        record.get(stageStatus),
                        record.get(stageVersion),
                        record.get(workflow.PROJECT_ID),
                        record.get(workflowStatus),
                        record.get(workflowVersion),
                        record.get(workflow.WORKFLOW_DEFINITION_VERSION_ID),
                        record.get(workflowDefinitionDigest),
                        record.get(workflow.CONFIGURATION_BUNDLE_ID),
                        record.get(workflow.CONFIGURATION_BUNDLE_DIGEST),
                        record.get(taskType),
                        record.get(taskKind),
                        record.get(taskStatus),
                        record.get(taskResultRef),
                        record.get(taskResultDigest),
                        record.get(taskDefinitionVersionId),
                        record.get(taskDefinitionDigest)))
                .orElseThrow(() -> new IllegalArgumentException("workflow wait node does not exist"));
    }

    private NodeRuntime lockCurrentNode(String tenantId, UUID nodeInstanceId) {
        WflNodeInstance node = WFL_NODE_INSTANCE;
        WflWorkflowInstance workflow = WFL_WORKFLOW_INSTANCE;
        WflStageInstance stage = WFL_STAGE_INSTANCE;
        TskTask task = TSK_TASK;
        Field<String> nodeStatus = node.STATUS.as("node_status");
        Field<Integer> stageSequenceNo = stage.SEQUENCE_NO.as("stage_sequence_no");
        Field<String> stageStatus = stage.STATUS.as("stage_status");
        Field<Long> stageVersion = stage.VERSION.as("stage_version");
        Field<String> workflowStatus = workflow.STATUS.as("workflow_status");
        Field<Long> workflowVersion = workflow.VERSION.as("workflow_version");
        Field<String> workflowDefinitionDigest = workflow.DEFINITION_DIGEST.as("workflow_definition_digest");
        Field<String> taskStatus = task.STATUS.as("task_status");
        Field<String> taskResultRef = task.RESULT_REF.as("task_result_ref");
        Field<String> taskResultDigest = task.RESULT_DIGEST.as("task_result_digest");
        Field<UUID> taskDefinitionVersionId = task.WORKFLOW_DEFINITION_VERSION_ID.as("task_definition_version_id");
        Field<String> taskDefinitionDigest = task.WORKFLOW_DEFINITION_DIGEST.as("task_definition_digest");
        return dsl.select(
                        node.WORKFLOW_NODE_INSTANCE_ID, node.WORKFLOW_INSTANCE_ID,
                        node.STAGE_INSTANCE_ID, node.WORK_ORDER_ID, node.NODE_ID,
                        node.TASK_ID, nodeStatus,
                        stage.STAGE_CODE, stageSequenceNo,
                        stageStatus, stageVersion,
                        workflow.PROJECT_ID, workflowStatus,
                        workflowVersion,
                        workflow.WORKFLOW_DEFINITION_VERSION_ID,
                        workflowDefinitionDigest,
                        workflow.CONFIGURATION_BUNDLE_ID,
                        workflow.CONFIGURATION_BUNDLE_DIGEST,
                        task.TASK_TYPE, task.TASK_KIND, taskStatus,
                        taskResultRef, taskResultDigest,
                        taskDefinitionVersionId, taskDefinitionDigest)
                .from(node)
                .join(workflow)
                .on(workflow.TENANT_ID.eq(node.TENANT_ID))
                .and(workflow.WORKFLOW_INSTANCE_ID.eq(node.WORKFLOW_INSTANCE_ID))
                .join(stage)
                .on(stage.TENANT_ID.eq(node.TENANT_ID))
                .and(stage.STAGE_INSTANCE_ID.eq(node.STAGE_INSTANCE_ID))
                .join(task)
                .on(task.TENANT_ID.eq(node.TENANT_ID))
                .and(task.TASK_ID.eq(node.TASK_ID))
                .where(node.TENANT_ID.eq(tenantId))
                .and(node.WORKFLOW_NODE_INSTANCE_ID.eq(nodeInstanceId))
                .forUpdate()
                .of(node)
                .fetchOptional(record -> new NodeRuntime(
                        record.get(node.WORKFLOW_NODE_INSTANCE_ID),
                        record.get(node.WORKFLOW_INSTANCE_ID),
                        record.get(node.STAGE_INSTANCE_ID),
                        record.get(node.WORK_ORDER_ID),
                        record.get(node.NODE_ID),
                        record.get(node.TASK_ID),
                        record.get(nodeStatus),
                        record.get(stage.STAGE_CODE),
                        record.get(stageSequenceNo),
                        record.get(stageStatus),
                        record.get(stageVersion),
                        record.get(workflow.PROJECT_ID),
                        record.get(workflowStatus),
                        record.get(workflowVersion),
                        record.get(workflow.WORKFLOW_DEFINITION_VERSION_ID),
                        record.get(workflowDefinitionDigest),
                        record.get(workflow.CONFIGURATION_BUNDLE_ID),
                        record.get(workflow.CONFIGURATION_BUNDLE_DIGEST),
                        record.get(task.TASK_TYPE),
                        record.get(task.TASK_KIND),
                        record.get(taskStatus),
                        record.get(taskResultRef),
                        record.get(taskResultDigest),
                        record.get(taskDefinitionVersionId),
                        record.get(taskDefinitionDigest)))
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
        WflReviewGateEarlySignal signal = WFL_REVIEW_GATE_EARLY_SIGNAL;
        return dsl.select(signal.SIGNAL_ID, signal.CORRELATION_ID)
                .from(signal)
                .where(signal.TENANT_ID.eq(tenantId))
                .and(signal.WORK_ORDER_ID.eq(workOrderId))
                .and(signal.CONSUMED_AT.isNull())
                .forUpdate()
                .fetchOptional(Records.mapping(EarlyReviewSignal::new))
                .orElse(null);
    }

    private void markEarlyReviewSignalConsumed(String tenantId, UUID workOrderId, Instant consumedAt) {
        WflReviewGateEarlySignal signal = WFL_REVIEW_GATE_EARLY_SIGNAL;
        dsl.update(signal)
                .set(signal.CONSUMED_AT, consumedAt)
                .where(signal.TENANT_ID.eq(tenantId))
                .and(signal.WORK_ORDER_ID.eq(workOrderId))
                .and(signal.CONSUMED_AT.isNull())
                .execute();
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
