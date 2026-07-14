package com.serviceos.dispatch.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.AbortServiceAssignmentActivationCommand;
import com.serviceos.dispatch.api.ActivateServiceAssignmentCommand;
import com.serviceos.dispatch.api.CompleteServiceAssignmentActivationCommand;
import com.serviceos.dispatch.api.ConfirmTaskAssignmentPreparedCommand;
import com.serviceos.dispatch.api.PrepareServiceAssignmentCommand;
import com.serviceos.dispatch.api.ServiceAssignmentChangedPayload;
import com.serviceos.dispatch.api.ServiceAssignmentHandshakePayload;
import com.serviceos.dispatch.api.ServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ServiceAssignmentService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * Dispatch 侧 ServiceAssignment/CapacityReservation 权威事务。
 *
 * <p>HELD 与 CONFIRMED 均占用 capacity counter；只有 abort、结束旧责任或受控补偿才释放。
 * Task 模块的 guard/assignment 引用只作为握手证明保存，本模块不跨边界写 Task 表。</p>
 */
@Service
final class DefaultServiceAssignmentService implements ServiceAssignmentService {
    private static final String PREPARE = "dispatch.assignment.prepare";
    private static final String CONFIRM_TASK = "dispatch.assignment.confirm-task-prepared";
    private static final String ACTIVATE = "dispatch.assignment.activate";
    private static final String ABORT = "dispatch.assignment.abort";
    private static final String COMPLETE = "dispatch.assignment.complete";
    private static final String CAPABILITY = "dispatch.assignment.manage";

    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultServiceAssignmentService(
            JdbcClient jdbc,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ServiceAssignmentReceipt prepare(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            PrepareServiceAssignmentCommand command
    ) {
        CommandContext context = context(principal, metadata);
        String digest = Sha256.digest(command.sagaId() + "|" + command.workOrderId() + "|"
                + command.taskId() + "|" + command.responsibilityLevel() + "|"
                + command.assigneeId() + "|" + command.businessType() + "|"
                + command.sourceDecisionId() + "|" + command.supersedesServiceAssignmentId()
                + "|" + command.reasonCode() + "|" + command.expectedCapacityVersion()
                + "|" + command.authorityAssignmentId() + "|" + command.authorityVersion()
                + "|" + command.fenceDecisionId() + "|" + command.fencePolicyVersion());
        AuthorizationDecision authorizationDecision = authorize(principal, context, command.taskId());
        IdempotencyDecision decision = idempotency.begin(context, PREPARE, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return frozenReceipt(context, PREPARE);
        }

        validateOldAssignment(context.tenantId(), command);
        Instant now = clock.instant();
        CapacityCounter counter = reserveCapacity(context, command, now);
        UUID assignmentId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO dsp_service_assignment (
                            service_assignment_id, tenant_id, work_order_id, task_id,
                            responsibility_level, assignee_id, business_type, source_decision_id,
                            status, activation_saga_id, supersedes_service_assignment_id,
                            reassignment_reason_code, created_by, created_at,
                            activation_protocol_version, pending_authority_assignment_id,
                            pending_authority_version, pending_fence_decision_id,
                            pending_fence_policy_version
                        ) VALUES (
                            :assignmentId, :tenantId, :workOrderId, :taskId,
                            :level, :assigneeId, :businessType, :decisionId,
                            'PENDING_ACTIVATION', :sagaId, :supersedesId,
                            :reasonCode, :actorId, :now,
                            :protocolVersion, :authorityAssignmentId,
                            :authorityVersion, :fenceDecisionId, :fencePolicyVersion
                        )
                        """)
                .param("assignmentId", assignmentId).param("tenantId", context.tenantId())
                .param("workOrderId", command.workOrderId()).param("taskId", command.taskId())
                .param("level", command.responsibilityLevel().name())
                .param("assigneeId", command.assigneeId()).param("businessType", command.businessType())
                .param("decisionId", command.sourceDecisionId()).param("sagaId", command.sagaId())
                .param("supersedesId", command.supersedesServiceAssignmentId())
                .param("reasonCode", command.reasonCode()).param("actorId", context.actorId())
                .param("protocolVersion", command.usesReliableReassignmentProtocol() ? 2 : 1)
                .param("authorityAssignmentId", command.authorityAssignmentId())
                .param("authorityVersion", command.authorityVersion() == 0 ? null : command.authorityVersion())
                .param("fenceDecisionId", command.fenceDecisionId())
                .param("fencePolicyVersion", command.fencePolicyVersion())
                .param("now", timestamptz(now)).update();
        jdbc.sql("""
                        INSERT INTO dsp_capacity_reservation (
                            capacity_reservation_id, tenant_id, service_assignment_id,
                            capacity_counter_id, units, status, held_at
                        ) VALUES (
                            :reservationId, :tenantId, :assignmentId,
                            :counterId, 1, 'HELD', :now
                        )
                        """)
                .param("reservationId", reservationId).param("tenantId", context.tenantId())
                .param("assignmentId", assignmentId).param("counterId", counter.capacityCounterId())
                .param("now", timestamptz(now)).update();
        jdbc.sql("""
                        INSERT INTO dsp_service_assignment_activation_saga (
                            activation_saga_id, tenant_id, task_id, new_service_assignment_id,
                            old_service_assignment_id, stage, version, started_at, updated_at
                        ) VALUES (
                            :sagaId, :tenantId, :taskId, :assignmentId,
                            :oldAssignmentId, 'PENDING', 1, :now, :now
                        )
                        """)
                .param("sagaId", command.sagaId()).param("tenantId", context.tenantId())
                .param("taskId", command.taskId()).param("assignmentId", assignmentId)
                .param("oldAssignmentId", command.supersedesServiceAssignmentId())
                .param("now", timestamptz(now)).update();

        ServiceAssignmentReceipt receipt = new ServiceAssignmentReceipt(
                assignmentId, command.sagaId(), command.taskId(), reservationId,
                "PENDING_ACTIVATION", "PENDING", 1, now);
        finish(context, authorizationDecision, PREPARE, "SERVICE_ASSIGNMENT_PREPARE",
                digest, receipt, "service.assignment.pending-activation", command.reasonCode());
        return receipt;
    }

    @Override
    @Transactional
    public ServiceAssignmentReceipt confirmTaskPrepared(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ConfirmTaskAssignmentPreparedCommand command
    ) {
        CommandContext context = context(principal, metadata);
        String digest = Sha256.digest(command.sagaId() + "|" + command.serviceAssignmentId()
                + "|" + command.taskId() + "|" + command.guardId() + "|"
                + command.preparedTaskAssignmentId() + "|" + command.expectedSagaVersion());
        AuthorizationDecision authorizationDecision = authorize(principal, context, command.taskId());
        IdempotencyDecision decision = idempotency.begin(context, CONFIRM_TASK, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return frozenReceipt(context, CONFIRM_TASK);
        }

        Instant now = clock.instant();
        int sagaUpdated = jdbc.sql("""
                        UPDATE dsp_service_assignment_activation_saga
                           SET stage = 'TASK_PREPARED', version = version + 1,
                               prepared_task_assignment_id = :preparedId,
                               task_execution_guard_id = :guardId, updated_at = :now
                         WHERE tenant_id = :tenantId AND activation_saga_id = :sagaId
                           AND new_service_assignment_id = :assignmentId AND task_id = :taskId
                           AND stage = 'PENDING' AND version = :expectedVersion
                        """)
                .param("preparedId", command.preparedTaskAssignmentId())
                .param("guardId", command.guardId()).param("now", timestamptz(now))
                .param("tenantId", context.tenantId()).param("sagaId", command.sagaId())
                .param("assignmentId", command.serviceAssignmentId()).param("taskId", command.taskId())
                .param("expectedVersion", command.expectedSagaVersion()).update();
        if (sagaUpdated != 1) {
            throwSagaConflict(context.tenantId(), command.sagaId(), command.serviceAssignmentId(),
                    command.expectedSagaVersion(), "PENDING");
        }
        int assignmentUpdated = jdbc.sql("""
                        UPDATE dsp_service_assignment
                           SET prepared_task_assignment_id = :preparedId,
                               task_execution_guard_id = :guardId
                         WHERE tenant_id = :tenantId AND service_assignment_id = :assignmentId
                           AND task_id = :taskId AND status = 'PENDING_ACTIVATION'
                        """)
                .param("preparedId", command.preparedTaskAssignmentId())
                .param("guardId", command.guardId()).param("tenantId", context.tenantId())
                .param("assignmentId", command.serviceAssignmentId()).param("taskId", command.taskId())
                .update();
        if (assignmentUpdated != 1) {
            throw new BusinessProblem(
                    ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "PENDING ServiceAssignment could not record TaskAssignmentPrepared");
        }

        AssignmentState state = assignment(context.tenantId(), command.serviceAssignmentId());
        ServiceAssignmentReceipt receipt = receipt(state, "TASK_PREPARED", 2, now);
        finish(context, authorizationDecision, CONFIRM_TASK, "SERVICE_ASSIGNMENT_TASK_PREPARED",
                digest, receipt, "service.assignment.task-prepared", "TASK_ASSIGNMENT_PREPARED");
        return receipt;
    }

    @Override
    @Transactional
    public ServiceAssignmentReceipt activate(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ActivateServiceAssignmentCommand command
    ) {
        CommandContext context = context(principal, metadata);
        AssignmentState state = assignment(context.tenantId(), command.serviceAssignmentId());
        String digest = Sha256.digest(command.sagaId() + "|" + command.serviceAssignmentId()
                + "|" + command.expectedSagaVersion() + "|" + command.authorityAssignmentId()
                + "|" + command.authorityVersion() + "|" + command.fenceDecisionId()
                + "|" + command.fencePolicyVersion());
        AuthorizationDecision authorizationDecision = authorize(principal, context, state.taskId());
        IdempotencyDecision decision = idempotency.begin(context, ACTIVATE, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return frozenReceipt(context, ACTIVATE);
        }
        if (state.preparedTaskAssignmentId() == null || state.guardId() == null) {
            throw new BusinessProblem(
                    ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "ServiceAssignment cannot activate before TaskAssignmentPrepared");
        }

        Instant now = clock.instant();
        int sagaUpdated = jdbc.sql("""
                        UPDATE dsp_service_assignment_activation_saga
                           SET stage = 'SERVICE_SWITCHED', version = version + 1, updated_at = :now
                         WHERE tenant_id = :tenantId AND activation_saga_id = :sagaId
                           AND new_service_assignment_id = :assignmentId
                           AND stage = 'TASK_PREPARED' AND version = :expectedVersion
                        """)
                .param("now", timestamptz(now)).param("tenantId", context.tenantId())
                .param("sagaId", command.sagaId()).param("assignmentId", command.serviceAssignmentId())
                .param("expectedVersion", command.expectedSagaVersion()).update();
        if (sagaUpdated != 1) {
            throwSagaConflict(context.tenantId(), command.sagaId(), command.serviceAssignmentId(),
                    command.expectedSagaVersion(), "TASK_PREPARED");
        }

        if (state.supersedesServiceAssignmentId() != null) {
            endOldAssignmentAndReleaseCapacity(context, state, now);
        } else if (activeAssignmentExists(
                context.tenantId(), state.taskId(), state.responsibilityLevel())) {
            throw new BusinessProblem(
                    ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "Initial activation found an existing ACTIVE ServiceAssignment");
        }
        int assignmentUpdated = jdbc.sql("""
                        UPDATE dsp_service_assignment
                           SET status = 'ACTIVE', effective_from = :now,
                               authority_assignment_id = :authorityAssignmentId,
                               authority_version = :authorityVersion,
                               fence_decision_id = :fenceDecisionId,
                               fence_policy_version = :fencePolicyVersion
                         WHERE tenant_id = :tenantId AND service_assignment_id = :assignmentId
                           AND activation_saga_id = :sagaId AND status = 'PENDING_ACTIVATION'
                        """)
                .param("now", timestamptz(now))
                .param("authorityAssignmentId", command.authorityAssignmentId())
                .param("authorityVersion", command.authorityVersion())
                .param("fenceDecisionId", command.fenceDecisionId())
                .param("fencePolicyVersion", command.fencePolicyVersion())
                .param("tenantId", context.tenantId()).param("assignmentId", command.serviceAssignmentId())
                .param("sagaId", command.sagaId()).update();
        if (assignmentUpdated != 1) {
            throw new BusinessProblem(
                    ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "PENDING ServiceAssignment could not be activated");
        }
        int reservationUpdated = jdbc.sql("""
                        UPDATE dsp_capacity_reservation
                           SET status = 'CONFIRMED', confirmed_at = :now
                         WHERE tenant_id = :tenantId AND service_assignment_id = :assignmentId
                           AND status = 'HELD'
                        """)
                .param("now", timestamptz(now)).param("tenantId", context.tenantId())
                .param("assignmentId", command.serviceAssignmentId()).update();
        if (reservationUpdated != 1) {
            throw new BusinessProblem(
                    ProblemCode.DISPATCH_CAPACITY_CONFLICT,
                    "HELD capacity reservation could not be confirmed");
        }

        ServiceAssignmentReceipt receipt = new ServiceAssignmentReceipt(
                state.serviceAssignmentId(), command.sagaId(), state.taskId(),
                state.capacityReservationId(), "ACTIVE", "SERVICE_SWITCHED",
                command.expectedSagaVersion() + 1, now);
        finish(context, authorizationDecision, ACTIVATE, "SERVICE_ASSIGNMENT_ACTIVATE",
                digest, receipt, "service.assignment.activated", "TASK_ASSIGNMENT_PREPARED");
        return receipt;
    }

    @Override
    @Transactional
    public ServiceAssignmentReceipt abort(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            AbortServiceAssignmentActivationCommand command
    ) {
        CommandContext context = context(principal, metadata);
        AssignmentState state = assignment(context.tenantId(), command.serviceAssignmentId());
        String digest = Sha256.digest(command.sagaId() + "|" + command.serviceAssignmentId()
                + "|" + command.expectedSagaVersion() + "|" + command.reasonCode());
        AuthorizationDecision authorizationDecision = authorize(principal, context, state.taskId());
        IdempotencyDecision decision = idempotency.begin(context, ABORT, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return frozenReceipt(context, ABORT);
        }

        Instant now = clock.instant();
        int sagaUpdated = jdbc.sql("""
                        UPDATE dsp_service_assignment_activation_saga
                           SET stage = 'ABORTED', version = version + 1,
                               last_error_code = :reasonCode, updated_at = :now
                         WHERE tenant_id = :tenantId AND activation_saga_id = :sagaId
                           AND new_service_assignment_id = :assignmentId
                           AND stage IN ('PENDING', 'TASK_PREPARED')
                           AND version = :expectedVersion
                        """)
                .param("reasonCode", command.reasonCode()).param("now", timestamptz(now))
                .param("tenantId", context.tenantId()).param("sagaId", command.sagaId())
                .param("assignmentId", command.serviceAssignmentId())
                .param("expectedVersion", command.expectedSagaVersion()).update();
        if (sagaUpdated != 1) {
            throwSagaConflict(context.tenantId(), command.sagaId(), command.serviceAssignmentId(),
                    command.expectedSagaVersion(), "PENDING_OR_TASK_PREPARED");
        }
        int assignmentUpdated = jdbc.sql("""
                        UPDATE dsp_service_assignment
                           SET status = 'FAILED_ACTIVATION', ended_by = :actorId,
                               end_reason_code = :reasonCode
                         WHERE tenant_id = :tenantId AND service_assignment_id = :assignmentId
                           AND status = 'PENDING_ACTIVATION'
                        """)
                .param("actorId", context.actorId()).param("reasonCode", command.reasonCode())
                .param("tenantId", context.tenantId()).param("assignmentId", command.serviceAssignmentId())
                .update();
        if (assignmentUpdated != 1) {
            throw new BusinessProblem(
                    ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "Only PENDING ServiceAssignment can abort before switch");
        }
        releaseReservationAndCapacity(context, state, command.reasonCode(), now);

        ServiceAssignmentReceipt receipt = new ServiceAssignmentReceipt(
                state.serviceAssignmentId(), command.sagaId(), state.taskId(),
                state.capacityReservationId(), "FAILED_ACTIVATION", "ABORTED",
                command.expectedSagaVersion() + 1, now);
        finish(context, authorizationDecision, ABORT, "SERVICE_ASSIGNMENT_ABORT",
                digest, receipt, "service.assignment.activation-aborted", command.reasonCode());
        return receipt;
    }

    @Override
    @Transactional
    public ServiceAssignmentReceipt complete(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            CompleteServiceAssignmentActivationCommand command
    ) {
        CommandContext context = context(principal, metadata);
        AssignmentState state = assignment(context.tenantId(), command.serviceAssignmentId());
        String digest = Sha256.digest(command.sagaId() + "|" + command.serviceAssignmentId()
                + "|" + command.preparedTaskAssignmentId() + "|" + command.expectedSagaVersion());
        AuthorizationDecision authorizationDecision = authorize(principal, context, state.taskId());
        IdempotencyDecision decision = idempotency.begin(context, COMPLETE, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return frozenReceipt(context, COMPLETE);
        }

        Instant now = clock.instant();
        int sagaUpdated = jdbc.sql("""
                        UPDATE dsp_service_assignment_activation_saga
                           SET stage = 'COMPLETED', version = version + 1,
                               updated_at = :now, completed_at = :now
                         WHERE tenant_id = :tenantId AND activation_saga_id = :sagaId
                           AND new_service_assignment_id = :assignmentId
                           AND prepared_task_assignment_id = :preparedId
                           AND stage = 'SERVICE_SWITCHED' AND version = :expectedVersion
                        """)
                .param("now", timestamptz(now)).param("tenantId", context.tenantId())
                .param("sagaId", command.sagaId()).param("assignmentId", command.serviceAssignmentId())
                .param("preparedId", command.preparedTaskAssignmentId())
                .param("expectedVersion", command.expectedSagaVersion()).update();
        if (sagaUpdated != 1) {
            throwSagaConflict(context.tenantId(), command.sagaId(), command.serviceAssignmentId(),
                    command.expectedSagaVersion(), "SERVICE_SWITCHED");
        }
        ServiceAssignmentReceipt receipt = new ServiceAssignmentReceipt(
                state.serviceAssignmentId(), command.sagaId(), state.taskId(),
                state.capacityReservationId(), "ACTIVE", "COMPLETED",
                command.expectedSagaVersion() + 1, now);
        finish(context, authorizationDecision, COMPLETE, "SERVICE_ASSIGNMENT_COMPLETE",
                digest, receipt, "service.assignment.activation-completed", "TASK_ASSIGNMENT_ACTIVATED");
        return receipt;
    }

    private CommandContext context(CurrentPrincipal principal, CommandMetadata metadata) {
        return new CommandContext(principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
    }

    private AuthorizationDecision authorize(CurrentPrincipal principal, CommandContext context, UUID taskId) {
        return authorization.require(principal,
                AuthorizationRequest.tenantCapability(
                        CAPABILITY, context.tenantId(), "ServiceAssignment", taskId.toString()),
                context.correlationId());
    }

    private void validateOldAssignment(String tenantId, PrepareServiceAssignmentCommand command) {
        if (command.supersedesServiceAssignmentId() == null) {
            if (activeAssignmentExists(tenantId, command.taskId(), command.responsibilityLevel().name())) {
                throw new BusinessProblem(
                        ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                        "Existing ACTIVE responsibility must be explicitly superseded");
            }
            return;
        }
        OldAssignment old = jdbc.sql("""
                        SELECT work_order_id, task_id, responsibility_level,
                               assignee_id, business_type, status
                          FROM dsp_service_assignment
                         WHERE tenant_id = :tenantId AND service_assignment_id = :assignmentId
                        """)
                .param("tenantId", tenantId)
                .param("assignmentId", command.supersedesServiceAssignmentId())
                .query(OldAssignment.class).optional()
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Superseded ServiceAssignment does not exist"));
        if (!"ACTIVE".equals(old.status())
                || !old.workOrderId().equals(command.workOrderId())
                || !old.taskId().equals(command.taskId())
                || !old.responsibilityLevel().equals(command.responsibilityLevel().name())
                || !old.businessType().equals(command.businessType())
                || old.assigneeId().equals(command.assigneeId())) {
            throw new BusinessProblem(
                    ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "Superseded responsibility must be the matching ACTIVE assignment for another assignee");
        }
    }

    private CapacityCounter reserveCapacity(
            CommandContext context,
            PrepareServiceAssignmentCommand command,
            Instant now
    ) {
        CapacityCounter counter = jdbc.sql("""
                        SELECT capacity_counter_id, occupied_units, max_units, version
                          FROM dsp_capacity_counter
                         WHERE tenant_id = :tenantId AND responsibility_level = :level
                           AND assignee_id = :assigneeId AND business_type = :businessType
                        """)
                .param("tenantId", context.tenantId())
                .param("level", command.responsibilityLevel().name())
                .param("assigneeId", command.assigneeId())
                .param("businessType", command.businessType())
                .query(CapacityCounter.class).optional()
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Capacity counter does not exist"));
        int updated = jdbc.sql("""
                        UPDATE dsp_capacity_counter
                           SET occupied_units = occupied_units + 1,
                               version = version + 1, updated_by = :actorId, updated_at = :now
                         WHERE capacity_counter_id = :counterId AND tenant_id = :tenantId
                           AND version = :expectedVersion AND occupied_units < max_units
                        """)
                .param("actorId", context.actorId()).param("now", timestamptz(now))
                .param("counterId", counter.capacityCounterId()).param("tenantId", context.tenantId())
                .param("expectedVersion", command.expectedCapacityVersion()).update();
        if (updated != 1) {
            CapacityCounter current = capacityCounter(context.tenantId(), counter.capacityCounterId());
            if (current.version() != command.expectedCapacityVersion()) {
                throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "Capacity counter version changed");
            }
            throw new BusinessProblem(
                    ProblemCode.DISPATCH_CAPACITY_CONFLICT,
                    "Capacity limit has been reached");
        }
        return counter;
    }

    private CapacityCounter capacityCounter(String tenantId, UUID counterId) {
        return jdbc.sql("""
                        SELECT capacity_counter_id, occupied_units, max_units, version
                          FROM dsp_capacity_counter
                         WHERE tenant_id = :tenantId AND capacity_counter_id = :counterId
                        """)
                .param("tenantId", tenantId).param("counterId", counterId)
                .query(CapacityCounter.class).single();
    }

    private void endOldAssignmentAndReleaseCapacity(
            CommandContext context, AssignmentState newAssignment, Instant now) {
        int ended = jdbc.sql("""
                        UPDATE dsp_service_assignment
                           SET status = 'ENDED', effective_to = :now,
                               ended_by = :actorId, end_reason_code = 'REASSIGNED'
                         WHERE tenant_id = :tenantId AND service_assignment_id = :oldAssignmentId
                           AND task_id = :taskId AND responsibility_level = :level
                           AND status = 'ACTIVE'
                        """)
                .param("now", timestamptz(now)).param("actorId", context.actorId())
                .param("tenantId", context.tenantId())
                .param("oldAssignmentId", newAssignment.supersedesServiceAssignmentId())
                .param("taskId", newAssignment.taskId())
                .param("level", newAssignment.responsibilityLevel()).update();
        if (ended != 1) {
            throw new BusinessProblem(
                    ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "Superseded ACTIVE ServiceAssignment could not be ended");
        }
        AssignmentState old = assignment(context.tenantId(), newAssignment.supersedesServiceAssignmentId());
        releaseReservationAndCapacity(context, old, "REASSIGNED", now);
    }

    private void releaseReservationAndCapacity(
            CommandContext context, AssignmentState state, String reasonCode, Instant now) {
        int reservationUpdated = jdbc.sql("""
                        UPDATE dsp_capacity_reservation
                           SET status = 'RELEASED', released_at = :now,
                               released_by = :actorId, release_reason_code = :reasonCode
                         WHERE tenant_id = :tenantId AND capacity_reservation_id = :reservationId
                           AND status IN ('HELD', 'CONFIRMED')
                        """)
                .param("now", timestamptz(now)).param("actorId", context.actorId())
                .param("reasonCode", reasonCode).param("tenantId", context.tenantId())
                .param("reservationId", state.capacityReservationId()).update();
        if (reservationUpdated != 1) {
            throw new BusinessProblem(
                    ProblemCode.DISPATCH_CAPACITY_CONFLICT,
                    "Capacity reservation was already released");
        }
        int counterUpdated = jdbc.sql("""
                        UPDATE dsp_capacity_counter
                           SET occupied_units = occupied_units - :units,
                               version = version + 1, updated_by = :actorId, updated_at = :now
                         WHERE tenant_id = :tenantId AND capacity_counter_id = :counterId
                           AND occupied_units >= :units
                        """)
                .param("units", state.reservationUnits()).param("actorId", context.actorId())
                .param("now", timestamptz(now)).param("tenantId", context.tenantId())
                .param("counterId", state.capacityCounterId()).update();
        if (counterUpdated != 1) {
            throw new BusinessProblem(
                    ProblemCode.DISPATCH_CAPACITY_CONFLICT,
                    "Capacity counter could not release reserved units");
        }
    }

    private boolean activeAssignmentExists(String tenantId, UUID taskId, String level) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM dsp_service_assignment
                             WHERE tenant_id = :tenantId AND task_id = :taskId
                               AND responsibility_level = :level AND status = 'ACTIVE'
                        )
                        """)
                .param("tenantId", tenantId).param("taskId", taskId).param("level", level)
                .query(Boolean.class).single();
    }

