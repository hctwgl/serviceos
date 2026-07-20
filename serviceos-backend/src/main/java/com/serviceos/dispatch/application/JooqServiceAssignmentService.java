package com.serviceos.dispatch.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.AbortServiceAssignmentActivationCommand;
import com.serviceos.dispatch.api.ActivateNetworkFromFrozenDispatchCommand;
import com.serviceos.dispatch.api.ActivateServiceAssignmentCommand;
import com.serviceos.dispatch.api.ActivateTechnicianFromFrozenDispatchCommand;
import com.serviceos.dispatch.api.CompleteServiceAssignmentActivationCommand;
import com.serviceos.dispatch.api.CompleteServiceAssignmentAbortCommand;
import com.serviceos.dispatch.api.ConfirmTaskAssignmentPreparedCommand;
import com.serviceos.dispatch.api.PrepareServiceAssignmentCommand;
import com.serviceos.dispatch.api.ResponsibilityLevel;
import com.serviceos.dispatch.api.ServiceAssignmentChangedPayload;
import com.serviceos.dispatch.api.ServiceAssignmentHandshakePayload;
import com.serviceos.dispatch.api.ServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ServiceAssignmentService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.jooq.generated.tables.DspAssignmentCommandResult;
import com.serviceos.jooq.generated.tables.DspCapacityCounter;
import com.serviceos.jooq.generated.tables.DspCapacityReservation;
import com.serviceos.jooq.generated.tables.DspServiceAssignment;
import com.serviceos.jooq.generated.tables.DspServiceAssignmentActivationSaga;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.Record4;
import org.jooq.Record5;
import org.jooq.Record6;
import org.jooq.Record8;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.DspAssignmentCommandResult.DSP_ASSIGNMENT_COMMAND_RESULT;
import static com.serviceos.jooq.generated.tables.DspCapacityCounter.DSP_CAPACITY_COUNTER;
import static com.serviceos.jooq.generated.tables.DspCapacityReservation.DSP_CAPACITY_RESERVATION;
import static com.serviceos.jooq.generated.tables.DspServiceAssignment.DSP_SERVICE_ASSIGNMENT;
import static com.serviceos.jooq.generated.tables.DspServiceAssignmentActivationSaga.DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA;

/**
 * Dispatch 侧 ServiceAssignment/CapacityReservation 权威事务。
 *
 * <p>HELD 与 CONFIRMED 均占用 capacity counter；只有 abort、结束旧责任或受控补偿才释放。
 * Task 模块的 guard/assignment 引用只作为握手证明保存，本模块不跨边界写 Task 表。</p>
 */
@Service
final class JooqServiceAssignmentService implements ServiceAssignmentService {
    private static final String PREPARE = "dispatch.assignment.prepare";
    private static final String CONFIRM_TASK = "dispatch.assignment.confirm-task-prepared";
    private static final String ACTIVATE = "dispatch.assignment.activate";
    private static final String ABORT = "dispatch.assignment.abort";
    private static final String COMPLETE_ABORT = "dispatch.assignment.complete-abort";
    private static final String COMPLETE = "dispatch.assignment.complete";
    private static final String CAPABILITY = "dispatch.assignment.manage";
    private static final String SYSTEM_ACTOR = "system:dispatch-policy";
    private static final String POLICY_OPERATION = "dispatch.assignment.activate-from-policy";
    private static final String POLICY_TECH_OPERATION = "dispatch.assignment.activate-tech-from-policy";

    private final DSLContext dsl;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration activationStageTimeout;

    JooqServiceAssignmentService(
            DSLContext dsl,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${serviceos.dispatch.activation-stage-timeout:PT15M}") Duration activationStageTimeout
    ) {
        this.dsl = dsl;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
        if (activationStageTimeout == null || activationStageTimeout.isZero()
                || activationStageTimeout.isNegative()) {
            throw new IllegalArgumentException("activationStageTimeout must be positive");
        }
        this.activationStageTimeout = activationStageTimeout;
    }

