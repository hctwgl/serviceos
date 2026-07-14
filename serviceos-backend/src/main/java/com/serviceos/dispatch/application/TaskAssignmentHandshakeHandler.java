package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.ActivateServiceAssignmentCommand;
import com.serviceos.dispatch.api.CompleteServiceAssignmentActivationCommand;
import com.serviceos.dispatch.api.ConfirmTaskAssignmentPreparedCommand;
import com.serviceos.dispatch.api.ServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ServiceAssignmentService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.Sha256;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * M25 Dispatch 侧 TaskAssignment Inbox 编排器。
 *
 * <p>Task prepared 后在同一消费者事务内记录握手证明并切换服务责任；Task activated 后完成 saga。
 * 任一 Outbox/审计/状态写入失败都会连同 Inbox begin 一起回滚，消息可按同一 eventId 向前重试。</p>
 */
@Service
final class TaskAssignmentHandshakeHandler implements OutboxMessageHandler {
    private static final String PREPARED_CONSUMER = "dispatch.task-assignment-prepared.v1";
    private static final String ACTIVATED_CONSUMER = "dispatch.task-assignment-activated.v1";

    private final JdbcClient jdbc;
    private final InboxService inbox;
    private final ServiceAssignmentService assignments;
    private final ObjectMapper objectMapper;

    TaskAssignmentHandshakeHandler(
            JdbcClient jdbc,
            InboxService inbox,
            ServiceAssignmentService assignments,
            ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.inbox = inbox;
        this.assignments = assignments;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return schemaVersion == 1 && (
                "task.assignment-prepared".equals(eventType)
                        || "task.assignment-activated".equals(eventType));
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        validateEnvelope(message);
        TaskAssignmentPayload payload = read(message.payload());
        if (!payload.taskId().toString().equals(message.aggregateId())) {
            throw new IllegalArgumentException("TaskAssignment aggregate identity mismatch");
        }
        UUID serviceAssignmentId;
        try {
            serviceAssignmentId = UUID.fromString(payload.serviceAssignmentId());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("M25 requires a UUID ServiceAssignment id", exception);
        }
        OrchestrationState state = state(message.tenantId(), serviceAssignmentId);
        validateLink(payload, state);
        if ("task.assignment-prepared".equals(message.eventType())) {
            switchService(message, payload, state);
        } else {
            complete(message, payload, state);
        }
    }

    private void switchService(
            OutboxMessage message,
            TaskAssignmentPayload payload,
            OrchestrationState state
    ) {
        InboxDecision decision = inbox.begin(
                message.tenantId(), PREPARED_CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) return;
        if (!"PREPARED".equals(payload.status())
                || !"PENDING".equals(state.stage()) || state.sagaVersion() != 1) {
            throw new IllegalArgumentException("TaskAssignmentPrepared does not match a pending v2 saga");
        }
        CurrentPrincipal principal = principal(message, state.createdBy());
        ServiceAssignmentReceipt prepared = assignments.confirmTaskPrepared(
                principal, metadata(message, "confirm"),
                new ConfirmTaskAssignmentPreparedCommand(
                        state.sagaId(), state.serviceAssignmentId(), state.taskId(),
                        payload.guardId(), payload.taskAssignmentId(), 1));
        ServiceAssignmentReceipt activated = assignments.activate(
                principal, metadata(message, "activate"),
                new ActivateServiceAssignmentCommand(
                        state.sagaId(), state.serviceAssignmentId(), prepared.sagaVersion(),
                        state.authorityAssignmentId(), state.authorityVersion(),
                        state.fenceDecisionId(), state.fencePolicyVersion()));
        inbox.complete(message.tenantId(), PREPARED_CONSUMER, message.eventId(), digest(activated));
    }

