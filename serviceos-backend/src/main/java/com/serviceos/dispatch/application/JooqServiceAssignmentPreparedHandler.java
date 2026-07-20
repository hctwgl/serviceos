package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.ActivateServiceAssignmentCommand;
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
import org.jooq.Record12;
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
 * M26 Dispatch 侧 TASK_PREPARED 检查点推进器。
 *
 * <p>confirm 与 activate 分属两个 Outbox/Inbox 事务，使激活技术失败后仍保留 Task 返回的 guard 与
 * PREPARED 责任引用。若受控终止已经把 saga 推进到 ABORTING/ABORTED，本处理器把较早的检查点事件
 * 视为已被后续事实取代并安全确认，绝不重新激活已失败的 ServiceAssignment。</p>
 */
@Service
final class JooqServiceAssignmentPreparedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "dispatch.service-assignment-task-prepared.v2";

    private final DSLContext dsl;
    private final InboxService inbox;
    private final ServiceAssignmentService assignments;
    private final ObjectMapper objectMapper;

    JooqServiceAssignmentPreparedHandler(
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
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT.as("assignment");
        DspServiceAssignmentActivationSaga saga = DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA.as("saga");
        // pending_authority_* / pending_fence_* 即激活命令所需的 authority/fence 参数；
        // 与原查询别名 authority_assignment_id 等一一对应。
        return dsl.select(
                        assignment.SERVICE_ASSIGNMENT_ID,
                        assignment.ACTIVATION_SAGA_ID,
                        assignment.TASK_ID,
                        assignment.CREATED_BY,
                        assignment.PENDING_AUTHORITY_ASSIGNMENT_ID,
                        assignment.PENDING_AUTHORITY_VERSION,
                        assignment.PENDING_FENCE_DECISION_ID,
                        assignment.PENDING_FENCE_POLICY_VERSION,
                        assignment.TASK_EXECUTION_GUARD_ID,
                        assignment.PREPARED_TASK_ASSIGNMENT_ID,
                        saga.STAGE,
                        saga.VERSION)
                .from(assignment)
                .join(saga)
                .on(saga.ACTIVATION_SAGA_ID.eq(assignment.ACTIVATION_SAGA_ID))
                .and(saga.TENANT_ID.eq(assignment.TENANT_ID))
                .where(assignment.TENANT_ID.eq(tenantId))
                .and(assignment.SERVICE_ASSIGNMENT_ID.eq(assignmentId))
                .and(assignment.ACTIVATION_PROTOCOL_VERSION.eq(2))
                .fetchSingle(JooqServiceAssignmentPreparedHandler::mapState);
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

    private static ActivationState mapState(Record12<
            UUID, UUID, UUID, String, String, Long, String, String, UUID, UUID, String, Long> row) {
        return new ActivationState(
                row.value1(), row.value2(), row.value3(), row.value4(),
                row.value5(), row.value6() == null ? 0L : row.value6(),
                row.value7(), row.value8(), row.value9(), row.value10(),
                row.value11(), row.value12());
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
