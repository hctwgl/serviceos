package com.serviceos.task.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.jooq.generated.tables.TskTask;
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
import com.serviceos.task.api.AssignTaskCandidatesCommand;
import com.serviceos.task.api.AssignmentSourceType;
import com.serviceos.task.api.TaskAssignedPayload;
import com.serviceos.task.api.TaskAssignmentBatchReceipt;
import com.serviceos.task.api.TaskAssignmentService;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;
import static com.serviceos.jooq.generated.tables.TskTaskAssignment.TSK_TASK_ASSIGNMENT;
import static com.serviceos.jooq.generated.tables.TskTaskAssignmentBatch.TSK_TASK_ASSIGNMENT_BATCH;
import static com.serviceos.jooq.generated.tables.TskTaskExecutionGuard.TSK_TASK_EXECUTION_GUARD;

/**
 * 人工 Task 候选责任快照命令。
 *
 * <p>M21 只接受已经解析成稳定 USER ID 的候选列表；角色、组织和网点策略解析属于后续配置/派单适配器，
 * 不能在 Task 模块运行时临时读取组织“当前值”并造成历史漂移。</p>
 *
 * <p>M323：Inbox 路径 {@link #assignCandidatesFromFrozenPolicy} 在已解析冻结 ASSIGNEE_POLICY
 * 后写入候选；不暴露 HTTP，审计 actor 为 system:assignee-policy。</p>
 */
@Service
final class JooqTaskAssignmentService implements TaskAssignmentService {
    private static final String OPERATION = "task.assignment.assign-candidates";
    private static final String POLICY_OPERATION = "task.assignment.assign-candidates-from-policy";
    private static final String SYSTEM_ACTOR = "system:assignee-policy";

