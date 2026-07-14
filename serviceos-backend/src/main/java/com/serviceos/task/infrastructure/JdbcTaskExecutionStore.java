package com.serviceos.task.infrastructure;

import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.CancelHandlingTaskCommand;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.HandlingTaskCancellationReceipt;
import com.serviceos.task.api.ScheduleAutomatedTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskCompletedPayload;
import com.serviceos.task.api.TaskCancelledPayload;
import com.serviceos.task.api.TaskCreatedPayload;
import com.serviceos.task.api.WorkflowTaskKind;
import com.serviceos.task.application.ClaimedTask;
import com.serviceos.task.application.TaskExecutionOutcome;
import com.serviceos.task.application.TaskExecutionQueue;
import com.serviceos.task.application.TaskResolution;
import com.serviceos.task.application.TaskSchedulingStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * PostgreSQL 任务存储。行锁只覆盖认领/落结果短事务，真实执行绝不占用数据库事务。
 */
@Repository
final class JdbcTaskExecutionStore implements TaskSchedulingStore, TaskExecutionQueue {
    private final JdbcClient jdbc;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JdbcTaskExecutionStore(JdbcClient jdbc, OutboxAppender outbox, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public ScheduledTaskView schedule(ScheduleAutomatedTaskCommand command) {
        Instant now = clock.instant();
        UUID taskId = UUID.randomUUID();
        int inserted = jdbc.sql("""
                        INSERT INTO tsk_task (
                            task_id, tenant_id, task_type, task_kind, business_key,
                            payload_ref, payload_digest, priority, status, next_run_at,
                            attempt_count, max_attempts, correlation_id, version, created_at, updated_at
                        ) VALUES (
                            :taskId, :tenantId, :taskType, 'AUTOMATED', :businessKey,
                            :payloadRef, :payloadDigest, :priority, 'PENDING', :nextRunAt,
                            0, :maxAttempts, :correlationId, 1, :now, :now
                        )
                        ON CONFLICT (tenant_id, task_type, business_key) DO NOTHING
                        """)
                .param("taskId", taskId)
                .param("tenantId", command.tenantId())
                .param("taskType", command.taskType())
                .param("businessKey", command.businessKey())
                .param("payloadRef", command.payloadRef(), java.sql.Types.VARCHAR)
                .param("payloadDigest", command.payloadDigest())
                .param("priority", command.priority())
                .param("nextRunAt", timestamptz(command.nextRunAt()))
                .param("maxAttempts", command.maxAttempts())
                .param("correlationId", command.correlationId())
                .param("now", timestamptz(now))
                .update();

        StoredTask stored = findByBusinessKey(command.tenantId(), command.taskType(), command.businessKey());
        if (inserted == 0 && !stored.payloadDigest().equals(command.payloadDigest())) {
            throw new BusinessProblem(
                    ProblemCode.TASK_SCHEDULE_CONFLICT,
                    "The task business key is already bound to a different payload digest");
        }
        return stored.toView();
    }

    @Override
    public ScheduledTaskView createHandlingTask(CreateHandlingTaskCommand command) {
        Instant now = clock.instant();
        UUID taskId = UUID.randomUUID();
        int inserted = jdbc.sql("""
                        INSERT INTO tsk_task (
                            task_id, tenant_id, task_type, task_kind, business_key,
                            payload_ref, payload_digest, priority, status, next_run_at,
                            attempt_count, max_attempts, correlation_id, version, created_at, updated_at
                        ) VALUES (
                            :taskId, :tenantId, :taskType, 'HUMAN', :businessKey,
                            :payloadRef, :payloadDigest, :priority, 'READY', :readyAt,
                            0, 1, :correlationId, 1, :now, :now
                        )
                        ON CONFLICT (tenant_id, task_type, business_key) DO NOTHING
                        """)
                .param("taskId", taskId)
                .param("tenantId", command.tenantId())
                .param("taskType", command.taskType())
                .param("businessKey", command.businessKey())
                .param("payloadRef", command.payloadRef(), java.sql.Types.VARCHAR)
                .param("payloadDigest", command.payloadDigest())
                .param("priority", command.priority())
                .param("readyAt", timestamptz(command.readyAt()))
                .param("correlationId", command.correlationId())
                .param("now", timestamptz(now))
                .update();
        StoredTask stored = findByBusinessKey(command.tenantId(), command.taskType(), command.businessKey());
        if (inserted == 0 && !stored.payloadDigest().equals(command.payloadDigest())) {
            throw new BusinessProblem(
                    ProblemCode.TASK_SCHEDULE_CONFLICT,
                    "The handling task business key is already bound to a different payload digest");
        }
        if (inserted == 1 && !command.candidatePrincipalIds().isEmpty()) {
            Instant assignedAt = command.readyAt() == null ? now : command.readyAt();
            UUID batchId = UUID.randomUUID();
            jdbc.sql("""
                            INSERT INTO tsk_task_assignment_batch (
                                assignment_batch_id, tenant_id, task_id, source_type, source_id,
                                candidate_count, task_version, assigned_by, assigned_at
                            ) VALUES (
                                :batchId, :tenantId, :taskId, 'SYSTEM', 'CORRECTION_AUTO_CANDIDATE',
                                :candidateCount, 1, 'system', :assignedAt
                            )
                            """)
                    .param("batchId", batchId)
                    .param("tenantId", command.tenantId())
                    .param("taskId", stored.taskId())
                    .param("candidateCount", command.candidatePrincipalIds().size())
                    .param("assignedAt", timestamptz(assignedAt))
                    .update();
            for (String candidateId : command.candidatePrincipalIds()) {
                jdbc.sql("""
                                INSERT INTO tsk_task_assignment (
                                    task_assignment_id, tenant_id, task_id, assignment_batch_id,
                                    assignment_kind, principal_type, principal_id, status,
                                    source_type, source_id, effective_from, created_by, created_at
                                ) VALUES (
                                    :assignmentId, :tenantId, :taskId, :batchId,
                                    'CANDIDATE', 'USER', :candidateId, 'ACTIVE',
                                    'SYSTEM', 'CORRECTION_AUTO_CANDIDATE', :assignedAt, 'system', :assignedAt
                                )
                                """)
                        .param("assignmentId", UUID.randomUUID())
                        .param("tenantId", command.tenantId())
                        .param("taskId", stored.taskId())
                        .param("batchId", batchId)
                        .param("candidateId", candidateId)
                        .param("assignedAt", timestamptz(assignedAt))
                        .update();
            }
        }
        return stored.toView();
    }

    @Override
    public HandlingTaskCancellationReceipt cancelHandlingTask(CancelHandlingTaskCommand command) {
        CancellationState state = jdbc.sql("""
                        SELECT task_id, task_type, business_key, task_kind, status, version,
                               cancellation_source_event_id, cancelled_at
                          FROM tsk_task
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                         FOR UPDATE
                        """)
                .param("tenantId", command.tenantId()).param("taskId", command.taskId())
                .query(CancellationState.class).optional()
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Handling task does not exist"));
        if (!"HUMAN".equals(state.taskKind())
                || !command.taskType().equals(state.taskType())
                || !command.businessKey().equals(state.businessKey())) {
            throw new BusinessProblem(
                    ProblemCode.TASK_SCHEDULE_CONFLICT,
                    "Task identity does not match the handling task cancellation command");
        }
        // 人工已经提交的事实不可被自动恢复覆盖；异常仍可引用恢复事件独立关单。
        if ("COMPLETED".equals(state.status())) {
            return new HandlingTaskCancellationReceipt(
                    state.taskId(), state.status(), state.version(),
                    command.sourceEventId(), command.cancelledAt());
        }
        if ("CANCELLED".equals(state.status())) {
            return new HandlingTaskCancellationReceipt(
                    state.taskId(), state.status(), state.version(),
                    state.cancellationSourceEventId(), state.cancelledAt());
        }
        if (!java.util.Set.of("READY", "CLAIMED", "RUNNING", "MANUAL_INTERVENTION")
                .contains(state.status())) {
            throw new BusinessProblem(
                    ProblemCode.TASK_SCHEDULE_CONFLICT,
                    "Handling task cannot be cancelled from status " + state.status());
        }

        int updated = jdbc.sql("""
                        UPDATE tsk_task
                           SET status = 'CANCELLED', claimed_by = NULL, claimed_at = NULL,
                               started_at = NULL, claim_owner = NULL, claim_until = NULL,
                               current_attempt_id = NULL, completed_at = NULL,
                               cancelled_at = :cancelledAt,
                               cancellation_reason_code = :reasonCode,
                               cancellation_source_event_id = :sourceEventId,
                               version = version + 1, updated_at = :cancelledAt
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND version = :version AND status = :status
                        """)
                .param("cancelledAt", timestamptz(command.cancelledAt()))
                .param("reasonCode", command.reasonCode())
                .param("sourceEventId", command.sourceEventId())
                .param("tenantId", command.tenantId()).param("taskId", command.taskId())
                .param("version", state.version()).param("status", state.status()).update();
        if (updated != 1) {
            throw new BusinessProblem(
                    ProblemCode.TASK_SCHEDULE_CONFLICT,
                    "Handling task changed concurrently during cancellation");
        }
        // 取消即撤权；未来即使接管任务增加候选/责任分配，也不能留下活动访问关系。
        jdbc.sql("""
                        UPDATE tsk_task_assignment
                           SET status = 'REVOKED', effective_to = :cancelledAt,
                               revoked_by = 'SYSTEM_RECOVERY', revoke_reason_code = :reasonCode
                         WHERE tenant_id = :tenantId AND task_id = :taskId AND status = 'ACTIVE'
                        """)
                .param("cancelledAt", timestamptz(command.cancelledAt()))
                .param("reasonCode", command.reasonCode())
                .param("tenantId", command.tenantId()).param("taskId", command.taskId()).update();

        long nextVersion = state.version() + 1;
        appendTaskCancelled(command, nextVersion);
        return new HandlingTaskCancellationReceipt(
                state.taskId(), "CANCELLED", nextVersion,
                command.sourceEventId(), command.cancelledAt());
    }

    private void appendTaskCancelled(CancelHandlingTaskCommand command, long taskVersion) {
        TaskCancelledPayload event = new TaskCancelledPayload(
                command.taskId(), command.taskType(), command.businessKey(), command.reasonCode(),
                command.sourceEventId(), command.cancelledAt());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("TaskCancelled event serialization failed", exception);
        }
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "task", "task.cancelled", 1,
                "Task", command.taskId().toString(), taskVersion,
                command.tenantId(), command.correlationId(), command.sourceEventId().toString(),
                command.taskId().toString(), payload, Sha256.digest(payload), command.cancelledAt()));
    }

    @Override
    public ScheduledTaskView createWorkflowTask(CreateWorkflowTaskCommand command) {
        Instant now = clock.instant();
        UUID taskId = UUID.randomUUID();
        String status = command.taskKind() == WorkflowTaskKind.AUTOMATED
                ? "PENDING" : "READY";
        int inserted = jdbc.sql("""
                        INSERT INTO tsk_task (
                            task_id, tenant_id, task_type, task_kind, business_key,
                            payload_ref, payload_digest, priority, status, next_run_at,
                            attempt_count, max_attempts, correlation_id, version, created_at, updated_at,
                            project_id, work_order_id, workflow_instance_id, stage_instance_id,
                            workflow_node_instance_id, workflow_node_id,
                            workflow_definition_version_id, workflow_definition_digest,
                            configuration_bundle_id, configuration_bundle_digest, stage_code, form_ref
                        ) VALUES (
                            :taskId, :tenantId, :taskType, :taskKind, :businessKey,
                            :payloadRef, :payloadDigest, :priority, :status, :readyAt,
                            0, :maxAttempts, :correlationId, 1, :now, :now,
                            :projectId, :workOrderId, :workflowInstanceId, :stageInstanceId,
                            :workflowNodeInstanceId, :workflowNodeId,
                            :workflowDefinitionVersionId, :workflowDefinitionDigest,
                            :configurationBundleId, :configurationBundleDigest, :stageCode, :formRef
                        )
                        ON CONFLICT (tenant_id, task_type, business_key) DO NOTHING
                        """)
                .param("taskId", taskId)
                .param("tenantId", command.tenantId())
                .param("taskType", command.taskType())
                .param("taskKind", command.taskKind().name())
                .param("businessKey", command.workflowNodeInstanceId().toString())
                .param("payloadRef", command.payloadRef(), java.sql.Types.VARCHAR)
                .param("payloadDigest", command.payloadDigest())
                .param("priority", command.priority())
                .param("status", status)
                .param("readyAt", timestamptz(command.readyAt()))
                .param("maxAttempts", command.maxAttempts())
                .param("correlationId", command.correlationId())
                .param("now", timestamptz(now))
                .param("projectId", command.projectId())
                .param("workOrderId", command.workOrderId())
                .param("workflowInstanceId", command.workflowInstanceId())
                .param("stageInstanceId", command.stageInstanceId())
                .param("workflowNodeInstanceId", command.workflowNodeInstanceId())
                .param("workflowNodeId", command.workflowNodeId())
                .param("workflowDefinitionVersionId", command.workflowDefinitionVersionId())
                .param("workflowDefinitionDigest", command.workflowDefinitionDigest())
                .param("configurationBundleId", command.configurationBundleId())
                .param("configurationBundleDigest", command.configurationBundleDigest())
                .param("stageCode", command.stageCode())
                .param("formRef", command.formRef(), java.sql.Types.VARCHAR)
                .update();

        StoredTask stored = findByBusinessKey(
                command.tenantId(), command.taskType(), command.workflowNodeInstanceId().toString());
        if (inserted == 0 && (!stored.payloadDigest().equals(command.payloadDigest())
                || !Objects.equals(stored.formRef(), command.formRef())
                || !Objects.equals(stored.stageCode(), command.stageCode())
                || !Objects.equals(stored.configurationBundleId(), command.configurationBundleId())
                || !Objects.equals(stored.configurationBundleDigest(), command.configurationBundleDigest()))) {
            throw new BusinessProblem(
                    ProblemCode.TASK_SCHEDULE_CONFLICT,
                    "The workflow node instance is already bound to a different task payload or form reference");
        }
        if (inserted == 1) {
            appendTaskCreated(command, taskId, status, now);
        }
        return stored.toView();
    }

    private void appendTaskCreated(
            CreateWorkflowTaskCommand command, UUID taskId, String status, Instant occurredAt) {
        TaskCreatedPayload event = new TaskCreatedPayload(
                taskId, command.projectId(), command.workOrderId(), command.workflowInstanceId(),
                command.stageInstanceId(), command.workflowNodeInstanceId(), command.workflowNodeId(),
                command.taskType(), command.taskKind(), status, command.workflowDefinitionVersionId(),
                command.workflowDefinitionDigest(), occurredAt);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("TaskCreated event serialization failed", exception);
        }
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "task", "task.created", 1,
                "Task", taskId.toString(), 1, command.tenantId(), command.correlationId(),
                command.causationId(), taskId.toString(), payload, Sha256.digest(payload), occurredAt));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverOneExhaustedLease(String recoveryWorkerId) {
        Instant now = clock.instant();
        Optional<TaskCandidate> selected = jdbc.sql("""
                        SELECT task_id, tenant_id, task_type, business_key, payload_ref, payload_digest,
                               correlation_id, status, current_attempt_id, attempt_count, max_attempts, version
                          FROM tsk_task
                         WHERE task_kind = 'AUTOMATED'
                           AND status = 'CLAIMED'
                           AND claim_until < :now
                           AND attempt_count >= max_attempts
                         ORDER BY claim_until, task_id
                         FOR UPDATE SKIP LOCKED
                         LIMIT 1
                        """)
                .param("now", timestamptz(now))
                .query(this::mapCandidate)
                .optional();
        if (selected.isEmpty()) {
            return false;
        }

        TaskCandidate candidate = selected.orElseThrow();
        expirePreviousAttempt(candidate.currentAttemptId(), now);
        int updated = jdbc.sql("""
                        UPDATE tsk_task
                           SET status = 'MANUAL_INTERVENTION', claim_owner = NULL, claim_until = NULL,
                               current_attempt_id = NULL, last_error_code = 'TASK_MAX_ATTEMPTS_EXHAUSTED',
                               version = version + 1, updated_at = :now
                         WHERE task_id = :taskId AND status = 'CLAIMED'
                        """)
                .params(Map.of("now", timestamptz(now), "taskId", candidate.taskId()))
                .update();
        requireLease(updated);
        ClaimedTask exhausted = new ClaimedTask(
                candidate.taskId(), candidate.currentAttemptId(), candidate.tenantId(), candidate.taskType(),
                candidate.businessKey(), candidate.payloadRef(), candidate.payloadDigest(),
                candidate.correlationId(), candidate.attemptCount(), candidate.maxAttempts(), candidate.version());
        appendEvent(
                exhausted, "task.execution.manual-intervention-required", "MANUAL_INTERVENTION",
                "TASK_MAX_ATTEMPTS_EXHAUSTED", null, now);
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedTask> claimNext(String workerId, Duration leaseDuration) {
        Instant now = clock.instant();
        Optional<TaskCandidate> selected = jdbc.sql("""
                        SELECT task_id, tenant_id, task_type, business_key, payload_ref, payload_digest,
                               correlation_id, status, current_attempt_id, attempt_count, max_attempts, version
                          FROM tsk_task
                         WHERE task_kind = 'AUTOMATED'
                           AND attempt_count < max_attempts
                           AND (
                                (status IN ('PENDING', 'RETRY_WAIT') AND next_run_at <= :now)
                                OR (status = 'CLAIMED' AND claim_until < :now)
                           )
                         ORDER BY priority DESC, next_run_at, created_at, task_id
                         FOR UPDATE SKIP LOCKED
                         LIMIT 1
                        """)
                .param("now", timestamptz(now))
                .query(this::mapCandidate)
                .optional();
        if (selected.isEmpty()) {
            return Optional.empty();
        }

        TaskCandidate candidate = selected.orElseThrow();
        if ("CLAIMED".equals(candidate.status())) {
            expirePreviousAttempt(candidate.currentAttemptId(), now);
        }

        UUID attemptId = UUID.randomUUID();
        int attemptNo = candidate.attemptCount() + 1;
        jdbc.sql("""
                        UPDATE tsk_task
                           SET status = 'CLAIMED', claim_owner = :workerId, claim_until = :claimUntil,
                               current_attempt_id = :attemptId, attempt_count = :attemptNo,
                               version = version + 1, updated_at = :now
                         WHERE task_id = :taskId
                        """)
                .params(Map.of(
                        "workerId", workerId,
                        "claimUntil", timestamptz(now.plus(leaseDuration)),
                        "attemptId", attemptId,
                        "attemptNo", attemptNo,
                        "now", timestamptz(now),
                        "taskId", candidate.taskId()))
                .update();
        jdbc.sql("""
                        INSERT INTO tsk_task_execution_attempt (
                            attempt_id, task_id, attempt_no, worker_id, started_at, result_code
                        ) VALUES (
                            :attemptId, :taskId, :attemptNo, :workerId, :now, 'RUNNING'
                        )
                        """)
                .params(Map.of(
                        "attemptId", attemptId,
                        "taskId", candidate.taskId(),
                        "attemptNo", attemptNo,
                        "workerId", workerId,
                        "now", timestamptz(now)))
                .update();

        return Optional.of(new ClaimedTask(
                candidate.taskId(), attemptId, candidate.tenantId(), candidate.taskType(),
                candidate.businessKey(), candidate.payloadRef(), candidate.payloadDigest(),
                candidate.correlationId(), attemptNo, candidate.maxAttempts(), candidate.version() + 1));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskResolution resolve(ClaimedTask task, String workerId, TaskExecutionOutcome outcome) {
        return switch (outcome.kind()) {
            case SUCCESS -> succeed(task, workerId, outcome);
            case RETRYABLE_FAILURE -> retryOrEscalate(task, workerId, outcome);
            case FINAL_FAILURE, UNKNOWN -> manualIntervention(task, workerId, outcome);
        };
    }

    private TaskResolution succeed(ClaimedTask task, String workerId, TaskExecutionOutcome outcome) {
        Instant now = clock.instant();
        requireLease(updateTaskTerminal(task, workerId, "SUCCEEDED", null, now, now));
        finishAttempt(task, "SUCCEEDED", null, outcome.resultRef(), null, now);
        appendEvent(task, "task.execution.succeeded", "SUCCEEDED", null, outcome.resultRef(), now);
        appendWorkflowTaskCompleted(task, outcome.resultRef(), now);
        return new TaskResolution(TaskResolution.Status.SUCCEEDED);
    }

    private TaskResolution retryOrEscalate(ClaimedTask task, String workerId, TaskExecutionOutcome outcome) {
        if (task.attemptNo() >= task.maxAttempts()) {
            TaskExecutionOutcome exhausted = TaskExecutionOutcome.finalFailure("TASK_MAX_ATTEMPTS_EXHAUSTED");
            return manualIntervention(task, workerId, exhausted);
        }
        Instant now = clock.instant();
        int updated = jdbc.sql("""
                        UPDATE tsk_task
                           SET status = 'RETRY_WAIT', next_run_at = :retryAt,
                               claim_owner = NULL, claim_until = NULL, current_attempt_id = NULL,
                               last_error_code = :errorCode, version = version + 1, updated_at = :now
                         WHERE task_id = :taskId AND status = 'CLAIMED'
                           AND claim_owner = :workerId AND current_attempt_id = :attemptId
                        """)
                .params(Map.of(
                        "retryAt", timestamptz(outcome.retryAt()),
                        "errorCode", truncate(outcome.errorCode(), 100),
                        "now", timestamptz(now),
                        "taskId", task.taskId(),
                        "workerId", workerId,
                        "attemptId", task.attemptId()))
                .update();
        requireLease(updated);
        finishAttempt(task, "RETRYABLE_FAILURE", outcome.errorCode(), null, outcome.retryAt(), now);
        appendEvent(task, "task.execution.retry-scheduled", "RETRY_WAIT", outcome.errorCode(), null, now);
        return new TaskResolution(TaskResolution.Status.RETRY_SCHEDULED);
    }

    private TaskResolution manualIntervention(
            ClaimedTask task,
            String workerId,
            TaskExecutionOutcome outcome
    ) {
        Instant now = clock.instant();
        String errorCode = truncate(outcome.errorCode(), 100);
        requireLease(updateTaskTerminal(task, workerId, "MANUAL_INTERVENTION", errorCode, now, null));
        String attemptResult = outcome.kind() == TaskExecutionOutcome.Kind.UNKNOWN ? "UNKNOWN" : "FINAL_FAILURE";
        finishAttempt(task, attemptResult, errorCode, null, null, now);
        appendEvent(
                task, "task.execution.manual-intervention-required",
                "MANUAL_INTERVENTION", errorCode, null, now);
        return new TaskResolution(TaskResolution.Status.MANUAL_INTERVENTION);
    }

    private int updateTaskTerminal(
            ClaimedTask task,
            String workerId,
            String status,
            String errorCode,
            Instant now,
            Instant completedAt
    ) {
        return jdbc.sql("""
                        UPDATE tsk_task
                           SET status = :status, claim_owner = NULL, claim_until = NULL,
                               current_attempt_id = NULL, last_error_code = :errorCode,
                               completed_at = :completedAt, version = version + 1, updated_at = :now
                         WHERE task_id = :taskId AND status = 'CLAIMED'
                           AND claim_owner = :workerId AND current_attempt_id = :attemptId
                        """)
                .param("status", status)
                .param("errorCode", errorCode, java.sql.Types.VARCHAR)
                .param("completedAt", timestamptz(completedAt))
                .param("now", timestamptz(now))
                .param("taskId", task.taskId())
                .param("workerId", workerId)
                .param("attemptId", task.attemptId())
                .update();
    }

    private void finishAttempt(
            ClaimedTask task,
            String resultCode,
            String errorCode,
            String resultRef,
            Instant nextRetryAt,
            Instant finishedAt
    ) {
        int updated = jdbc.sql("""
                        UPDATE tsk_task_execution_attempt
                           SET finished_at = :finishedAt, result_code = :resultCode,
                               error_code = :errorCode, result_ref = :resultRef,
                               next_retry_at = :nextRetryAt
                         WHERE attempt_id = :attemptId AND result_code = 'RUNNING'
                        """)
                .param("finishedAt", timestamptz(finishedAt))
                .param("resultCode", resultCode)
                .param("errorCode", errorCode, java.sql.Types.VARCHAR)
                .param("resultRef", resultRef, java.sql.Types.VARCHAR)
                .param("nextRetryAt", timestamptz(nextRetryAt))
                .param("attemptId", task.attemptId())
                .update();
        requireLease(updated);
    }

    private void expirePreviousAttempt(UUID attemptId, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE tsk_task_execution_attempt
                           SET finished_at = :now, result_code = 'LEASE_EXPIRED',
                               error_code = 'TASK_LEASE_EXPIRED'
                         WHERE attempt_id = :attemptId AND result_code = 'RUNNING'
                        """)
                .params(Map.of("now", timestamptz(now), "attemptId", attemptId))
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Expired task claim has no running attempt");
        }
    }

    private void appendEvent(
            ClaimedTask task,
            String eventType,
            String status,
            String errorCode,
            String resultRef,
            Instant occurredAt
    ) {
        TaskExecutionEventPayload event = new TaskExecutionEventPayload(
                task.taskId(), task.attemptId(), task.taskType(), task.businessKey(),
                task.attemptNo(), status, errorCode, resultRef);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Task event serialization failed", exception);
        }
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "task", eventType, 1,
                "Task", task.taskId().toString(), task.taskVersion() + 1,
                task.tenantId(), task.correlationId(), task.attemptId().toString(),
                task.taskId().toString(), payload, Sha256.digest(payload), occurredAt));
    }

    /**
     * 技术执行成功只描述 worker 结果；工作流推进必须依赖稳定的 TaskCompleted 领域事实。
     * 非工作流后台任务没有流程上下文，因此不会伪造该事件。
     */
    private void appendWorkflowTaskCompleted(ClaimedTask task, String resultRef, Instant completedAt) {
        WorkflowTaskContext context = jdbc.sql("""
                        SELECT project_id, work_order_id, workflow_instance_id, stage_instance_id,
                               workflow_node_instance_id, workflow_node_id,
                               workflow_definition_version_id, workflow_definition_digest
                          FROM tsk_task
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                        """)
                .param("tenantId", task.tenantId())
                .param("taskId", task.taskId())
                .query(WorkflowTaskContext.class)
                .single();
        if (context.workflowNodeInstanceId() == null) {
            return;
        }

        String resultDigest = Sha256.digest(resultRef == null ? "" : resultRef);
        TaskCompletedPayload event = new TaskCompletedPayload(
                task.taskId(), context.projectId(), context.workOrderId(), context.workflowInstanceId(),
                context.stageInstanceId(), context.workflowNodeInstanceId(), context.workflowNodeId(),
                task.taskType(), context.workflowDefinitionVersionId(),
                context.workflowDefinitionDigest(), resultRef, resultDigest, completedAt);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("TaskCompleted event serialization failed", exception);
        }
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "task", "task.completed", 1,
                "Task", task.taskId().toString(), task.taskVersion() + 1,
                task.tenantId(), task.correlationId(), task.attemptId().toString(),
                task.taskId().toString(), payload, Sha256.digest(payload), completedAt));
    }

    private StoredTask findByBusinessKey(String tenantId, String taskType, String businessKey) {
        return jdbc.sql("""
                        SELECT task_id, tenant_id, task_type, business_key, payload_digest, form_ref,
                               configuration_bundle_id, configuration_bundle_digest, stage_code,
                               status, next_run_at, attempt_count, max_attempts, version
                          FROM tsk_task
                         WHERE tenant_id = :tenantId AND task_type = :taskType AND business_key = :businessKey
                        """)
                .params(Map.of("tenantId", tenantId, "taskType", taskType, "businessKey", businessKey))
                .query(StoredTask.class)
                .single();
    }

    private TaskCandidate mapCandidate(ResultSet rs, int rowNumber) throws SQLException {
        return new TaskCandidate(
                rs.getObject("task_id", UUID.class), rs.getString("tenant_id"),
                rs.getString("task_type"), rs.getString("business_key"),
                rs.getString("payload_ref"), rs.getString("payload_digest"),
                rs.getString("correlation_id"), rs.getString("status"),
                rs.getObject("current_attempt_id", UUID.class), rs.getInt("attempt_count"),
                rs.getInt("max_attempts"), rs.getLong("version"));
    }

    private static void requireLease(int updated) {
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.TASK_LEASE_LOST, "The task lease is no longer owned");
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record TaskCandidate(
            UUID taskId, String tenantId, String taskType, String businessKey,
            String payloadRef, String payloadDigest, String correlationId,
            String status, UUID currentAttemptId, int attemptCount, int maxAttempts, long version
    ) {
    }

    private record StoredTask(
            UUID taskId, String tenantId, String taskType, String businessKey, String payloadDigest, String formRef,
            UUID configurationBundleId, String configurationBundleDigest, String stageCode,
            String status, Instant nextRunAt, int attemptCount, int maxAttempts, long version
    ) {
        ScheduledTaskView toView() {
            return new ScheduledTaskView(
                    taskId, tenantId, taskType, businessKey, status,
                    nextRunAt, attemptCount, maxAttempts, version);
        }
    }

    private record CancellationState(
            UUID taskId,
            String taskType,
            String businessKey,
            String taskKind,
            String status,
            long version,
            UUID cancellationSourceEventId,
            Instant cancelledAt
    ) {
    }

    private record TaskExecutionEventPayload(
            UUID taskId,
            UUID attemptId,
            String taskType,
            String businessKey,
            int attemptNo,
            String status,
            String errorCode,
            String resultRef
    ) {
    }

    private record WorkflowTaskContext(
            UUID projectId,
            UUID workOrderId,
            UUID workflowInstanceId,
            UUID stageInstanceId,
            UUID workflowNodeInstanceId,
            String workflowNodeId,
            UUID workflowDefinitionVersionId,
            String workflowDefinitionDigest
    ) {
    }
}
