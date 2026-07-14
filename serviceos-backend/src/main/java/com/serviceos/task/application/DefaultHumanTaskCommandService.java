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
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCommandReceipt;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.StartHumanTaskCommand;
import com.serviceos.task.api.TaskClaimedPayload;
import com.serviceos.task.api.TaskCompletedPayload;
import com.serviceos.task.api.TaskStartedPayload;
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
 * 人工工作流 Task 的命令状态机。
 *
 * <p>授权、幂等抢占、状态更新、审计、领域事件及冻结响应位于同一事务。actorId 只取自认证主体，
 * 请求体不能自报执行人；expectedVersion 同时保护 UI 陈旧操作与并发领取。</p>
 */
@Service
final class DefaultHumanTaskCommandService implements HumanTaskCommandService {
    private static final String CLAIM = "task.human.claim";
    private static final String START = "task.human.start";
    private static final String COMPLETE = "task.human.complete";

    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultHumanTaskCommandService(
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
    public HumanTaskCommandReceipt claim(
            CurrentPrincipal principal, CommandMetadata metadata, ClaimHumanTaskCommand command) {
        String digest = Sha256.digest(command.taskId() + "|" + command.expectedVersion());
        return execute(principal, metadata, CLAIM, "task.claim", command.taskId(),
                command.expectedVersion(), "READY", null, digest);
    }

    @Override
    @Transactional
    public HumanTaskCommandReceipt start(
            CurrentPrincipal principal, CommandMetadata metadata, StartHumanTaskCommand command) {
        String digest = Sha256.digest(command.taskId() + "|" + command.expectedVersion());
        return execute(principal, metadata, START, "task.start", command.taskId(),
                command.expectedVersion(), "CLAIMED", null, digest);
    }

    @Override
    @Transactional
    public HumanTaskCommandReceipt complete(
            CurrentPrincipal principal, CommandMetadata metadata, CompleteHumanTaskCommand command) {
        String digest = Sha256.digest(command.taskId() + "|" + command.expectedVersion()
                + "|" + command.resultRef() + "|" + command.resultDigest());
        return execute(principal, metadata, COMPLETE, "task.complete", command.taskId(),
                command.expectedVersion(), "RUNNING", command, digest);
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

        Instant occurredAt = clock.instant();
        String nextStatus = switch (operation) {
            case CLAIM -> "CLAIMED";
            case START -> "RUNNING";
            case COMPLETE -> "COMPLETED";
            default -> throw new IllegalStateException("unsupported human task operation");
        };
        int updated = updateTask(
                context, taskId, expectedVersion, expectedStatus, nextStatus, completion, occurredAt);
        if (updated != 1) {
            throwConflict(
                    context.tenantId(), taskId, context.actorId(),
                    expectedVersion, expectedStatus, operation);
        }

        HumanTaskRow task = findTask(context.tenantId(), taskId);
        appendEvent(context, operation, task, completion, occurredAt);
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
            Instant now
    ) {
        if (completion != null) {
            return jdbc.sql("""
                            UPDATE tsk_task
                               SET status = 'COMPLETED', result_ref = :resultRef,
                                   result_digest = :resultDigest, completed_at = :now,
                                   version = version + 1, updated_at = :now
                             WHERE tenant_id = :tenantId AND task_id = :taskId
                               AND task_kind = 'HUMAN' AND status = :expectedStatus
                               AND claimed_by = :actorId AND version = :expectedVersion
                               AND workflow_node_instance_id IS NOT NULL
                            """)
                    .param("resultRef", completion.resultRef())
                    .param("resultDigest", completion.resultDigest())
                    .param("now", timestamptz(now))
                    .param("tenantId", context.tenantId())
                    .param("taskId", taskId)
                    .param("expectedStatus", expectedStatus)
                    .param("actorId", context.actorId())
                    .param("expectedVersion", expectedVersion)
                    .update();
        }
        if ("CLAIMED".equals(nextStatus)) {
            return jdbc.sql("""
                            UPDATE tsk_task
                               SET status = 'CLAIMED', claimed_by = :actorId, claimed_at = :now,
                                   version = version + 1, updated_at = :now
                             WHERE tenant_id = :tenantId AND task_id = :taskId
                               AND task_kind = 'HUMAN' AND status = :expectedStatus
                               AND version = :expectedVersion
                            """)
                    .param("actorId", context.actorId()).param("now", timestamptz(now))
                    .param("tenantId", context.tenantId()).param("taskId", taskId)
                    .param("expectedStatus", expectedStatus).param("expectedVersion", expectedVersion)
                    .update();
        }
        return jdbc.sql("""
                        UPDATE tsk_task
                           SET status = 'RUNNING', started_at = :now,
                               version = version + 1, updated_at = :now
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND task_kind = 'HUMAN' AND status = :expectedStatus
                           AND claimed_by = :actorId AND version = :expectedVersion
                        """)
                .param("now", timestamptz(now)).param("tenantId", context.tenantId())
                .param("taskId", taskId).param("expectedStatus", expectedStatus)
                .param("actorId", context.actorId()).param("expectedVersion", expectedVersion)
                .update();
    }

    private void throwConflict(
            String tenantId,
            UUID taskId,
            String actorId,
            long expectedVersion,
            String expectedStatus,
            String operation
    ) {
        HumanTaskRow current = findTask(tenantId, taskId);
        if (!"HUMAN".equals(current.taskKind())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT, "Only HUMAN tasks accept human commands");
        }
        if (current.version() != expectedVersion) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "Task version changed from expected " + expectedVersion + " to " + current.version());
        }
        if (!expectedStatus.equals(current.status())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    operation + " requires task status " + expectedStatus + " but was " + current.status());
        }
        if ((START.equals(operation) || COMPLETE.equals(operation))
                && !actorId.equals(current.claimedBy())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT, "Task is owned by another actor");
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
        } else {
            eventType = "task.completed";
            payload = new TaskCompletedPayload(
                    task.taskId(), task.projectId(), task.workOrderId(), task.workflowInstanceId(),
                    task.stageInstanceId(), task.workflowNodeInstanceId(), task.workflowNodeId(),
                    task.taskType(), task.workflowDefinitionVersionId(), task.workflowDefinitionDigest(),
                    completion.resultRef(), completion.resultDigest(), occurredAt);
        }
        String json = serialize(payload);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "task", eventType, 1,
                "Task", task.taskId().toString(), task.version(), context.tenantId(),
                context.correlationId(), context.idempotencyKey(), task.taskId().toString(),
                json, Sha256.digest(json), occurredAt));
    }

    private HumanTaskRow findTask(String tenantId, UUID taskId) {
        return jdbc.sql("""
                        SELECT task_id, task_type, task_kind, status, claimed_by, version,
                               project_id, work_order_id, workflow_instance_id, stage_instance_id,
                               workflow_node_instance_id, workflow_node_id,
                               workflow_definition_version_id, workflow_definition_digest
                          FROM tsk_task
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                        """)
                .param("tenantId", tenantId).param("taskId", taskId)
                .query(HumanTaskRow.class).optional()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
    }

    private void insertFrozenReceipt(
            CommandContext context, String operation, HumanTaskCommandReceipt receipt) {
        jdbc.sql("""
                        INSERT INTO tsk_human_task_command_result (
                            tenant_id, operation_type, idempotency_key, task_id,
                            status, actor_id, task_version, occurred_at
                        ) VALUES (
                            :tenantId, :operation, :idempotencyKey, :taskId,
                            :status, :actorId, :version, :occurredAt
                        )
                        """)
                .param("tenantId", context.tenantId()).param("operation", operation)
                .param("idempotencyKey", context.idempotencyKey()).param("taskId", receipt.taskId())
                .param("status", receipt.status()).param("actorId", receipt.actorId())
                .param("version", receipt.version()).param("occurredAt", timestamptz(receipt.occurredAt()))
                .update();
    }

    private HumanTaskCommandReceipt frozenReceipt(CommandContext context, String operation) {
        return jdbc.sql("""
                        SELECT task_id, status, actor_id, task_version AS version, occurred_at
                          FROM tsk_human_task_command_result
                         WHERE tenant_id = :tenantId AND operation_type = :operation
                           AND idempotency_key = :idempotencyKey
                        """)
                .param("tenantId", context.tenantId()).param("operation", operation)
                .param("idempotencyKey", context.idempotencyKey())
                .query(HumanTaskCommandReceipt.class).single();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Task command payload cannot be serialized", exception);
        }
    }

    private record HumanTaskRow(
            UUID taskId, String taskType, String taskKind, String status, String claimedBy, long version,
            UUID projectId, UUID workOrderId, UUID workflowInstanceId, UUID stageInstanceId,
            UUID workflowNodeInstanceId, String workflowNodeId,
            UUID workflowDefinitionVersionId, String workflowDefinitionDigest
    ) {
    }
}
