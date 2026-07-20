package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.jooq.generated.tables.WflMultiInstance;
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
import org.jooq.DSLContext;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WflCompensationRequest.WFL_COMPENSATION_REQUEST;
import static com.serviceos.jooq.generated.tables.WflMultiInstance.WFL_MULTI_INSTANCE;
import static com.serviceos.jooq.generated.tables.WflMultiInstanceSlot.WFL_MULTI_INSTANCE_SLOT;
import static com.serviceos.jooq.generated.tables.WflNodeInstance.WFL_NODE_INSTANCE;
import static com.serviceos.jooq.generated.tables.WflParallelJoin.WFL_PARALLEL_JOIN;
import static com.serviceos.jooq.generated.tables.WflStageInstance.WFL_STAGE_INSTANCE;
import static com.serviceos.jooq.generated.tables.WflSubprocessLink.WFL_SUBPROCESS_LINK;
import static com.serviceos.jooq.generated.tables.WflTimerSubscription.WFL_TIMER_SUBSCRIPTION;
import static com.serviceos.jooq.generated.tables.WflWaitSubscription.WFL_WAIT_SUBSCRIPTION;
import static com.serviceos.jooq.generated.tables.WflWorkflowInstance.WFL_WORKFLOW_INSTANCE;

/**
 * workorder.cancelled 的可靠消费者（jOOQ 实现）：级联取消运行时，并为已完成且声明补偿的节点创建补偿任务。
 *
 * <p>事务边界：Inbox begin → 取消流程/节点/等待/任务 → 按定义创建补偿请求与任务 → Inbox complete。
 * 补偿幂等键为 (tenant, cancel_event_id, source_node_instance_id)。</p>
 */
