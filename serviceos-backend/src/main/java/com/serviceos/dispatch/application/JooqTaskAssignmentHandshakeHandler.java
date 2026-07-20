package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.CompleteServiceAssignmentAbortCommand;
import com.serviceos.dispatch.api.CompleteServiceAssignmentActivationCommand;
import com.serviceos.dispatch.api.ConfirmTaskAssignmentPreparedCommand;
import com.serviceos.dispatch.api.ServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ServiceAssignmentService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.jooq.generated.tables.DspServiceAssignment;
import com.serviceos.jooq.generated.tables.DspServiceAssignmentActivationSaga;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.Sha256;
import org.jooq.DSLContext;
import org.jooq.Record7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.DspServiceAssignment.DSP_SERVICE_ASSIGNMENT;
import static com.serviceos.jooq.generated.tables.DspServiceAssignmentActivationSaga.DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA;

/**
 * M25 Dispatch 侧 TaskAssignment Inbox 编排器。
 *
 * <p>M26 将 Task prepared 固化为独立检查点，再由 ServiceAssignmentTaskPrepared 事件推进责任切换；
 * Task activated 完成正常 saga，Task aborted 则完成切换前终止 saga。</p>
 */
@Service
final class JooqTaskAssignmentHandshakeHandler implements OutboxMessageHandler {
    private static final String PREPARED_CONSUMER = "dispatch.task-assignment-prepared.v1";
    private static final String ACTIVATED_CONSUMER = "dispatch.task-assignment-activated.v1";
    private static final String ABORTED_CONSUMER = "dispatch.task-assignment-aborted.v1";

    private final DSLContext dsl;
    private final InboxService inbox;
    private final ServiceAssignmentService assignments;
    private final ObjectMapper objectMapper;

    JooqTaskAssignmentHandshakeHandler(
            DSLContext dsl,
            InboxService inbox,
            ServiceAssignmentService assignments,
            ObjectMapper objectMapper
    ) {
        this.dsl = dsl;
        this.inbox = inbox;
        this.assignments = assignments;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return schemaVersion == 1 && (
                "task.assignment-prepared".equals(eventType)
                        || "task.assignment-activated".equals(eventType)
                        || "task.assignment-aborted".equals(eventType));
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
            recordPrepared(message, payload, state);
        } else if ("task.assignment-activated".equals(message.eventType())) {
            complete(message, payload, state);
        } else {
            completeAbort(message, payload, state);
        }
    }

    private void recordPrepared(
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
        ServiceAssignmentReceipt prepared = assignments.confirmTaskPrepared(
                principal(message, state.createdBy()), metadata(message, "confirm"),
                new ConfirmTaskAssignmentPreparedCommand(
                        state.sagaId(), state.serviceAssignmentId(), state.taskId(),
                        payload.guardId(), payload.taskAssignmentId(), 1));
        inbox.complete(message.tenantId(), PREPARED_CONSUMER, message.eventId(), digest(prepared));
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

    private void completeAbort(
            OutboxMessage message,
            TaskAssignmentPayload payload,
            OrchestrationState state
    ) {
        InboxDecision decision = inbox.begin(
                message.tenantId(), ABORTED_CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) return;
        if (!"ABORTED".equals(payload.status())
                || !"ABORTING".equals(state.stage()) || state.sagaVersion() != 3
                || !payload.taskAssignmentId().equals(state.preparedTaskAssignmentId())) {
            throw new IllegalArgumentException("TaskAssignmentAborted does not match aborting v2 saga");
        }
        ServiceAssignmentReceipt completed = assignments.completeAbort(
                principal(message, state.createdBy()), metadata(message, "complete-abort"),
                new CompleteServiceAssignmentAbortCommand(
                        state.sagaId(), state.serviceAssignmentId(),
                        payload.taskAssignmentId(), state.sagaVersion()));
        inbox.complete(message.tenantId(), ABORTED_CONSUMER, message.eventId(), digest(completed));
    }

    private OrchestrationState state(String tenantId, UUID serviceAssignmentId) {
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT.as("assignment");
        DspServiceAssignmentActivationSaga saga = DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA.as("saga");
        return dsl.select(
                        assignment.SERVICE_ASSIGNMENT_ID,
                        assignment.ACTIVATION_SAGA_ID,
                        assignment.TASK_ID,
                        assignment.CREATED_BY,
                        assignment.PREPARED_TASK_ASSIGNMENT_ID,
                        saga.STAGE,
                        saga.VERSION)
                .from(assignment)
                .join(saga)
                .on(saga.ACTIVATION_SAGA_ID.eq(assignment.ACTIVATION_SAGA_ID))
                .and(saga.TENANT_ID.eq(assignment.TENANT_ID))
                .where(assignment.TENANT_ID.eq(tenantId))
                .and(assignment.SERVICE_ASSIGNMENT_ID.eq(serviceAssignmentId))
                .and(assignment.ACTIVATION_PROTOCOL_VERSION.eq(2))
                .fetchSingle(JooqTaskAssignmentHandshakeHandler::mapState);
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

    private static OrchestrationState mapState(
            Record7<UUID, UUID, UUID, String, UUID, String, Long> row) {
        return new OrchestrationState(
                row.value1(), row.value2(), row.value3(), row.value4(),
                row.value5(), row.value6(), row.value7());
    }

    private record OrchestrationState(
            UUID serviceAssignmentId,
            UUID sagaId,
            UUID taskId,
            String createdBy,
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
