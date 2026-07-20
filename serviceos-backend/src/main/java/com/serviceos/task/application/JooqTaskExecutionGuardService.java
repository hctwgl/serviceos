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
import com.serviceos.task.api.AcquireTaskExecutionGuardCommand;
import com.serviceos.task.api.ReleaseTaskExecutionGuardCommand;
import com.serviceos.task.api.TaskExecutionGuardChangedPayload;
import com.serviceos.task.api.TaskExecutionGuardReceipt;
import com.serviceos.task.api.TaskExecutionGuardService;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.ResultQuery;
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

/**
 * Task 执行保护窗的权威实现。
 *
 * <p>guard 与 Task 版本、审计、幂等和 Outbox 在同一事务提交。M22 只实现保护窗事实；
 * ServiceAssignment 激活者必须在自身责任和容量事实已经原子切换后才能调用 release。</p>
 */
@Service
final class JooqTaskExecutionGuardService implements TaskExecutionGuardService {
    private static final String ACQUIRE = "task.execution-guard.acquire";
    private static final String RELEASE = "task.execution-guard.release";
    private static final String CAPABILITY = "task.guard.manage";
    private static final String GUARD_TYPE = "REASSIGNMENT";

    private final DSLContext dsl;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqTaskExecutionGuardService(
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
        TskTask task = TSK_TASK;
        TskTaskExecutionGuard guardRow = TSK_TASK_EXECUTION_GUARD.as("guard_row");
        int updated = dsl.update(task)
                .set(task.VERSION, task.VERSION.plus(1))
                .set(task.UPDATED_AT, now)
                .where(task.TENANT_ID.eq(context.tenantId()))
                .and(task.TASK_ID.eq(command.taskId()))
                .and(task.TASK_KIND.eq("HUMAN"))
                .and(task.STATUS.in("READY", "CLAIMED", "RUNNING"))
                .and(task.VERSION.eq(command.expectedVersion()))
                .and(DSL.notExists(DSL.selectOne()
                        .from(guardRow)
                        .where(guardRow.TENANT_ID.eq(task.TENANT_ID))
                        .and(guardRow.TASK_ID.eq(task.TASK_ID))
                        .and(guardRow.STATUS.eq("ACTIVE"))))
                .execute();
        if (updated != 1) {
            throwAcquireConflict(context.tenantId(), command.taskId(), command.expectedVersion());
        }

        UUID guardId = UUID.randomUUID();
        dsl.insertInto(TSK_TASK_EXECUTION_GUARD)
                .set(TSK_TASK_EXECUTION_GUARD.TASK_EXECUTION_GUARD_ID, guardId)
                .set(TSK_TASK_EXECUTION_GUARD.TENANT_ID, context.tenantId())
                .set(TSK_TASK_EXECUTION_GUARD.TASK_ID, command.taskId())
                .set(TSK_TASK_EXECUTION_GUARD.GUARD_TYPE, GUARD_TYPE)
                .set(TSK_TASK_EXECUTION_GUARD.GUARD_KEY, command.guardKey())
                .set(TSK_TASK_EXECUTION_GUARD.REASON_CODE, command.reasonCode())
                .set(TSK_TASK_EXECUTION_GUARD.STATUS, "ACTIVE")
                .set(TSK_TASK_EXECUTION_GUARD.ACTIVATED_TASK_VERSION, command.expectedVersion() + 1)
                .set(TSK_TASK_EXECUTION_GUARD.ACTIVATED_BY, context.actorId())
                .set(TSK_TASK_EXECUTION_GUARD.ACTIVATED_AT, now)
                .execute();

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
        TskTask task = TSK_TASK;
        TskTaskExecutionGuard guardRow = TSK_TASK_EXECUTION_GUARD.as("guard_row");
        TskTaskAssignment prepared = TSK_TASK_ASSIGNMENT.as("prepared");
        int taskUpdated = dsl.update(task)
                .set(task.VERSION, task.VERSION.plus(1))
                .set(task.UPDATED_AT, now)
                .where(task.TENANT_ID.eq(context.tenantId()))
                .and(task.TASK_ID.eq(command.taskId()))
                .and(task.VERSION.eq(command.expectedVersion()))
                .and(DSL.exists(DSL.selectOne()
                        .from(guardRow)
                        .where(guardRow.TENANT_ID.eq(task.TENANT_ID))
                        .and(guardRow.TASK_ID.eq(task.TASK_ID))
                        .and(guardRow.TASK_EXECUTION_GUARD_ID.eq(command.guardId()))
                        .and(guardRow.STATUS.eq("ACTIVE"))))
                .and(DSL.notExists(DSL.selectOne()
                        .from(prepared)
                        .where(prepared.TENANT_ID.eq(task.TENANT_ID))
                        .and(prepared.TASK_ID.eq(task.TASK_ID))
                        .and(prepared.TASK_EXECUTION_GUARD_ID.eq(command.guardId()))
                        .and(prepared.STATUS.eq("PREPARED"))))
                .execute();
        if (taskUpdated != 1) {
            throwReleaseConflict(context.tenantId(), command.taskId(), command.guardId(), command.expectedVersion());
        }
        dsl.update(TSK_TASK_EXECUTION_GUARD)
                .set(TSK_TASK_EXECUTION_GUARD.STATUS, "RELEASED")
                .set(TSK_TASK_EXECUTION_GUARD.RELEASED_TASK_VERSION, command.expectedVersion() + 1)
                .set(TSK_TASK_EXECUTION_GUARD.RELEASED_BY, context.actorId())
                .set(TSK_TASK_EXECUTION_GUARD.RELEASED_AT, now)
                .set(TSK_TASK_EXECUTION_GUARD.RELEASE_REASON_CODE, command.reasonCode())
                .where(TSK_TASK_EXECUTION_GUARD.TENANT_ID.eq(context.tenantId()))
                .and(TSK_TASK_EXECUTION_GUARD.TASK_ID.eq(command.taskId()))
                .and(TSK_TASK_EXECUTION_GUARD.TASK_EXECUTION_GUARD_ID.eq(command.guardId()))
                .and(TSK_TASK_EXECUTION_GUARD.STATUS.eq("ACTIVE"))
                .execute();

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
        boolean prepared = dsl.fetchExists(TSK_TASK_ASSIGNMENT,
                TSK_TASK_ASSIGNMENT.TENANT_ID.eq(tenantId)
                        .and(TSK_TASK_ASSIGNMENT.TASK_ID.eq(taskId))
                        .and(TSK_TASK_ASSIGNMENT.TASK_EXECUTION_GUARD_ID.eq(guardId))
                        .and(TSK_TASK_ASSIGNMENT.STATUS.eq("PREPARED")));
        if (prepared) {
            throw new BusinessProblem(
                    ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    "A guard with PREPARED responsibility must be closed by activate or abort");
        }
        boolean active = dsl.fetchExists(TSK_TASK_EXECUTION_GUARD,
                TSK_TASK_EXECUTION_GUARD.TENANT_ID.eq(tenantId)
                        .and(TSK_TASK_EXECUTION_GUARD.TASK_ID.eq(taskId))
                        .and(TSK_TASK_EXECUTION_GUARD.TASK_EXECUTION_GUARD_ID.eq(guardId))
                        .and(TSK_TASK_EXECUTION_GUARD.STATUS.eq("ACTIVE")));
        if (!active) {
            throw new BusinessProblem(ProblemCode.TASK_EXECUTION_GUARDED,
                    "The requested ACTIVE execution guard does not exist");
        }
        throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT, "Execution guard release failed");
    }

    private GuardedTask task(String tenantId, UUID taskId) {
        TskTask task = TSK_TASK;
        TskTaskExecutionGuard guardRow = TSK_TASK_EXECUTION_GUARD.as("guard_row");
        return dsl.select(task.VERSION,
                        DSL.exists(DSL.selectOne()
                                        .from(guardRow)
                                        .where(guardRow.TENANT_ID.eq(task.TENANT_ID))
                                        .and(guardRow.TASK_ID.eq(task.TASK_ID))
                                        .and(guardRow.STATUS.eq("ACTIVE")))
                                .as("active_guard"))
                .from(task)
                .where(task.TENANT_ID.eq(tenantId))
                .and(task.TASK_ID.eq(taskId))
                .fetchOptional(record -> new GuardedTask(
                        record.get(task.VERSION), record.get("active_guard", Boolean.class)))
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
    }

    private GuardIdentity guardIdentity(String tenantId, UUID guardId) {
        return dsl.select(TSK_TASK_EXECUTION_GUARD.GUARD_KEY)
                .from(TSK_TASK_EXECUTION_GUARD)
                .where(TSK_TASK_EXECUTION_GUARD.TENANT_ID.eq(tenantId))
                .and(TSK_TASK_EXECUTION_GUARD.TASK_EXECUTION_GUARD_ID.eq(guardId))
                .fetchSingle(record -> new GuardIdentity(record.value1()));
    }

    private TaskExecutionGuardReceipt receipt(String tenantId, UUID guardId, boolean released) {
        TskTaskExecutionGuard guardRow = TSK_TASK_EXECUTION_GUARD;
        ResultQuery<?> query = released
                ? dsl.select(guardRow.TASK_EXECUTION_GUARD_ID, guardRow.TASK_ID, guardRow.GUARD_KEY,
                        guardRow.STATUS.as("status"), guardRow.RELEASED_TASK_VERSION.as("task_version"),
                        guardRow.RELEASED_AT.as("occurred_at"))
                        .from(guardRow)
                        .where(guardRow.TENANT_ID.eq(tenantId))
                        .and(guardRow.TASK_EXECUTION_GUARD_ID.eq(guardId))
                : dsl.select(guardRow.TASK_EXECUTION_GUARD_ID, guardRow.TASK_ID, guardRow.GUARD_KEY,
                        DSL.inline("ACTIVE").as("status"), guardRow.ACTIVATED_TASK_VERSION.as("task_version"),
                        guardRow.ACTIVATED_AT.as("occurred_at"))
                        .from(guardRow)
                        .where(guardRow.TENANT_ID.eq(tenantId))
                        .and(guardRow.TASK_EXECUTION_GUARD_ID.eq(guardId));
        return query.fetchSingle(JooqTaskExecutionGuardService::mapReceipt);
    }

    private static TaskExecutionGuardReceipt mapReceipt(Record record) {
        TskTaskExecutionGuard guardRow = TSK_TASK_EXECUTION_GUARD;
        return new TaskExecutionGuardReceipt(
                record.get(guardRow.TASK_EXECUTION_GUARD_ID),
                record.get(guardRow.TASK_ID),
                record.get(guardRow.GUARD_KEY),
                record.get("status", String.class),
                record.get("task_version", Long.class),
                record.get("occurred_at", Instant.class));
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
