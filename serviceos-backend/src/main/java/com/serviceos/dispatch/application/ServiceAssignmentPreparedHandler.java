package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.ActivateServiceAssignmentCommand;
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
 * M26 Dispatch 侧 TASK_PREPARED 检查点推进器。
 *
 * <p>confirm 与 activate 分属两个 Outbox/Inbox 事务，使激活技术失败后仍保留 Task 返回的 guard 与
 * PREPARED 责任引用。若受控终止已经把 saga 推进到 ABORTING/ABORTED，本处理器把较早的检查点事件
 * 视为已被后续事实取代并安全确认，绝不重新激活已失败的 ServiceAssignment。</p>
 */
@Service
final class ServiceAssignmentPreparedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "dispatch.service-assignment-task-prepared.v2";

    private final JdbcClient jdbc;
    private final InboxService inbox;
    private final ServiceAssignmentService assignments;
    private final ObjectMapper objectMapper;

    ServiceAssignmentPreparedHandler(
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
        return schemaVersion == 2 && "service.assignment.task-prepared".equals(eventType);
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        validateEnvelope(message);
        HandshakePayload payload = read(message.payload());
        if (payload.protocolVersion() != 2
                || !payload.serviceAssignmentId().toString().equals(message.aggregateId())
                || !"PENDING_ACTIVATION".equals(payload.status())
                || payload.guardId() == null || payload.preparedTaskAssignmentId() == null) {
            throw new IllegalArgumentException("ServiceAssignmentTaskPrepared payload is invalid");
        }
        ActivationState state = state(message.tenantId(), payload.serviceAssignmentId());
        if (!state.sagaId().equals(payload.sagaId()) || !state.taskId().equals(payload.taskId())
                || !state.guardId().equals(payload.guardId())
                || !state.preparedTaskAssignmentId().equals(payload.preparedTaskAssignmentId())
                || !state.createdBy().equals(payload.initiatedBy())) {
            throw new IllegalArgumentException("ServiceAssignmentTaskPrepared link mismatch");
        }

        InboxDecision decision = inbox.begin(
                message.tenantId(), CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) return;

        if ("ABORTING".equals(state.stage()) || "ABORTED".equals(state.stage())) {
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest("SUPERSEDED_BY_ABORT|" + state.stage() + "|" + state.sagaVersion()));
            return;
        }
        if (!"TASK_PREPARED".equals(state.stage()) || state.sagaVersion() != 2) {
            throw new IllegalArgumentException("ServiceAssignmentTaskPrepared requires TASK_PREPARED:2");
        }

        ServiceAssignmentReceipt activated = assignments.activate(
                principal(message, state.createdBy()), metadata(message),
                new ActivateServiceAssignmentCommand(
                        state.sagaId(), state.serviceAssignmentId(), state.sagaVersion(),
                        state.authorityAssignmentId(), state.authorityVersion(),
                        state.fenceDecisionId(), state.fencePolicyVersion()));
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(), digest(activated));
    }

    private ActivationState state(String tenantId, UUID assignmentId) {
        return jdbc.sql("""
                        SELECT assignment.service_assignment_id,
                               assignment.activation_saga_id AS saga_id,
                               assignment.task_id, assignment.created_by,
                               assignment.pending_authority_assignment_id AS authority_assignment_id,
                               assignment.pending_authority_version AS authority_version,
                               assignment.pending_fence_decision_id AS fence_decision_id,
                               assignment.pending_fence_policy_version AS fence_policy_version,
                               assignment.task_execution_guard_id AS guard_id,
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
                .param("tenantId", tenantId).param("assignmentId", assignmentId)
                .query(ActivationState.class).single();
    }

    private HandshakePayload read(String payload) {
        try {
            return objectMapper.readValue(payload, HandshakePayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("ServiceAssignmentTaskPrepared payload cannot be decoded", exception);
        }
    }

    private static void validateEnvelope(OutboxMessage message) {
        if (!"dispatch".equals(message.module()) || !"ServiceAssignment".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported ServiceAssignmentTaskPrepared envelope");
        }
    }

    private static CurrentPrincipal principal(OutboxMessage message, String initiatedBy) {
        return new CurrentPrincipal(initiatedBy, message.tenantId(),
                CurrentPrincipal.PrincipalType.USER, "dispatch-checkpoint-inbox",
                Set.of("dispatch.assignment.manage"));
    }

    private static CommandMetadata metadata(OutboxMessage message) {
        return new CommandMetadata(message.correlationId(), "inbox-activate-" + message.eventId());
    }

    private static String digest(ServiceAssignmentReceipt receipt) {
        return Sha256.digest(receipt.serviceAssignmentId() + "|" + receipt.sagaId() + "|"
                + receipt.assignmentStatus() + "|" + receipt.sagaStage() + "|" + receipt.sagaVersion());
    }

    private record ActivationState(
            UUID serviceAssignmentId,
            UUID sagaId,
            UUID taskId,
            String createdBy,
            String authorityAssignmentId,
            long authorityVersion,
            String fenceDecisionId,
            String fencePolicyVersion,
            UUID guardId,
            UUID preparedTaskAssignmentId,
            String stage,
            long sagaVersion
    ) {
    }

    private record HandshakePayload(
            UUID serviceAssignmentId,
            UUID sagaId,
            UUID workOrderId,
            UUID taskId,
            String responsibilityLevel,
            String assigneeId,
            String businessType,
            String status,
            UUID supersedesServiceAssignmentId,
            UUID capacityReservationId,
            UUID guardId,
            UUID preparedTaskAssignmentId,
            String reasonCode,
            String initiatedBy,
            int protocolVersion,
            Instant occurredAt
    ) {
    }
}