    @Override
    @Transactional
    public ServiceAssignmentReceipt activateNetworkFromFrozenDispatchPolicy(
            String tenantId,
            String correlationId,
            ActivateNetworkFromFrozenDispatchCommand command
    ) {
        // Inbox 系统路径：复用 protocol v1 初始指派四步，但鉴权短路为系统 actor。
        CurrentPrincipal system = new CurrentPrincipal(
                SYSTEM_ACTOR, tenantId, CurrentPrincipal.PrincipalType.USER,
                "dispatch-policy", Set.of());
        // 幂等键有 160 上限；决策细节进入 digest，键本身用短摘要。
        String stem = "dp:" + Sha256.digest(command.taskId() + "|" + command.sourceDecisionId())
                .substring(0, 40);
        CommandContext context = new CommandContext(tenantId, SYSTEM_ACTOR, correlationId, stem);
        String digest = Sha256.digest(command.workOrderId() + "|" + command.taskId() + "|"
                + command.networkAssigneeId() + "|" + command.businessType() + "|"
                + command.sourceDecisionId() + "|" + command.expectedCapacityVersion());
        IdempotencyDecision decision = idempotency.begin(context, POLICY_OPERATION, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return frozenReceipt(context, POLICY_OPERATION);
        }

        var existing = findActiveWithConfirmedReservation(tenantId, command.taskId(), "NETWORK");
        ServiceAssignmentReceipt completed;
        if (existing.isPresent()) {
            ActiveNetworkRow row = existing.get();
            if (!row.assigneeId().equals(command.networkAssigneeId())) {
                throw new BusinessProblem(ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                        "ACTIVE NETWORK responsibility already belongs to another assignee");
            }
            completed = new ServiceAssignmentReceipt(
                    row.serviceAssignmentId(), row.sagaId(), command.taskId(),
                    row.reservationId(), "ACTIVE", "COMPLETED",
                    row.sagaVersion(), clock.instant());
        } else {
            ServiceAssignmentReceipt pending = prepare(
                    system, child(correlationId, stem + "-p"),
                    new PrepareServiceAssignmentCommand(
                            UUID.randomUUID(), command.workOrderId(), command.taskId(),
                            ResponsibilityLevel.NETWORK, command.networkAssigneeId(),
                            command.businessType(), command.sourceDecisionId(),
                            null, null, command.expectedCapacityVersion()));
            UUID preparedId = UUID.randomUUID();
            confirmTaskPrepared(
                    system, child(correlationId, stem + "-t"),
                    new ConfirmTaskAssignmentPreparedCommand(
                            pending.sagaId(), pending.serviceAssignmentId(), command.taskId(),
                            UUID.randomUUID(), preparedId, 1));
            ServiceAssignmentReceipt activated = activate(
                    system, child(correlationId, stem + "-a"),
                    new ActivateServiceAssignmentCommand(
                            pending.sagaId(), pending.serviceAssignmentId(), 2,
                            "authority://dispatch-policy/" + command.networkAssigneeId(), 1,
                            "fence://dispatch-policy/" + command.networkAssigneeId(),
                            "dispatch-policy-geo-v1"));
            completed = complete(
                    system, child(correlationId, stem + "-c"),
                    new CompleteServiceAssignmentActivationCommand(
                            activated.sagaId(), activated.serviceAssignmentId(), preparedId, 3));
        }
        insertFrozenReceipt(context, POLICY_OPERATION, completed);
        idempotency.complete(context, POLICY_OPERATION, completed.serviceAssignmentId().toString(),
                Sha256.digest(serialize(completed)));
        return completed;
    }

