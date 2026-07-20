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
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCommandReceipt;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.HumanTaskCompletionValidator;
import com.serviceos.task.api.ReleaseHumanTaskCommand;
import com.serviceos.task.api.StartHumanTaskCommand;
import com.serviceos.task.api.TaskClaimedPayload;
import com.serviceos.task.api.TaskCompletedPayload;
import com.serviceos.task.api.TaskStartedPayload;
import com.serviceos.task.api.TaskReleasedPayload;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.TskHumanTaskCommandResult.TSK_HUMAN_TASK_COMMAND_RESULT;
import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;
import static com.serviceos.jooq.generated.tables.TskTaskAssignment.TSK_TASK_ASSIGNMENT;
import static com.serviceos.jooq.generated.tables.TskTaskExecutionGuard.TSK_TASK_EXECUTION_GUARD;

/**
 * 人工工作流 Task 的命令状态机。
 *
 * <p>授权、幂等抢占、状态更新、审计、领域事件及冻结响应位于同一事务。actorId 只取自认证主体，
 * 请求体不能自报执行人；expectedVersion 同时保护 UI 陈旧操作与并发领取。</p>
 */
@Service
final class JooqHumanTaskCommandService implements HumanTaskCommandService {
    private static final String CLAIM = "task.human.claim";
    private static final String START = "task.human.start";
    private static final String COMPLETE = "task.human.complete";
    private static final String RELEASE = "task.human.release";

    private final DSLContext dsl;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final List<HumanTaskCompletionValidator> completionValidators;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqHumanTaskCommandService(
            DSLContext dsl,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            List<HumanTaskCompletionValidator> completionValidators,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.dsl = dsl;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.completionValidators = List.copyOf(completionValidators);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public HumanTaskCommandReceipt claim(
            CurrentPrincipal principal, CommandMetadata metadata, ClaimHumanTaskCommand command) {
        String digest = Sha256.digest(command.taskId() + "|" + command.expectedVersion());
        return execute(principal, metadata, CLAIM, "task.claim", command.taskId(),
                command.expectedVersion(), "READY", null, null, digest);
    }

    @Override
    @Transactional
    public HumanTaskCommandReceipt start(
            CurrentPrincipal principal, CommandMetadata metadata, StartHumanTaskCommand command) {
        String digest = Sha256.digest(command.taskId() + "|" + command.expectedVersion());
        return execute(principal, metadata, START, "task.start", command.taskId(),
                command.expectedVersion(), "CLAIMED", null, null, digest);
    }

    @Override
    @Transactional
    public HumanTaskCommandReceipt complete(
            CurrentPrincipal principal, CommandMetadata metadata, CompleteHumanTaskCommand command) {
        String digest = Sha256.digest(command.taskId() + "|" + command.expectedVersion()
                + "|" + command.resultRef() + "|" + command.resultDigest()
                + "|" + serialize(command.inputVersionRefs()));
        return execute(principal, metadata, COMPLETE, "task.complete", command.taskId(),
                command.expectedVersion(), "RUNNING", command, null, digest);
    }

    @Override
    @Transactional
    public HumanTaskCommandReceipt release(
            CurrentPrincipal principal, CommandMetadata metadata, ReleaseHumanTaskCommand command) {
        String digest = Sha256.digest(
                command.taskId() + "|" + command.expectedVersion() + "|" + command.reasonCode());
        return execute(principal, metadata, RELEASE, "task.release", command.taskId(),
                command.expectedVersion(), "CLAIMED", null, command, digest);
    }

    private HumanTaskCommandReceipt execute(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String operation,
            String capability,
            UUID taskId,
            long expectedVersion,
            String expectedStatus,
            CompleteHumanTaskCommand completion,
            ReleaseHumanTaskCommand release,
            String requestDigest
    ) {
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        AuthorizationDecision authorizationDecision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        capability, context.tenantId(), "Task", taskId.toString()),
                context.correlationId());
        IdempotencyDecision decision = idempotency.begin(context, operation, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return frozenReceipt(context, operation);
        }
        if (completion != null) {
            // M330：校验器可取得 correlationId，便于 RULE 拒绝审计与命令关联。
            completionValidators.forEach(validator ->
                    validator.validate(principal, context.correlationId(), completion));
        }

