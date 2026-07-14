package com.serviceos.task.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
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
import com.serviceos.task.api.AbortPreparedTaskAssignmentCommand;
import com.serviceos.task.api.ActivatePreparedTaskAssignmentCommand;
import com.serviceos.task.api.PrepareTaskReassignmentCommand;
import com.serviceos.task.api.TaskAssignmentChangedPayload;
import com.serviceos.task.api.TaskExecutionGuardChangedPayload;
import com.serviceos.task.api.TaskReassignmentReceipt;
import com.serviceos.task.api.TaskReassignmentService;
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
 * Task 侧可靠改派握手。
 *
 * <p>prepare 在一个事务中同时建立 guard 和 PREPARED 责任；activate 只有在收到同一
 * ServiceAssignment 的激活确认后才切换当前责任并解除 guard；abort 仅适用于外部责任尚未切换的路径。</p>
 */
@Service
final class DefaultTaskReassignmentService implements TaskReassignmentService {
    private static final String PREPARE = "task.reassignment.prepare";
    private static final String ACTIVATE = "task.reassignment.activate";
    private static final String ABORT = "task.reassignment.abort";
    private static final String CAPABILITY = "task.reassignment.manage";

    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultTaskReassignmentService(
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
        int taskUpdated = jdbc.sql("""
                        UPDATE tsk_task
                           SET version = version + 1, updated_at = :now
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND task_kind = 'HUMAN'
                           AND status IN ('CLAIMED', 'RUNNING')
                           AND version = :expectedVersion
                           AND EXISTS (
                               SELECT 1 FROM tsk_task_assignment responsible
                                WHERE responsible.tenant_id = tsk_task.tenant_id
                                  AND responsible.task_id = tsk_task.task_id
                                  AND responsible.assignment_kind = 'RESPONSIBLE'
                                  AND responsible.status = 'ACTIVE'
                                  AND responsible.principal_id <> :newPrincipalId
                           )
                           AND NOT EXISTS (
                               SELECT 1 FROM tsk_task_execution_guard guard_row
                                WHERE guard_row.tenant_id = tsk_task.tenant_id
                                  AND guard_row.task_id = tsk_task.task_id
                                  AND guard_row.status = 'ACTIVE'
                           )
                           AND NOT EXISTS (
                               SELECT 1 FROM tsk_task_assignment prepared
                                WHERE prepared.tenant_id = tsk_task.tenant_id
                                  AND prepared.task_id = tsk_task.task_id
                                  AND prepared.assignment_kind = 'RESPONSIBLE'
                                  AND prepared.status = 'PREPARED'
                           )
                           AND NOT EXISTS (
                               SELECT 1 FROM tsk_task_execution_guard used_key
                                WHERE used_key.tenant_id = :tenantId
                                  AND used_key.guard_type = 'REASSIGNMENT'
                                  AND used_key.guard_key = :preparationKey
                           )
                        """)
                .param("now", timestamptz(now)).param("tenantId", context.tenantId())
                .param("taskId", command.taskId()).param("expectedVersion", command.expectedVersion())
                .param("newPrincipalId", command.newPrincipalId())
                .param("preparationKey", command.preparationKey()).update();
        if (taskUpdated != 1) {
            throwPrepareConflict(context.tenantId(), command);
        }

        UUID oldResponsibilityId = activeResponsibility(context.tenantId(), command.taskId()).taskAssignmentId();
        UUID guardId = UUID.randomUUID();
        UUID preparedAssignmentId = UUID.randomUUID();
        long taskVersion = command.expectedVersion() + 1;
        jdbc.sql("""
                        INSERT INTO tsk_task_execution_guard (
                            task_execution_guard_id, tenant_id, task_id, guard_type, guard_key,
                            reason_code, status, activated_task_version, activated_by, activated_at
                        ) VALUES (
                            :guardId, :tenantId, :taskId, 'REASSIGNMENT', :preparationKey,
                            :reasonCode, 'ACTIVE', :taskVersion, :actorId, :now
                        )
                        """)
                .param("guardId", guardId).param("tenantId", context.tenantId())
                .param("taskId", command.taskId()).param("preparationKey", command.preparationKey())
                .param("reasonCode", command.reasonCode()).param("taskVersion", taskVersion)
                .param("actorId", context.actorId()).param("now", timestamptz(now)).update();
        jdbc.sql("""
                        INSERT INTO tsk_task_assignment (
                            task_assignment_id, tenant_id, task_id, assignment_kind,
                            principal_type, principal_id, status, source_type, source_id,
                            supersedes_task_assignment_id, created_by, created_at,
                            task_execution_guard_id, preparation_key
                        ) VALUES (
                            :assignmentId, :tenantId, :taskId, 'RESPONSIBLE',
                            'USER', :principalId, 'PREPARED', 'SERVICE_ASSIGNMENT_PENDING', :sourceId,
                            :supersedesId, :actorId, :now, :guardId, :preparationKey
                        )
                        """)
                .param("assignmentId", preparedAssignmentId).param("tenantId", context.tenantId())
                .param("taskId", command.taskId()).param("principalId", command.newPrincipalId())
                .param("sourceId", command.pendingServiceAssignmentId())
                .param("supersedesId", oldResponsibilityId).param("actorId", context.actorId())
                .param("now", timestamptz(now)).param("guardId", guardId)
                .param("preparationKey", command.preparationKey()).update();

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
        int taskUpdated = jdbc.sql("""
                        UPDATE tsk_task
                           SET claimed_by = :principalId,
                               claimed_at = CASE WHEN status = 'CLAIMED' THEN :now ELSE claimed_at END,
                               version = version + 1, updated_at = :now
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND task_kind = 'HUMAN' AND status IN ('CLAIMED', 'RUNNING')
                           AND version = :expectedVersion
                           AND EXISTS (
                               SELECT 1 FROM tsk_task_execution_guard guard_row
                                WHERE guard_row.tenant_id = tsk_task.tenant_id
                                  AND guard_row.task_id = tsk_task.task_id
                                  AND guard_row.task_execution_guard_id = :guardId
                                  AND guard_row.status = 'ACTIVE'
                           )
                           AND EXISTS (
                               SELECT 1 FROM tsk_task_assignment assignment
                                WHERE assignment.tenant_id = tsk_task.tenant_id
                                  AND assignment.task_id = tsk_task.task_id
                                  AND assignment.task_assignment_id = :assignmentId
                                  AND assignment.task_execution_guard_id = :guardId
                                  AND assignment.status = 'PREPARED'
                           )
                        """)
                .param("principalId", prepared.principalId()).param("now", timestamptz(now))
                .param("tenantId", context.tenantId()).param("taskId", command.taskId())
                .param("expectedVersion", command.expectedVersion()).param("guardId", command.guardId())
                .param("assignmentId", command.preparedTaskAssignmentId()).update();
        if (taskUpdated != 1) {
            throwTransitionConflict(context.tenantId(), command.taskId(), command.guardId(),
                    command.preparedTaskAssignmentId(), command.expectedVersion(), "activate");
        }

        closeActiveAssignments(context, command.taskId(), now);
        int assignmentUpdated = jdbc.sql("""
                        UPDATE tsk_task_assignment
                           SET status = 'ACTIVE', effective_from = :now,
                               activation_ref = :activationRef
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND task_assignment_id = :assignmentId
                           AND task_execution_guard_id = :guardId AND status = 'PREPARED'
                        """)
                .param("now", timestamptz(now)).param("activationRef", command.activeServiceAssignmentId())
                .param("tenantId", context.tenantId()).param("taskId", command.taskId())
                .param("assignmentId", command.preparedTaskAssignmentId())
                .param("guardId", command.guardId()).update();
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
        int taskUpdated = jdbc.sql("""
                        UPDATE tsk_task
                           SET version = version + 1, updated_at = :now
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND task_kind = 'HUMAN' AND status IN ('CLAIMED', 'RUNNING')
                           AND version = :expectedVersion
                           AND EXISTS (
                               SELECT 1 FROM tsk_task_execution_guard guard_row
                                WHERE guard_row.tenant_id = tsk_task.tenant_id
                                  AND guard_row.task_id = tsk_task.task_id
                                  AND guard_row.task_execution_guard_id = :guardId
                                  AND guard_row.status = 'ACTIVE'
                           )
                           AND EXISTS (
                               SELECT 1 FROM tsk_task_assignment assignment
                                WHERE assignment.tenant_id = tsk_task.tenant_id
                                  AND assignment.task_id = tsk_task.task_id
                                  AND assignment.task_assignment_id = :assignmentId
                                  AND assignment.task_execution_guard_id = :guardId
                                  AND assignment.status = 'PREPARED'
                           )
                        """)
                .param("now", timestamptz(now)).param("tenantId", context.tenantId())
                .param("taskId", command.taskId()).param("expectedVersion", command.expectedVersion())
                .param("guardId", command.guardId())
                .param("assignmentId", command.preparedTaskAssignmentId()).update();
        if (taskUpdated != 1) {
            throwTransitionConflict(context.tenantId(), command.taskId(), command.guardId(),
                    command.preparedTaskAssignmentId(), command.expectedVersion(), "abort");
        }
        jdbc.sql("""
                        UPDATE tsk_task_assignment
                           SET status = 'ABORTED', revoked_by = :actorId,
                               revoke_reason_code = :reasonCode
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND task_assignment_id = :assignmentId
                           AND task_execution_guard_id = :guardId AND status = 'PREPARED'
                        """)
                .param("actorId", context.actorId()).param("reasonCode", command.reasonCode())
                .param("tenantId", context.tenantId()).param("taskId", command.taskId())
                .param("assignmentId", command.preparedTaskAssignmentId())
                .param("guardId", command.guardId()).update();
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
        boolean linked = jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM tsk_task_execution_guard guard_row
                            JOIN tsk_task_assignment assignment
                              ON assignment.task_execution_guard_id = guard_row.task_execution_guard_id
                             AND assignment.tenant_id = guard_row.tenant_id
                             AND assignment.task_id = guard_row.task_id
                           WHERE guard_row.tenant_id = :tenantId AND guard_row.task_id = :taskId
                             AND guard_row.task_execution_guard_id = :guardId
                             AND guard_row.status = 'ACTIVE'
                             AND assignment.task_assignment_id = :assignmentId
                             AND assignment.status = 'PREPARED'
                        )
                        """)
                .param("tenantId", tenantId).param("taskId", taskId).param("guardId", guardId)
                .param("assignmentId", assignmentId).query(Boolean.class).single();
        if (!linked) {
            throw new BusinessProblem(
                    ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    operation + " requires the matching ACTIVE guard and PREPARED responsibility");
        }
        throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                "Task responsibility transition failed");
    }

    private TaskReassignmentState state(String tenantId, UUID taskId) {
        return jdbc.sql("""
                        SELECT task.task_kind, task.status, task.version,
                               (SELECT responsible.principal_id
                                  FROM tsk_task_assignment responsible
                                 WHERE responsible.tenant_id = task.tenant_id
                                   AND responsible.task_id = task.task_id
                                   AND responsible.assignment_kind = 'RESPONSIBLE'
                                   AND responsible.status = 'ACTIVE') AS current_principal_id,
                               EXISTS (SELECT 1 FROM tsk_task_execution_guard guard_row
                                        WHERE guard_row.tenant_id = task.tenant_id
                                          AND guard_row.task_id = task.task_id
                                          AND guard_row.status = 'ACTIVE') AS active_guard,
                               EXISTS (SELECT 1 FROM tsk_task_assignment prepared
                                        WHERE prepared.tenant_id = task.tenant_id
                                          AND prepared.task_id = task.task_id
                                          AND prepared.assignment_kind = 'RESPONSIBLE'
                                          AND prepared.status = 'PREPARED') AS prepared_assignment
                          FROM tsk_task task
                         WHERE task.tenant_id = :tenantId AND task.task_id = :taskId
                        """)
                .param("tenantId", tenantId).param("taskId", taskId)
                .query(TaskReassignmentState.class).optional()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
    }

    private ActiveResponsibility activeResponsibility(String tenantId, UUID taskId) {
        return jdbc.sql("""
                        SELECT task_assignment_id FROM tsk_task_assignment
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND assignment_kind = 'RESPONSIBLE' AND status = 'ACTIVE'
                        """)
                .param("tenantId", tenantId).param("taskId", taskId)
                .query(ActiveResponsibility.class).single();
    }

    private PreparedAssignment prepared(String tenantId, UUID assignmentId) {
        return jdbc.sql("""
                        SELECT task_assignment_id, task_id, task_execution_guard_id,
                               preparation_key, principal_id, source_id
                          FROM tsk_task_assignment
                         WHERE tenant_id = :tenantId AND task_assignment_id = :assignmentId
                        """)
                .param("tenantId", tenantId).param("assignmentId", assignmentId)
                .query(PreparedAssignment.class).optional()
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                        "Prepared TaskAssignment does not exist"));
    }

    private void closeActiveAssignments(CommandContext context, UUID taskId, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE tsk_task_assignment
                           SET status = 'REVOKED', effective_to = :now,
                               revoked_by = :actorId, revoke_reason_code = 'REASSIGNED'
                         WHERE tenant_id = :tenantId AND task_id = :taskId AND status = 'ACTIVE'
                           AND assignment_kind IN ('CANDIDATE', 'RESPONSIBLE')
                        """)
                .param("now", timestamptz(now)).param("actorId", context.actorId())
                .param("tenantId", context.tenantId()).param("taskId", taskId).update();
        if (updated < 2) {
            throw new BusinessProblem(
                    ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    "Responsibility activation must close the old candidate and responsible facts");
        }
    }

    private void insertActiveCandidate(
            CommandContext context, UUID taskId, PreparedAssignment prepared, Instant now) {
        jdbc.sql("""
                        INSERT INTO tsk_task_assignment (
                            task_assignment_id, tenant_id, task_id, assignment_kind,
                            principal_type, principal_id, status, source_type, source_id,
                            effective_from, created_by, created_at,
                            task_execution_guard_id, preparation_key, activation_ref
                        ) VALUES (
                            :assignmentId, :tenantId, :taskId, 'CANDIDATE',
                            'USER', :principalId, 'ACTIVE', 'SERVICE_ASSIGNMENT', :sourceId,
                            :now, :actorId, :now, :guardId, NULL, :sourceId
                        )
                        """)
                .param("assignmentId", UUID.randomUUID()).param("tenantId", context.tenantId())
                .param("taskId", taskId).param("principalId", prepared.principalId())
                .param("sourceId", prepared.sourceId()).param("now", timestamptz(now))
                .param("actorId", context.actorId()).param("guardId", prepared.taskExecutionGuardId())
                .update();
    }

    private void releaseGuard(
            CommandContext context,
            UUID taskId,
            UUID guardId,
            long taskVersion,
            String reasonCode,
            Instant now
    ) {
        int updated = jdbc.sql("""
                        UPDATE tsk_task_execution_guard
                           SET status = 'RELEASED', released_task_version = :taskVersion,
                               released_by = :actorId, released_at = :now,
                               release_reason_code = :reasonCode
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND task_execution_guard_id = :guardId AND status = 'ACTIVE'
                        """)
                .param("taskVersion", taskVersion).param("actorId", context.actorId())
                .param("now", timestamptz(now)).param("reasonCode", reasonCode)
                .param("tenantId", context.tenantId()).param("taskId", taskId)
                .param("guardId", guardId).update();
        if (updated != 1) {
            throw new BusinessProblem(
                    ProblemCode.TASK_EXECUTION_GUARDED,
                    "ACTIVE reassignment guard could not be released");
        }
    }

    private void insertFrozenReceipt(
            CommandContext context, String operation, TaskReassignmentReceipt receipt) {
        jdbc.sql("""
                        INSERT INTO tsk_task_reassignment_command_result (
                            tenant_id, operation_type, idempotency_key, task_id,
                            task_execution_guard_id, prepared_task_assignment_id,
                            principal_id, status, task_version, occurred_at
                        ) VALUES (
                            :tenantId, :operation, :idempotencyKey, :taskId,
                            :guardId, :assignmentId, :principalId, :status, :taskVersion, :occurredAt
                        )
                        """)
                .param("tenantId", context.tenantId()).param("operation", operation)
                .param("idempotencyKey", context.idempotencyKey()).param("taskId", receipt.taskId())
                .param("guardId", receipt.guardId())
                .param("assignmentId", receipt.preparedTaskAssignmentId())
                .param("principalId", receipt.principalId()).param("status", receipt.status())
                .param("taskVersion", receipt.taskVersion())
                .param("occurredAt", timestamptz(receipt.occurredAt())).update();
    }

    private TaskReassignmentReceipt frozenReceipt(CommandContext context, String operation) {
        return jdbc.sql("""
                        SELECT task_id, task_execution_guard_id AS guard_id,
                               prepared_task_assignment_id, principal_id, status,
                               task_version, occurred_at
                          FROM tsk_task_reassignment_command_result
                         WHERE tenant_id = :tenantId AND operation_type = :operation
                           AND idempotency_key = :idempotencyKey
                        """)
                .param("tenantId", context.tenantId()).param("operation", operation)
                .param("idempotencyKey", context.idempotencyKey())
                .query(TaskReassignmentReceipt.class).single();
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
