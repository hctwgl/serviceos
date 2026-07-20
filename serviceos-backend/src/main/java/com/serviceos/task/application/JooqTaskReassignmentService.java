package com.serviceos.task.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.jooq.generated.tables.TskTask;
import com.serviceos.jooq.generated.tables.TskTaskAssignment;
import com.serviceos.jooq.generated.tables.TskTaskExecutionGuard;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.AbortPreparedTaskAssignmentCommand;
import com.serviceos.task.api.ActivatePreparedTaskAssignmentCommand;
import com.serviceos.task.api.PrepareTaskReassignmentCommand;
import com.serviceos.task.api.TaskAssignmentChangedPayload;
import com.serviceos.task.api.TaskExecutionGuardChangedPayload;
import com.serviceos.task.api.TaskReassignmentReceipt;
import com.serviceos.task.api.TaskReassignmentService;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;
import static com.serviceos.jooq.generated.tables.TskTaskAssignment.TSK_TASK_ASSIGNMENT;
import static com.serviceos.jooq.generated.tables.TskTaskExecutionGuard.TSK_TASK_EXECUTION_GUARD;
import static com.serviceos.jooq.generated.tables.TskTaskReassignmentCommandResult.TSK_TASK_REASSIGNMENT_COMMAND_RESULT;

/**
 * Task 侧可靠改派握手。
 *
 * <p>prepare 在一个事务中同时建立 guard 和 PREPARED 责任；activate 只有在收到同一
 * ServiceAssignment 的激活确认后才切换当前责任并解除 guard；abort 仅适用于外部责任尚未切换的路径。</p>
 */
@Service
final class JooqTaskReassignmentService implements TaskReassignmentService {
    private static final String PREPARE = "task.reassignment.prepare";
    private static final String ACTIVATE = "task.reassignment.activate";
    private static final String ABORT = "task.reassignment.abort";
    private static final String CAPABILITY = "task.reassignment.manage";