@Service
final class JooqWorkflowWorkOrderCancelledHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "workflow.work-order-cancelled.v1";

    private final DSLContext dsl;
    private final InboxService inbox;
    private final TaskSchedulingService tasks;
    private final ConfigurationService configurations;
    private final WorkflowDefinitionParser parser;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqWorkflowWorkOrderCancelledHandler(
            DSLContext dsl,
            InboxService inbox,
            TaskSchedulingService tasks,
            ConfigurationService configurations,
            WorkflowDefinitionParser parser,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.dsl = dsl;
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
        return dsl.select(WFL_WORKFLOW_INSTANCE.WORKFLOW_INSTANCE_ID)
                .from(WFL_WORKFLOW_INSTANCE)
                .where(WFL_WORKFLOW_INSTANCE.TENANT_ID.eq(tenantId))
                .and(WFL_WORKFLOW_INSTANCE.WORK_ORDER_ID.eq(workOrderId))
                .and(WFL_WORKFLOW_INSTANCE.STATUS.in("ACTIVE", "SUSPENDED"))
                .fetch(WFL_WORKFLOW_INSTANCE.WORKFLOW_INSTANCE_ID);
    }

    private List<CompensationCandidate> listCompensationCandidates(String tenantId, UUID workOrderId) {
        return dsl.select(
                        WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID,
                        WFL_NODE_INSTANCE.NODE_ID,
                        WFL_NODE_INSTANCE.WORKFLOW_INSTANCE_ID,
                        WFL_WORKFLOW_INSTANCE.PROJECT_ID,
                        WFL_WORKFLOW_INSTANCE.WORK_ORDER_ID,
                        WFL_WORKFLOW_INSTANCE.CONFIGURATION_BUNDLE_ID,
                        WFL_WORKFLOW_INSTANCE.CONFIGURATION_BUNDLE_DIGEST,
                        WFL_WORKFLOW_INSTANCE.WORKFLOW_DEFINITION_VERSION_ID,
                        WFL_WORKFLOW_INSTANCE.DEFINITION_DIGEST)
                .from(WFL_NODE_INSTANCE)
                .join(WFL_WORKFLOW_INSTANCE)
                .on(WFL_WORKFLOW_INSTANCE.TENANT_ID.eq(WFL_NODE_INSTANCE.TENANT_ID))
                .and(WFL_WORKFLOW_INSTANCE.WORKFLOW_INSTANCE_ID.eq(WFL_NODE_INSTANCE.WORKFLOW_INSTANCE_ID))
                .where(WFL_NODE_INSTANCE.TENANT_ID.eq(tenantId))
                .and(WFL_NODE_INSTANCE.WORK_ORDER_ID.eq(workOrderId))
                .and(WFL_NODE_INSTANCE.STATUS.eq("COMPLETED"))
                .fetch(Records.mapping(CompensationCandidate::new));
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

        boolean alreadyRequested = dsl.selectCount()
                .from(WFL_COMPENSATION_REQUEST)
                .where(WFL_COMPENSATION_REQUEST.TENANT_ID.eq(message.tenantId()))
                .and(WFL_COMPENSATION_REQUEST.CANCEL_EVENT_ID.eq(message.eventId()))
                .and(WFL_COMPENSATION_REQUEST.SOURCE_NODE_INSTANCE_ID.eq(candidate.nodeInstanceId()))
                .fetchSingleInto(Integer.class) > 0;
        if (alreadyRequested) {
            return false;
        }

        UUID stageId = UUID.randomUUID();
        UUID nodeInstanceId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        int nextSequence = dsl.select(
                        DSL.coalesce(DSL.max(WFL_STAGE_INSTANCE.SEQUENCE_NO), 0).plus(1))
                .from(WFL_STAGE_INSTANCE)
                .where(WFL_STAGE_INSTANCE.TENANT_ID.eq(message.tenantId()))
                .and(WFL_STAGE_INSTANCE.WORKFLOW_INSTANCE_ID.eq(candidate.workflowInstanceId()))
                .fetchSingleInto(Integer.class);

        // 补偿阶段挂在已取消流程上，作为取消后仍需执行的配置化善后任务载体。
        dsl.insertInto(WFL_STAGE_INSTANCE)
                .set(WFL_STAGE_INSTANCE.STAGE_INSTANCE_ID, stageId)
                .set(WFL_STAGE_INSTANCE.TENANT_ID, message.tenantId())
                .set(WFL_STAGE_INSTANCE.WORKFLOW_INSTANCE_ID, candidate.workflowInstanceId())
                .set(WFL_STAGE_INSTANCE.WORK_ORDER_ID, candidate.workOrderId())
                .set(WFL_STAGE_INSTANCE.STAGE_CODE, definition.stageCode())
                .set(WFL_STAGE_INSTANCE.SEQUENCE_NO, nextSequence)
                .set(WFL_STAGE_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_STAGE_INSTANCE.ACTIVATION_EVENT_ID, message.eventId())
                .set(WFL_STAGE_INSTANCE.VERSION, 1L)
                .set(WFL_STAGE_INSTANCE.ACTIVATED_AT, now)
                .execute();

        String payloadDigest = Sha256.digest(
                "compensation|" + candidate.nodeInstanceId() + "|" + message.eventId());
        ScheduledTaskView task = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                message.tenantId(), candidate.projectId(), candidate.workOrderId(),
                candidate.workflowInstanceId(), stageId, nodeInstanceId,
                "COMPENSATE_" + candidate.nodeId(), candidate.definitionVersionId(),
                candidate.definitionDigest(), candidate.bundleId(), candidate.bundleDigest(),
                definition.stageCode(), definition.taskType(), WorkflowTaskKind.AUTOMATED,
                null, null, null, null, null,
                "compensation:" + candidate.nodeInstanceId() + ":" + message.eventId(),
                payloadDigest, 100, now, 3,
                message.correlationId(), message.eventId().toString()));

        dsl.insertInto(WFL_NODE_INSTANCE)
                .set(WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID, nodeInstanceId)
                .set(WFL_NODE_INSTANCE.TENANT_ID, message.tenantId())
                .set(WFL_NODE_INSTANCE.WORKFLOW_INSTANCE_ID, candidate.workflowInstanceId())
                .set(WFL_NODE_INSTANCE.STAGE_INSTANCE_ID, stageId)
                .set(WFL_NODE_INSTANCE.WORK_ORDER_ID, candidate.workOrderId())
                .set(WFL_NODE_INSTANCE.NODE_ID, "COMPENSATE_" + candidate.nodeId())
                .set(WFL_NODE_INSTANCE.TASK_ID, task.taskId())
                .set(WFL_NODE_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_NODE_INSTANCE.ACTIVATION_EVENT_ID, message.eventId())
                .set(WFL_NODE_INSTANCE.VERSION, 1L)
                .set(WFL_NODE_INSTANCE.ACTIVATED_AT, now)
                .execute();

        dsl.insertInto(WFL_COMPENSATION_REQUEST)
                .set(WFL_COMPENSATION_REQUEST.COMPENSATION_REQUEST_ID, requestId)
                .set(WFL_COMPENSATION_REQUEST.TENANT_ID, message.tenantId())
                .set(WFL_COMPENSATION_REQUEST.WORKFLOW_INSTANCE_ID, candidate.workflowInstanceId())
                .set(WFL_COMPENSATION_REQUEST.WORK_ORDER_ID, candidate.workOrderId())
                .set(WFL_COMPENSATION_REQUEST.SOURCE_NODE_INSTANCE_ID, candidate.nodeInstanceId())
                .set(WFL_COMPENSATION_REQUEST.SOURCE_NODE_ID, candidate.nodeId())
                .set(WFL_COMPENSATION_REQUEST.COMPENSATION_TASK_TYPE, definition.taskType())
                .set(WFL_COMPENSATION_REQUEST.COMPENSATION_STAGE_CODE, definition.stageCode())
                .set(WFL_COMPENSATION_REQUEST.COMPENSATION_TASK_ID, task.taskId())
                .set(WFL_COMPENSATION_REQUEST.CANCEL_EVENT_ID, message.eventId())
                .set(WFL_COMPENSATION_REQUEST.STATUS, "REQUESTED")
                .set(WFL_COMPENSATION_REQUEST.VERSION, 1L)
                .set(WFL_COMPENSATION_REQUEST.REQUESTED_AT, now)
                .execute();
        return true;
    }

    private void cancelWorkflowRuntime(String tenantId, List<UUID> workflowIds, Instant now) {
        dsl.update(WFL_WORKFLOW_INSTANCE)
                .set(WFL_WORKFLOW_INSTANCE.STATUS, "CANCELLED")
                .set(WFL_WORKFLOW_INSTANCE.VERSION, WFL_WORKFLOW_INSTANCE.VERSION.plus(1))
                .where(WFL_WORKFLOW_INSTANCE.TENANT_ID.eq(tenantId))
                .and(WFL_WORKFLOW_INSTANCE.WORKFLOW_INSTANCE_ID.in(workflowIds))
                .and(WFL_WORKFLOW_INSTANCE.STATUS.in("ACTIVE", "SUSPENDED"))
                .execute();

        dsl.update(WFL_STAGE_INSTANCE)
                .set(WFL_STAGE_INSTANCE.STATUS, "CANCELLED")
                .set(WFL_STAGE_INSTANCE.VERSION, WFL_STAGE_INSTANCE.VERSION.plus(1))
                .where(WFL_STAGE_INSTANCE.TENANT_ID.eq(tenantId))
                .and(WFL_STAGE_INSTANCE.WORKFLOW_INSTANCE_ID.in(workflowIds))
                .and(WFL_STAGE_INSTANCE.STATUS.in("PENDING", "ACTIVE", "BLOCKED"))
                .execute();

        dsl.update(WFL_NODE_INSTANCE)
                .set(WFL_NODE_INSTANCE.STATUS, "CANCELLED")
                .set(WFL_NODE_INSTANCE.VERSION, WFL_NODE_INSTANCE.VERSION.plus(1))
                .where(WFL_NODE_INSTANCE.TENANT_ID.eq(tenantId))
                .and(WFL_NODE_INSTANCE.WORKFLOW_INSTANCE_ID.in(workflowIds))
                .and(WFL_NODE_INSTANCE.STATUS.in("ACTIVE", "WAITING"))
                .execute();

        dsl.update(WFL_WAIT_SUBSCRIPTION)
                .set(WFL_WAIT_SUBSCRIPTION.STATUS, "CANCELLED")
                .where(WFL_WAIT_SUBSCRIPTION.TENANT_ID.eq(tenantId))
                .and(WFL_WAIT_SUBSCRIPTION.WORKFLOW_INSTANCE_ID.in(workflowIds))
                .and(WFL_WAIT_SUBSCRIPTION.STATUS.eq("WAITING"))
                .execute();

        dsl.update(WFL_TIMER_SUBSCRIPTION)
                .set(WFL_TIMER_SUBSCRIPTION.STATUS, "CANCELLED")
                .where(WFL_TIMER_SUBSCRIPTION.TENANT_ID.eq(tenantId))
                .and(WFL_TIMER_SUBSCRIPTION.WORKFLOW_INSTANCE_ID.in(workflowIds))
                .and(WFL_TIMER_SUBSCRIPTION.STATUS.in("WAITING", "CLAIMED"))
                .execute();

        dsl.update(WFL_PARALLEL_JOIN)
                .set(WFL_PARALLEL_JOIN.STATUS, "CANCELLED")
                .where(WFL_PARALLEL_JOIN.TENANT_ID.eq(tenantId))
                .and(WFL_PARALLEL_JOIN.WORKFLOW_INSTANCE_ID.in(workflowIds))
                .and(WFL_PARALLEL_JOIN.STATUS.eq("OPEN"))
                .execute();

        dsl.update(WFL_MULTI_INSTANCE)
                .set(WFL_MULTI_INSTANCE.STATUS, "CANCELLED")
                .where(WFL_MULTI_INSTANCE.TENANT_ID.eq(tenantId))
                .and(WFL_MULTI_INSTANCE.WORKFLOW_INSTANCE_ID.in(workflowIds))
                .and(WFL_MULTI_INSTANCE.STATUS.eq("OPEN"))
                .execute();

        WflMultiInstance multiInstance = WFL_MULTI_INSTANCE;
        dsl.update(WFL_MULTI_INSTANCE_SLOT)
                .set(WFL_MULTI_INSTANCE_SLOT.STATUS, "CANCELLED")
                .where(WFL_MULTI_INSTANCE_SLOT.TENANT_ID.eq(tenantId))
                .and(WFL_MULTI_INSTANCE_SLOT.MULTI_INSTANCE_ID.in(
                        dsl.select(multiInstance.MULTI_INSTANCE_ID)
                                .from(multiInstance)
                                .where(multiInstance.TENANT_ID.eq(tenantId))
                                .and(multiInstance.WORKFLOW_INSTANCE_ID.in(workflowIds))))
                .and(WFL_MULTI_INSTANCE_SLOT.STATUS.eq("ACTIVE"))
                .execute();

        dsl.update(WFL_SUBPROCESS_LINK)
                .set(WFL_SUBPROCESS_LINK.STATUS, "CANCELLED")
                .set(WFL_SUBPROCESS_LINK.VERSION, WFL_SUBPROCESS_LINK.VERSION.plus(1))
                .where(WFL_SUBPROCESS_LINK.TENANT_ID.eq(tenantId))
                .and(WFL_SUBPROCESS_LINK.PARENT_WORKFLOW_INSTANCE_ID.in(workflowIds)
                        .or(WFL_SUBPROCESS_LINK.CHILD_WORKFLOW_INSTANCE_ID.in(workflowIds)))
                .and(WFL_SUBPROCESS_LINK.STATUS.eq("RUNNING"))
                .execute();
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
