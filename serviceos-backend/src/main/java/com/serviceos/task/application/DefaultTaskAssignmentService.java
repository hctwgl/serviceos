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
import com.serviceos.task.api.AssignTaskCandidatesCommand;
import com.serviceos.task.api.TaskAssignedPayload;
import com.serviceos.task.api.TaskAssignmentBatchReceipt;
import com.serviceos.task.api.TaskAssignmentService;
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
 * 人工 Task 候选责任快照命令。
 *
 * <p>M21 只接受已经解析成稳定 USER ID 的候选列表；角色、组织和网点策略解析属于后续配置/派单适配器，
 * 不能在 Task 模块运行时临时读取组织“当前值”并造成历史漂移。</p>
 */
@Service
final class DefaultTaskAssignmentService implements TaskAssignmentService {
    private static final String OPERATION = "task.assignment.assign-candidates";

    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultTaskAssignmentService(
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
    public TaskAssignmentBatchReceipt assignCandidates(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            AssignTaskCandidatesCommand command
    ) {
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        String requestDigest = Sha256.digest(
                command.taskId() + "|" + command.expectedVersion() + "|"
                        + String.join(",", command.candidatePrincipalIds()) + "|"
                        + command.sourceType() + "|" + command.sourceId());
        AuthorizationDecision authorizationDecision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        "task.assign", context.tenantId(), "Task", command.taskId().toString()),
                context.correlationId());
        IdempotencyDecision decision = idempotency.begin(context, OPERATION, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return findBatch(context.tenantId(), UUID.fromString(decision.resourceId().orElseThrow()));
        }

        Instant assignedAt = clock.instant();
        int updated = jdbc.sql("""
                        UPDATE tsk_task
                           SET version = version + 1, updated_at = :assignedAt
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND task_kind = 'HUMAN' AND status = 'READY'
                           AND version = :expectedVersion
                        """)
                .param("assignedAt", timestamptz(assignedAt))
                .param("tenantId", context.tenantId())
                .param("taskId", command.taskId())
                .param("expectedVersion", command.expectedVersion())
                .update();
        if (updated != 1) {
            throwAssignmentConflict(context.tenantId(), command.taskId(), command.expectedVersion());
        }

        // 新快照先关闭旧候选，再写入同一批次；事务失败时旧候选不会被提前撤销。
        jdbc.sql("""
                        UPDATE tsk_task_assignment
                           SET status = 'REVOKED', effective_to = :assignedAt,
                               revoked_by = :actorId, revoke_reason_code = 'ASSIGNMENT_REPLACED'
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND assignment_kind = 'CANDIDATE' AND status = 'ACTIVE'
                        """)
                .param("assignedAt", timestamptz(assignedAt))
                .param("actorId", context.actorId())
                .param("tenantId", context.tenantId())
                .param("taskId", command.taskId())
                .update();

        UUID batchId = UUID.randomUUID();
        long taskVersion = command.expectedVersion() + 1;
        jdbc.sql("""
                        INSERT INTO tsk_task_assignment_batch (
                            assignment_batch_id, tenant_id, task_id, source_type, source_id,
                            candidate_count, task_version, assigned_by, assigned_at
                        ) VALUES (
                            :batchId, :tenantId, :taskId, :sourceType, :sourceId,
                            :candidateCount, :taskVersion, :actorId, :assignedAt
                        )
                        """)
                .param("batchId", batchId).param("tenantId", context.tenantId())
                .param("taskId", command.taskId()).param("sourceType", command.sourceType().name())
                .param("sourceId", command.sourceId())
                .param("candidateCount", command.candidatePrincipalIds().size())
                .param("taskVersion", taskVersion).param("actorId", context.actorId())
                .param("assignedAt", timestamptz(assignedAt)).update();
        for (String candidateId : command.candidatePrincipalIds()) {
            jdbc.sql("""
                            INSERT INTO tsk_task_assignment (
                                task_assignment_id, tenant_id, task_id, assignment_batch_id,
                                assignment_kind, principal_type, principal_id, status,
                                source_type, source_id, effective_from, created_by, created_at
                            ) VALUES (
                                :assignmentId, :tenantId, :taskId, :batchId,
                                'CANDIDATE', 'USER', :candidateId, 'ACTIVE',
                                :sourceType, :sourceId, :assignedAt, :actorId, :assignedAt
                            )
                            """)
                    .param("assignmentId", UUID.randomUUID()).param("tenantId", context.tenantId())
                    .param("taskId", command.taskId()).param("batchId", batchId)
                    .param("candidateId", candidateId).param("sourceType", command.sourceType().name())
                    .param("sourceId", command.sourceId()).param("assignedAt", timestamptz(assignedAt))
                    .param("actorId", context.actorId()).update();
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
                "TASK_ASSIGN_CANDIDATES", "task.assign", "Task", command.taskId().toString(),
                "ALLOW", authorizationDecision.matchedGrantIds(), authorizationDecision.policyVersion(),
                "SUCCEEDED", null, requestDigest, context.correlationId(), assignedAt));

        TaskAssignmentBatchReceipt receipt = new TaskAssignmentBatchReceipt(
                batchId, command.taskId(), command.candidatePrincipalIds().size(), taskVersion, assignedAt);
        idempotency.complete(context, OPERATION, batchId.toString(), Sha256.digest(serialize(receipt)));
        return receipt;
    }

    private void throwAssignmentConflict(String tenantId, UUID taskId, long expectedVersion) {
        TaskState state = jdbc.sql("""
                        SELECT task_kind, status, version FROM tsk_task
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                        """)
                .param("tenantId", tenantId).param("taskId", taskId)
                .query(TaskState.class).optional()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        if (state.version() != expectedVersion) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "Task version changed");
        }
        throw new BusinessProblem(
                ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                "Candidate assignment requires a READY HUMAN task but was "
                        + state.taskKind() + "/" + state.status());
    }

    private TaskAssignmentBatchReceipt findBatch(String tenantId, UUID batchId) {
        return jdbc.sql("""
                        SELECT assignment_batch_id, task_id, candidate_count,
                               task_version, assigned_at
                          FROM tsk_task_assignment_batch
                         WHERE tenant_id = :tenantId AND assignment_batch_id = :batchId
                        """)
                .param("tenantId", tenantId).param("batchId", batchId)
                .query(TaskAssignmentBatchReceipt.class).single();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Task assignment payload cannot be serialized", exception);
        }
    }

    private record TaskState(String taskKind, String status, long version) {
    }
}
