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
import com.serviceos.workorder.api.ActivateWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderReceivedPayload;
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

/** WorkOrderReceived v1 的可靠本地消费者（jOOQ 实现），拥有 M17 流程启动原子事务。 */
@Service
final class JooqWorkflowWorkOrderReceivedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "workflow.work-order-received.v1";

    private final DSLContext dsl;
    private final InboxService inbox;
    private final ConfigurationService configurations;
    private final WorkflowDefinitionParser parser;
    private final TaskSchedulingService tasks;
    private final WorkOrderCommandService workOrders;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqWorkflowWorkOrderReceivedHandler(
            DSLContext dsl, InboxService inbox, ConfigurationService configurations,
            WorkflowDefinitionParser parser, TaskSchedulingService tasks,
            WorkOrderCommandService workOrders, OutboxAppender outbox,
            ObjectMapper objectMapper, Clock clock) {
        this.dsl = dsl;
        this.inbox = inbox;
        this.configurations = configurations;
        this.parser = parser;
        this.tasks = tasks;
        this.workOrders = workOrders;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return "workorder.received".equals(eventType) && schemaVersion == 1;
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        validateEnvelope(message);
        InboxDecision decision = inbox.begin(
                message.tenantId(), CONSUMER, message.eventId(), message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }

        WorkOrderReceivedPayload received = readPayload(message.payload());
        if (!received.workOrderId().toString().equals(message.aggregateId())) {
            throw new IllegalArgumentException("WorkOrderReceived aggregateId does not match payload");
        }
        var asset = configurations.requireBundleAsset(
                message.tenantId(), received.bundleRef().bundleId(),
                received.bundleRef().manifestDigest(), ConfigurationAssetType.WORKFLOW);
        var definition = parser.parse(asset);

        Instant now = clock.instant();
        UUID workflowId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID nodeInstanceId = UUID.randomUUID();
        dsl.insertInto(WFL_WORKFLOW_INSTANCE)
                .set(WFL_WORKFLOW_INSTANCE.WORKFLOW_INSTANCE_ID, workflowId)
                .set(WFL_WORKFLOW_INSTANCE.TENANT_ID, message.tenantId())
                .set(WFL_WORKFLOW_INSTANCE.PROJECT_ID, received.projectId())
                .set(WFL_WORKFLOW_INSTANCE.WORK_ORDER_ID, received.workOrderId())
                .set(WFL_WORKFLOW_INSTANCE.CONFIGURATION_BUNDLE_ID, received.bundleRef().bundleId())
                .set(WFL_WORKFLOW_INSTANCE.CONFIGURATION_BUNDLE_DIGEST, received.bundleRef().manifestDigest())
                .set(WFL_WORKFLOW_INSTANCE.WORKFLOW_DEFINITION_VERSION_ID, asset.versionId())
                .set(WFL_WORKFLOW_INSTANCE.WORKFLOW_KEY, definition.workflowKey())
                .set(WFL_WORKFLOW_INSTANCE.WORKFLOW_VERSION, definition.workflowVersion())
                .set(WFL_WORKFLOW_INSTANCE.DEFINITION_DIGEST, asset.contentDigest())
                .set(WFL_WORKFLOW_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_WORKFLOW_INSTANCE.START_EVENT_ID, message.eventId())
                .set(WFL_WORKFLOW_INSTANCE.CORRELATION_ID, message.correlationId())
                .set(WFL_WORKFLOW_INSTANCE.VERSION, 1L)
                .set(WFL_WORKFLOW_INSTANCE.STARTED_AT, now)
                .execute();
        dsl.insertInto(WFL_STAGE_INSTANCE)
                .set(WFL_STAGE_INSTANCE.STAGE_INSTANCE_ID, stageId)
                .set(WFL_STAGE_INSTANCE.TENANT_ID, message.tenantId())
                .set(WFL_STAGE_INSTANCE.WORKFLOW_INSTANCE_ID, workflowId)
                .set(WFL_STAGE_INSTANCE.WORK_ORDER_ID, received.workOrderId())
                .set(WFL_STAGE_INSTANCE.STAGE_CODE, definition.firstStageCode())
                .set(WFL_STAGE_INSTANCE.SEQUENCE_NO, 1)
                .set(WFL_STAGE_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_STAGE_INSTANCE.ACTIVATION_EVENT_ID, message.eventId())
                .set(WFL_STAGE_INSTANCE.VERSION, 1L)
                .set(WFL_STAGE_INSTANCE.ACTIVATED_AT, now)
                .execute();

        ScheduledTaskView firstTask = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                message.tenantId(), received.projectId(), received.workOrderId(), workflowId, stageId,
                nodeInstanceId, definition.firstNodeId(), asset.versionId(), asset.contentDigest(),
                received.bundleRef().bundleId(), received.bundleRef().manifestDigest(),
                definition.firstStageCode(), definition.firstTaskType(), definition.firstTaskKind(),
                definition.firstFormRef(), definition.firstSlaRef(), definition.firstAssigneePolicyRef(),
                definition.firstDispatchPolicyRef(), definition.firstRuleRef(),
                "work-order:" + received.workOrderId(), message.payloadDigest(), 100, now, 3,
                message.correlationId(), message.eventId().toString()));
        dsl.insertInto(WFL_NODE_INSTANCE)
                .set(WFL_NODE_INSTANCE.WORKFLOW_NODE_INSTANCE_ID, nodeInstanceId)
                .set(WFL_NODE_INSTANCE.TENANT_ID, message.tenantId())
                .set(WFL_NODE_INSTANCE.WORKFLOW_INSTANCE_ID, workflowId)
                .set(WFL_NODE_INSTANCE.STAGE_INSTANCE_ID, stageId)
                .set(WFL_NODE_INSTANCE.WORK_ORDER_ID, received.workOrderId())
                .set(WFL_NODE_INSTANCE.NODE_ID, definition.firstNodeId())
                .set(WFL_NODE_INSTANCE.TASK_ID, firstTask.taskId())
                .set(WFL_NODE_INSTANCE.STATUS, "ACTIVE")
                .set(WFL_NODE_INSTANCE.ACTIVATION_EVENT_ID, message.eventId())
                .set(WFL_NODE_INSTANCE.VERSION, 1L)
                .set(WFL_NODE_INSTANCE.ACTIVATED_AT, now)
                .execute();
        workOrders.activate(new ActivateWorkOrderCommand(
                message.tenantId(), received.workOrderId(), message.eventId(), message.correlationId()));

        append(message, "workflow.started", "Workflow", workflowId, 1,
                new WorkflowStartedPayload(
                        workflowId, received.projectId(), received.workOrderId(), received.bundleRef().bundleId(),
                        asset.versionId(), definition.workflowKey(), definition.workflowVersion(),
                        asset.contentDigest(), now), now);
        append(message, "stage.activated", "Stage", stageId, 1,
                new StageActivatedPayload(stageId, workflowId, received.workOrderId(),
                        definition.firstStageCode(), 1, now), now);
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(workflowId + "|" + stageId + "|" + nodeInstanceId));
    }

    private void append(
            OutboxMessage source, String eventType, String aggregateType, UUID aggregateId,
            long aggregateVersion, Object payload, Instant occurredAt) {
        String json = write(payload);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "workflow", eventType, 1,
                aggregateType, aggregateId.toString(), aggregateVersion, source.tenantId(),
                source.correlationId(), source.eventId().toString(), source.partitionKey(),
                json, Sha256.digest(json), occurredAt));
    }

    private WorkOrderReceivedPayload readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, WorkOrderReceivedPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("WorkOrderReceived payload cannot be decoded", exception);
        }
    }

    private String write(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("workflow event serialization failed", exception);
        }
    }

    private static void validateEnvelope(OutboxMessage message) {
        if (!"workorder".equals(message.module()) || !"WorkOrder".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported WorkOrderReceived envelope");
        }
    }
}
