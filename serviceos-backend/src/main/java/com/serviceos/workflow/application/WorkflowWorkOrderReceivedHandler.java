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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** WorkOrderReceived v1 的可靠本地消费者，拥有 M17 流程启动原子事务。 */
@Service
final class WorkflowWorkOrderReceivedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "workflow.work-order-received.v1";

    private final JdbcClient jdbc;
    private final InboxService inbox;
    private final ConfigurationService configurations;
    private final WorkflowDefinitionParser parser;
    private final TaskSchedulingService tasks;
    private final WorkOrderCommandService workOrders;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    WorkflowWorkOrderReceivedHandler(
            JdbcClient jdbc, InboxService inbox, ConfigurationService configurations,
            WorkflowDefinitionParser parser, TaskSchedulingService tasks,
            WorkOrderCommandService workOrders, OutboxAppender outbox,
            ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
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
        jdbc.sql("""
                INSERT INTO wfl_workflow_instance (
                    workflow_instance_id, tenant_id, project_id, work_order_id,
                    configuration_bundle_id, configuration_bundle_digest,
                    workflow_definition_version_id,
                    workflow_key, workflow_version, definition_digest, status,
                    start_event_id, correlation_id, version, started_at
                ) VALUES (
                    :workflowId, :tenantId, :projectId, :workOrderId,
                    :bundleId, :bundleDigest, :definitionVersionId, :workflowKey, :workflowVersion,
                    :definitionDigest, 'ACTIVE', :startEventId, :correlationId, 1, :startedAt
                )
                """)
                .param("workflowId", workflowId)
                .param("tenantId", message.tenantId())
                .param("projectId", received.projectId())
                .param("workOrderId", received.workOrderId())
                .param("bundleId", received.bundleRef().bundleId())
                .param("bundleDigest", received.bundleRef().manifestDigest())
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
                        "stageId", stageId, "tenantId", message.tenantId(), "workflowId", workflowId,
                        "workOrderId", received.workOrderId(), "stageCode", definition.firstStageCode(),
                        "activationEventId", message.eventId(), "activatedAt", java.sql.Timestamp.from(now)))
                .update();

        ScheduledTaskView firstTask = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                message.tenantId(), received.projectId(), received.workOrderId(), workflowId, stageId,
                nodeInstanceId, definition.firstNodeId(), asset.versionId(), asset.contentDigest(),
                received.bundleRef().bundleId(), received.bundleRef().manifestDigest(),
                definition.firstStageCode(), definition.firstTaskType(), definition.firstTaskKind(),
                definition.firstFormRef(), definition.firstSlaRef(), definition.firstAssigneePolicyRef(),
                definition.firstDispatchPolicyRef(),
                "work-order:" + received.workOrderId(), message.payloadDigest(), 100, now, 3,
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
                .param("workOrderId", received.workOrderId())
                .param("nodeId", definition.firstNodeId())
                .param("taskId", firstTask.taskId())
                .param("activationEventId", message.eventId())
                .param("activatedAt", java.sql.Timestamp.from(now))
                .update();
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