    @Override
    @Transactional
    public ServiceAssignmentReceipt activateTechnicianFromFrozenDispatchPolicy(
            String tenantId,
            String correlationId,
            ActivateTechnicianFromFrozenDispatchCommand command
    ) {
        // Inbox 系统路径：与 NETWORK 同构，责任级别改为 TECHNICIAN。
        CurrentPrincipal system = new CurrentPrincipal(
                SYSTEM_ACTOR, tenantId, CurrentPrincipal.PrincipalType.USER,
                "dispatch-policy", Set.of());
        String stem = "dpt:" + Sha256.digest(command.taskId() + "|" + command.sourceDecisionId())
                .substring(0, 40);
        CommandContext context = new CommandContext(tenantId, SYSTEM_ACTOR, correlationId, stem);
        String digest = Sha256.digest(command.workOrderId() + "|" + command.taskId() + "|"
                + command.technicianAssigneeId() + "|" + command.businessType() + "|"
                + command.sourceDecisionId() + "|" + command.expectedCapacityVersion());
        IdempotencyDecision decision = idempotency.begin(context, POLICY_TECH_OPERATION, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return frozenReceipt(context, POLICY_TECH_OPERATION);
        }

        var existing = findActiveWithConfirmedReservation(tenantId, command.taskId(), "TECHNICIAN");
        ServiceAssignmentReceipt completed;
        if (existing.isPresent()) {
            ActiveNetworkRow row = existing.get();
            if (!row.assigneeId().equals(command.technicianAssigneeId())) {
                throw new BusinessProblem(ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                        "ACTIVE TECHNICIAN responsibility already belongs to another assignee");
            }
            completed = new ServiceAssignmentReceipt(
                    row.serviceAssignmentId(), row.sagaId(), command.taskId(),
                    row.reservationId(), "ACTIVE", "COMPLETED",
                    row.sagaVersion(), clock.instant());
        } else {
            ServiceAssignmentReceipt pending = prepare(
                    system, child(correlationId, stem + "-p"),
                    new PrepareServiceAssignmentCommand(
                            UUID.randomUUID(), command.workOrderId(), command.taskId(),
                            ResponsibilityLevel.TECHNICIAN, command.technicianAssigneeId(),
                            command.businessType(), command.sourceDecisionId(),
                            null, null, command.expectedCapacityVersion()));
            UUID preparedId = UUID.randomUUID();
            confirmTaskPrepared(
                    system, child(correlationId, stem + "-t"),
                    new ConfirmTaskAssignmentPreparedCommand(
                            pending.sagaId(), pending.serviceAssignmentId(), command.taskId(),
                            UUID.randomUUID(), preparedId, 1));
            ServiceAssignmentReceipt activated = activate(
                    system, child(correlationId, stem + "-a"),
                    new ActivateServiceAssignmentCommand(
                            pending.sagaId(), pending.serviceAssignmentId(), 2,
                            "authority://dispatch-policy/" + command.technicianAssigneeId(), 1,
                            "fence://dispatch-policy/" + command.technicianAssigneeId(),
                            "dispatch-policy-geo-v1"));
            completed = complete(
                    system, child(correlationId, stem + "-c"),
                    new CompleteServiceAssignmentActivationCommand(
                            activated.sagaId(), activated.serviceAssignmentId(), preparedId, 3));
        }
        insertFrozenReceipt(context, POLICY_TECH_OPERATION, completed);
        idempotency.complete(context, POLICY_TECH_OPERATION, completed.serviceAssignmentId().toString(),
                Sha256.digest(serialize(completed)));
        return completed;
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
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT;
        dsl.insertInto(assignment)
                .set(assignment.SERVICE_ASSIGNMENT_ID, assignmentId)
                .set(assignment.TENANT_ID, context.tenantId())
                .set(assignment.WORK_ORDER_ID, command.workOrderId())
                .set(assignment.TASK_ID, command.taskId())
                .set(assignment.RESPONSIBILITY_LEVEL, command.responsibilityLevel().name())
                .set(assignment.ASSIGNEE_ID, command.assigneeId())
                .set(assignment.BUSINESS_TYPE, command.businessType())
                .set(assignment.SOURCE_DECISION_ID, command.sourceDecisionId())
                .set(assignment.STATUS, "PENDING_ACTIVATION")
                .set(assignment.ACTIVATION_SAGA_ID, command.sagaId())
                .set(assignment.SUPERSEDES_SERVICE_ASSIGNMENT_ID, command.supersedesServiceAssignmentId())
                .set(assignment.REASSIGNMENT_REASON_CODE, command.reasonCode())
                .set(assignment.CREATED_BY, context.actorId())
                .set(assignment.CREATED_AT, now)
                .set(assignment.ACTIVATION_PROTOCOL_VERSION,
                        command.usesReliableReassignmentProtocol() ? 2 : 1)
                .set(assignment.PENDING_AUTHORITY_ASSIGNMENT_ID, command.authorityAssignmentId())
                .set(assignment.PENDING_AUTHORITY_VERSION,
                        command.authorityVersion() == 0 ? null : command.authorityVersion())
                .set(assignment.PENDING_FENCE_DECISION_ID, command.fenceDecisionId())
                .set(assignment.PENDING_FENCE_POLICY_VERSION, command.fencePolicyVersion())
                .execute();
        DspCapacityReservation reservation = DSP_CAPACITY_RESERVATION;
        dsl.insertInto(reservation)
                .set(reservation.CAPACITY_RESERVATION_ID, reservationId)
                .set(reservation.TENANT_ID, context.tenantId())
                .set(reservation.SERVICE_ASSIGNMENT_ID, assignmentId)
                .set(reservation.CAPACITY_COUNTER_ID, counter.capacityCounterId())
                .set(reservation.UNITS, 1)
                .set(reservation.STATUS, "HELD")
                .set(reservation.HELD_AT, now)
                .execute();
        DspServiceAssignmentActivationSaga saga = DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA;
        dsl.insertInto(saga)
                .set(saga.ACTIVATION_SAGA_ID, command.sagaId())
                .set(saga.TENANT_ID, context.tenantId())
                .set(saga.TASK_ID, command.taskId())
                .set(saga.NEW_SERVICE_ASSIGNMENT_ID, assignmentId)
                .set(saga.OLD_SERVICE_ASSIGNMENT_ID, command.supersedesServiceAssignmentId())
                .set(saga.STAGE, "PENDING")
                .set(saga.VERSION, 1L)
                .set(saga.STARTED_AT, now)
                .set(saga.UPDATED_AT, now)
                .set(saga.DEADLINE_AT, deadline(now))
                .execute();

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
        DspServiceAssignmentActivationSaga saga = DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA;
        // 乐观并发：stage 与 version 双条件，影响行数不为 1 即视为 saga 已被并发推进。
        int sagaUpdated = dsl.update(saga)
                .set(saga.STAGE, "TASK_PREPARED")
                .set(saga.VERSION, saga.VERSION.plus(1))
                .set(saga.PREPARED_TASK_ASSIGNMENT_ID, command.preparedTaskAssignmentId())
                .set(saga.TASK_EXECUTION_GUARD_ID, command.guardId())
                .set(saga.UPDATED_AT, now)
                .set(saga.DEADLINE_AT, deadline(now))
                .where(saga.TENANT_ID.eq(context.tenantId()))
                .and(saga.ACTIVATION_SAGA_ID.eq(command.sagaId()))
                .and(saga.NEW_SERVICE_ASSIGNMENT_ID.eq(command.serviceAssignmentId()))
                .and(saga.TASK_ID.eq(command.taskId()))
                .and(saga.STAGE.eq("PENDING"))
                .and(saga.VERSION.eq(command.expectedSagaVersion()))
                .execute();
        if (sagaUpdated != 1) {
            throwSagaConflict(context.tenantId(), command.sagaId(), command.serviceAssignmentId(),
                    command.expectedSagaVersion(), "PENDING");
        }
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT;
        int assignmentUpdated = dsl.update(assignment)
                .set(assignment.PREPARED_TASK_ASSIGNMENT_ID, command.preparedTaskAssignmentId())
                .set(assignment.TASK_EXECUTION_GUARD_ID, command.guardId())
                .where(assignment.TENANT_ID.eq(context.tenantId()))
                .and(assignment.SERVICE_ASSIGNMENT_ID.eq(command.serviceAssignmentId()))
                .and(assignment.TASK_ID.eq(command.taskId()))
                .and(assignment.STATUS.eq("PENDING_ACTIVATION"))
                .execute();
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
        DspServiceAssignmentActivationSaga saga = DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA;
        int sagaUpdated = dsl.update(saga)
                .set(saga.STAGE, "SERVICE_SWITCHED")
                .set(saga.VERSION, saga.VERSION.plus(1))
                .set(saga.UPDATED_AT, now)
                .set(saga.DEADLINE_AT, deadline(now))
                .where(saga.TENANT_ID.eq(context.tenantId()))
                .and(saga.ACTIVATION_SAGA_ID.eq(command.sagaId()))
                .and(saga.NEW_SERVICE_ASSIGNMENT_ID.eq(command.serviceAssignmentId()))
                .and(saga.STAGE.eq("TASK_PREPARED"))
                .and(saga.VERSION.eq(command.expectedSagaVersion()))
                .execute();
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
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT;
        int assignmentUpdated = dsl.update(assignment)
                .set(assignment.STATUS, "ACTIVE")
                .set(assignment.EFFECTIVE_FROM, now)
                .set(assignment.AUTHORITY_ASSIGNMENT_ID, command.authorityAssignmentId())
                .set(assignment.AUTHORITY_VERSION, command.authorityVersion())
                .set(assignment.FENCE_DECISION_ID, command.fenceDecisionId())
                .set(assignment.FENCE_POLICY_VERSION, command.fencePolicyVersion())
                .where(assignment.TENANT_ID.eq(context.tenantId()))
                .and(assignment.SERVICE_ASSIGNMENT_ID.eq(command.serviceAssignmentId()))
                .and(assignment.ACTIVATION_SAGA_ID.eq(command.sagaId()))
                .and(assignment.STATUS.eq("PENDING_ACTIVATION"))
                .execute();
        if (assignmentUpdated != 1) {
            throw new BusinessProblem(
                    ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "PENDING ServiceAssignment could not be activated");
        }
        DspCapacityReservation reservation = DSP_CAPACITY_RESERVATION;
        int reservationUpdated = dsl.update(reservation)
                .set(reservation.STATUS, "CONFIRMED")
                .set(reservation.CONFIRMED_AT, now)
                .where(reservation.TENANT_ID.eq(context.tenantId()))
                .and(reservation.SERVICE_ASSIGNMENT_ID.eq(command.serviceAssignmentId()))
                .and(reservation.STATUS.eq("HELD"))
                .execute();
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
        boolean requiresTaskAck = state.protocolVersion() == 2
                && state.preparedTaskAssignmentId() != null;
        DspServiceAssignmentActivationSaga saga = DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA;
        // 与原 CASE WHEN :requiresTaskAck 等价：该标志对单条语句是常量，直接在 Java 侧分支。
        // requiresTaskAck -> ABORTING（等待 Task 确认）；否则直接 ABORTED 并清空 deadline。
        var sagaUpdate = dsl.update(saga)
                .set(saga.STAGE, requiresTaskAck ? "ABORTING" : "ABORTED")
                .set(saga.VERSION, saga.VERSION.plus(1))
                .set(saga.LAST_ERROR_CODE, command.reasonCode())
                .set(saga.UPDATED_AT, now);
        if (requiresTaskAck) {
            sagaUpdate.setNull(saga.COMPLETED_AT)
                    .set(saga.DEADLINE_AT, deadline(now));
        } else {
            sagaUpdate.set(saga.COMPLETED_AT, now)
                    .setNull(saga.DEADLINE_AT);
        }
        int sagaUpdated = sagaUpdate
                .where(saga.TENANT_ID.eq(context.tenantId()))
                .and(saga.ACTIVATION_SAGA_ID.eq(command.sagaId()))
                .and(saga.NEW_SERVICE_ASSIGNMENT_ID.eq(command.serviceAssignmentId()))
                .and(saga.STAGE.in("PENDING", "TASK_PREPARED"))
                .and(saga.VERSION.eq(command.expectedSagaVersion()))
                .execute();
        if (sagaUpdated != 1) {
            throwSagaConflict(context.tenantId(), command.sagaId(), command.serviceAssignmentId(),
                    command.expectedSagaVersion(), "PENDING_OR_TASK_PREPARED");
        }
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT;
        int assignmentUpdated = dsl.update(assignment)
                .set(assignment.STATUS, "FAILED_ACTIVATION")
                .set(assignment.ENDED_BY, context.actorId())
                .set(assignment.END_REASON_CODE, command.reasonCode())
                .where(assignment.TENANT_ID.eq(context.tenantId()))
                .and(assignment.SERVICE_ASSIGNMENT_ID.eq(command.serviceAssignmentId()))
                .and(assignment.STATUS.eq("PENDING_ACTIVATION"))
                .execute();
        if (assignmentUpdated != 1) {
            throw new BusinessProblem(
                    ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "Only PENDING ServiceAssignment can abort before switch");
        }
        releaseReservationAndCapacity(context, state, command.reasonCode(), now);

        String nextStage = requiresTaskAck ? "ABORTING" : "ABORTED";
        ServiceAssignmentReceipt receipt = new ServiceAssignmentReceipt(
                state.serviceAssignmentId(), command.sagaId(), state.taskId(),
                state.capacityReservationId(), "FAILED_ACTIVATION", nextStage,
                command.expectedSagaVersion() + 1, now);
        finish(context, authorizationDecision, ABORT, "SERVICE_ASSIGNMENT_ABORT",
                digest, receipt, "service.assignment.activation-aborted", command.reasonCode());
        return receipt;
    }

