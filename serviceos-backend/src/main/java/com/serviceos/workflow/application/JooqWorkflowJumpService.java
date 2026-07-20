package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.jooq.generated.tables.WflMultiInstance;
import com.serviceos.jooq.generated.tables.WflWorkflowInstance;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CancelOpenWorkflowTasksCommand;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.workflow.api.JumpWorkflowCommand;
import com.serviceos.workflow.api.WorkflowJumpReceipt;
import com.serviceos.workflow.api.WorkflowJumpService;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WflMultiInstance.WFL_MULTI_INSTANCE;
import static com.serviceos.jooq.generated.tables.WflMultiInstanceSlot.WFL_MULTI_INSTANCE_SLOT;
import static com.serviceos.jooq.generated.tables.WflNodeInstance.WFL_NODE_INSTANCE;
import static com.serviceos.jooq.generated.tables.WflParallelJoin.WFL_PARALLEL_JOIN;
import static com.serviceos.jooq.generated.tables.WflStageInstance.WFL_STAGE_INSTANCE;
import static com.serviceos.jooq.generated.tables.WflTimerSubscription.WFL_TIMER_SUBSCRIPTION;
import static com.serviceos.jooq.generated.tables.WflWaitSubscription.WFL_WAIT_SUBSCRIPTION;
import static com.serviceos.jooq.generated.tables.WflWorkflowInstance.WFL_WORKFLOW_INSTANCE;

/**
 * 人工跳转（jOOQ 实现）：取消当前开放运行时后激活目标任务节点。
 *
 * <p>事务内完成：定位 ACTIVE 根流程 → 解析目标 → 取消开放节点/等待/任务 → 新建阶段与任务。
 * 失败关闭：无开放根流程、目标非法、并发取消后无 ACTIVE 流程。</p>
 */
@Service
final class JooqWorkflowJumpService implements WorkflowJumpService {
    private final DSLContext dsl;
    private final ConfigurationService configurations;
    private final WorkflowDefinitionParser parser;
    private final TaskSchedulingService tasks;
    private final Clock clock;

