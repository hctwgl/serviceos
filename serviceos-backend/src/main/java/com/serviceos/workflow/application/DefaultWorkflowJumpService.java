package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CancelOpenWorkflowTasksCommand;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.workflow.api.JumpWorkflowCommand;
import com.serviceos.workflow.api.WorkflowJumpReceipt;
import com.serviceos.workflow.api.WorkflowJumpService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 人工跳转：取消当前开放运行时后激活目标任务节点。
 *
 * <p>事务内完成：定位 ACTIVE 根流程 → 解析目标 → 取消开放节点/等待/任务 → 新建阶段与任务。
 * 失败关闭：无开放根流程、目标非法、并发取消后无 ACTIVE 流程。</p>
 */
@Service
final class DefaultWorkflowJumpService implements WorkflowJumpService {
    private final JdbcClient jdbc;
    private final ConfigurationService configurations;
    private final WorkflowDefinitionParser parser;
    private final TaskSchedulingService tasks;
    private final Clock clock;

    DefaultWorkflowJumpService(
            JdbcClient jdbc,
            ConfigurationService configurations,
            WorkflowDefinitionParser parser,
            TaskSchedulingService tasks,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.configurations = configurations;
        this.parser = parser;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WorkflowJumpReceipt jump(JumpWorkflowCommand command) {
        RootRuntime root = jdbc.sql("""
                        SELECT workflow_instance_id, project_id, work_order_id,
                               configuration_bundle_id, configuration_bundle_digest,
                               workflow_definition_version_id, definition_digest, version
                          FROM wfl_workflow_instance
                         WHERE tenant_id = :tenantId
                           AND work_order_id = :workOrderId
                           AND instance_role = 'ROOT'
                           AND status = 'ACTIVE'
                         FOR UPDATE
                        """)
                .param("tenantId", command.tenantId())
                .param("workOrderId", command.workOrderId())
                .query((rs, rowNum) -> new RootRuntime(
                        rs.getObject("workflow_instance_id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("work_order_id", UUID.class),
                        rs.getObject("configuration_bundle_id", UUID.class),
                        rs.getString("configuration_bundle_digest"),
                        rs.getObject("workflow_definition_version_id", UUID.class),
                        rs.getString("definition_digest"),
                        rs.getLong("version")))
                .optional()
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

        int maxSequence = jdbc.sql("""
                        SELECT COALESCE(MAX(sequence_no), 0)
                          FROM wfl_stage_instance
                         WHERE tenant_id = :tenantId
                           AND workflow_instance_id = :workflowId
                        """)
                .param("tenantId", command.tenantId())
                .param("workflowId", root.workflowInstanceId())
                .query(Integer.class)
                .single();

        UUID stageId = UUID.randomUUID();
        UUID nodeInstanceId = UUID.randomUUID();
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
                .param("tenantId", command.tenantId())
                .param("workflowId", root.workflowInstanceId())
                .param("workOrderId", root.workOrderId())
                .param("stageCode", target.stageCode())
                .param("sequenceNo", maxSequence + 1)
                .param("activationEventId", jumpEventId)
                .param("activatedAt", java.sql.Timestamp.from(now))
                .update();

        String payloadDigest = Sha256.digest(
                "jump|" + command.workOrderId() + "|" + command.targetNodeId()
                        + "|" + command.approvalRef());
        ScheduledTaskView task = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                command.tenantId(), root.projectId(), root.workOrderId(),
                root.workflowInstanceId(), stageId, nodeInstanceId,
                target.nodeId(), root.definitionVersionId(), root.definitionDigest(),
                root.bundleId(), root.bundleDigest(), target.stageCode(),
                target.taskType(), target.taskKind(), target.formRef(), target.slaRef(),
                "work-order:" + root.workOrderId() + "|jump:" + jumpEventId,
                payloadDigest, 100, now, 3,
                command.correlationId(), jumpEventId.toString()));

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
                .param("tenantId", command.tenantId())
                .param("workflowId", root.workflowInstanceId())
                .param("stageId", stageId)
                .param("workOrderId", root.workOrderId())
                .param("nodeId", target.nodeId())
                .param("taskId", task.taskId())
                .param("activationEventId", jumpEventId)
                .param("activatedAt", java.sql.Timestamp.from(now))
                .update();

        jdbc.sql("""
                        UPDATE wfl_workflow_instance
                           SET version = version + 1
                         WHERE tenant_id = :tenantId
                           AND workflow_instance_id = :workflowId
                           AND status = 'ACTIVE'
                        """)
                .param("tenantId", command.tenantId())
                .param("workflowId", root.workflowInstanceId())
                .update();

        return new WorkflowJumpReceipt(
                root.workflowInstanceId(), stageId, nodeInstanceId, task.taskId(), target.nodeId());
    }

    private void cancelOpenRuntime(String tenantId, List<UUID> workflowIds) {
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