    @Override
    @Transactional
    public ServiceAssignmentReceipt completeAbort(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            CompleteServiceAssignmentAbortCommand command
    ) {
        CommandContext context = context(principal, metadata);
        AssignmentState state = assignment(context.tenantId(), command.serviceAssignmentId());
        String digest = Sha256.digest(command.sagaId() + "|" + command.serviceAssignmentId()
                + "|" + command.preparedTaskAssignmentId() + "|" + command.expectedSagaVersion());
        AuthorizationDecision authorizationDecision = authorize(principal, context, state.taskId());
        IdempotencyDecision decision = idempotency.begin(context, COMPLETE_ABORT, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return frozenReceipt(context, COMPLETE_ABORT);
        }

        Instant now = clock.instant();
        DspServiceAssignmentActivationSaga saga = DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA;
        int sagaUpdated = dsl.update(saga)
                .set(saga.STAGE, "ABORTED")
                .set(saga.VERSION, saga.VERSION.plus(1))
                .set(saga.UPDATED_AT, now)
                .set(saga.COMPLETED_AT, now)
                .setNull(saga.DEADLINE_AT)
                .where(saga.TENANT_ID.eq(context.tenantId()))
                .and(saga.ACTIVATION_SAGA_ID.eq(command.sagaId()))
                .and(saga.NEW_SERVICE_ASSIGNMENT_ID.eq(command.serviceAssignmentId()))
                .and(saga.PREPARED_TASK_ASSIGNMENT_ID.eq(command.preparedTaskAssignmentId()))
                .and(saga.STAGE.eq("ABORTING"))
                .and(saga.VERSION.eq(command.expectedSagaVersion()))
                .execute();
        if (sagaUpdated != 1) {
            throwSagaConflict(context.tenantId(), command.sagaId(), command.serviceAssignmentId(),
                    command.expectedSagaVersion(), "ABORTING");
        }

        ServiceAssignmentReceipt receipt = new ServiceAssignmentReceipt(
                state.serviceAssignmentId(), command.sagaId(), state.taskId(),
                state.capacityReservationId(), "FAILED_ACTIVATION", "ABORTED",
                command.expectedSagaVersion() + 1, now);
        finish(context, authorizationDecision, COMPLETE_ABORT, "SERVICE_ASSIGNMENT_ABORT_COMPLETE",
                digest, receipt, "service.assignment.activation-abort-completed",
                "TASK_ASSIGNMENT_ABORTED");
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
        DspServiceAssignmentActivationSaga saga = DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA;
        int sagaUpdated = dsl.update(saga)
                .set(saga.STAGE, "COMPLETED")
                .set(saga.VERSION, saga.VERSION.plus(1))
                .set(saga.UPDATED_AT, now)
                .set(saga.COMPLETED_AT, now)
                .setNull(saga.DEADLINE_AT)
                .where(saga.TENANT_ID.eq(context.tenantId()))
                .and(saga.ACTIVATION_SAGA_ID.eq(command.sagaId()))
                .and(saga.NEW_SERVICE_ASSIGNMENT_ID.eq(command.serviceAssignmentId()))
                .and(saga.PREPARED_TASK_ASSIGNMENT_ID.eq(command.preparedTaskAssignmentId()))
                .and(saga.STAGE.eq("SERVICE_SWITCHED"))
                .and(saga.VERSION.eq(command.expectedSagaVersion()))
                .execute();
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

    private Instant deadline(Instant stageStartedAt) {
        return stageStartedAt.plus(activationStageTimeout);
    }

    private AuthorizationDecision authorize(CurrentPrincipal principal, CommandContext context, UUID taskId) {
        // M324：仅 Inbox 系统 actor 可短路；HTTP 身份始终来自 JWT，客户端无法伪造该 principalId。
        if (SYSTEM_ACTOR.equals(principal.principalId())) {
            return new AuthorizationDecision(
                    AuthorizationDecision.Effect.ALLOW,
                    List.of("SYSTEM_DISPATCH_POLICY"),
                    List.of(),
                    List.of(),
                    List.of(),
                    "dispatch-policy-runtime-v1");
        }
        // M196：Network Portal 委托期间按 NETWORK scope 鉴权；Admin TENANT 路径不变。
        String networkScope = NetworkScopedDispatchAuthorization.currentNetworkId();
        if (networkScope != null) {
            return authorization.require(principal,
                    AuthorizationRequest.networkCapability(
                            CAPABILITY, context.tenantId(), "ServiceAssignment",
                            taskId.toString(), networkScope),
                    context.correlationId());
        }
        return authorization.require(principal,
                AuthorizationRequest.tenantCapability(
                        CAPABILITY, context.tenantId(), "ServiceAssignment", taskId.toString()),
                context.correlationId());
    }

    private static CommandMetadata child(String correlationId, String idempotencyKey) {
        return new CommandMetadata(correlationId, idempotencyKey);
    }

    /**
     * ACTIVE 责任 + CONFIRMED reservation 的复合读取；saga version 缺失时按 0 处理
     * （与原 coalesce(s.version, 0) 一致）。
     */
    private java.util.Optional<ActiveNetworkRow> findActiveWithConfirmedReservation(
            String tenantId, UUID taskId, String level) {
        DspServiceAssignment a = DSP_SERVICE_ASSIGNMENT.as("a");
        DspCapacityReservation r = DSP_CAPACITY_RESERVATION.as("r");
        DspServiceAssignmentActivationSaga s = DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA.as("s");
        return dsl.select(
                        a.SERVICE_ASSIGNMENT_ID,
                        a.ACTIVATION_SAGA_ID,
                        a.ASSIGNEE_ID,
                        r.CAPACITY_RESERVATION_ID,
                        DSL.coalesce(s.VERSION, 0L))
                .from(a)
                .join(r)
                .on(r.TENANT_ID.eq(a.TENANT_ID))
                .and(r.SERVICE_ASSIGNMENT_ID.eq(a.SERVICE_ASSIGNMENT_ID))
                .and(r.STATUS.eq("CONFIRMED"))
                .leftJoin(s)
                .on(s.TENANT_ID.eq(a.TENANT_ID))
                .and(s.ACTIVATION_SAGA_ID.eq(a.ACTIVATION_SAGA_ID))
                .where(a.TENANT_ID.eq(tenantId))
                .and(a.TASK_ID.eq(taskId))
                .and(a.RESPONSIBILITY_LEVEL.eq(level))
                .and(a.STATUS.eq("ACTIVE"))
                .orderBy(a.CREATED_AT)
                .limit(1)
                .fetchOptional(JooqServiceAssignmentService::mapActiveNetworkRow);
    }

    private record ActiveNetworkRow(
            UUID serviceAssignmentId,
            UUID sagaId,
            String assigneeId,
            UUID reservationId,
            long sagaVersion
    ) {
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
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT;
        OldAssignment old = dsl.select(
                        assignment.WORK_ORDER_ID,
                        assignment.TASK_ID,
                        assignment.RESPONSIBILITY_LEVEL,
                        assignment.ASSIGNEE_ID,
                        assignment.BUSINESS_TYPE,
                        assignment.STATUS)
                .from(assignment)
                .where(assignment.TENANT_ID.eq(tenantId))
                .and(assignment.SERVICE_ASSIGNMENT_ID.eq(command.supersedesServiceAssignmentId()))
                .fetchOptional(JooqServiceAssignmentService::mapOldAssignment)
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
        DspCapacityCounter counterTable = DSP_CAPACITY_COUNTER;
        CapacityCounter counter = dsl.select(
                        counterTable.CAPACITY_COUNTER_ID,
                        counterTable.OCCUPIED_UNITS,
                        counterTable.MAX_UNITS,
                        counterTable.VERSION)
                .from(counterTable)
                .where(counterTable.TENANT_ID.eq(context.tenantId()))
                .and(counterTable.RESPONSIBILITY_LEVEL.eq(command.responsibilityLevel().name()))
                .and(counterTable.ASSIGNEE_ID.eq(command.assigneeId()))
                .and(counterTable.BUSINESS_TYPE.eq(command.businessType()))
                .fetchOptional(JooqServiceAssignmentService::mapCapacityCounter)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Capacity counter does not exist"));
        // 乐观并发：版本条件 + 占用不得超过上限，影响行数不为 1 即失败关闭。
        int updated = dsl.update(counterTable)
                .set(counterTable.OCCUPIED_UNITS, counterTable.OCCUPIED_UNITS.plus(1))
                .set(counterTable.VERSION, counterTable.VERSION.plus(1))
                .set(counterTable.UPDATED_BY, context.actorId())
                .set(counterTable.UPDATED_AT, now)
                .where(counterTable.CAPACITY_COUNTER_ID.eq(counter.capacityCounterId()))
                .and(counterTable.TENANT_ID.eq(context.tenantId()))
                .and(counterTable.VERSION.eq(command.expectedCapacityVersion()))
                .and(counterTable.OCCUPIED_UNITS.lt(counterTable.MAX_UNITS))
                .execute();
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
        DspCapacityCounter counterTable = DSP_CAPACITY_COUNTER;
        return dsl.select(
                        counterTable.CAPACITY_COUNTER_ID,
                        counterTable.OCCUPIED_UNITS,
                        counterTable.MAX_UNITS,
                        counterTable.VERSION)
                .from(counterTable)
                .where(counterTable.TENANT_ID.eq(tenantId))
                .and(counterTable.CAPACITY_COUNTER_ID.eq(counterId))
                .fetchSingle(JooqServiceAssignmentService::mapCapacityCounter);
    }

    private void endOldAssignmentAndReleaseCapacity(
            CommandContext context, AssignmentState newAssignment, Instant now) {
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT;
        int ended = dsl.update(assignment)
                .set(assignment.STATUS, "ENDED")
                .set(assignment.EFFECTIVE_TO, now)
                .set(assignment.ENDED_BY, context.actorId())
                .set(assignment.END_REASON_CODE, "REASSIGNED")
                .where(assignment.TENANT_ID.eq(context.tenantId()))
                .and(assignment.SERVICE_ASSIGNMENT_ID.eq(newAssignment.supersedesServiceAssignmentId()))
                .and(assignment.TASK_ID.eq(newAssignment.taskId()))
                .and(assignment.RESPONSIBILITY_LEVEL.eq(newAssignment.responsibilityLevel()))
                .and(assignment.STATUS.eq("ACTIVE"))
                .execute();
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
        DspCapacityReservation reservation = DSP_CAPACITY_RESERVATION;
        int reservationUpdated = dsl.update(reservation)
                .set(reservation.STATUS, "RELEASED")
                .set(reservation.RELEASED_AT, now)
                .set(reservation.RELEASED_BY, context.actorId())
                .set(reservation.RELEASE_REASON_CODE, reasonCode)
                .where(reservation.TENANT_ID.eq(context.tenantId()))
                .and(reservation.CAPACITY_RESERVATION_ID.eq(state.capacityReservationId()))
                .and(reservation.STATUS.in("HELD", "CONFIRMED"))
                .execute();
        if (reservationUpdated != 1) {
            throw new BusinessProblem(
                    ProblemCode.DISPATCH_CAPACITY_CONFLICT,
                    "Capacity reservation was already released");
        }
        DspCapacityCounter counterTable = DSP_CAPACITY_COUNTER;
        int counterUpdated = dsl.update(counterTable)
                .set(counterTable.OCCUPIED_UNITS, counterTable.OCCUPIED_UNITS.minus(state.reservationUnits()))
                .set(counterTable.VERSION, counterTable.VERSION.plus(1))
                .set(counterTable.UPDATED_BY, context.actorId())
                .set(counterTable.UPDATED_AT, now)
                .where(counterTable.TENANT_ID.eq(context.tenantId()))
                .and(counterTable.CAPACITY_COUNTER_ID.eq(state.capacityCounterId()))
                .and(counterTable.OCCUPIED_UNITS.ge(state.reservationUnits()))
                .execute();
        if (counterUpdated != 1) {
            throw new BusinessProblem(
                    ProblemCode.DISPATCH_CAPACITY_CONFLICT,
                    "Capacity counter could not release reserved units");
        }
    }

    private boolean activeAssignmentExists(String tenantId, UUID taskId, String level) {
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT;
        return dsl.fetchExists(assignment,
                assignment.TENANT_ID.eq(tenantId)
                        .and(assignment.TASK_ID.eq(taskId))
                        .and(assignment.RESPONSIBILITY_LEVEL.eq(level))
                        .and(assignment.STATUS.eq("ACTIVE")));
    }

    private AssignmentState assignment(String tenantId, UUID assignmentId) {
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT.as("assignment");
        DspCapacityReservation reservation = DSP_CAPACITY_RESERVATION.as("reservation");
        // 与原查询一致：reservation JOIN 仅按 service_assignment_id，不加 tenant 条件。
        Record row = dsl.select(
                        assignment.SERVICE_ASSIGNMENT_ID,
                        assignment.ACTIVATION_SAGA_ID,
                        assignment.WORK_ORDER_ID,
                        assignment.TASK_ID,
                        assignment.RESPONSIBILITY_LEVEL,
                        assignment.ASSIGNEE_ID,
                        assignment.BUSINESS_TYPE,
                        assignment.STATUS,
                        assignment.SUPERSEDES_SERVICE_ASSIGNMENT_ID,
                        assignment.TASK_EXECUTION_GUARD_ID,
                        assignment.PREPARED_TASK_ASSIGNMENT_ID,
                        assignment.CREATED_BY,
                        assignment.ACTIVATION_PROTOCOL_VERSION,
                        assignment.PENDING_AUTHORITY_ASSIGNMENT_ID,
                        assignment.PENDING_AUTHORITY_VERSION,
                        assignment.PENDING_FENCE_DECISION_ID,
                        assignment.PENDING_FENCE_POLICY_VERSION,
                        reservation.CAPACITY_RESERVATION_ID,
                        reservation.CAPACITY_COUNTER_ID,
                        reservation.UNITS)
                .from(assignment)
                .join(reservation)
                .on(reservation.SERVICE_ASSIGNMENT_ID.eq(assignment.SERVICE_ASSIGNMENT_ID))
                .where(assignment.TENANT_ID.eq(tenantId))
                .and(assignment.SERVICE_ASSIGNMENT_ID.eq(assignmentId))
                .fetchOptional()
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "ServiceAssignment does not exist"));
        return new AssignmentState(
                row.get(assignment.SERVICE_ASSIGNMENT_ID),
                row.get(assignment.ACTIVATION_SAGA_ID),
                row.get(assignment.WORK_ORDER_ID),
                row.get(assignment.TASK_ID),
                row.get(assignment.RESPONSIBILITY_LEVEL),
                row.get(assignment.ASSIGNEE_ID),
                row.get(assignment.BUSINESS_TYPE),
                row.get(assignment.STATUS),
                row.get(assignment.SUPERSEDES_SERVICE_ASSIGNMENT_ID),
                row.get(assignment.TASK_EXECUTION_GUARD_ID),
                row.get(assignment.PREPARED_TASK_ASSIGNMENT_ID),
                row.get(assignment.CREATED_BY),
                row.get(assignment.ACTIVATION_PROTOCOL_VERSION),
                row.get(assignment.PENDING_AUTHORITY_ASSIGNMENT_ID),
                row.get(assignment.PENDING_AUTHORITY_VERSION),
                row.get(assignment.PENDING_FENCE_DECISION_ID),
                row.get(assignment.PENDING_FENCE_POLICY_VERSION),
                row.get(reservation.CAPACITY_RESERVATION_ID),
                row.get(reservation.CAPACITY_COUNTER_ID),
                row.get(reservation.UNITS));
    }

    private void throwSagaConflict(
            String tenantId,
            UUID sagaId,
            UUID assignmentId,
            long expectedVersion,
            String expectedStage
    ) {
        DspServiceAssignmentActivationSaga saga = DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA;
        SagaState state = dsl.select(saga.STAGE, saga.VERSION)
                .from(saga)
                .where(saga.TENANT_ID.eq(tenantId))
                .and(saga.ACTIVATION_SAGA_ID.eq(sagaId))
                .and(saga.NEW_SERVICE_ASSIGNMENT_ID.eq(assignmentId))
                .fetchOptional(JooqServiceAssignmentService::mapSagaState)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "ServiceAssignment activation saga does not exist"));
        if (state.version() != expectedVersion) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "Activation saga version changed");
        }
        throw new BusinessProblem(
                ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                "Activation saga requires stage " + expectedStage + " but was " + state.stage());
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
        DspAssignmentCommandResult result = DSP_ASSIGNMENT_COMMAND_RESULT;
        dsl.insertInto(result)
                .set(result.TENANT_ID, context.tenantId())
                .set(result.OPERATION_TYPE, operation)
                .set(result.IDEMPOTENCY_KEY, context.idempotencyKey())
                .set(result.SERVICE_ASSIGNMENT_ID, receipt.serviceAssignmentId())
                .set(result.ACTIVATION_SAGA_ID, receipt.sagaId())
                .set(result.TASK_ID, receipt.taskId())
                .set(result.CAPACITY_RESERVATION_ID, receipt.capacityReservationId())
                .set(result.ASSIGNMENT_STATUS, receipt.assignmentStatus())
                .set(result.SAGA_STAGE, receipt.sagaStage())
                .set(result.SAGA_VERSION, receipt.sagaVersion())
                .set(result.OCCURRED_AT, receipt.occurredAt())
                .execute();
    }

    private ServiceAssignmentReceipt frozenReceipt(CommandContext context, String operation) {
        DspAssignmentCommandResult result = DSP_ASSIGNMENT_COMMAND_RESULT;
        return dsl.select(
                        result.SERVICE_ASSIGNMENT_ID,
                        result.ACTIVATION_SAGA_ID,
                        result.TASK_ID,
                        result.CAPACITY_RESERVATION_ID,
                        result.ASSIGNMENT_STATUS,
                        result.SAGA_STAGE,
                        result.SAGA_VERSION,
                        result.OCCURRED_AT)
                .from(result)
                .where(result.TENANT_ID.eq(context.tenantId()))
                .and(result.OPERATION_TYPE.eq(operation))
                .and(result.IDEMPOTENCY_KEY.eq(context.idempotencyKey()))
                .fetchSingle(JooqServiceAssignmentService::mapReceipt);
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
                || "service.assignment.task-prepared".equals(eventType)
                || "service.assignment.activated".equals(eventType)
                || "service.assignment.activation-aborted".equals(eventType));
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

    private static ActiveNetworkRow mapActiveNetworkRow(Record5<UUID, UUID, String, UUID, Long> row) {
        return new ActiveNetworkRow(row.value1(), row.value2(), row.value3(), row.value4(), row.value5());
    }

    private static OldAssignment mapOldAssignment(
            Record6<UUID, UUID, String, String, String, String> row) {
        return new OldAssignment(
                row.value1(), row.value2(), row.value3(), row.value4(), row.value5(), row.value6());
    }

    private static CapacityCounter mapCapacityCounter(Record4<UUID, Integer, Integer, Long> row) {
        return new CapacityCounter(row.value1(), row.value2(), row.value3(), row.value4());
    }

    private static SagaState mapSagaState(Record2<String, Long> row) {
        return new SagaState(row.value1(), row.value2());
    }

    private static ServiceAssignmentReceipt mapReceipt(Record8<
            UUID, UUID, UUID, UUID, String, String, Long, Instant> row) {
        return new ServiceAssignmentReceipt(
                row.value1(), row.value2(), row.value3(), row.value4(),
                row.value5(), row.value6(), row.value7(), row.value8());
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
