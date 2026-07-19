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
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.workflow.api.StageActivatedPayload;
import com.serviceos.workflow.api.WorkflowStartedPayload;
import com.serviceos.workorder.api.WorkOrderReopenedPayload;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * workorder.reopened 的可靠消费者：按冻结 Bundle 新建 ROOT 流程实例。
 *
 * <p>前提：取消级联已关闭旧根流程，`uq_wfl_root_work_order_open` 仅约束 ACTIVE/SUSPENDED。</p>
 */
@Service
final class WorkflowWorkOrderReopenedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "workflow.work-order-reopened.v1";

    private final JdbcClient jdbc;
    private final InboxService inbox;
    private final ConfigurationService configurations;
    private final WorkflowDefinitionParser parser;
    private final TaskSchedulingService tasks;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    WorkflowWorkOrderReopenedHandler(
            JdbcClient jdbc,
            InboxService inbox,
            ConfigurationService configurations,
            WorkflowDefinitionParser parser,
            TaskSchedulingService tasks,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.inbox = inbox;
        this.configurations = configurations;
        this.parser = parser;
        this.tasks = tasks;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return "workorder.reopened".equals(eventType) && schemaVersion == 1;
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        if (!"workorder".equals(message.module()) || !"WorkOrder".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported WorkOrderReopened envelope");
        }
        InboxDecision decision = inbox.begin(
                message.tenantId(), CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }

        WorkOrderReopenedPayload reopened = readPayload(message.payload());
        if (!reopened.workOrderId().toString().equals(message.aggregateId())) {
            throw new IllegalArgumentException("WorkOrderReopened aggregateId does not match payload");
        }

        boolean openRootExists = jdbc.sql("""
                        SELECT COUNT(1)
                          FROM wfl_workflow_instance
                         WHERE tenant_id = :tenantId
                           AND work_order_id = :workOrderId
                           AND instance_role = 'ROOT'
                           AND status IN ('ACTIVE', 'SUSPENDED')
                        """)
                .param("tenantId", message.tenantId())
                .param("workOrderId", reopened.workOrderId())
                .query(Integer.class)
                .single() > 0;
        if (openRootExists) {
            throw new IllegalStateException(
                    "cannot reopen workflow while an open ROOT instance still exists");
        }

        var asset = configurations.requireBundleAsset(
                message.tenantId(), reopened.bundleRef().bundleId(),
                reopened.bundleRef().manifestDigest(), ConfigurationAssetType.WORKFLOW);
        var definition = parser.parse(asset);

        Instant now = clock.instant();
        UUID workflowId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID nodeInstanceId = UUID.randomUUID();

        jdbc.sql("""
                INSERT INTO wfl_workflow_instance (
                    workflow_instance_id, tenant_id, project_id, work_order_id,
                    configuration_bundle_id, configuration_bundle_digest,
                    workflow_definition_version_id,
                    workflow_key, workflow_version, definition_digest, status,
                    instance_role, start_event_id, correlation_id, version, started_at
                ) VALUES (
                    :workflowId, :tenantId, :projectId, :workOrderId,
                    :bundleId, :bundleDigest, :definitionVersionId, :workflowKey, :workflowVersion,
                    :definitionDigest, 'ACTIVE', 'ROOT', :startEventId, :correlationId, 1, :startedAt
                )
                """)
                .param("workflowId", workflowId)
                .param("tenantId", message.tenantId())
                .param("projectId", reopened.projectId())
                .param("workOrderId", reopened.workOrderId())
                .param("bundleId", reopened.bundleRef().bundleId())
                .param("bundleDigest", reopened.bundleRef().manifestDigest())
                .param("definitionVersionId", asset.versionId())
                .param("workflowKey", definition.workflowKey())
                .param("workflowVersion", definition.workflowVersion())
                .param("definitionDigest", asset.contentDigest())
                .param("startEventId", message.eventId())
                .param("correlationId", message.correlationId())
                .param("startedAt", java.sql.Timestamp.from(now))
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
                .params(Map.of(
                        "stageId", stageId,
                        "tenantId", message.tenantId(),
                        "workflowId", workflowId,
                        "workOrderId", reopened.workOrderId(),
                        "stageCode", definition.firstStageCode(),
                        "activationEventId", message.eventId(),
                        "activatedAt", java.sql.Timestamp.from(now)))
                .update();

        ScheduledTaskView firstTask = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                message.tenantId(), reopened.projectId(), reopened.workOrderId(), workflowId, stageId,
                nodeInstanceId, definition.firstNodeId(), asset.versionId(), asset.contentDigest(),
                reopened.bundleRef().bundleId(), reopened.bundleRef().manifestDigest(),
                definition.firstStageCode(), definition.firstTaskType(), definition.firstTaskKind(),
                definition.firstFormRef(), definition.firstSlaRef(), definition.firstAssigneePolicyRef(),
                "work-order-reopen:" + reopened.workOrderId() + ":" + message.eventId(),
                message.payloadDigest(), 100, now, 3,
                message.correlationId(), message.eventId().toString()));

        jdbc.sql("""
                INSERT INTO wfl_node_instance (
                    workflow_node_instance_id, tenant_id, workflow_instance_id, stage_instance_id,
                    work_order_id, node_id, task_id, status, activation_event_id, version, activated_at
                ) VALUES (
                    :nodeInstanceId, :tenantId, :workflowId, :stageId,
                    :workOrderId, :nodeId, :taskId, 'ACTIVE', :activationEventId, 1, :activatedAt
                )
                """)
                .param("nodeInstanceId", nodeInstanceId)
                .param("tenantId", message.tenantId())
                .param("workflowId", workflowId)
                .param("stageId", stageId)
                .param("workOrderId", reopened.workOrderId())
                .param("nodeId", definition.firstNodeId())
                .param("taskId", firstTask.taskId())
                .param("activationEventId", message.eventId())
                .param("activatedAt", java.sql.Timestamp.from(now))
                .update();

        String startedJson = write(new WorkflowStartedPayload(
                workflowId, reopened.projectId(), reopened.workOrderId(),
                reopened.bundleRef().bundleId(), asset.versionId(), definition.workflowKey(),
                definition.workflowVersion(), asset.contentDigest(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "workflow", "workflow.started", 1,
                "Workflow", workflowId.toString(), 1, message.tenantId(),
                message.correlationId(), message.eventId().toString(), message.partitionKey(),
                startedJson, Sha256.digest(startedJson), now));

        String stageJson = write(new StageActivatedPayload(
                stageId, workflowId, reopened.workOrderId(),
                definition.firstStageCode(), 1, now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "workflow", "stage.activated", 1,
                "Stage", stageId.toString(), 1, message.tenantId(),
                message.correlationId(), message.eventId().toString(), message.partitionKey(),
                stageJson, Sha256.digest(stageJson), now));

        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(workflowId + "|" + stageId + "|" + nodeInstanceId));
    }

    private WorkOrderReopenedPayload readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, WorkOrderReopenedPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("WorkOrderReopened payload cannot be decoded", exception);
        }
    }

    private String write(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("workflow event serialization failed", exception);
        }
    }
}
