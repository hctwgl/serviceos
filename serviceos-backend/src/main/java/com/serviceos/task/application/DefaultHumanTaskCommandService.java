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
import com.serviceos.task.api.ReleaseHumanTaskCommand;
import com.serviceos.task.api.StartHumanTaskCommand;
import com.serviceos.task.api.TaskClaimedPayload;
import com.serviceos.task.api.TaskCompletedPayload;
import com.serviceos.task.api.TaskStartedPayload;
import com.serviceos.task.api.TaskReleasedPayload;
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
    private static final String RELEASE = "task.human.release";

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
                + "|" + command.resultRef() + "|" + command.resultDigest());
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
                               AND NOT EXISTS (
                                   SELECT 1 FROM tsk_task_execution_guard guard_row
                                    WHERE guard_row.tenant_id = tsk_task.tenant_id
                                      AND guard_row.task_id = tsk_task.task_id
                                      AND guard_row.status = 'ACTIVE'
                               )
                               AND EXISTS (
                                   SELECT 1 FROM tsk_task_assignment assignment
                                    WHERE assignment.tenant_id = tsk_task.tenant_id
                                      AND assignment.task_id = tsk_task.task_id
                                      AND assignment.assignment_kind = 'RESPONSIBLE'
                                      AND assignment.principal_type = 'USER'
                                      AND assignment.principal_id = :actorId
                                      AND assignment.status = 'ACTIVE'
                               )
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
        if (release != null) {
            return jdbc.sql("""
                            UPDATE tsk_task
                               SET status = 'READY', claimed_by = NULL, claimed_at = NULL,
                                   version = version + 1, updated_at = :now
                             WHERE tenant_id = :tenantId AND task_id = :taskId
                               AND task_kind = 'HUMAN' AND status = :expectedStatus
                               AND claimed_by = :actorId AND version = :expectedVersion
                               AND NOT EXISTS (
                                   SELECT 1 FROM tsk_task_execution_guard guard_row
                                    WHERE guard_row.tenant_id = tsk_task.tenant_id
                                      AND guard_row.task_id = tsk_task.task_id
                                      AND guard_row.status = 'ACTIVE'
                               )
                               AND EXISTS (
                                   SELECT 1 FROM tsk_task_assignment assignment
                                    WHERE assignment.tenant_id = tsk_task.tenant_id
                                      AND assignment.task_id = tsk_task.task_id
                                      AND assignment.assignment_kind = 'RESPONSIBLE'
                                      AND assignment.principal_id = :actorId
                                      AND assignment.status = 'ACTIVE'
                               )
                            """)
                    .param("now", timestamptz(now)).param("tenantId", context.tenantId())
                    .param("taskId", taskId).param("expectedStatus", expectedStatus)
                    .param("actorId", context.actorId()).param("expectedVersion", expectedVersion)
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
                               AND NOT EXISTS (
                                   SELECT 1 FROM tsk_task_execution_guard guard_row
                                    WHERE guard_row.tenant_id = tsk_task.tenant_id
                                      AND guard_row.task_id = tsk_task.task_id
                                      AND guard_row.status = 'ACTIVE'
                               )
                               AND EXISTS (
                                   SELECT 1 FROM tsk_task_assignment assignment
                                    WHERE assignment.tenant_id = tsk_task.tenant_id
                                      AND assignment.task_id = tsk_task.task_id
                                      AND assignment.assignment_kind = 'CANDIDATE'
                                      AND assignment.principal_type = 'USER'
                                      AND assignment.principal_id = :actorId
                                      AND assignment.status = 'ACTIVE'
                               )
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
                           AND NOT EXISTS (
                               SELECT 1 FROM tsk_task_execution_guard guard_row
                                WHERE guard_row.tenant_id = tsk_task.tenant_id
                                  AND guard_row.task_id = tsk_task.task_id
                                  AND guard_row.status = 'ACTIVE'
                           )
                           AND EXISTS (
                               SELECT 1 FROM tsk_task_assignment assignment
                                WHERE assignment.tenant_id = tsk_task.tenant_id
                                  AND assignment.task_id = tsk_task.task_id
                                  AND assignment.assignment_kind = 'RESPONSIBLE'
                                  AND assignment.principal_id = :actorId
                                  AND assignment.status = 'ACTIVE'
                           )
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
                    completion.resultRef(), completion.resultDigest(), occurredAt);
        } else {
            eventType = "task.released";
            payload = new TaskReleasedPayload(
                    task.taskId(), context.actorId(), release.reasonCode(), occurredAt);
        }
        String json = serialize(payload);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "task", eventType, 1,
                "Task", task.taskId().toString(), task.version(), context.tenantId(),
                context.correlationId(), context.idempotencyKey(), task.taskId().toString(),
                json, Sha256.digest(json), occurredAt));
    }

    private HumanTaskRow findTask(String tenantId, UUID taskId, String actorId) {
        return jdbc.sql("""
                        SELECT task.task_id, task.task_type, task.task_kind, task.status,
                               task.claimed_by, task.version,
                               project_id, work_order_id, workflow_instance_id, stage_instance_id,
                               workflow_node_instance_id, workflow_node_id,
                               workflow_definition_version_id, workflow_definition_digest,
                               EXISTS (
                                   SELECT 1 FROM tsk_task_execution_guard guard_row
                                    WHERE guard_row.tenant_id = task.tenant_id
                                      AND guard_row.task_id = task.task_id
                                      AND guard_row.status = 'ACTIVE'
                               ) AS active_guard,
                               EXISTS (
                                   SELECT 1 FROM tsk_task_assignment candidate
                                    WHERE candidate.tenant_id = task.tenant_id
                                      AND candidate.task_id = task.task_id
                                      AND candidate.assignment_kind = 'CANDIDATE'
                                      AND candidate.principal_id = :actorId
                                      AND candidate.status = 'ACTIVE'
                               ) AS actor_candidate,
                               EXISTS (
                                   SELECT 1 FROM tsk_task_assignment responsible
                                    WHERE responsible.tenant_id = task.tenant_id
                                      AND responsible.task_id = task.task_id
                                      AND responsible.assignment_kind = 'RESPONSIBLE'
                                      AND responsible.principal_id = :actorId
                                      AND responsible.status = 'ACTIVE'
                               ) AS actor_responsible
                          FROM tsk_task task
                         WHERE task.tenant_id = :tenantId AND task.task_id = :taskId
                        """)
                .param("tenantId", tenantId).param("taskId", taskId).param("actorId", actorId)
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

    private void activateResponsibility(CommandContext context, UUID taskId, Instant claimedAt) {
        CandidateAssignment candidate = jdbc.sql("""
                        SELECT task_assignment_id, assignment_batch_id
                          FROM tsk_task_assignment
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND assignment_kind = 'CANDIDATE' AND principal_type = 'USER'
                           AND principal_id = :actorId AND status = 'ACTIVE'
                        """)
                .param("tenantId", context.tenantId()).param("taskId", taskId)
                .param("actorId", context.actorId()).query(CandidateAssignment.class).single();
        jdbc.sql("""
                        INSERT INTO tsk_task_assignment (
                            task_assignment_id, tenant_id, task_id, assignment_batch_id,
                            assignment_kind, principal_type, principal_id, status,
                            source_type, source_id, effective_from, created_by, created_at
                        ) VALUES (
                            :assignmentId, :tenantId, :taskId, :batchId,
                            'RESPONSIBLE', 'USER', :actorId, 'ACTIVE',
                            'CANDIDATE_CLAIM', :sourceId, :claimedAt, :actorId, :claimedAt
                        )
                        """)
                .param("assignmentId", UUID.randomUUID()).param("tenantId", context.tenantId())
                .param("taskId", taskId).param("batchId", candidate.assignmentBatchId())
                .param("actorId", context.actorId()).param("sourceId", candidate.taskAssignmentId().toString())
                .param("claimedAt", timestamptz(claimedAt)).update();
    }

    private void revokeResponsibility(
            CommandContext context, UUID taskId, String reasonCode, Instant releasedAt) {
        int updated = jdbc.sql("""
                        UPDATE tsk_task_assignment
                           SET status = 'REVOKED', effective_to = :releasedAt,
                               revoked_by = :actorId, revoke_reason_code = :reasonCode
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND assignment_kind = 'RESPONSIBLE' AND principal_id = :actorId
                           AND status = 'ACTIVE'
                        """)
                .param("releasedAt", timestamptz(releasedAt)).param("actorId", context.actorId())
                .param("reasonCode", reasonCode).param("tenantId", context.tenantId())
                .param("taskId", taskId).update();
        if (updated != 1) {
            throw new BusinessProblem(
                    ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    "ACTIVE responsible assignment could not be revoked");
        }
    }

    private void expireAssignments(CommandContext context, UUID taskId, Instant completedAt) {
        int updated = jdbc.sql("""
                        UPDATE tsk_task_assignment
                           SET status = 'EXPIRED', effective_to = :completedAt,
                               revoked_by = :actorId, revoke_reason_code = 'TASK_COMPLETED'
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND status = 'ACTIVE'
                        """)
                .param("completedAt", timestamptz(completedAt)).param("actorId", context.actorId())
                .param("tenantId", context.tenantId()).param("taskId", taskId).update();
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