    private AssignmentState assignment(String tenantId, UUID assignmentId) {
        return jdbc.sql("""
                        SELECT assignment.service_assignment_id,
                               assignment.activation_saga_id AS saga_id,
                               assignment.work_order_id, assignment.task_id,
                               assignment.responsibility_level, assignment.assignee_id,
                               assignment.business_type, assignment.status,
                               assignment.supersedes_service_assignment_id,
                               assignment.task_execution_guard_id AS guard_id,
                               assignment.prepared_task_assignment_id,
                               assignment.created_by,
                               assignment.activation_protocol_version AS protocol_version,
                               assignment.pending_authority_assignment_id,
                               assignment.pending_authority_version,
                               assignment.pending_fence_decision_id,
                               assignment.pending_fence_policy_version,
                               reservation.capacity_reservation_id,
                               reservation.capacity_counter_id, reservation.units AS reservation_units
                          FROM dsp_service_assignment assignment
                          JOIN dsp_capacity_reservation reservation
                            ON reservation.service_assignment_id = assignment.service_assignment_id
                         WHERE assignment.tenant_id = :tenantId
                           AND assignment.service_assignment_id = :assignmentId
                        """)
                .param("tenantId", tenantId).param("assignmentId", assignmentId)
                .query(AssignmentState.class).optional()
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "ServiceAssignment does not exist"));
    }

    private void throwSagaConflict(
            String tenantId,
            UUID sagaId,
            UUID assignmentId,
            long expectedVersion,
            String expectedStage
    ) {
        SagaState saga = jdbc.sql("""
                        SELECT stage, version FROM dsp_service_assignment_activation_saga
                         WHERE tenant_id = :tenantId AND activation_saga_id = :sagaId
                           AND new_service_assignment_id = :assignmentId
                        """)
                .param("tenantId", tenantId).param("sagaId", sagaId)
                .param("assignmentId", assignmentId).query(SagaState.class).optional()
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "ServiceAssignment activation saga does not exist"));
        if (saga.version() != expectedVersion) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "Activation saga version changed");
        }
        throw new BusinessProblem(
                ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                "Activation saga requires stage " + expectedStage + " but was " + saga.stage());
    }

    private ServiceAssignmentReceipt receipt(
            AssignmentState state, String sagaStage, long sagaVersion, Instant occurredAt) {
        return new ServiceAssignmentReceipt(
                state.serviceAssignmentId(), state.sagaId(), state.taskId(),
                state.capacityReservationId(), state.status(), sagaStage, sagaVersion, occurredAt);
    }

    private void finish(
            CommandContext context,
            AuthorizationDecision authorizationDecision,
            String operation,
            String action,
            String requestDigest,
            ServiceAssignmentReceipt receipt,
            String eventType,
            String reasonCode
    ) {
        insertFrozenReceipt(context, operation, receipt);
        appendEvent(context, receipt, eventType, reasonCode);
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(), action, CAPABILITY,
                "ServiceAssignment", receipt.serviceAssignmentId().toString(), "ALLOW",
                authorizationDecision.matchedGrantIds(), authorizationDecision.policyVersion(),
                "SUCCEEDED", null, requestDigest, context.correlationId(), receipt.occurredAt()));
        idempotency.complete(context, operation, receipt.serviceAssignmentId().toString(),
                Sha256.digest(serialize(receipt)));
    }

    private void insertFrozenReceipt(
            CommandContext context, String operation, ServiceAssignmentReceipt receipt) {
        jdbc.sql("""
                        INSERT INTO dsp_assignment_command_result (
                            tenant_id, operation_type, idempotency_key,
                            service_assignment_id, activation_saga_id, task_id,
                            capacity_reservation_id, assignment_status, saga_stage,
                            saga_version, occurred_at
                        ) VALUES (
                            :tenantId, :operation, :idempotencyKey,
                            :assignmentId, :sagaId, :taskId,
                            :reservationId, :assignmentStatus, :sagaStage,
                            :sagaVersion, :occurredAt
                        )
                        """)
                .param("tenantId", context.tenantId()).param("operation", operation)
                .param("idempotencyKey", context.idempotencyKey())
                .param("assignmentId", receipt.serviceAssignmentId()).param("sagaId", receipt.sagaId())
                .param("taskId", receipt.taskId()).param("reservationId", receipt.capacityReservationId())
                .param("assignmentStatus", receipt.assignmentStatus())
                .param("sagaStage", receipt.sagaStage()).param("sagaVersion", receipt.sagaVersion())
                .param("occurredAt", timestamptz(receipt.occurredAt())).update();
    }

    private ServiceAssignmentReceipt frozenReceipt(CommandContext context, String operation) {
        return jdbc.sql("""
                        SELECT service_assignment_id, activation_saga_id AS saga_id,
                               task_id, capacity_reservation_id, assignment_status,
                               saga_stage, saga_version, occurred_at
                          FROM dsp_assignment_command_result
                         WHERE tenant_id = :tenantId AND operation_type = :operation
                           AND idempotency_key = :idempotencyKey
                        """)
                .param("tenantId", context.tenantId()).param("operation", operation)
                .param("idempotencyKey", context.idempotencyKey())
                .query(ServiceAssignmentReceipt.class).single();
    }

    private void appendEvent(
            CommandContext context,
            ServiceAssignmentReceipt receipt,
            String eventType,
            String reasonCode
    ) {
        AssignmentState state = assignment(context.tenantId(), receipt.serviceAssignmentId());
        boolean handshakeEvent = state.protocolVersion() == 2
                && ("service.assignment.pending-activation".equals(eventType)
                || "service.assignment.activated".equals(eventType));
        Object payload = handshakeEvent
                ? new ServiceAssignmentHandshakePayload(
                        state.serviceAssignmentId(), state.sagaId(), state.workOrderId(), state.taskId(),
                        state.responsibilityLevel(), state.assigneeId(), state.businessType(),
                        receipt.assignmentStatus(), state.supersedesServiceAssignmentId(),
                        state.capacityReservationId(), state.guardId(), state.preparedTaskAssignmentId(),
                        reasonCode, state.createdBy(), state.protocolVersion(), receipt.occurredAt())
                : new ServiceAssignmentChangedPayload(
                        state.serviceAssignmentId(), state.sagaId(), state.workOrderId(), state.taskId(),
                        state.responsibilityLevel(), state.assigneeId(), state.businessType(),
                        receipt.assignmentStatus(), state.supersedesServiceAssignmentId(),
                        state.capacityReservationId(), state.guardId(), state.preparedTaskAssignmentId(),
                        reasonCode, receipt.occurredAt());
        String json = serialize(payload);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "dispatch", eventType, handshakeEvent ? 2 : 1,
                "ServiceAssignment", receipt.serviceAssignmentId().toString(),
                receipt.sagaVersion(), context.tenantId(), context.correlationId(),
                context.idempotencyKey(), state.taskId().toString(), json,
                Sha256.digest(json), receipt.occurredAt()));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("ServiceAssignment payload cannot be serialized", exception);
        }
    }

    private record CapacityCounter(
            UUID capacityCounterId, int occupiedUnits, int maxUnits, long version) {
    }

    private record OldAssignment(
            UUID workOrderId,
            UUID taskId,
            String responsibilityLevel,
            String assigneeId,
            String businessType,
            String status
    ) {
    }

    private record SagaState(String stage, long version) {
    }

    private record AssignmentState(
            UUID serviceAssignmentId,
            UUID sagaId,
            UUID workOrderId,
            UUID taskId,
            String responsibilityLevel,
            String assigneeId,
            String businessType,
            String status,
            UUID supersedesServiceAssignmentId,
            UUID guardId,
            UUID preparedTaskAssignmentId,
            String createdBy,
            int protocolVersion,
            String pendingAuthorityAssignmentId,
            Long pendingAuthorityVersion,
            String pendingFenceDecisionId,
            String pendingFencePolicyVersion,
            UUID capacityReservationId,
            UUID capacityCounterId,
            int reservationUnits
    ) {
    }
}