    private final DSLContext dsl;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqTaskAssignmentService(
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
    public TaskAssignmentBatchReceipt assignCandidates(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            AssignTaskCandidatesCommand command
    ) {
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        String requestDigest = requestDigest(command);
        AuthorizationDecision authorizationDecision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        "task.assign", context.tenantId(), "Task", command.taskId().toString()),
                context.correlationId());
        IdempotencyDecision decision = idempotency.begin(context, OPERATION, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return findBatch(context.tenantId(), UUID.fromString(decision.resourceId().orElseThrow()));
        }
        TaskAssignmentBatchReceipt receipt = writeCandidateSnapshot(
                context, command, requestDigest,
                authorizationDecision.matchedGrantIds(), authorizationDecision.policyVersion(),
                "TASK_ASSIGN_CANDIDATES");
        idempotency.complete(context, OPERATION, receipt.assignmentBatchId().toString(),
                Sha256.digest(serialize(receipt)));
        return receipt;
    }

    @Override
    @Transactional
    public TaskAssignmentBatchReceipt assignCandidatesFromFrozenPolicy(
            String tenantId,
            String correlationId,
            AssignTaskCandidatesCommand command
    ) {
        if (command.sourceType() != AssignmentSourceType.ASSIGNEE_POLICY) {
            throw new IllegalArgumentException(
                    "assignCandidatesFromFrozenPolicy requires ASSIGNEE_POLICY sourceType");
        }
        CommandContext context = new CommandContext(
                tenantId, SYSTEM_ACTOR, correlationId,
                "assignee-policy:" + command.taskId() + ":" + command.sourceId());
        String requestDigest = requestDigest(command);
        IdempotencyDecision decision = idempotency.begin(context, POLICY_OPERATION, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return findBatch(context.tenantId(), UUID.fromString(decision.resourceId().orElseThrow()));
        }
        TaskAssignmentBatchReceipt receipt = writeCandidateSnapshot(
                context, command, requestDigest, List.of(), "assignee-policy-runtime-v1",
                "TASK_ASSIGN_CANDIDATES_FROM_POLICY");
        idempotency.complete(context, POLICY_OPERATION, receipt.assignmentBatchId().toString(),
                Sha256.digest(serialize(receipt)));
        return receipt;
    }

    private TaskAssignmentBatchReceipt writeCandidateSnapshot(
            CommandContext context,
            AssignTaskCandidatesCommand command,
            String requestDigest,
            List<String> matchedGrantIds,
            String policyVersion,
            String auditAction
    ) {
        Instant assignedAt = clock.instant();
        TskTask task = TSK_TASK;
        int updated = dsl.update(task)
                .set(task.VERSION, task.VERSION.plus(1))
                .set(task.UPDATED_AT, assignedAt)
                .where(task.TENANT_ID.eq(context.tenantId()))
                .and(task.TASK_ID.eq(command.taskId()))
                .and(task.TASK_KIND.eq("HUMAN"))
                .and(task.STATUS.eq("READY"))
                .and(task.VERSION.eq(command.expectedVersion()))
                .and(noActiveGuard())
                .execute();
        if (updated != 1) {
            throwAssignmentConflict(context.tenantId(), command.taskId(), command.expectedVersion());
        }

        // 新快照先关闭旧候选，再写入同一批次；事务失败时旧候选不会被提前撤销。
        dsl.update(TSK_TASK_ASSIGNMENT)
                .set(TSK_TASK_ASSIGNMENT.STATUS, "REVOKED")
                .set(TSK_TASK_ASSIGNMENT.EFFECTIVE_TO, assignedAt)
                .set(TSK_TASK_ASSIGNMENT.REVOKED_BY, context.actorId())
                .set(TSK_TASK_ASSIGNMENT.REVOKE_REASON_CODE, "ASSIGNMENT_REPLACED")
                .where(TSK_TASK_ASSIGNMENT.TENANT_ID.eq(context.tenantId()))
                .and(TSK_TASK_ASSIGNMENT.TASK_ID.eq(command.taskId()))
                .and(TSK_TASK_ASSIGNMENT.ASSIGNMENT_KIND.eq("CANDIDATE"))
                .and(TSK_TASK_ASSIGNMENT.STATUS.eq("ACTIVE"))
                .execute();

        UUID batchId = UUID.randomUUID();
        long taskVersion = command.expectedVersion() + 1;
        dsl.insertInto(TSK_TASK_ASSIGNMENT_BATCH)
                .set(TSK_TASK_ASSIGNMENT_BATCH.ASSIGNMENT_BATCH_ID, batchId)
                .set(TSK_TASK_ASSIGNMENT_BATCH.TENANT_ID, context.tenantId())
                .set(TSK_TASK_ASSIGNMENT_BATCH.TASK_ID, command.taskId())
                .set(TSK_TASK_ASSIGNMENT_BATCH.SOURCE_TYPE, command.sourceType().name())
                .set(TSK_TASK_ASSIGNMENT_BATCH.SOURCE_ID, command.sourceId())
                .set(TSK_TASK_ASSIGNMENT_BATCH.CANDIDATE_COUNT, command.candidatePrincipalIds().size())
                .set(TSK_TASK_ASSIGNMENT_BATCH.TASK_VERSION, taskVersion)
                .set(TSK_TASK_ASSIGNMENT_BATCH.ASSIGNED_BY, context.actorId())
                .set(TSK_TASK_ASSIGNMENT_BATCH.ASSIGNED_AT, assignedAt)
                .execute();
        for (String candidateId : command.candidatePrincipalIds()) {
            dsl.insertInto(TSK_TASK_ASSIGNMENT)
                    .set(TSK_TASK_ASSIGNMENT.TASK_ASSIGNMENT_ID, UUID.randomUUID())
                    .set(TSK_TASK_ASSIGNMENT.TENANT_ID, context.tenantId())
                    .set(TSK_TASK_ASSIGNMENT.TASK_ID, command.taskId())
                    .set(TSK_TASK_ASSIGNMENT.ASSIGNMENT_BATCH_ID, batchId)
                    .set(TSK_TASK_ASSIGNMENT.ASSIGNMENT_KIND, "CANDIDATE")
                    .set(TSK_TASK_ASSIGNMENT.PRINCIPAL_TYPE, "USER")
                    .set(TSK_TASK_ASSIGNMENT.PRINCIPAL_ID, candidateId)
                    .set(TSK_TASK_ASSIGNMENT.STATUS, "ACTIVE")
                    .set(TSK_TASK_ASSIGNMENT.SOURCE_TYPE, command.sourceType().name())
                    .set(TSK_TASK_ASSIGNMENT.SOURCE_ID, command.sourceId())
                    .set(TSK_TASK_ASSIGNMENT.EFFECTIVE_FROM, assignedAt)
                    .set(TSK_TASK_ASSIGNMENT.CREATED_BY, context.actorId())
                    .set(TSK_TASK_ASSIGNMENT.CREATED_AT, assignedAt)
                    .execute();
        }

        TaskAssignedPayload payload = new TaskAssignedPayload(
                command.taskId(), batchId, command.candidatePrincipalIds(),
                command.sourceType(), command.sourceId(), assignedAt);
        String payloadJson = serialize(payload);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "task", "task.assigned", 1,
                "Task", command.taskId().toString(), taskVersion, context.tenantId(),
                context.correlationId(), context.idempotencyKey(), command.taskId().toString(),
                payloadJson, Sha256.digest(payloadJson), assignedAt));
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(),
                auditAction, "task.assign", "Task", command.taskId().toString(),
                "ALLOW", matchedGrantIds, policyVersion,
                "SUCCEEDED", null, requestDigest, context.correlationId(), assignedAt));

        return new TaskAssignmentBatchReceipt(
                batchId, command.taskId(), command.candidatePrincipalIds().size(), taskVersion, assignedAt);
    }

    private static Condition noActiveGuard() {
        TskTaskExecutionGuard guard = TSK_TASK_EXECUTION_GUARD.as("guard_row");
        return DSL.notExists(DSL.selectOne()
                .from(guard)
                .where(guard.TENANT_ID.eq(TSK_TASK.TENANT_ID))
                .and(guard.TASK_ID.eq(TSK_TASK.TASK_ID))
                .and(guard.STATUS.eq("ACTIVE")));
    }

    private static String requestDigest(AssignTaskCandidatesCommand command) {
        return Sha256.digest(
                command.taskId() + "|" + command.expectedVersion() + "|"
                        + String.join(",", command.candidatePrincipalIds()) + "|"
                        + command.sourceType() + "|" + command.sourceId());
    }

    private void throwAssignmentConflict(String tenantId, UUID taskId, long expectedVersion) {
        TskTask task = TSK_TASK;
        TskTaskExecutionGuard guard = TSK_TASK_EXECUTION_GUARD.as("guard_row");
        TaskState state = dsl.select(task.TASK_KIND, task.STATUS, task.VERSION,
                        DSL.exists(DSL.selectOne()
                                        .from(guard)
                                        .where(guard.TENANT_ID.eq(task.TENANT_ID))
                                        .and(guard.TASK_ID.eq(task.TASK_ID))
                                        .and(guard.STATUS.eq("ACTIVE")))
                                .as("active_guard"))
                .from(task)
                .where(task.TENANT_ID.eq(tenantId))
                .and(task.TASK_ID.eq(taskId))
                .fetchOptional(record -> new TaskState(
                        record.get(task.TASK_KIND), record.get(task.STATUS), record.get(task.VERSION),
                        record.get("active_guard", Boolean.class)))
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        if (state.activeGuard()) {
            throw new BusinessProblem(
                    ProblemCode.TASK_EXECUTION_GUARDED,
                    "Candidate assignment is disabled while an execution guard is ACTIVE");
        }
        if (state.version() != expectedVersion) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "Task version changed");
        }
        throw new BusinessProblem(
                ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                "Candidate assignment requires a READY HUMAN task but was "
                        + state.taskKind() + "/" + state.status());
    }

    private TaskAssignmentBatchReceipt findBatch(String tenantId, UUID batchId) {
        return dsl.select(TSK_TASK_ASSIGNMENT_BATCH.ASSIGNMENT_BATCH_ID,
                        TSK_TASK_ASSIGNMENT_BATCH.TASK_ID,
                        TSK_TASK_ASSIGNMENT_BATCH.CANDIDATE_COUNT,
                        TSK_TASK_ASSIGNMENT_BATCH.TASK_VERSION,
                        TSK_TASK_ASSIGNMENT_BATCH.ASSIGNED_AT)
                .from(TSK_TASK_ASSIGNMENT_BATCH)
                .where(TSK_TASK_ASSIGNMENT_BATCH.TENANT_ID.eq(tenantId))
                .and(TSK_TASK_ASSIGNMENT_BATCH.ASSIGNMENT_BATCH_ID.eq(batchId))
                .fetchSingle(record -> new TaskAssignmentBatchReceipt(
                        record.value1(), record.value2(), record.value3(),
                        record.value4(), record.value5()));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Task assignment payload cannot be serialized", exception);
        }
    }

    private record TaskState(String taskKind, String status, long version, boolean activeGuard) {
    }
}