    private final DSLContext dsl;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqTaskReassignmentService(
            DSLContext dsl,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.dsl = dsl;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TaskReassignmentReceipt prepare(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            PrepareTaskReassignmentCommand command
    ) {
        CommandContext context = context(principal, metadata);
        String digest = Sha256.digest(command.taskId() + "|" + command.expectedVersion() + "|"
                + command.preparationKey() + "|" + command.newPrincipalId() + "|"
                + command.pendingServiceAssignmentId() + "|" + command.reasonCode());
        AuthorizationDecision authorizationDecision = authorize(principal, context, command.taskId());
        IdempotencyDecision decision = idempotency.begin(context, PREPARE, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return frozenReceipt(context, PREPARE);
        }

        Instant now = clock.instant();
        TskTask task = TSK_TASK;
        TskTaskAssignment responsible = TSK_TASK_ASSIGNMENT.as("responsible");
        TskTaskExecutionGuard guardRow = TSK_TASK_EXECUTION_GUARD.as("guard_row");
        TskTaskAssignment preparedRow = TSK_TASK_ASSIGNMENT.as("prepared");
        TskTaskExecutionGuard usedKey = TSK_TASK_EXECUTION_GUARD.as("used_key");
        int taskUpdated = dsl.update(task)
                .set(task.VERSION, task.VERSION.plus(1))
                .set(task.UPDATED_AT, now)
                .where(task.TENANT_ID.eq(context.tenantId()))
                .and(task.TASK_ID.eq(command.taskId()))
                .and(task.TASK_KIND.eq("HUMAN"))
                .and(task.STATUS.in("CLAIMED", "RUNNING"))
                .and(task.VERSION.eq(command.expectedVersion()))
                .and(DSL.exists(DSL.selectOne()
                        .from(responsible)
                        .where(responsible.TENANT_ID.eq(task.TENANT_ID))
                        .and(responsible.TASK_ID.eq(task.TASK_ID))
                        .and(responsible.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                        .and(responsible.STATUS.eq("ACTIVE"))
                        .and(responsible.PRINCIPAL_ID.ne(command.newPrincipalId()))))
                .and(DSL.notExists(DSL.selectOne()
                        .from(guardRow)
                        .where(guardRow.TENANT_ID.eq(task.TENANT_ID))
                        .and(guardRow.TASK_ID.eq(task.TASK_ID))
                        .and(guardRow.STATUS.eq("ACTIVE"))))
                .and(DSL.notExists(DSL.selectOne()
                        .from(preparedRow)
                        .where(preparedRow.TENANT_ID.eq(task.TENANT_ID))
                        .and(preparedRow.TASK_ID.eq(task.TASK_ID))
                        .and(preparedRow.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                        .and(preparedRow.STATUS.eq("PREPARED"))))
                .and(DSL.notExists(DSL.selectOne()
                        .from(usedKey)
                        .where(usedKey.TENANT_ID.eq(context.tenantId()))
                        .and(usedKey.GUARD_TYPE.eq("REASSIGNMENT"))
                        .and(usedKey.GUARD_KEY.eq(command.preparationKey()))))
                .execute();
        if (taskUpdated != 1) {
            throwPrepareConflict(context.tenantId(), command);
        }

        UUID oldResponsibilityId = activeResponsibility(context.tenantId(), command.taskId()).taskAssignmentId();
        UUID guardId = UUID.randomUUID();
        UUID preparedAssignmentId = UUID.randomUUID();
        long taskVersion = command.expectedVersion() + 1;
        dsl.insertInto(TSK_TASK_EXECUTION_GUARD)
                .set(TSK_TASK_EXECUTION_GUARD.TASK_EXECUTION_GUARD_ID, guardId)
                .set(TSK_TASK_EXECUTION_GUARD.TENANT_ID, context.tenantId())
                .set(TSK_TASK_EXECUTION_GUARD.TASK_ID, command.taskId())
                .set(TSK_TASK_EXECUTION_GUARD.GUARD_TYPE, "REASSIGNMENT")
                .set(TSK_TASK_EXECUTION_GUARD.GUARD_KEY, command.preparationKey())
                .set(TSK_TASK_EXECUTION_GUARD.REASON_CODE, command.reasonCode())
                .set(TSK_TASK_EXECUTION_GUARD.STATUS, "ACTIVE")
                .set(TSK_TASK_EXECUTION_GUARD.ACTIVATED_TASK_VERSION, taskVersion)
                .set(TSK_TASK_EXECUTION_GUARD.ACTIVATED_BY, context.actorId())
                .set(TSK_TASK_EXECUTION_GUARD.ACTIVATED_AT, now)
                .execute();
        dsl.insertInto(TSK_TASK_ASSIGNMENT)
                .set(TSK_TASK_ASSIGNMENT.TASK_ASSIGNMENT_ID, preparedAssignmentId)
                .set(TSK_TASK_ASSIGNMENT.TENANT_ID, context.tenantId())
                .set(TSK_TASK_ASSIGNMENT.TASK_ID, command.taskId())
                .set(TSK_TASK_ASSIGNMENT.ASSIGNMENT_KIND, "RESPONSIBLE")
                .set(TSK_TASK_ASSIGNMENT.PRINCIPAL_TYPE, "USER")
                .set(TSK_TASK_ASSIGNMENT.PRINCIPAL_ID, command.newPrincipalId())
                .set(TSK_TASK_ASSIGNMENT.STATUS, "PREPARED")
                .set(TSK_TASK_ASSIGNMENT.SOURCE_TYPE, "SERVICE_ASSIGNMENT_PENDING")
                .set(TSK_TASK_ASSIGNMENT.SOURCE_ID, command.pendingServiceAssignmentId())
                .set(TSK_TASK_ASSIGNMENT.SUPERSEDES_TASK_ASSIGNMENT_ID, oldResponsibilityId)
                .set(TSK_TASK_ASSIGNMENT.CREATED_BY, context.actorId())
                .set(TSK_TASK_ASSIGNMENT.CREATED_AT, now)
                .set(TSK_TASK_ASSIGNMENT.TASK_EXECUTION_GUARD_ID, guardId)
                .set(TSK_TASK_ASSIGNMENT.PREPARATION_KEY, command.preparationKey())
                .execute();

        TaskReassignmentReceipt receipt = new TaskReassignmentReceipt(
                command.taskId(), guardId, preparedAssignmentId, command.newPrincipalId(),
                "PREPARED", taskVersion, now);
        insertFrozenReceipt(context, PREPARE, receipt);
        appendGuardEvent(context, receipt, command.preparationKey(), "ACTIVE",
                command.reasonCode(), "task.execution-guard.activated");
        appendAssignmentEvent(context, receipt, command.preparationKey(),
                command.pendingServiceAssignmentId(), command.reasonCode(), "task.assignment-prepared");
        appendAudit(context, authorizationDecision, command.taskId(),
                "TASK_REASSIGNMENT_PREPARE", digest, now);
        idempotency.complete(
                context, PREPARE, preparedAssignmentId.toString(), Sha256.digest(serialize(receipt)));
        return receipt;
    }

    @Override
    @Transactional
    public TaskReassignmentReceipt activate(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ActivatePreparedTaskAssignmentCommand command
    ) {
        CommandContext context = context(principal, metadata);
        String digest = Sha256.digest(command.taskId() + "|" + command.guardId() + "|"
                + command.preparedTaskAssignmentId() + "|" + command.expectedVersion()
                + "|" + command.activeServiceAssignmentId());
        AuthorizationDecision authorizationDecision = authorize(principal, context, command.taskId());
        IdempotencyDecision decision = idempotency.begin(context, ACTIVATE, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return frozenReceipt(context, ACTIVATE);
        }

        PreparedAssignment prepared = prepared(context.tenantId(), command.preparedTaskAssignmentId());
        if (!prepared.sourceId().equals(command.activeServiceAssignmentId())) {
            throw new BusinessProblem(
                    ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    "Activated ServiceAssignment does not match the prepared source");
        }
        Instant now = clock.instant();
        TskTask task = TSK_TASK;
        TskTaskExecutionGuard guardRow = TSK_TASK_EXECUTION_GUARD.as("guard_row");
        TskTaskAssignment assignmentRow = TSK_TASK_ASSIGNMENT.as("assignment");
        // claimed_at 仅对 CLAIMED 状态刷新，RUNNING 保留首次开始时间（与原 CASE WHEN 语义一致）。
        int taskUpdated = dsl.update(task)
                .set(task.CLAIMED_BY, prepared.principalId())
                .set(task.CLAIMED_AT, DSL.when(task.STATUS.eq("CLAIMED"), now).otherwise(task.CLAIMED_AT))
                .set(task.VERSION, task.VERSION.plus(1))
                .set(task.UPDATED_AT, now)
                .where(task.TENANT_ID.eq(context.tenantId()))
                .and(task.TASK_ID.eq(command.taskId()))
                .and(task.TASK_KIND.eq("HUMAN"))
                .and(task.STATUS.in("CLAIMED", "RUNNING"))
                .and(task.VERSION.eq(command.expectedVersion()))
                .and(DSL.exists(DSL.selectOne()
                        .from(guardRow)
                        .where(guardRow.TENANT_ID.eq(task.TENANT_ID))
                        .and(guardRow.TASK_ID.eq(task.TASK_ID))
                        .and(guardRow.TASK_EXECUTION_GUARD_ID.eq(command.guardId()))
                        .and(guardRow.STATUS.eq("ACTIVE"))))
                .and(DSL.exists(DSL.selectOne()
                        .from(assignmentRow)
                        .where(assignmentRow.TENANT_ID.eq(task.TENANT_ID))
                        .and(assignmentRow.TASK_ID.eq(task.TASK_ID))
                        .and(assignmentRow.TASK_ASSIGNMENT_ID.eq(command.preparedTaskAssignmentId()))
                        .and(assignmentRow.TASK_EXECUTION_GUARD_ID.eq(command.guardId()))
                        .and(assignmentRow.STATUS.eq("PREPARED"))))
                .execute();
        if (taskUpdated != 1) {
            throwTransitionConflict(context.tenantId(), command.taskId(), command.guardId(),
                    command.preparedTaskAssignmentId(), command.expectedVersion(), "activate");
        }

        closeActiveAssignments(context, command.taskId(), now);
        int assignmentUpdated = dsl.update(TSK_TASK_ASSIGNMENT)
                .set(TSK_TASK_ASSIGNMENT.STATUS, "ACTIVE")
                .set(TSK_TASK_ASSIGNMENT.EFFECTIVE_FROM, now)
                .set(TSK_TASK_ASSIGNMENT.ACTIVATION_REF, command.activeServiceAssignmentId())
                .where(TSK_TASK_ASSIGNMENT.TENANT_ID.eq(context.tenantId()))
                .and(TSK_TASK_ASSIGNMENT.TASK_ID.eq(command.taskId()))
                .and(TSK_TASK_ASSIGNMENT.TASK_ASSIGNMENT_ID.eq(command.preparedTaskAssignmentId()))
                .and(TSK_TASK_ASSIGNMENT.TASK_EXECUTION_GUARD_ID.eq(command.guardId()))
                .and(TSK_TASK_ASSIGNMENT.STATUS.eq("PREPARED"))
                .execute();
        if (assignmentUpdated != 1) {
            throw new BusinessProblem(ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    "PREPARED responsibility could not be activated");
        }
        insertActiveCandidate(context, command.taskId(), prepared, now);
        long taskVersion = command.expectedVersion() + 1;
        releaseGuard(context, command.taskId(), command.guardId(), taskVersion,
                "TASK_ASSIGNMENT_ACTIVATED", now);

        TaskReassignmentReceipt receipt = new TaskReassignmentReceipt(
                command.taskId(), command.guardId(), command.preparedTaskAssignmentId(),
                prepared.principalId(), "ACTIVE", taskVersion, now);
        insertFrozenReceipt(context, ACTIVATE, receipt);
        appendAssignmentEvent(context, receipt, prepared.preparationKey(),
                command.activeServiceAssignmentId(), "SERVICE_ASSIGNMENT_ACTIVATED",
                "task.assignment-activated");
        appendGuardEvent(context, receipt, prepared.preparationKey(), "RELEASED",
                "TASK_ASSIGNMENT_ACTIVATED", "task.execution-guard.released");
        appendAudit(context, authorizationDecision, command.taskId(),
                "TASK_REASSIGNMENT_ACTIVATE", digest, now);
        idempotency.complete(
                context, ACTIVATE, command.preparedTaskAssignmentId().toString(),
                Sha256.digest(serialize(receipt)));
        return receipt;
    }

    @Override
    @Transactional
    public TaskReassignmentReceipt abort(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            AbortPreparedTaskAssignmentCommand command
    ) {
        CommandContext context = context(principal, metadata);
        String digest = Sha256.digest(command.taskId() + "|" + command.guardId() + "|"
                + command.preparedTaskAssignmentId() + "|" + command.expectedVersion()
                + "|" + command.reasonCode());
        AuthorizationDecision authorizationDecision = authorize(principal, context, command.taskId());
        IdempotencyDecision decision = idempotency.begin(context, ABORT, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return frozenReceipt(context, ABORT);
        }

        PreparedAssignment prepared = prepared(context.tenantId(), command.preparedTaskAssignmentId());
        Instant now = clock.instant();
        TskTask task = TSK_TASK;
        TskTaskExecutionGuard guardRow = TSK_TASK_EXECUTION_GUARD.as("guard_row");
        TskTaskAssignment assignmentRow = TSK_TASK_ASSIGNMENT.as("assignment");
        int taskUpdated = dsl.update(task)
                .set(task.VERSION, task.VERSION.plus(1))
                .set(task.UPDATED_AT, now)
                .where(task.TENANT_ID.eq(context.tenantId()))
                .and(task.TASK_ID.eq(command.taskId()))
                .and(task.TASK_KIND.eq("HUMAN"))
                .and(task.STATUS.in("CLAIMED", "RUNNING"))
                .and(task.VERSION.eq(command.expectedVersion()))
                .and(DSL.exists(DSL.selectOne()
                        .from(guardRow)
                        .where(guardRow.TENANT_ID.eq(task.TENANT_ID))
                        .and(guardRow.TASK_ID.eq(task.TASK_ID))
                        .and(guardRow.TASK_EXECUTION_GUARD_ID.eq(command.guardId()))
                        .and(guardRow.STATUS.eq("ACTIVE"))))
                .and(DSL.exists(DSL.selectOne()
                        .from(assignmentRow)
                        .where(assignmentRow.TENANT_ID.eq(task.TENANT_ID))
                        .and(assignmentRow.TASK_ID.eq(task.TASK_ID))
                        .and(assignmentRow.TASK_ASSIGNMENT_ID.eq(command.preparedTaskAssignmentId()))
                        .and(assignmentRow.TASK_EXECUTION_GUARD_ID.eq(command.guardId()))
                        .and(assignmentRow.STATUS.eq("PREPARED"))))
                .execute();
        if (taskUpdated != 1) {
            throwTransitionConflict(context.tenantId(), command.taskId(), command.guardId(),
                    command.preparedTaskAssignmentId(), command.expectedVersion(), "abort");
        }
        dsl.update(TSK_TASK_ASSIGNMENT)
                .set(TSK_TASK_ASSIGNMENT.STATUS, "ABORTED")
                .set(TSK_TASK_ASSIGNMENT.REVOKED_BY, context.actorId())
                .set(TSK_TASK_ASSIGNMENT.REVOKE_REASON_CODE, command.reasonCode())
                .where(TSK_TASK_ASSIGNMENT.TENANT_ID.eq(context.tenantId()))
                .and(TSK_TASK_ASSIGNMENT.TASK_ID.eq(command.taskId()))
                .and(TSK_TASK_ASSIGNMENT.TASK_ASSIGNMENT_ID.eq(command.preparedTaskAssignmentId()))
                .and(TSK_TASK_ASSIGNMENT.TASK_EXECUTION_GUARD_ID.eq(command.guardId()))
                .and(TSK_TASK_ASSIGNMENT.STATUS.eq("PREPARED"))
                .execute();
        long taskVersion = command.expectedVersion() + 1;
        releaseGuard(context, command.taskId(), command.guardId(), taskVersion,
                "PREPARATION_ABORTED", now);

        TaskReassignmentReceipt receipt = new TaskReassignmentReceipt(
                command.taskId(), command.guardId(), command.preparedTaskAssignmentId(),
                prepared.principalId(), "ABORTED", taskVersion, now);
        insertFrozenReceipt(context, ABORT, receipt);
        appendAssignmentEvent(context, receipt, prepared.preparationKey(), prepared.sourceId(),
                command.reasonCode(), "task.assignment-aborted");
        appendGuardEvent(context, receipt, prepared.preparationKey(), "RELEASED",
                "PREPARATION_ABORTED", "task.execution-guard.released");
        appendAudit(context, authorizationDecision, command.taskId(),
                "TASK_REASSIGNMENT_ABORT", digest, now);
        idempotency.complete(
                context, ABORT, command.preparedTaskAssignmentId().toString(),
                Sha256.digest(serialize(receipt)));
        return receipt;
    }

    private CommandContext context(CurrentPrincipal principal, CommandMetadata metadata) {
        return new CommandContext(principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
    }

    private AuthorizationDecision authorize(CurrentPrincipal principal, CommandContext context, UUID taskId) {
        return authorization.require(principal,
                AuthorizationRequest.tenantCapability(
                        CAPABILITY, context.tenantId(), "Task", taskId.toString()),
                context.correlationId());
    }

    private void throwPrepareConflict(String tenantId, PrepareTaskReassignmentCommand command) {
        TaskReassignmentState state = state(tenantId, command.taskId());
        if (state.activeGuard() || state.preparedAssignment()) {
            throw new BusinessProblem(
                    ProblemCode.TASK_EXECUTION_GUARDED,
                    "Task already has an active responsibility transition");
        }
        if (state.version() != command.expectedVersion()) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "Task version changed");
        }
        if (!"HUMAN".equals(state.taskKind())
                || !("CLAIMED".equals(state.status()) || "RUNNING".equals(state.status()))) {
            throw new BusinessProblem(
                    ProblemCode.TASK_STATE_CONFLICT,
                    "Reassignment preparation requires a CLAIMED or RUNNING HUMAN task");
        }
        if (command.newPrincipalId().equals(state.currentPrincipalId())) {
            throw new BusinessProblem(
                    ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    "New principal must differ from the current responsible principal");
        }
        throw new BusinessProblem(
                ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                "Task does not have an ACTIVE responsible assignment or preparation key was already used");
    }

    private void throwTransitionConflict(
            String tenantId,
            UUID taskId,
            UUID guardId,
            UUID assignmentId,
            long expectedVersion,
            String operation
    ) {
        TaskReassignmentState state = state(tenantId, taskId);
        if (state.version() != expectedVersion) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "Task version changed");
        }
        TskTaskExecutionGuard guardRow = TSK_TASK_EXECUTION_GUARD.as("guard_row");
        TskTaskAssignment assignment = TSK_TASK_ASSIGNMENT.as("assignment");
        boolean linked = dsl.fetchExists(
                DSL.selectOne()
                        .from(guardRow)
                        .join(assignment)
                        .on(assignment.TASK_EXECUTION_GUARD_ID.eq(guardRow.TASK_EXECUTION_GUARD_ID))
                        .and(assignment.TENANT_ID.eq(guardRow.TENANT_ID))
                        .and(assignment.TASK_ID.eq(guardRow.TASK_ID))
                        .where(guardRow.TENANT_ID.eq(tenantId))
                        .and(guardRow.TASK_ID.eq(taskId))
                        .and(guardRow.TASK_EXECUTION_GUARD_ID.eq(guardId))
                        .and(guardRow.STATUS.eq("ACTIVE"))
                        .and(assignment.TASK_ASSIGNMENT_ID.eq(assignmentId))
                        .and(assignment.STATUS.eq("PREPARED")));
        if (!linked) {
            throw new BusinessProblem(
                    ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    operation + " requires the matching ACTIVE guard and PREPARED responsibility");
        }
        throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                "Task responsibility transition failed");
    }

    private TaskReassignmentState state(String tenantId, UUID taskId) {
        TskTask task = TSK_TASK;
        TskTaskAssignment responsible = TSK_TASK_ASSIGNMENT.as("responsible");
        TskTaskExecutionGuard guardRow = TSK_TASK_EXECUTION_GUARD.as("guard_row");
        TskTaskAssignment prepared = TSK_TASK_ASSIGNMENT.as("prepared");
        Field<String> currentPrincipalId = DSL.field(dsl.select(responsible.PRINCIPAL_ID)
                        .from(responsible)
                        .where(responsible.TENANT_ID.eq(task.TENANT_ID))
                        .and(responsible.TASK_ID.eq(task.TASK_ID))
                        .and(responsible.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                        .and(responsible.STATUS.eq("ACTIVE")))
                .as("current_principal_id");
        Field<Boolean> activeGuard = DSL.exists(DSL.selectOne()
                        .from(guardRow)
                        .where(guardRow.TENANT_ID.eq(task.TENANT_ID))
                        .and(guardRow.TASK_ID.eq(task.TASK_ID))
                        .and(guardRow.STATUS.eq("ACTIVE")))
                .as("active_guard");
        Field<Boolean> preparedAssignment = DSL.exists(DSL.selectOne()
                        .from(prepared)
                        .where(prepared.TENANT_ID.eq(task.TENANT_ID))
                        .and(prepared.TASK_ID.eq(task.TASK_ID))
                        .and(prepared.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                        .and(prepared.STATUS.eq("PREPARED")))
                .as("prepared_assignment");
        return dsl.select(task.TASK_KIND, task.STATUS, task.VERSION,
                        currentPrincipalId, activeGuard, preparedAssignment)
                .from(task)
                .where(task.TENANT_ID.eq(tenantId))
                .and(task.TASK_ID.eq(taskId))
                .fetchOptional(record -> new TaskReassignmentState(
                        record.get(task.TASK_KIND), record.get(task.STATUS), record.get(task.VERSION),
                        record.get(currentPrincipalId), record.get(activeGuard),
                        record.get(preparedAssignment)))
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
    }

    private ActiveResponsibility activeResponsibility(String tenantId, UUID taskId) {
        return dsl.select(TSK_TASK_ASSIGNMENT.TASK_ASSIGNMENT_ID)
                .from(TSK_TASK_ASSIGNMENT)
                .where(TSK_TASK_ASSIGNMENT.TENANT_ID.eq(tenantId))
                .and(TSK_TASK_ASSIGNMENT.TASK_ID.eq(taskId))
                .and(TSK_TASK_ASSIGNMENT.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                .and(TSK_TASK_ASSIGNMENT.STATUS.eq("ACTIVE"))
                .fetchSingle(record -> new ActiveResponsibility(record.value1()));
    }

    private PreparedAssignment prepared(String tenantId, UUID assignmentId) {
        return dsl.select(TSK_TASK_ASSIGNMENT.TASK_ASSIGNMENT_ID, TSK_TASK_ASSIGNMENT.TASK_ID,
                        TSK_TASK_ASSIGNMENT.TASK_EXECUTION_GUARD_ID, TSK_TASK_ASSIGNMENT.PREPARATION_KEY,
                        TSK_TASK_ASSIGNMENT.PRINCIPAL_ID, TSK_TASK_ASSIGNMENT.SOURCE_ID)
                .from(TSK_TASK_ASSIGNMENT)
                .where(TSK_TASK_ASSIGNMENT.TENANT_ID.eq(tenantId))
                .and(TSK_TASK_ASSIGNMENT.TASK_ASSIGNMENT_ID.eq(assignmentId))
                .fetchOptional(record -> new PreparedAssignment(
                        record.value1(), record.value2(), record.value3(),
                        record.value4(), record.value5(), record.value6()))
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                        "Prepared TaskAssignment does not exist"));
    }

    private void closeActiveAssignments(CommandContext context, UUID taskId, Instant now) {
        int updated = dsl.update(TSK_TASK_ASSIGNMENT)
                .set(TSK_TASK_ASSIGNMENT.STATUS, "REVOKED")
                .set(TSK_TASK_ASSIGNMENT.EFFECTIVE_TO, now)
                .set(TSK_TASK_ASSIGNMENT.REVOKED_BY, context.actorId())
                .set(TSK_TASK_ASSIGNMENT.REVOKE_REASON_CODE, "REASSIGNED")
                .where(TSK_TASK_ASSIGNMENT.TENANT_ID.eq(context.tenantId()))
                .and(TSK_TASK_ASSIGNMENT.TASK_ID.eq(taskId))
                .and(TSK_TASK_ASSIGNMENT.STATUS.eq("ACTIVE"))
                .and(TSK_TASK_ASSIGNMENT.ASSIGNMENT_KIND.in("CANDIDATE", "RESPONSIBLE"))
                .execute();
        if (updated < 2) {
            throw new BusinessProblem(
                    ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    "Responsibility activation must close the old candidate and responsible facts");
        }
    }

    private void insertActiveCandidate(
            CommandContext context, UUID taskId, PreparedAssignment prepared, Instant now) {
        dsl.insertInto(TSK_TASK_ASSIGNMENT)
                .set(TSK_TASK_ASSIGNMENT.TASK_ASSIGNMENT_ID, UUID.randomUUID())
                .set(TSK_TASK_ASSIGNMENT.TENANT_ID, context.tenantId())
                .set(TSK_TASK_ASSIGNMENT.TASK_ID, taskId)
                .set(TSK_TASK_ASSIGNMENT.ASSIGNMENT_KIND, "CANDIDATE")
                .set(TSK_TASK_ASSIGNMENT.PRINCIPAL_TYPE, "USER")
                .set(TSK_TASK_ASSIGNMENT.PRINCIPAL_ID, prepared.principalId())
                .set(TSK_TASK_ASSIGNMENT.STATUS, "ACTIVE")
                .set(TSK_TASK_ASSIGNMENT.SOURCE_TYPE, "SERVICE_ASSIGNMENT")
                .set(TSK_TASK_ASSIGNMENT.SOURCE_ID, prepared.sourceId())
                .set(TSK_TASK_ASSIGNMENT.EFFECTIVE_FROM, now)
                .set(TSK_TASK_ASSIGNMENT.CREATED_BY, context.actorId())
                .set(TSK_TASK_ASSIGNMENT.CREATED_AT, now)
                .set(TSK_TASK_ASSIGNMENT.TASK_EXECUTION_GUARD_ID, prepared.taskExecutionGuardId())
                .setNull(TSK_TASK_ASSIGNMENT.PREPARATION_KEY)
                .set(TSK_TASK_ASSIGNMENT.ACTIVATION_REF, prepared.sourceId())
                .execute();
    }

    private void releaseGuard(
            CommandContext context,
            UUID taskId,
            UUID guardId,
            long taskVersion,
            String reasonCode,
            Instant now
    ) {
        int updated = dsl.update(TSK_TASK_EXECUTION_GUARD)
                .set(TSK_TASK_EXECUTION_GUARD.STATUS, "RELEASED")
                .set(TSK_TASK_EXECUTION_GUARD.RELEASED_TASK_VERSION, taskVersion)
                .set(TSK_TASK_EXECUTION_GUARD.RELEASED_BY, context.actorId())
                .set(TSK_TASK_EXECUTION_GUARD.RELEASED_AT, now)
                .set(TSK_TASK_EXECUTION_GUARD.RELEASE_REASON_CODE, reasonCode)
                .where(TSK_TASK_EXECUTION_GUARD.TENANT_ID.eq(context.tenantId()))
                .and(TSK_TASK_EXECUTION_GUARD.TASK_ID.eq(taskId))
                .and(TSK_TASK_EXECUTION_GUARD.TASK_EXECUTION_GUARD_ID.eq(guardId))
                .and(TSK_TASK_EXECUTION_GUARD.STATUS.eq("ACTIVE"))
                .execute();
        if (updated != 1) {
            throw new BusinessProblem(
                    ProblemCode.TASK_EXECUTION_GUARDED,
                    "ACTIVE reassignment guard could not be released");
        }
    }

    private void insertFrozenReceipt(
            CommandContext context, String operation, TaskReassignmentReceipt receipt) {
        dsl.insertInto(TSK_TASK_REASSIGNMENT_COMMAND_RESULT)
                .set(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.TENANT_ID, context.tenantId())
                .set(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.OPERATION_TYPE, operation)
                .set(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.IDEMPOTENCY_KEY, context.idempotencyKey())
                .set(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.TASK_ID, receipt.taskId())
                .set(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.TASK_EXECUTION_GUARD_ID, receipt.guardId())
                .set(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.PREPARED_TASK_ASSIGNMENT_ID,
                        receipt.preparedTaskAssignmentId())
                .set(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.PRINCIPAL_ID, receipt.principalId())
                .set(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.STATUS, receipt.status())
                .set(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.TASK_VERSION, receipt.taskVersion())
                .set(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.OCCURRED_AT, receipt.occurredAt())
                .execute();
    }

    private TaskReassignmentReceipt frozenReceipt(CommandContext context, String operation) {
        return dsl.select(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.TASK_ID,
                        TSK_TASK_REASSIGNMENT_COMMAND_RESULT.TASK_EXECUTION_GUARD_ID,
                        TSK_TASK_REASSIGNMENT_COMMAND_RESULT.PREPARED_TASK_ASSIGNMENT_ID,
                        TSK_TASK_REASSIGNMENT_COMMAND_RESULT.PRINCIPAL_ID,
                        TSK_TASK_REASSIGNMENT_COMMAND_RESULT.STATUS,
                        TSK_TASK_REASSIGNMENT_COMMAND_RESULT.TASK_VERSION,
                        TSK_TASK_REASSIGNMENT_COMMAND_RESULT.OCCURRED_AT)
                .from(TSK_TASK_REASSIGNMENT_COMMAND_RESULT)
                .where(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.TENANT_ID.eq(context.tenantId()))
                .and(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.OPERATION_TYPE.eq(operation))
                .and(TSK_TASK_REASSIGNMENT_COMMAND_RESULT.IDEMPOTENCY_KEY.eq(context.idempotencyKey()))
                .fetchSingle(record -> new TaskReassignmentReceipt(
                        record.value1(), record.value2(), record.value3(), record.value4(),
                        record.value5(), record.value6(), record.value7()));
    }

    private void appendAssignmentEvent(
            CommandContext context,
            TaskReassignmentReceipt receipt,
            String preparationKey,
            String serviceAssignmentId,
            String reasonCode,
            String eventType
    ) {
        TaskAssignmentChangedPayload payload = new TaskAssignmentChangedPayload(
                receipt.taskId(), receipt.guardId(), receipt.preparedTaskAssignmentId(),
                preparationKey, receipt.principalId(), receipt.status(), serviceAssignmentId,
                reasonCode, receipt.occurredAt());
        appendEvent(context, receipt.taskId(), receipt.taskVersion(), receipt.occurredAt(),
                eventType, payload);
    }

    private void appendGuardEvent(
            CommandContext context,
            TaskReassignmentReceipt receipt,
            String preparationKey,
            String status,
            String reasonCode,
            String eventType
    ) {
        TaskExecutionGuardChangedPayload payload = new TaskExecutionGuardChangedPayload(
                receipt.taskId(), receipt.guardId(), "REASSIGNMENT", preparationKey,
                status, reasonCode, receipt.occurredAt());
        appendEvent(context, receipt.taskId(), receipt.taskVersion(), receipt.occurredAt(),
                eventType, payload);
    }

    private void appendEvent(
            CommandContext context,
            UUID taskId,
            long taskVersion,
            Instant occurredAt,
            String eventType,
            Object payload
    ) {
        String json = serialize(payload);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "task", eventType, 1,
                "Task", taskId.toString(), taskVersion, context.tenantId(),
                context.correlationId(), context.idempotencyKey(), taskId.toString(),
                json, Sha256.digest(json), occurredAt));
    }

    private void appendAudit(
            CommandContext context,
            AuthorizationDecision decision,
            UUID taskId,
            String action,
            String digest,
            Instant now
    ) {
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(), action, CAPABILITY,
                "Task", taskId.toString(), "ALLOW", decision.matchedGrantIds(), decision.policyVersion(),
                "SUCCEEDED", null, digest, context.correlationId(), now));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Task reassignment payload cannot be serialized", exception);
        }
    }

    private record ActiveResponsibility(UUID taskAssignmentId) {
    }

    private record PreparedAssignment(
            UUID taskAssignmentId,
            UUID taskId,
            UUID taskExecutionGuardId,
            String preparationKey,
            String principalId,
            String sourceId
    ) {
    }

    private record TaskReassignmentState(
            String taskKind,
            String status,
            long version,
            String currentPrincipalId,
            boolean activeGuard,
            boolean preparedAssignment
    ) {
    }
}
