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
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WflNodeInstance.WFL_NODE_INSTANCE;
import static com.serviceos.jooq.generated.tables.WflStageInstance.WFL_STAGE_INSTANCE;
import static com.serviceos.jooq.generated.tables.WflWorkflowInstance.WFL_WORKFLOW_INSTANCE;

/**
 * workorder.reopened 的可靠消费者（jOOQ 实现）：按冻结 Bundle 新建 ROOT 流程实例。
 *
 * <p>前提：取消级联已关闭旧根流程，`uq_wfl_root_work_order_open` 仅约束 ACTIVE/SUSPENDED。</p>
 */
@Service
final class JooqWorkflowWorkOrderReopenedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "workflow.work-order-reopened.v1";

    private final DSLContext dsl;
    private final InboxService inbox;
    private final ConfigurationService configurations;
    private final WorkflowDefinitionParser parser;
    private final TaskSchedulingService tasks;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqWorkflowWorkOrderReopenedHandler(
            DSLContext dsl,
            InboxService inbox,
            ConfigurationService configurations,
            WorkflowDefinitionParser parser,
            TaskSchedulingService tasks,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.dsl = dsl;
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

        boolean openRootExists = dsl.selectCount()
                .from(WFL_WORKFLOW_INSTANCE)
                .where(WFL_WORKFLOW_INSTANCE.TENANT_ID.eq(message.tenantId()))
                .and(WFL_WORKFLOW_INSTANCE.WORK_ORDER_ID.eq(reopened.workOrderId()))
                .and(WFL_WORKFLOW_INSTANCE.INSTANCE_ROLE.eq("ROOT"))
                .and(WFL_WORKFLOW_INSTANCE.STATUS.in("ACTIVE", "SUSPENDED"))
                .fetchSingleInto(Integer.class) > 0;
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

        dsl.insertInto(WFL_WORKFLOW_INSTANCE)
                .set(WFL_WORKFLOW_INSTANCE.WORKFLOW_INSTANCE_ID, workflowId)
                .set(WFL_WORKFLOW_INSTANCE.TENANT_ID, message.tenantId())
                .set(WFL_WORKFLOW_INSTANCE.PROJECT_ID, reopened.projectId())
                .set(WFL_WORKFLOW_INSTANCE.WORK_ORDER_ID, reopened.workOrderId())
                .set(WFL_WORKFLOW_INSTANCE.CONFIGURATION_BUNDLE_ID, reopened.bundleRef().bundleId())
                .set(WFL_WORKFLOW_INSTANCE.CONFIGURATION_BUNDLE_DIGEST, reopened.bundleRef().manifestDigest())
                .set(WFL_WORKFLOW_INSTANCE.WORKFLOW_DEFINITION_VERSION_ID, asset.versionId())
                .set(WFL_WORKFLOW_INSTANCE.WORKFLOW_KEY, definition.workflowKey())
                .set(WFL_WORKFLOW_INSTANCE.WORKFLOW_VERSION, definition.workflowVersion())
                .set(WFL_WORKFLOW_INSTANCE.DEFINITION_DIGEST, asset.contentDigest())
                .set(WFL_WORKFLOW_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_WORKFLOW_INSTANCE.INSTANCE_ROLE, "ROOT")
                .set(WFL_WORKFLOW_INSTANCE.START_EVENT_ID, message.eventId())
                .set(WFL_WORKFLOW_INSTANCE.CORRELATION_ID, message.correlationId())
                .set(WFL_WORKFLOW_INSTANCE.VERSION, 1L)
                .set(WFL_WORKFLOW_INSTANCE.STARTED_AT, now)
                .execute();

        dsl.insertInto(WFL_STAGE_INSTANCE)
                .set(WFL_STAGE_INSTANCE.STAGE_INSTANCE_ID, stageId)
                .set(WFL_STAGE_INSTANCE.TENANT_ID, message.tenantId())
                .set(WFL_STAGE_INSTANCE.WORKFLOW_INSTANCE_ID, workflowId)
                .set(WFL_STAGE_INSTANCE.WORK_ORDER_ID, reopened.workOrderId())
                .set(WFL_STAGE_INSTANCE.STAGE_CODE, definition.firstStageCode())
                .set(WFL_STAGE_INSTANCE.SEQUENCE_NO, 1)
                .set(WFL_STAGE_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_STAGE_INSTANCE.ACTIVATION_EVENT_ID, message.eventId())
                .set(WFL_STAGE_INSTANCE.VERSION, 1L)
                .set(WFL_STAGE_INSTANCE.ACTIVATED_AT, now)
                .execute();

        ScheduledTaskView firstTask = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                message.tenantId(), reopened.projectId(), reopened.workOrderId(), workflowId, stageId,
                nodeInstanceId, definition.firstNodeId(), asset.versionId(), asset.contentDigest(),
                reopened.bundleRef().bundleId(), reopened.bundleRef().manifestDigest(),
                definition.firstStageCode(), definition.firstTaskType(), definition.firstTaskKind(),
                definition.firstFormRef(), definition.firstSlaRef(), definition.firstAssigneePolicyRef(),
                definition.firstDispatchPolicyRef(), definition.firstRuleRef(),
                "work-order-reopen:" + reopened.workOrderId() + ":" + message.eventId(),
                message.payloadDigest(), 100, now, 3,
                message.correlationId(), message.eventId().toString()));

        dsl.insertInto(WFL_NODE_INSTANCE)
                .set(WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID, nodeInstanceId)
                .set(WFL_NODE_INSTANCE.TENANT_ID, message.tenantId())
                .set(WFL_NODE_INSTANCE.WORKFLOW_INSTANCE_ID, workflowId)
                .set(WFL_NODE_INSTANCE.STAGE_INSTANCE_ID, stageId)
                .set(WFL_NODE_INSTANCE.WORK_ORDER_ID, reopened.workOrderId())
                .set(WFL_NODE_INSTANCE.NODE_ID, definition.firstNodeId())
                .set(WFL_NODE_INSTANCE.TASK_ID, firstTask.taskId())
                .set(WFL_NODE_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_NODE_INSTANCE.ACTIVATION_EVENT_ID, message.eventId())
                .set(WFL_NODE_INSTANCE.VERSION, 1L)
                .set(WFL_NODE_INSTANCE.ACTIVATED_AT, now)
                .execute();

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