        Instant occurredAt = clock.instant();
        String nextStatus = switch (operation) {
            case CLAIM -> "CLAIMED";
            case START -> "RUNNING";
            case COMPLETE -> "COMPLETED";
            case RELEASE -> "READY";
            default -> throw new IllegalStateException("unsupported human task operation");
        };
        int updated = updateTask(
                context, taskId, expectedVersion, expectedStatus, nextStatus, completion, release, occurredAt);
        if (updated != 1) {
            throwConflict(
                    context.tenantId(), taskId, context.actorId(),
                    expectedVersion, expectedStatus, operation);
        }

        HumanTaskRow task = findTask(context.tenantId(), taskId, context.actorId());
        if (CLAIM.equals(operation)) {
            activateResponsibility(context, taskId, occurredAt);
        } else if (RELEASE.equals(operation)) {
            revokeResponsibility(context, taskId, release.reasonCode(), occurredAt);
        } else if (COMPLETE.equals(operation)) {
            expireAssignments(context, taskId, occurredAt);
        }
        appendEvent(context, operation, task, completion, release, occurredAt);
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(),
                operation.toUpperCase().replace('.', '_'), capability,
                "Task", taskId.toString(), "ALLOW", authorizationDecision.matchedGrantIds(),
                authorizationDecision.policyVersion(), "SUCCEEDED", null, requestDigest,
                context.correlationId(), occurredAt));
        HumanTaskCommandReceipt receipt = new HumanTaskCommandReceipt(
                taskId, nextStatus, context.actorId(), task.version(), occurredAt);
        insertFrozenReceipt(context, operation, receipt);
        idempotency.complete(
                context, operation, taskId.toString(), Sha256.digest(serialize(receipt)));
        return receipt;
    }

    private int updateTask(
            CommandContext context,
            UUID taskId,
            long expectedVersion,
            String expectedStatus,
            String nextStatus,
            CompleteHumanTaskCommand completion,
            ReleaseHumanTaskCommand release,
            Instant now
    ) {
        TskTask task = TSK_TASK;
        if (completion != null) {
            // input_version_refs 生成类型即 String（jsonb 绑定由公共 Converter 完成，无需显式 CAST）。
            return dsl.update(task)
                    .set(task.STATUS, "COMPLETED")
                    .set(task.RESULT_REF, completion.resultRef())
                    .set(task.RESULT_DIGEST, completion.resultDigest())
                    .set(task.INPUT_VERSION_REFS, serialize(completion.inputVersionRefs()))
                    .set(task.COMPLETED_AT, now)
                    .set(task.VERSION, task.VERSION.plus(1))
                    .set(task.UPDATED_AT, now)
                    .where(task.TENANT_ID.eq(context.tenantId()))
                    .and(task.TASK_ID.eq(taskId))
                    .and(task.TASK_KIND.eq("HUMAN"))
                    .and(task.STATUS.eq(expectedStatus))
                    .and(task.CLAIMED_BY.eq(context.actorId()))
                    .and(task.VERSION.eq(expectedVersion))
                    .and(task.WORKFLOW_NODE_INSTANCE_ID.isNotNull())
                    .and(noActiveGuard())
                    .and(assignmentExists("RESPONSIBLE", context.actorId(), true))
                    .execute();
        }
        if (release != null) {
            return dsl.update(task)
                    .set(task.STATUS, "READY")
                    .setNull(task.CLAIMED_BY)
                    .setNull(task.CLAIMED_AT)
                    .set(task.VERSION, task.VERSION.plus(1))
                    .set(task.UPDATED_AT, now)
                    .where(task.TENANT_ID.eq(context.tenantId()))
                    .and(task.TASK_ID.eq(taskId))
                    .and(task.TASK_KIND.eq("HUMAN"))
                    .and(task.STATUS.eq(expectedStatus))
                    .and(task.CLAIMED_BY.eq(context.actorId()))
                    .and(task.VERSION.eq(expectedVersion))
                    .and(noActiveGuard())
                    .and(assignmentExists("RESPONSIBLE", context.actorId(), false))
                    .execute();
        }
        if ("CLAIMED".equals(nextStatus)) {
            return dsl.update(task)
                    .set(task.STATUS, "CLAIMED")
                    .set(task.CLAIMED_BY, context.actorId())
                    .set(task.CLAIMED_AT, now)
                    .set(task.VERSION, task.VERSION.plus(1))
                    .set(task.UPDATED_AT, now)
                    .where(task.TENANT_ID.eq(context.tenantId()))
                    .and(task.TASK_ID.eq(taskId))
                    .and(task.TASK_KIND.eq("HUMAN"))
                    .and(task.STATUS.eq(expectedStatus))
                    .and(task.VERSION.eq(expectedVersion))
                    .and(noActiveGuard())
                    .and(assignmentExists("CANDIDATE", context.actorId(), true))
                    .execute();
        }
        return dsl.update(task)
                .set(task.STATUS, "RUNNING")
                .set(task.STARTED_AT, now)
                .set(task.VERSION, task.VERSION.plus(1))
                .set(task.UPDATED_AT, now)
                .where(task.TENANT_ID.eq(context.tenantId()))
                .and(task.TASK_ID.eq(taskId))
                .and(task.TASK_KIND.eq("HUMAN"))
                .and(task.STATUS.eq(expectedStatus))
                .and(task.CLAIMED_BY.eq(context.actorId()))
                .and(task.VERSION.eq(expectedVersion))
                .and(noActiveGuard())
                .and(assignmentExists("RESPONSIBLE", context.actorId(), false))
                .execute();
    }

    /** 与原 SQL 一致：ACTIVE guard 存在即拒绝命令，子查询按别名 guard_row 关联外层任务行。 */
    private static Condition noActiveGuard() {
        TskTaskExecutionGuard guard = TSK_TASK_EXECUTION_GUARD.as("guard_row");
        return DSL.notExists(DSL.selectOne()
                .from(guard)
                .where(guard.TENANT_ID.eq(TSK_TASK.TENANT_ID))
                .and(guard.TASK_ID.eq(TSK_TASK.TASK_ID))
                .and(guard.STATUS.eq("ACTIVE")));
    }

    /**
     * 与原 SQL 一致：CLAIM/COMPLETE 检查 principal_type = 'USER'；START/RELEASE 不检查。
     * 差异来自原状态机 SQL，不得擅自对齐。
     */
    private static Condition assignmentExists(String kind, String actorId, boolean withPrincipalType) {
        TskTaskAssignment assignment = TSK_TASK_ASSIGNMENT.as("assignment");
        var select = DSL.selectOne()
                .from(assignment)
                .where(assignment.TENANT_ID.eq(TSK_TASK.TENANT_ID))
                .and(assignment.TASK_ID.eq(TSK_TASK.TASK_ID))
                .and(assignment.ASSIGNMENT_KIND.eq(kind))
                .and(assignment.PRINCIPAL_ID.eq(actorId))
                .and(assignment.STATUS.eq("ACTIVE"));
        if (withPrincipalType) {
            select = select.and(assignment.PRINCIPAL_TYPE.eq("USER"));
        }
        return DSL.exists(select);
    }

    private void throwConflict(
            String tenantId,
            UUID taskId,
            String actorId,
            long expectedVersion,
            String expectedStatus,
            String operation
    ) {
        HumanTaskRow current = findTask(tenantId, taskId, actorId);
        if (!"HUMAN".equals(current.taskKind())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT, "Only HUMAN tasks accept human commands");
        }
        if (current.activeGuard()) {
            throw new BusinessProblem(
                    ProblemCode.TASK_EXECUTION_GUARDED,
                    "Task commands are disabled while an execution guard is ACTIVE");
        }
        if (current.version() != expectedVersion) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "Task version changed from expected " + expectedVersion + " to " + current.version());
        }
        if (!expectedStatus.equals(current.status())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    operation + " requires task status " + expectedStatus + " but was " + current.status());
        }
        if (CLAIM.equals(operation) && !current.actorCandidate()) {
            throw new BusinessProblem(
                    ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    "Task can only be claimed by an ACTIVE candidate");
        }
        if ((START.equals(operation) || COMPLETE.equals(operation))
                && !actorId.equals(current.claimedBy())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT, "Task is owned by another actor");
        }
        if ((START.equals(operation) || COMPLETE.equals(operation) || RELEASE.equals(operation))
                && !current.actorResponsible()) {
            throw new BusinessProblem(
                    ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    "Task command requires the ACTIVE responsible assignment");
        }
        if (COMPLETE.equals(operation) && current.workflowNodeInstanceId() == null) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "M20 completion requires a workflow-backed human task");
        }
        throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT, "Task command precondition failed");
    }

    private void appendEvent(
            CommandContext context,
            String operation,
            HumanTaskRow task,
            CompleteHumanTaskCommand completion,
            ReleaseHumanTaskCommand release,
            Instant occurredAt
    ) {
        String eventType;
        Object payload;
        if (CLAIM.equals(operation)) {
            eventType = "task.claimed";
            payload = new TaskClaimedPayload(task.taskId(), context.actorId(), occurredAt);
        } else if (START.equals(operation)) {
            eventType = "task.started";
            payload = new TaskStartedPayload(task.taskId(), context.actorId(), occurredAt);
        } else if (COMPLETE.equals(operation)) {
            eventType = "task.completed";
            payload = new TaskCompletedPayload(
                    task.taskId(), task.projectId(), task.workOrderId(), task.workflowInstanceId(),
                    task.stageInstanceId(), task.workflowNodeInstanceId(), task.workflowNodeId(),
                    task.taskType(), task.workflowDefinitionVersionId(), task.workflowDefinitionDigest(),
                    completion.resultRef(), completion.resultDigest(), occurredAt,
                    completion.inputVersionRefs());
        } else {
            eventType = "task.released";
            payload = new TaskReleasedPayload(
                    task.taskId(), context.actorId(), release.reasonCode(), occurredAt);
        }
        String json = serialize(payload);
        int schemaVersion = COMPLETE.equals(operation) ? 2 : 1;
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "task", eventType, schemaVersion,
                "Task", task.taskId().toString(), task.version(), context.tenantId(),
                context.correlationId(), context.idempotencyKey(), task.taskId().toString(),
                json, Sha256.digest(json), occurredAt));
    }

    private HumanTaskRow findTask(String tenantId, UUID taskId, String actorId) {
        TskTask task = TSK_TASK;
        TskTaskExecutionGuard guard = TSK_TASK_EXECUTION_GUARD.as("guard_row");
        TskTaskAssignment candidate = TSK_TASK_ASSIGNMENT.as("candidate");
        TskTaskAssignment responsible = TSK_TASK_ASSIGNMENT.as("responsible");
        Field<Boolean> activeGuard = DSL.exists(dsl.selectOne()
                        .from(guard)
                        .where(guard.TENANT_ID.eq(task.TENANT_ID))
                        .and(guard.TASK_ID.eq(task.TASK_ID))
                        .and(guard.STATUS.eq("ACTIVE")))
                .as("active_guard");
        Field<Boolean> actorCandidate = DSL.exists(dsl.selectOne()
                        .from(candidate)
                        .where(candidate.TENANT_ID.eq(task.TENANT_ID))
                        .and(candidate.TASK_ID.eq(task.TASK_ID))
                        .and(candidate.ASSIGNMENT_KIND.eq("CANDIDATE"))
                        .and(candidate.PRINCIPAL_ID.eq(actorId))
                        .and(candidate.STATUS.eq("ACTIVE")))
                .as("actor_candidate");
        Field<Boolean> actorResponsible = DSL.exists(dsl.selectOne()
                        .from(responsible)
                        .where(responsible.TENANT_ID.eq(task.TENANT_ID))
                        .and(responsible.TASK_ID.eq(task.TASK_ID))
                        .and(responsible.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                        .and(responsible.PRINCIPAL_ID.eq(actorId))
                        .and(responsible.STATUS.eq("ACTIVE")))
                .as("actor_responsible");
        return dsl.select(task.TASK_ID, task.TASK_TYPE, task.TASK_KIND, task.STATUS,
                        task.CLAIMED_BY, task.VERSION,
                        task.PROJECT_ID, task.WORK_ORDER_ID, task.WORKFLOW_INSTANCE_ID,
                        task.STAGE_INSTANCE_ID, task.WORKFLOW_NODE_INSTANCE_ID, task.WORKFLOW_NODE_ID,
                        task.WORKFLOW_DEFINITION_VERSION_ID, task.WORKFLOW_DEFINITION_DIGEST,
                        activeGuard, actorCandidate, actorResponsible)
                .from(task)
                .where(task.TENANT_ID.eq(tenantId))
                .and(task.TASK_ID.eq(taskId))
                .fetchOptional(record -> mapHumanTaskRow(record, activeGuard, actorCandidate, actorResponsible))
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
    }

    private static HumanTaskRow mapHumanTaskRow(
            Record record, Field<Boolean> activeGuard, Field<Boolean> actorCandidate,
            Field<Boolean> actorResponsible) {
        TskTask task = TSK_TASK;
        return new HumanTaskRow(
                record.get(task.TASK_ID), record.get(task.TASK_TYPE), record.get(task.TASK_KIND),
                record.get(task.STATUS), record.get(task.CLAIMED_BY), record.get(task.VERSION),
                record.get(task.PROJECT_ID), record.get(task.WORK_ORDER_ID),
                record.get(task.WORKFLOW_INSTANCE_ID), record.get(task.STAGE_INSTANCE_ID),
                record.get(task.WORKFLOW_NODE_INSTANCE_ID), record.get(task.WORKFLOW_NODE_ID),
                record.get(task.WORKFLOW_DEFINITION_VERSION_ID),
                record.get(task.WORKFLOW_DEFINITION_DIGEST),
                record.get(activeGuard), record.get(actorCandidate), record.get(actorResponsible));
    }

    private void insertFrozenReceipt(
            CommandContext context, String operation, HumanTaskCommandReceipt receipt) {
        dsl.insertInto(TSK_HUMAN_TASK_COMMAND_RESULT)
                .set(TSK_HUMAN_TASK_COMMAND_RESULT.TENANT_ID, context.tenantId())
                .set(TSK_HUMAN_TASK_COMMAND_RESULT.OPERATION_TYPE, operation)
                .set(TSK_HUMAN_TASK_COMMAND_RESULT.IDEMPOTENCY_KEY, context.idempotencyKey())
                .set(TSK_HUMAN_TASK_COMMAND_RESULT.TASK_ID, receipt.taskId())
                .set(TSK_HUMAN_TASK_COMMAND_RESULT.STATUS, receipt.status())
                .set(TSK_HUMAN_TASK_COMMAND_RESULT.ACTOR_ID, receipt.actorId())
                .set(TSK_HUMAN_TASK_COMMAND_RESULT.TASK_VERSION, receipt.version())
                .set(TSK_HUMAN_TASK_COMMAND_RESULT.OCCURRED_AT, receipt.occurredAt())
                .execute();
    }

    private HumanTaskCommandReceipt frozenReceipt(CommandContext context, String operation) {
        return dsl.select(TSK_HUMAN_TASK_COMMAND_RESULT.TASK_ID,
                        TSK_HUMAN_TASK_COMMAND_RESULT.STATUS,
                        TSK_HUMAN_TASK_COMMAND_RESULT.ACTOR_ID,
                        TSK_HUMAN_TASK_COMMAND_RESULT.TASK_VERSION,
                        TSK_HUMAN_TASK_COMMAND_RESULT.OCCURRED_AT)
                .from(TSK_HUMAN_TASK_COMMAND_RESULT)
                .where(TSK_HUMAN_TASK_COMMAND_RESULT.TENANT_ID.eq(context.tenantId()))
                .and(TSK_HUMAN_TASK_COMMAND_RESULT.OPERATION_TYPE.eq(operation))
                .and(TSK_HUMAN_TASK_COMMAND_RESULT.IDEMPOTENCY_KEY.eq(context.idempotencyKey()))
                .fetchSingle(record -> new HumanTaskCommandReceipt(
                        record.value1(), record.value2(), record.value3(), record.value4(), record.value5()));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Task command payload cannot be serialized", exception);
        }
    }

    private void activateResponsibility(CommandContext context, UUID taskId, Instant claimedAt) {
        CandidateAssignment candidate = dsl
                .select(TSK_TASK_ASSIGNMENT.TASK_ASSIGNMENT_ID, TSK_TASK_ASSIGNMENT.ASSIGNMENT_BATCH_ID)
                .from(TSK_TASK_ASSIGNMENT)
                .where(TSK_TASK_ASSIGNMENT.TENANT_ID.eq(context.tenantId()))
                .and(TSK_TASK_ASSIGNMENT.TASK_ID.eq(taskId))
                .and(TSK_TASK_ASSIGNMENT.ASSIGNMENT_KIND.eq("CANDIDATE"))
                .and(TSK_TASK_ASSIGNMENT.PRINCIPAL_TYPE.eq("USER"))
                .and(TSK_TASK_ASSIGNMENT.PRINCIPAL_ID.eq(context.actorId()))
                .and(TSK_TASK_ASSIGNMENT.STATUS.eq("ACTIVE"))
                .fetchSingle(record -> new CandidateAssignment(record.value1(), record.value2()));
        dsl.insertInto(TSK_TASK_ASSIGNMENT)
                .set(TSK_TASK_ASSIGNMENT.TASK_ASSIGNMENT_ID, UUID.randomUUID())
                .set(TSK_TASK_ASSIGNMENT.TENANT_ID, context.tenantId())
                .set(TSK_TASK_ASSIGNMENT.TASK_ID, taskId)
                .set(TSK_TASK_ASSIGNMENT.ASSIGNMENT_BATCH_ID, candidate.assignmentBatchId())
                .set(TSK_TASK_ASSIGNMENT.ASSIGNMENT_KIND, "RESPONSIBLE")
                .set(TSK_TASK_ASSIGNMENT.PRINCIPAL_TYPE, "USER")
                .set(TSK_TASK_ASSIGNMENT.PRINCIPAL_ID, context.actorId())
                .set(TSK_TASK_ASSIGNMENT.STATUS, "ACTIVE")
                .set(TSK_TASK_ASSIGNMENT.SOURCE_TYPE, "CANDIDATE_CLAIM")
                .set(TSK_TASK_ASSIGNMENT.SOURCE_ID, candidate.taskAssignmentId().toString())
                .set(TSK_TASK_ASSIGNMENT.EFFECTIVE_FROM, claimedAt)
                .set(TSK_TASK_ASSIGNMENT.CREATED_BY, context.actorId())
                .set(TSK_TASK_ASSIGNMENT.CREATED_AT, claimedAt)
                .execute();
    }

    private void revokeResponsibility(
            CommandContext context, UUID taskId, String reasonCode, Instant releasedAt) {
        int updated = dsl.update(TSK_TASK_ASSIGNMENT)
                .set(TSK_TASK_ASSIGNMENT.STATUS, "REVOKED")
                .set(TSK_TASK_ASSIGNMENT.EFFECTIVE_TO, releasedAt)
                .set(TSK_TASK_ASSIGNMENT.REVOKED_BY, context.actorId())
                .set(TSK_TASK_ASSIGNMENT.REVOKE_REASON_CODE, reasonCode)
                .where(TSK_TASK_ASSIGNMENT.TENANT_ID.eq(context.tenantId()))
                .and(TSK_TASK_ASSIGNMENT.TASK_ID.eq(taskId))
                .and(TSK_TASK_ASSIGNMENT.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                .and(TSK_TASK_ASSIGNMENT.PRINCIPAL_ID.eq(context.actorId()))
                .and(TSK_TASK_ASSIGNMENT.STATUS.eq("ACTIVE"))
                .execute();
        if (updated != 1) {
            throw new BusinessProblem(
                    ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    "ACTIVE responsible assignment could not be revoked");
        }
    }

    private void expireAssignments(CommandContext context, UUID taskId, Instant completedAt) {
        int updated = dsl.update(TSK_TASK_ASSIGNMENT)
                .set(TSK_TASK_ASSIGNMENT.STATUS, "EXPIRED")
                .set(TSK_TASK_ASSIGNMENT.EFFECTIVE_TO, completedAt)
                .set(TSK_TASK_ASSIGNMENT.REVOKED_BY, context.actorId())
                .set(TSK_TASK_ASSIGNMENT.REVOKE_REASON_CODE, "TASK_COMPLETED")
                .where(TSK_TASK_ASSIGNMENT.TENANT_ID.eq(context.tenantId()))
                .and(TSK_TASK_ASSIGNMENT.TASK_ID.eq(taskId))
                .and(TSK_TASK_ASSIGNMENT.STATUS.eq("ACTIVE"))
                .execute();
        if (updated < 2) {
            throw new BusinessProblem(
                    ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    "Completed task must close candidate and responsible assignments");
        }
    }

    private record HumanTaskRow(
            UUID taskId, String taskType, String taskKind, String status, String claimedBy, long version,
            UUID projectId, UUID workOrderId, UUID workflowInstanceId, UUID stageInstanceId,
            UUID workflowNodeInstanceId, String workflowNodeId,
            UUID workflowDefinitionVersionId, String workflowDefinitionDigest,
            boolean activeGuard, boolean actorCandidate, boolean actorResponsible
    ) {
    }

    private record CandidateAssignment(UUID taskAssignmentId, UUID assignmentBatchId) {
    }
}