    private void complete(
            OutboxMessage message,
            TaskAssignmentPayload payload,
            OrchestrationState state
    ) {
        InboxDecision decision = inbox.begin(
                message.tenantId(), ACTIVATED_CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) return;
        if (!"ACTIVE".equals(payload.status())
                || !"SERVICE_SWITCHED".equals(state.stage()) || state.sagaVersion() != 3
                || !payload.taskAssignmentId().equals(state.preparedTaskAssignmentId())) {
            throw new IllegalArgumentException("TaskAssignmentActivated does not match switched v2 saga");
        }
        ServiceAssignmentReceipt completed = assignments.complete(
                principal(message, state.createdBy()), metadata(message, "complete"),
                new CompleteServiceAssignmentActivationCommand(
                        state.sagaId(), state.serviceAssignmentId(),
                        payload.taskAssignmentId(), state.sagaVersion()));
        inbox.complete(message.tenantId(), ACTIVATED_CONSUMER, message.eventId(), digest(completed));
    }

    private OrchestrationState state(String tenantId, UUID serviceAssignmentId) {
        return jdbc.sql("""
                        SELECT assignment.service_assignment_id,
                               assignment.activation_saga_id AS saga_id,
                               assignment.task_id, assignment.created_by,
                               assignment.pending_authority_assignment_id AS authority_assignment_id,
                               assignment.pending_authority_version AS authority_version,
                               assignment.pending_fence_decision_id AS fence_decision_id,
                               assignment.pending_fence_policy_version AS fence_policy_version,
                               assignment.prepared_task_assignment_id,
                               saga.stage, saga.version AS saga_version
                          FROM dsp_service_assignment assignment
                          JOIN dsp_service_assignment_activation_saga saga
                            ON saga.activation_saga_id = assignment.activation_saga_id
                           AND saga.tenant_id = assignment.tenant_id
                         WHERE assignment.tenant_id = :tenantId
                           AND assignment.service_assignment_id = :assignmentId
                           AND assignment.activation_protocol_version = 2
                        """)
                .param("tenantId", tenantId).param("assignmentId", serviceAssignmentId)
                .query(OrchestrationState.class).single();
    }

    private static void validateLink(TaskAssignmentPayload payload, OrchestrationState state) {
        if (!payload.taskId().equals(state.taskId())
                || !payload.preparationKey().equals(state.sagaId().toString())) {
            throw new IllegalArgumentException("TaskAssignment handshake link does not match Dispatch saga");
        }
    }

    private TaskAssignmentPayload read(String payload) {
        try {
            return objectMapper.readValue(payload, TaskAssignmentPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("TaskAssignment handshake payload cannot be decoded", exception);
        }
    }

    private static void validateEnvelope(OutboxMessage message) {
        if (!"task".equals(message.module()) || !"Task".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported TaskAssignment handshake envelope");
        }
    }

    private static CurrentPrincipal principal(OutboxMessage message, String initiatedBy) {
        return new CurrentPrincipal(initiatedBy, message.tenantId(),
                CurrentPrincipal.PrincipalType.USER, "task-inbox",
                Set.of("dispatch.assignment.manage"));
    }

    private static CommandMetadata metadata(OutboxMessage message, String operation) {
        return new CommandMetadata(message.correlationId(),
                "inbox-" + operation + "-" + message.eventId());
    }

    private static String digest(ServiceAssignmentReceipt receipt) {
        return Sha256.digest(receipt.serviceAssignmentId() + "|" + receipt.sagaId() + "|"
                + receipt.assignmentStatus() + "|" + receipt.sagaStage()
                + "|" + receipt.sagaVersion());
    }

    private record OrchestrationState(
            UUID serviceAssignmentId,
            UUID sagaId,
            UUID taskId,
            String createdBy,
            String authorityAssignmentId,
            long authorityVersion,
            String fenceDecisionId,
            String fencePolicyVersion,
            UUID preparedTaskAssignmentId,
            String stage,
            long sagaVersion
    ) {
    }

    private record TaskAssignmentPayload(
            UUID taskId,
            UUID guardId,
            UUID taskAssignmentId,
            String preparationKey,
            String principalId,
            String status,
            String serviceAssignmentId,
            String reasonCode,
            Instant occurredAt
    ) {
    }
}
