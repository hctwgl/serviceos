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
import com.serviceos.task.api.AcquireTaskExecutionGuardCommand;
import com.serviceos.task.api.ReleaseTaskExecutionGuardCommand;
import com.serviceos.task.api.TaskExecutionGuardChangedPayload;
import com.serviceos.task.api.TaskExecutionGuardReceipt;
import com.serviceos.task.api.TaskExecutionGuardService;
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
 * Task 执行保护窗的权威实现。
 *
 * <p>guard 与 Task 版本、审计、幂等和 Outbox 在同一事务提交。M22 只实现保护窗事实；
 * ServiceAssignment 激活者必须在自身责任和容量事实已经原子切换后才能调用 release。</p>
 */
@Service
final class DefaultTaskExecutionGuardService implements TaskExecutionGuardService {
    private static final String ACQUIRE = "task.execution-guard.acquire";
    private static final String RELEASE = "task.execution-guard.release";
    private static final String CAPABILITY = "task.guard.manage";
    private static final String GUARD_TYPE = "REASSIGNMENT";

    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultTaskExecutionGuardService(
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
    public TaskExecutionGuardReceipt acquire(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            AcquireTaskExecutionGuardCommand command
    ) {
        CommandContext context = context(principal, metadata);
        String digest = Sha256.digest(command.taskId() + "|" + command.expectedVersion()
                + "|" + command.guardKey() + "|" + command.reasonCode());
        AuthorizationDecision authorizationDecision = authorize(principal, context, command.taskId());
        IdempotencyDecision decision = idempotency.begin(context, ACQUIRE, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return receipt(context.tenantId(), UUID.fromString(decision.resourceId().orElseThrow()), false);
        }

        Instant now = clock.instant();
        int updated = jdbc.sql("""
                        UPDATE tsk_task
                           SET version = version + 1, updated_at = :now
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND task_kind = 'HUMAN'
                           AND status IN ('READY', 'CLAIMED', 'RUNNING')
                           AND version = :expectedVersion
                           AND NOT EXISTS (
                               SELECT 1 FROM tsk_task_execution_guard guard_row
                                WHERE guard_row.tenant_id = tsk_task.tenant_id
                                  AND guard_row.task_id = tsk_task.task_id
                                  AND guard_row.status = 'ACTIVE'
                           )
                        """)
                .param("now", timestamptz(now)).param("tenantId", context.tenantId())
                .param("taskId", command.taskId()).param("expectedVersion", command.expectedVersion())
                .update();
        if (updated != 1) {
            throwAcquireConflict(context.tenantId(), command.taskId(), command.expectedVersion());
        }

        UUID guardId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO tsk_task_execution_guard (
                            task_execution_guard_id, tenant_id, task_id, guard_type, guard_key,
                            reason_code, status, activated_task_version, activated_by, activated_at
                        ) VALUES (
                            :guardId, :tenantId, :taskId, :guardType, :guardKey,
                            :reasonCode, 'ACTIVE', :taskVersion, :actorId, :now
                        )
                        """)
                .param("guardId", guardId).param("tenantId", context.tenantId())
                .param("taskId", command.taskId()).param("guardType", GUARD_TYPE)
                .param("guardKey", command.guardKey()).param("reasonCode", command.reasonCode())
                .param("taskVersion", command.expectedVersion() + 1)
                .param("actorId", context.actorId()).param("now", timestamptz(now)).update();

        long taskVersion = command.expectedVersion() + 1;
        TaskExecutionGuardReceipt receipt = new TaskExecutionGuardReceipt(
                guardId, command.taskId(), command.guardKey(), "ACTIVE", taskVersion, now);
        appendEvent(context, receipt, command.reasonCode(), "task.execution-guard.activated");
        appendAudit(context, authorizationDecision, command.taskId(),
                "TASK_EXECUTION_GUARD_ACQUIRE", digest, now);
        idempotency.complete(context, ACQUIRE, guardId.toString(), Sha256.digest(serialize(receipt)));
        return receipt;
    }

    @Override
    @Transactional
    public TaskExecutionGuardReceipt release(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ReleaseTaskExecutionGuardCommand command
    ) {
        CommandContext context = context(principal, metadata);
        String digest = Sha256.digest(command.taskId() + "|" + command.guardId()
                + "|" + command.expectedVersion() + "|" + command.reasonCode());
        AuthorizationDecision authorizationDecision = authorize(principal, context, command.taskId());
        IdempotencyDecision decision = idempotency.begin(context, RELEASE, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return receipt(context.tenantId(), UUID.fromString(decision.resourceId().orElseThrow()), true);
        }

        Instant now = clock.instant();
        int taskUpdated = jdbc.sql("""
                        UPDATE tsk_task
                           SET version = version + 1, updated_at = :now
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND version = :expectedVersion
                           AND EXISTS (
                               SELECT 1 FROM tsk_task_execution_guard guard_row
                                WHERE guard_row.tenant_id = tsk_task.tenant_id
                                  AND guard_row.task_id = tsk_task.task_id
                                  AND guard_row.task_execution_guard_id = :guardId
                                  AND guard_row.status = 'ACTIVE'
                           )
                        """)
                .param("now", timestamptz(now)).param("tenantId", context.tenantId())
                .param("taskId", command.taskId()).param("guardId", command.guardId())
                .param("expectedVersion", command.expectedVersion()).update();
        if (taskUpdated != 1) {
            throwReleaseConflict(context.tenantId(), command.taskId(), command.guardId(), command.expectedVersion());
        }
        jdbc.sql("""
                        UPDATE tsk_task_execution_guard
                           SET status = 'RELEASED', released_task_version = :taskVersion,
                               released_by = :actorId,
                               released_at = :now, release_reason_code = :reasonCode
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND task_execution_guard_id = :guardId AND status = 'ACTIVE'
                        """)
                .param("actorId", context.actorId()).param("now", timestamptz(now))
                .param("taskVersion", command.expectedVersion() + 1)
                .param("reasonCode", command.reasonCode()).param("tenantId", context.tenantId())
                .param("taskId", command.taskId()).param("guardId", command.guardId()).update();

        GuardIdentity identity = guardIdentity(context.tenantId(), command.guardId());
        TaskExecutionGuardReceipt receipt = new TaskExecutionGuardReceipt(
                command.guardId(), command.taskId(), identity.guardKey(), "RELEASED",
                command.expectedVersion() + 1, now);
        appendEvent(context, receipt, command.reasonCode(), "task.execution-guard.released");
        appendAudit(context, authorizationDecision, command.taskId(),
                "TASK_EXECUTION_GUARD_RELEASE", digest, now);
        idempotency.complete(context, RELEASE, command.guardId().toString(), Sha256.digest(serialize(receipt)));
        return receipt;
    }

    private CommandContext context(CurrentPrincipal principal, CommandMetadata metadata) {
        return new CommandContext(principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
    }

    private AuthorizationDecision authorize(CurrentPrincipal principal, CommandContext context, UUID taskId) {
        return authorization.require(principal,
                AuthorizationRequest.tenantCapability(CAPABILITY, context.tenantId(), "Task", taskId.toString()),
                context.correlationId());
    }

    private void throwAcquireConflict(String tenantId, UUID taskId, long expectedVersion) {
        GuardedTask task = task(tenantId, taskId);
        if (task.activeGuard()) {
            throw new BusinessProblem(ProblemCode.TASK_EXECUTION_GUARDED, "Task already has an ACTIVE execution guard");
        }
        if (task.version() != expectedVersion) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "Task version changed");
        }
        throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                "Execution guard requires a READY, CLAIMED or RUNNING HUMAN task");
    }

    private void throwReleaseConflict(String tenantId, UUID taskId, UUID guardId, long expectedVersion) {
        GuardedTask task = task(tenantId, taskId);
        if (task.version() != expectedVersion) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "Task version changed");
        }
        boolean active = jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM tsk_task_execution_guard
                             WHERE tenant_id = :tenantId AND task_id = :taskId
                               AND task_execution_guard_id = :guardId AND status = 'ACTIVE'
                        )
                        """)
                .param("tenantId", tenantId).param("taskId", taskId).param("guardId", guardId)
                .query(Boolean.class).single();
        if (!active) {
            throw new BusinessProblem(ProblemCode.TASK_EXECUTION_GUARDED,
                    "The requested ACTIVE execution guard does not exist");
        }
        throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT, "Execution guard release failed");
    }

    private GuardedTask task(String tenantId, UUID taskId) {
        return jdbc.sql("""
                        SELECT task.version,
                               EXISTS (SELECT 1 FROM tsk_task_execution_guard guard_row
                                        WHERE guard_row.tenant_id = task.tenant_id
                                          AND guard_row.task_id = task.task_id
                                          AND guard_row.status = 'ACTIVE') AS active_guard
                          FROM tsk_task task
                         WHERE task.tenant_id = :tenantId AND task.task_id = :taskId
                        """)
                .param("tenantId", tenantId).param("taskId", taskId)
                .query(GuardedTask.class).optional()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
    }

    private GuardIdentity guardIdentity(String tenantId, UUID guardId) {
        return jdbc.sql("""
                        SELECT guard_key FROM tsk_task_execution_guard
                         WHERE tenant_id = :tenantId AND task_execution_guard_id = :guardId
                        """)
                .param("tenantId", tenantId).param("guardId", guardId)
                .query(GuardIdentity.class).single();
    }

    private TaskExecutionGuardReceipt receipt(String tenantId, UUID guardId, boolean released) {
        String sql = released
                ? """
                  SELECT task_execution_guard_id AS guard_id, task_id, guard_key, status,
                         released_task_version AS task_version,
                         released_at AS occurred_at
                    FROM tsk_task_execution_guard guard_row
                   WHERE tenant_id = :tenantId AND task_execution_guard_id = :guardId
                  """
                : """
                  SELECT task_execution_guard_id AS guard_id, task_id, guard_key, 'ACTIVE' AS status,
                         activated_task_version AS task_version,
                         activated_at AS occurred_at
                    FROM tsk_task_execution_guard guard_row
                   WHERE tenant_id = :tenantId AND task_execution_guard_id = :guardId
                  """;
        return jdbc.sql(sql).param("tenantId", tenantId).param("guardId", guardId)
                .query(TaskExecutionGuardReceipt.class).single();
    }

    private void appendEvent(
            CommandContext context, TaskExecutionGuardReceipt receipt, String reasonCode, String eventType) {
        TaskExecutionGuardChangedPayload payload = new TaskExecutionGuardChangedPayload(
                receipt.taskId(), receipt.guardId(), GUARD_TYPE, receipt.guardKey(),
                receipt.status(), reasonCode, receipt.occurredAt());
        String json = serialize(payload);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "task", eventType, 1,
                "Task", receipt.taskId().toString(), receipt.taskVersion(), context.tenantId(),
                context.correlationId(), context.idempotencyKey(), receipt.taskId().toString(),
                json, Sha256.digest(json), receipt.occurredAt()));
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
            throw new IllegalArgumentException("Task execution guard payload cannot be serialized", exception);
        }
    }

    private record GuardedTask(long version, boolean activeGuard) {
    }

    private record GuardIdentity(String guardKey) {
    }
}