    JooqWorkflowJumpService(
            DSLContext dsl,
            ConfigurationService configurations,
            WorkflowDefinitionParser parser,
            TaskSchedulingService tasks,
            Clock clock
    ) {
        this.dsl = dsl;
        this.configurations = configurations;
        this.parser = parser;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WorkflowJumpReceipt jump(JumpWorkflowCommand command) {
        WflWorkflowInstance workflowTable = WFL_WORKFLOW_INSTANCE;
        RootRuntime root = dsl.select(
                        workflowTable.WORKFLOW_INSTANCE_ID, workflowTable.PROJECT_ID,
                        workflowTable.WORK_ORDER_ID, workflowTable.CONFIGURATION_BUNDLE_ID,
                        workflowTable.CONFIGURATION_BUNDLE_DIGEST,
                        workflowTable.WORKFLOW_DEFINITION_VERSION_ID,
                        workflowTable.DEFINITION_DIGEST, workflowTable.VERSION)
                .from(workflowTable)
                .where(workflowTable.TENANT_ID.eq(command.tenantId()))
                .and(workflowTable.WORK_ORDER_ID.eq(command.workOrderId()))
                .and(workflowTable.INSTANCE_ROLE.eq("ROOT"))
                .and(workflowTable.STATUS.eq("ACTIVE"))
                .forUpdate()
                .fetchOptional(Records.mapping(RootRuntime::new))
                .orElseThrow(() -> new IllegalStateException(
                        "no ACTIVE ROOT workflow for work order " + command.workOrderId()));

        var asset = configurations.requireBundleAsset(
                command.tenantId(), root.bundleId(), root.bundleDigest(),
                ConfigurationAssetType.WORKFLOW);
        var target = parser.resolveJumpTarget(asset, command.targetNodeId());

        Instant now = clock.instant();
        UUID jumpEventId = UUID.nameUUIDFromBytes(
                ("jump|" + command.causationId() + "|" + command.targetNodeId())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        List<UUID> workflowIds = List.of(root.workflowInstanceId());
        cancelOpenRuntime(command.tenantId(), workflowIds);
        tasks.cancelOpenTasksForWorkflows(new CancelOpenWorkflowTasksCommand(
                command.tenantId(), workflowIds, command.reasonCode(),
                jumpEventId, now, command.correlationId()));

        int maxSequence = dsl.select(DSL.coalesce(DSL.max(WFL_STAGE_INSTANCE.SEQUENCE_NO), 0))
                .from(WFL_STAGE_INSTANCE)
                .where(WFL_STAGE_INSTANCE.TENANT_ID.eq(command.tenantId()))
                .and(WFL_STAGE_INSTANCE.WORKFLOW_INSTANCE_ID.eq(root.workflowInstanceId()))
                .fetchSingleInto(Integer.class);

        UUID stageId = UUID.randomUUID();
        UUID nodeInstanceId = UUID.randomUUID();
        dsl.insertInto(WFL_STAGE_INSTANCE)
                .set(WFL_STAGE_INSTANCE.STAGE_INSTANCE_ID, stageId)
                .set(WFL_STAGE_INSTANCE.TENANT_ID, command.tenantId())
                .set(WFL_STAGE_INSTANCE.WORKFLOW_INSTANCE_ID, root.workflowInstanceId())
                .set(WFL_STAGE_INSTANCE.WORK_ORDER_ID, root.workOrderId())
                .set(WFL_STAGE_INSTANCE.STAGE_CODE, target.stageCode())
                .set(WFL_STAGE_INSTANCE.SEQUENCE_NO, maxSequence + 1)
                .set(WFL_STAGE_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_STAGE_INSTANCE.ACTIVATION_EVENT_ID, jumpEventId)
                .set(WFL_STAGE_INSTANCE.VERSION, 1L)
                .set(WFL_STAGE_INSTANCE.ACTIVATED_AT, now)
                .execute();

        String payloadDigest = Sha256.digest(
                "jump|" + command.workOrderId() + "|" + command.targetNodeId()
                        + "|" + command.approvalRef());
        ScheduledTaskView task = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                command.tenantId(), root.projectId(), root.workOrderId(),
                root.workflowInstanceId(), stageId, nodeInstanceId,
                target.nodeId(), root.definitionVersionId(), root.definitionDigest(),
                root.bundleId(), root.bundleDigest(), target.stageCode(),
                target.taskType(), target.taskKind(), target.formRef(), target.slaRef(),
                target.assigneePolicyRef(), target.dispatchPolicyRef(), target.ruleRef(),
                "work-order:" + root.workOrderId() + "|jump:" + jumpEventId,
                payloadDigest, 100, now, 3,
                command.correlationId(), jumpEventId.toString()));

        dsl.insertInto(WFL_NODE_INSTANCE)
                .set(WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID, nodeInstanceId)
                .set(WFL_NODE_INSTANCE.TENANT_ID, command.tenantId())
                .set(WFL_NODE_INSTANCE.WORKFLOW_INSTANCE_ID, root.workflowInstanceId())
                .set(WFL_NODE_INSTANCE.STAGE_INSTANCE_ID, stageId)
                .set(WFL_NODE_INSTANCE.WORK_ORDER_ID, root.workOrderId())
                .set(WFL_NODE_INSTANCE.NODE_ID, target.nodeId())
                .set(WFL_NODE_INSTANCE.TASK_ID, task.taskId())
                .set(WFL_NODE_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_NODE_INSTANCE.ACTIVATION_EVENT_ID, jumpEventId)
                .set(WFL_NODE_INSTANCE.VERSION, 1L)
                .set(WFL_NODE_INSTANCE.ACTIVATED_AT, now)
                .execute();

        dsl.update(WFL_WORKFLOW_INSTANCE)
                .set(WFL_WORKFLOW_INSTANCE.VERSION, WFL_WORKFLOW_INSTANCE.VERSION.plus(1))
                .where(WFL_WORKFLOW_INSTANCE.TENANT_ID.eq(command.tenantId()))
                .and(WFL_WORKFLOW_INSTANCE.WORKFLOW_INSTANCE_ID.eq(root.workflowInstanceId()))
                .and(WFL_WORKFLOW_INSTANCE.STATUS.eq("ACTIVE"))
                .execute();

        return new WorkflowJumpReceipt(
                root.workflowInstanceId(), stageId, nodeInstanceId, task.taskId(), target.nodeId());
    }

    private void cancelOpenRuntime(String tenantId, List<UUID> workflowIds) {
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
    }

    private record RootRuntime(
            UUID workflowInstanceId,
            UUID projectId,
            UUID workOrderId,
            UUID bundleId,
            String bundleDigest,
            UUID definitionVersionId,
            String definitionDigest,
            long version
    ) {
    }
}
