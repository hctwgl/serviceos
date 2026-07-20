package com.serviceos.task.infrastructure;

import com.serviceos.jooq.generated.tables.TskTask;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.CancelHandlingTaskCommand;
import com.serviceos.task.api.CancelOpenWorkflowTasksCommand;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.HandlingTaskCancellationReceipt;
import com.serviceos.task.api.CompleteHandlingTaskCommand;
import com.serviceos.task.api.HandlingTaskCompletedPayload;
import com.serviceos.task.api.HandlingTaskCompletionReceipt;
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
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;
import static com.serviceos.jooq.generated.tables.TskTaskAssignment.TSK_TASK_ASSIGNMENT;
import static com.serviceos.jooq.generated.tables.TskTaskAssignmentBatch.TSK_TASK_ASSIGNMENT_BATCH;
import static com.serviceos.jooq.generated.tables.TskTaskExecutionAttempt.TSK_TASK_EXECUTION_ATTEMPT;

/**
 * PostgreSQL 任务存储（jOOQ）。行锁只覆盖认领/落结果短事务，真实执行绝不占用数据库事务。
 */
@Repository
final class JooqTaskExecutionStore implements TaskSchedulingStore, TaskExecutionQueue {
    private final DSLContext dsl;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqTaskExecutionStore(DSLContext dsl, OutboxAppender outbox, ObjectMapper objectMapper, Clock clock) {
        this.dsl = dsl;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public ScheduledTaskView schedule(ScheduleAutomatedTaskCommand command) {
        Instant now = clock.instant();
        UUID taskId = UUID.randomUUID();
        TskTask task = TSK_TASK;
        int inserted = dsl.insertInto(task)
                .set(task.TASK_ID, taskId)
                .set(task.TENANT_ID, command.tenantId())
                .set(task.TASK_TYPE, command.taskType())
                .set(task.TASK_KIND, "AUTOMATED")
                .set(task.BUSINESS_KEY, command.businessKey())
                .set(task.PAYLOAD_REF, command.payloadRef())
                .set(task.PAYLOAD_DIGEST, command.payloadDigest())
                .set(task.PRIORITY, command.priority())
                .set(task.STATUS, "PENDING")
                .set(task.NEXT_RUN_AT, command.nextRunAt())
                .set(task.ATTEMPT_COUNT, 0)
                .set(task.MAX_ATTEMPTS, command.maxAttempts())
                .set(task.CORRELATION_ID, command.correlationId())
                .set(task.VERSION, 1L)
                .set(task.CREATED_AT, now)
                .set(task.UPDATED_AT, now)
                .onConflict(task.TENANT_ID, task.TASK_TYPE, task.BUSINESS_KEY)
                .doNothing()
                .execute();

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
        TskTask task = TSK_TASK;
        int inserted = dsl.insertInto(task)
                .set(task.TASK_ID, taskId)
                .set(task.TENANT_ID, command.tenantId())
                .set(task.TASK_TYPE, command.taskType())
                .set(task.TASK_KIND, "HUMAN")
                .set(task.BUSINESS_KEY, command.businessKey())
                .set(task.PAYLOAD_REF, command.payloadRef())
                .set(task.PAYLOAD_DIGEST, command.payloadDigest())
                .set(task.PRIORITY, command.priority())
                .set(task.STATUS, "READY")
                .set(task.NEXT_RUN_AT, command.readyAt())
                .set(task.ATTEMPT_COUNT, 0)
                .set(task.MAX_ATTEMPTS, 1)
                .set(task.CORRELATION_ID, command.correlationId())
                .set(task.VERSION, 1L)
                .set(task.CREATED_AT, now)
                .set(task.UPDATED_AT, now)
                .onConflict(task.TENANT_ID, task.TASK_TYPE, task.BUSINESS_KEY)
                .doNothing()
                .execute();
        StoredTask stored = findByBusinessKey(command.tenantId(), command.taskType(), command.businessKey());
        if (inserted == 0 && !stored.payloadDigest().equals(command.payloadDigest())) {
            throw new BusinessProblem(
                    ProblemCode.TASK_SCHEDULE_CONFLICT,
                    "The handling task business key is already bound to a different payload digest");
        }
        if (inserted == 1 && !command.candidatePrincipalIds().isEmpty()) {
            Instant assignedAt = command.readyAt() == null ? now : command.readyAt();
            UUID batchId = UUID.randomUUID();
            dsl.insertInto(TSK_TASK_ASSIGNMENT_BATCH)
                    .set(TSK_TASK_ASSIGNMENT_BATCH.ASSIGNMENT_BATCH_ID, batchId)
                    .set(TSK_TASK_ASSIGNMENT_BATCH.TENANT_ID, command.tenantId())
                    .set(TSK_TASK_ASSIGNMENT_BATCH.TASK_ID, stored.taskId())
                    .set(TSK_TASK_ASSIGNMENT_BATCH.SOURCE_TYPE, "SYSTEM")
                    .set(TSK_TASK_ASSIGNMENT_BATCH.SOURCE_ID, "CORRECTION_AUTO_CANDIDATE")
                    .set(TSK_TASK_ASSIGNMENT_BATCH.CANDIDATE_COUNT, command.candidatePrincipalIds().size())
                    .set(TSK_TASK_ASSIGNMENT_BATCH.TASK_VERSION, 1L)
                    .set(TSK_TASK_ASSIGNMENT_BATCH.ASSIGNED_BY, "system")
                    .set(TSK_TASK_ASSIGNMENT_BATCH.ASSIGNED_AT, assignedAt)
                    .execute();
            for (String candidateId : command.candidatePrincipalIds()) {
                dsl.insertInto(TSK_TASK_ASSIGNMENT)
                        .set(TSK_TASK_ASSIGNMENT.TASK_ASSIGNMENT_ID, UUID.randomUUID())
                        .set(TSK_TASK_ASSIGNMENT.TENANT_ID, command.tenantId())
                        .set(TSK_TASK_ASSIGNMENT.TASK_ID, stored.taskId())
                        .set(TSK_TASK_ASSIGNMENT.ASSIGNMENT_BATCH_ID, batchId)
                        .set(TSK_TASK_ASSIGNMENT.ASSIGNMENT_KIND, "CANDIDATE")
                        .set(TSK_TASK_ASSIGNMENT.PRINCIPAL_TYPE, "USER")
                        .set(TSK_TASK_ASSIGNMENT.PRINCIPAL_ID, candidateId)
                        .set(TSK_TASK_ASSIGNMENT.STATUS, "ACTIVE")
                        .set(TSK_TASK_ASSIGNMENT.SOURCE_TYPE, "SYSTEM")
                        .set(TSK_TASK_ASSIGNMENT.SOURCE_ID, "CORRECTION_AUTO_CANDIDATE")
                        .set(TSK_TASK_ASSIGNMENT.EFFECTIVE_FROM, assignedAt)
                        .set(TSK_TASK_ASSIGNMENT.CREATED_BY, "system")
                        .set(TSK_TASK_ASSIGNMENT.CREATED_AT, assignedAt)
                        .execute();
            }
        }
        return stored.toView();
    }

    @Override
    public HandlingTaskCancellationReceipt cancelHandlingTask(CancelHandlingTaskCommand command) {
        TskTask task = TSK_TASK;
        CancellationState state = dsl
                .select(task.TASK_ID, task.TASK_TYPE, task.BUSINESS_KEY, task.TASK_KIND,
                        task.STATUS, task.VERSION, task.CANCELLATION_SOURCE_EVENT_ID, task.CANCELLED_AT)
                .from(task)
                .where(task.TENANT_ID.eq(command.tenantId()))
                .and(task.TASK_ID.eq(command.taskId()))
                .forUpdate()
                .fetchOptional(JooqTaskExecutionStore::mapCancellationState)
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

        int updated = dsl.update(task)
                .set(task.STATUS, "CANCELLED")
                .setNull(task.CLAIMED_BY)
                .setNull(task.CLAIMED_AT)
                .setNull(task.STARTED_AT)
                .setNull(task.CLAIM_OWNER)
                .setNull(task.CLAIM_UNTIL)
                .setNull(task.CURRENT_ATTEMPT_ID)
                .setNull(task.COMPLETED_AT)
                .set(task.CANCELLED_AT, command.cancelledAt())
                .set(task.CANCELLATION_REASON_CODE, command.reasonCode())
                .set(task.CANCELLATION_SOURCE_EVENT_ID, command.sourceEventId())
                .set(task.VERSION, task.VERSION.plus(1))
                .set(task.UPDATED_AT, command.cancelledAt())
                .where(task.TENANT_ID.eq(command.tenantId()))
                .and(task.TASK_ID.eq(command.taskId()))
                .and(task.VERSION.eq(state.version()))
                .and(task.STATUS.eq(state.status()))
                .execute();
        if (updated != 1) {
            throw new BusinessProblem(
                    ProblemCode.TASK_SCHEDULE_CONFLICT,
                    "Handling task changed concurrently during cancellation");
        }
        // 取消即撤权；未来即使接管任务增加候选/责任分配，也不能留下活动访问关系。
        dsl.update(TSK_TASK_ASSIGNMENT)
                .set(TSK_TASK_ASSIGNMENT.STATUS, "REVOKED")
                .set(TSK_TASK_ASSIGNMENT.EFFECTIVE_TO, command.cancelledAt())
                .set(TSK_TASK_ASSIGNMENT.REVOKED_BY, "SYSTEM_RECOVERY")
                .set(TSK_TASK_ASSIGNMENT.REVOKE_REASON_CODE, command.reasonCode())
                .where(TSK_TASK_ASSIGNMENT.TENANT_ID.eq(command.tenantId()))
                .and(TSK_TASK_ASSIGNMENT.TASK_ID.eq(command.taskId()))
                .and(TSK_TASK_ASSIGNMENT.STATUS.eq("ACTIVE"))
                .execute();

        long nextVersion = state.version() + 1;
        appendTaskCancelled(command, nextVersion);
        return new HandlingTaskCancellationReceipt(
                state.taskId(), "CANCELLED", nextVersion,
                command.sourceEventId(), command.cancelledAt());
    }

    @Override
    public HandlingTaskCompletionReceipt completeHandlingTask(CompleteHandlingTaskCommand command) {
        TskTask task = TSK_TASK;
        CompletionState state = dsl
                .select(task.TASK_ID, task.TASK_TYPE, task.BUSINESS_KEY, task.TASK_KIND,
                        task.STATUS, task.VERSION, task.RESULT_REF, task.RESULT_DIGEST, task.COMPLETED_AT)
                .from(task)
                .where(task.TENANT_ID.eq(command.tenantId()))
                .and(task.TASK_ID.eq(command.taskId()))
                .forUpdate()
                .fetchOptional(JooqTaskExecutionStore::mapCompletionState)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Handling task does not exist"));
        if (!"HUMAN".equals(state.taskKind())
                || !command.taskType().equals(state.taskType())
                || !command.businessKey().equals(state.businessKey())) {
            throw new BusinessProblem(ProblemCode.TASK_SCHEDULE_CONFLICT,
                    "Task identity does not match the handling task completion command");
        }
        if ("COMPLETED".equals(state.status())) {
            if (!command.resultRef().equals(state.resultRef())
                    || !command.resultDigest().equals(state.resultDigest())) {
                throw new BusinessProblem(ProblemCode.TASK_SCHEDULE_CONFLICT,
                        "Completed handling task is bound to another result");
            }
            return new HandlingTaskCompletionReceipt(
                    state.taskId(), state.status(), state.version(), state.resultRef(), state.completedAt());
        }
        if (!java.util.Set.of("READY", "CLAIMED", "RUNNING", "MANUAL_INTERVENTION")
                .contains(state.status())) {
            throw new BusinessProblem(ProblemCode.TASK_SCHEDULE_CONFLICT,
                    "Handling task cannot be completed from status " + state.status());
        }
        // HUMAN 完成必须满足 ck_tsk_human_lifecycle（claimed_by/claimed_at/started_at 非空）。
        // 整改 handling Task 可能仍停留在 READY；系统关闭 Case 时补齐最小生命周期字段。
        int updated = dsl.update(task)
                .set(task.STATUS, "COMPLETED")
                .set(task.RESULT_REF, command.resultRef())
                .set(task.RESULT_DIGEST, command.resultDigest())
                .set(task.COMPLETED_AT, command.completedAt())
                .set(task.CLAIMED_BY, DSL.coalesce(task.CLAIMED_BY, command.completedBy()))
                .set(task.CLAIMED_AT, DSL.coalesce(task.CLAIMED_AT, command.completedAt()))
                .set(task.STARTED_AT, DSL.coalesce(task.STARTED_AT, command.completedAt()))
                .setNull(task.CLAIM_OWNER)
                .setNull(task.CLAIM_UNTIL)
                .setNull(task.CURRENT_ATTEMPT_ID)
                .set(task.VERSION, task.VERSION.plus(1))
                .set(task.UPDATED_AT, command.completedAt())
                .where(task.TENANT_ID.eq(command.tenantId()))
                .and(task.TASK_ID.eq(command.taskId()))
                .and(task.VERSION.eq(state.version()))
                .and(task.STATUS.eq(state.status()))
                .execute();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.TASK_SCHEDULE_CONFLICT,
                    "Handling task changed concurrently during completion");
        }
        // 权威业务终态完成后撤销所有活动分配，避免已结束整改仍出现在候选/责任队列。
        dsl.update(TSK_TASK_ASSIGNMENT)
                .set(TSK_TASK_ASSIGNMENT.STATUS, "EXPIRED")
                .set(TSK_TASK_ASSIGNMENT.EFFECTIVE_TO, command.completedAt())
                .set(TSK_TASK_ASSIGNMENT.REVOKED_BY, command.completedBy())
                .set(TSK_TASK_ASSIGNMENT.REVOKE_REASON_CODE, "HANDLING_COMPLETED")
                .where(TSK_TASK_ASSIGNMENT.TENANT_ID.eq(command.tenantId()))
                .and(TSK_TASK_ASSIGNMENT.TASK_ID.eq(command.taskId()))
                .and(TSK_TASK_ASSIGNMENT.STATUS.eq("ACTIVE"))
                .execute();

        long nextVersion = state.version() + 1;
        appendHandlingTaskCompleted(command, nextVersion);
        return new HandlingTaskCompletionReceipt(
                state.taskId(), "COMPLETED", nextVersion, command.resultRef(), command.completedAt());
    }

    private void appendHandlingTaskCompleted(CompleteHandlingTaskCommand command, long taskVersion) {
        HandlingTaskCompletedPayload payload = new HandlingTaskCompletedPayload(
                command.taskId(), command.taskType(), command.businessKey(), command.resultRef(),
                command.resultDigest(), command.completedBy(), command.completedAt());
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Handling task completion payload serialization failed", exception);
        }
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "task", "task.handling-completed", 1,
                "Task", command.taskId().toString(), taskVersion, command.tenantId(),
                command.correlationId(), command.correlationId(), command.taskId().toString(),
                json, Sha256.digest(json), command.completedAt()));
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
        TskTask task = TSK_TASK;
        int inserted = dsl.insertInto(task)
                .set(task.TASK_ID, taskId)
                .set(task.TENANT_ID, command.tenantId())
                .set(task.TASK_TYPE, command.taskType())
                .set(task.TASK_KIND, command.taskKind().name())
                .set(task.BUSINESS_KEY, command.workflowNodeInstanceId().toString())
                .set(task.PAYLOAD_REF, command.payloadRef())
                .set(task.PAYLOAD_DIGEST, command.payloadDigest())
                .set(task.PRIORITY, command.priority())
                .set(task.STATUS, status)
                .set(task.NEXT_RUN_AT, command.readyAt())
                .set(task.ATTEMPT_COUNT, 0)
                .set(task.MAX_ATTEMPTS, command.maxAttempts())
                .set(task.CORRELATION_ID, command.correlationId())
                .set(task.VERSION, 1L)
                .set(task.CREATED_AT, now)
                .set(task.UPDATED_AT, now)
                .set(task.PROJECT_ID, command.projectId())
                .set(task.WORK_ORDER_ID, command.workOrderId())
                .set(task.WORKFLOW_INSTANCE_ID, command.workflowInstanceId())
                .set(task.STAGE_INSTANCE_ID, command.stageInstanceId())
                .set(task.WORKFLOW_NODE_INSTANCE_ID, command.workflowNodeInstanceId())
                .set(task.WORKFLOW_NODE_ID, command.workflowNodeId())
                .set(task.WORKFLOW_DEFINITION_VERSION_ID, command.workflowDefinitionVersionId())
                .set(task.WORKFLOW_DEFINITION_DIGEST, command.workflowDefinitionDigest())
                .set(task.CONFIGURATION_BUNDLE_ID, command.configurationBundleId())
                .set(task.CONFIGURATION_BUNDLE_DIGEST, command.configurationBundleDigest())
                .set(task.STAGE_CODE, command.stageCode())
                .set(task.FORM_REF, command.formRef())
                .set(task.SLA_REF, command.slaRef())
                .set(task.ASSIGNEE_POLICY_REF, command.assigneePolicyRef())
                .set(task.DISPATCH_POLICY_REF, command.dispatchPolicyRef())
                .set(task.RULE_REF, command.ruleRef())
                .onConflict(task.TENANT_ID, task.TASK_TYPE, task.BUSINESS_KEY)
                .doNothing()
                .execute();

        StoredTask stored = findByBusinessKey(
                command.tenantId(), command.taskType(), command.workflowNodeInstanceId().toString());
        if (inserted == 0 && (!stored.payloadDigest().equals(command.payloadDigest())
                || !Objects.equals(stored.formRef(), command.formRef())
                || !Objects.equals(stored.slaRef(), command.slaRef())
                || !Objects.equals(stored.assigneePolicyRef(), command.assigneePolicyRef())
                || !Objects.equals(stored.dispatchPolicyRef(), command.dispatchPolicyRef())
                || !Objects.equals(stored.ruleRef(), command.ruleRef())
                || !Objects.equals(stored.stageCode(), command.stageCode())
                || !Objects.equals(stored.configurationBundleId(), command.configurationBundleId())
                || !Objects.equals(stored.configurationBundleDigest(), command.configurationBundleDigest()))) {
            throw new BusinessProblem(
                    ProblemCode.TASK_SCHEDULE_CONFLICT,
                    "The workflow node instance is already bound to different payload or frozen references");
        }
        if (inserted == 1) {
            appendTaskCreated(command, taskId, status, now);
        }
        return stored.toView();
    }

    @Override
    public int cancelOpenTasksForWorkflows(CancelOpenWorkflowTasksCommand command) {
        // 工单取消/跳转级联：批量关闭仍开放任务并撤消分配；已完成任务保持不变。
        TskTask task = TSK_TASK;
        int updated = dsl.update(task)
                .set(task.STATUS, "CANCELLED")
                .setNull(task.CLAIMED_BY)
                .setNull(task.CLAIMED_AT)
                .setNull(task.STARTED_AT)
                .setNull(task.CLAIM_OWNER)
                .setNull(task.CLAIM_UNTIL)
                .setNull(task.CURRENT_ATTEMPT_ID)
                .set(task.CANCELLED_AT, command.cancelledAt())
                .set(task.CANCELLATION_REASON_CODE, command.reasonCode())
                .set(task.CANCELLATION_SOURCE_EVENT_ID, command.sourceEventId())
                .set(task.VERSION, task.VERSION.plus(1))
                .set(task.UPDATED_AT, command.cancelledAt())
                .where(task.TENANT_ID.eq(command.tenantId()))
                .and(task.WORKFLOW_INSTANCE_ID.in(command.workflowInstanceIds()))
                .and(task.STATUS.in("PENDING", "READY", "CLAIMED", "RUNNING",
                        "RETRY_WAIT", "MANUAL_INTERVENTION"))
                .execute();
        if (updated > 0) {
            dsl.update(TSK_TASK_ASSIGNMENT)
                    .set(TSK_TASK_ASSIGNMENT.STATUS, "REVOKED")
                    .set(TSK_TASK_ASSIGNMENT.EFFECTIVE_TO, command.cancelledAt())
                    .set(TSK_TASK_ASSIGNMENT.REVOKED_BY, "SYSTEM_WORKFLOW_CASCADE")
                    .set(TSK_TASK_ASSIGNMENT.REVOKE_REASON_CODE, command.reasonCode())
                    .where(TSK_TASK_ASSIGNMENT.TENANT_ID.eq(command.tenantId()))
                    .and(TSK_TASK_ASSIGNMENT.STATUS.eq("ACTIVE"))
                    .and(TSK_TASK_ASSIGNMENT.TASK_ID.in(
                            dsl.select(task.TASK_ID)
                                    .from(task)
                                    .where(task.TENANT_ID.eq(command.tenantId()))
                                    .and(task.WORKFLOW_INSTANCE_ID.in(command.workflowInstanceIds()))
                                    .and(task.STATUS.eq("CANCELLED"))
                                    .and(task.CANCELLATION_SOURCE_EVENT_ID.eq(command.sourceEventId()))))
                    .execute();
        }
        return updated;
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
        TskTask task = TSK_TASK;
        Optional<TaskCandidate> selected = dsl.select(candidateColumns())
                .from(task)
                .where(task.TASK_KIND.eq("AUTOMATED"))
                .and(task.STATUS.eq("CLAIMED"))
                .and(task.CLAIM_UNTIL.lt(now))
                .and(task.ATTEMPT_COUNT.ge(task.MAX_ATTEMPTS))
                .orderBy(task.CLAIM_UNTIL, task.TASK_ID)
                .limit(1)
                .forUpdate()
                .skipLocked()
                .fetchOptional(this::mapCandidate);
        if (selected.isEmpty()) {
            return false;
        }

        TaskCandidate candidate = selected.orElseThrow();
        expirePreviousAttempt(candidate.currentAttemptId(), now);
        int updated = dsl.update(task)
                .set(task.STATUS, "MANUAL_INTERVENTION")
                .setNull(task.CLAIM_OWNER)
                .setNull(task.CLAIM_UNTIL)
                .setNull(task.CURRENT_ATTEMPT_ID)
                .set(task.LAST_ERROR_CODE, "TASK_MAX_ATTEMPTS_EXHAUSTED")
                .set(task.VERSION, task.VERSION.plus(1))
                .set(task.UPDATED_AT, now)
                .where(task.TASK_ID.eq(candidate.taskId()))
                .and(task.STATUS.eq("CLAIMED"))
                .execute();
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
        TskTask task = TSK_TASK;
        Condition due = task.STATUS.in("PENDING", "RETRY_WAIT").and(task.NEXT_RUN_AT.le(now));
        Condition expiredLease = task.STATUS.eq("CLAIMED").and(task.CLAIM_UNTIL.lt(now));
        Optional<TaskCandidate> selected = dsl.select(candidateColumns())
                .from(task)
                .where(task.TASK_KIND.eq("AUTOMATED"))
                .and(task.ATTEMPT_COUNT.lt(task.MAX_ATTEMPTS))
                .and(due.or(expiredLease))
                .orderBy(task.PRIORITY.desc(), task.NEXT_RUN_AT, task.CREATED_AT, task.TASK_ID)
                .limit(1)
                .forUpdate()
                .skipLocked()
                .fetchOptional(this::mapCandidate);
        if (selected.isEmpty()) {
            return Optional.empty();
        }

        TaskCandidate candidate = selected.orElseThrow();
        if ("CLAIMED".equals(candidate.status())) {
            expirePreviousAttempt(candidate.currentAttemptId(), now);
        }

        UUID attemptId = UUID.randomUUID();
        int attemptNo = candidate.attemptCount() + 1;
        dsl.update(task)
                .set(task.STATUS, "CLAIMED")
                .set(task.CLAIM_OWNER, workerId)
                .set(task.CLAIM_UNTIL, now.plus(leaseDuration))
                .set(task.CURRENT_ATTEMPT_ID, attemptId)
                .set(task.ATTEMPT_COUNT, attemptNo)
                .set(task.VERSION, task.VERSION.plus(1))
                .set(task.UPDATED_AT, now)
                .where(task.TASK_ID.eq(candidate.taskId()))
                .execute();
        dsl.insertInto(TSK_TASK_EXECUTION_ATTEMPT)
                .set(TSK_TASK_EXECUTION_ATTEMPT.ATTEMPT_ID, attemptId)
                .set(TSK_TASK_EXECUTION_ATTEMPT.TASK_ID, candidate.taskId())
                .set(TSK_TASK_EXECUTION_ATTEMPT.ATTEMPT_NO, attemptNo)
                .set(TSK_TASK_EXECUTION_ATTEMPT.WORKER_ID, workerId)
                .set(TSK_TASK_EXECUTION_ATTEMPT.STARTED_AT, now)
                .set(TSK_TASK_EXECUTION_ATTEMPT.RESULT_CODE, "RUNNING")
                .execute();

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
        TskTask taskRow = TSK_TASK;
        int updated = dsl.update(taskRow)
                .set(taskRow.STATUS, "RETRY_WAIT")
                .set(taskRow.NEXT_RUN_AT, outcome.retryAt())
                .setNull(taskRow.CLAIM_OWNER)
                .setNull(taskRow.CLAIM_UNTIL)
                .setNull(taskRow.CURRENT_ATTEMPT_ID)
                .set(taskRow.LAST_ERROR_CODE, truncate(outcome.errorCode(), 100))
                .set(taskRow.VERSION, taskRow.VERSION.plus(1))
                .set(taskRow.UPDATED_AT, now)
                .where(taskRow.TASK_ID.eq(task.taskId()))
                .and(taskRow.STATUS.eq("CLAIMED"))
                .and(taskRow.CLAIM_OWNER.eq(workerId))
                .and(taskRow.CURRENT_ATTEMPT_ID.eq(task.attemptId()))
                .execute();
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
        TskTask taskRow = TSK_TASK;
        return dsl.update(taskRow)
                .set(taskRow.STATUS, status)
                .setNull(taskRow.CLAIM_OWNER)
                .setNull(taskRow.CLAIM_UNTIL)
                .setNull(taskRow.CURRENT_ATTEMPT_ID)
                .set(taskRow.LAST_ERROR_CODE, errorCode)
                .set(taskRow.COMPLETED_AT, completedAt)
                .set(taskRow.VERSION, taskRow.VERSION.plus(1))
                .set(taskRow.UPDATED_AT, now)
                .where(taskRow.TASK_ID.eq(task.taskId()))
                .and(taskRow.STATUS.eq("CLAIMED"))
                .and(taskRow.CLAIM_OWNER.eq(workerId))
                .and(taskRow.CURRENT_ATTEMPT_ID.eq(task.attemptId()))
                .execute();
    }

    private void finishAttempt(
            ClaimedTask task,
            String resultCode,
            String errorCode,
            String resultRef,
            Instant nextRetryAt,
            Instant finishedAt
    ) {
        int updated = dsl.update(TSK_TASK_EXECUTION_ATTEMPT)
                .set(TSK_TASK_EXECUTION_ATTEMPT.FINISHED_AT, finishedAt)
                .set(TSK_TASK_EXECUTION_ATTEMPT.RESULT_CODE, resultCode)
                .set(TSK_TASK_EXECUTION_ATTEMPT.ERROR_CODE, errorCode)
                .set(TSK_TASK_EXECUTION_ATTEMPT.RESULT_REF, resultRef)
                .set(TSK_TASK_EXECUTION_ATTEMPT.NEXT_RETRY_AT, nextRetryAt)
                .where(TSK_TASK_EXECUTION_ATTEMPT.ATTEMPT_ID.eq(task.attemptId()))
                .and(TSK_TASK_EXECUTION_ATTEMPT.RESULT_CODE.eq("RUNNING"))
                .execute();
        requireLease(updated);
    }

    private void expirePreviousAttempt(UUID attemptId, Instant now) {
        int updated = dsl.update(TSK_TASK_EXECUTION_ATTEMPT)
                .set(TSK_TASK_EXECUTION_ATTEMPT.FINISHED_AT, now)
                .set(TSK_TASK_EXECUTION_ATTEMPT.RESULT_CODE, "LEASE_EXPIRED")
                .set(TSK_TASK_EXECUTION_ATTEMPT.ERROR_CODE, "TASK_LEASE_EXPIRED")
                .where(TSK_TASK_EXECUTION_ATTEMPT.ATTEMPT_ID.eq(attemptId))
                .and(TSK_TASK_EXECUTION_ATTEMPT.RESULT_CODE.eq("RUNNING"))
                .execute();
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
        TskTask taskRow = TSK_TASK;
        WorkflowTaskContext context = dsl
                .select(taskRow.PROJECT_ID, taskRow.WORK_ORDER_ID, taskRow.WORKFLOW_INSTANCE_ID,
                        taskRow.STAGE_INSTANCE_ID, taskRow.WORKFLOW_NODE_INSTANCE_ID, taskRow.WORKFLOW_NODE_ID,
                        taskRow.WORKFLOW_DEFINITION_VERSION_ID, taskRow.WORKFLOW_DEFINITION_DIGEST)
                .from(taskRow)
                .where(taskRow.TENANT_ID.eq(task.tenantId()))
                .and(taskRow.TASK_ID.eq(task.taskId()))
                .fetchSingle(JooqTaskExecutionStore::mapWorkflowTaskContext);
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
        TskTask task = TSK_TASK;
        return dsl
                .select(task.TASK_ID, task.TENANT_ID, task.TASK_TYPE, task.BUSINESS_KEY, task.PAYLOAD_DIGEST,
                        task.FORM_REF, task.SLA_REF, task.ASSIGNEE_POLICY_REF, task.DISPATCH_POLICY_REF,
                        task.RULE_REF, task.CONFIGURATION_BUNDLE_ID, task.CONFIGURATION_BUNDLE_DIGEST,
                        task.STAGE_CODE, task.STATUS, task.NEXT_RUN_AT, task.ATTEMPT_COUNT,
                        task.MAX_ATTEMPTS, task.VERSION)
                .from(task)
                .where(task.TENANT_ID.eq(tenantId))
                .and(task.TASK_TYPE.eq(taskType))
                .and(task.BUSINESS_KEY.eq(businessKey))
                .fetchSingle(JooqTaskExecutionStore::mapStoredTask);
    }

    private static List<SelectField<?>> candidateColumns() {
        TskTask task = TSK_TASK;
        return List.of(task.TASK_ID, task.TENANT_ID, task.TASK_TYPE, task.BUSINESS_KEY,
                task.PAYLOAD_REF, task.PAYLOAD_DIGEST, task.CORRELATION_ID, task.STATUS,
                task.CURRENT_ATTEMPT_ID, task.ATTEMPT_COUNT, task.MAX_ATTEMPTS, task.VERSION);
    }

    private TaskCandidate mapCandidate(Record record) {
        TskTask task = TSK_TASK;
        return new TaskCandidate(
                record.get(task.TASK_ID), record.get(task.TENANT_ID),
                record.get(task.TASK_TYPE), record.get(task.BUSINESS_KEY),
                record.get(task.PAYLOAD_REF), record.get(task.PAYLOAD_DIGEST),
                record.get(task.CORRELATION_ID), record.get(task.STATUS),
                record.get(task.CURRENT_ATTEMPT_ID), record.get(task.ATTEMPT_COUNT),
                record.get(task.MAX_ATTEMPTS), record.get(task.VERSION));
    }

    private static StoredTask mapStoredTask(Record record) {
        TskTask task = TSK_TASK;
        return new StoredTask(
                record.get(task.TASK_ID), record.get(task.TENANT_ID), record.get(task.TASK_TYPE),
                record.get(task.BUSINESS_KEY), record.get(task.PAYLOAD_DIGEST), record.get(task.FORM_REF),
                record.get(task.SLA_REF), record.get(task.ASSIGNEE_POLICY_REF),
                record.get(task.DISPATCH_POLICY_REF), record.get(task.RULE_REF),
                record.get(task.CONFIGURATION_BUNDLE_ID), record.get(task.CONFIGURATION_BUNDLE_DIGEST),
                record.get(task.STAGE_CODE), record.get(task.STATUS), record.get(task.NEXT_RUN_AT),
                record.get(task.ATTEMPT_COUNT), record.get(task.MAX_ATTEMPTS), record.get(task.VERSION));
    }

    private static CancellationState mapCancellationState(Record record) {
        TskTask task = TSK_TASK;
        return new CancellationState(
                record.get(task.TASK_ID), record.get(task.TASK_TYPE), record.get(task.BUSINESS_KEY),
                record.get(task.TASK_KIND), record.get(task.STATUS), record.get(task.VERSION),
                record.get(task.CANCELLATION_SOURCE_EVENT_ID), record.get(task.CANCELLED_AT));
    }

    private static CompletionState mapCompletionState(Record record) {
        TskTask task = TSK_TASK;
        return new CompletionState(
                record.get(task.TASK_ID), record.get(task.TASK_TYPE), record.get(task.BUSINESS_KEY),
                record.get(task.TASK_KIND), record.get(task.STATUS), record.get(task.VERSION),
                record.get(task.RESULT_REF), record.get(task.RESULT_DIGEST), record.get(task.COMPLETED_AT));
    }

    private static WorkflowTaskContext mapWorkflowTaskContext(Record record) {
        TskTask task = TSK_TASK;
        return new WorkflowTaskContext(
                record.get(task.PROJECT_ID), record.get(task.WORK_ORDER_ID),
                record.get(task.WORKFLOW_INSTANCE_ID), record.get(task.STAGE_INSTANCE_ID),
                record.get(task.WORKFLOW_NODE_INSTANCE_ID), record.get(task.WORKFLOW_NODE_ID),
                record.get(task.WORKFLOW_DEFINITION_VERSION_ID), record.get(task.WORKFLOW_DEFINITION_DIGEST));
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
            UUID taskId, String tenantId, String taskType, String businessKey, String payloadDigest,
            String formRef, String slaRef, String assigneePolicyRef, String dispatchPolicyRef,
            String ruleRef,
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

    private record CompletionState(
            UUID taskId,
            String taskType,
            String businessKey,
            String taskKind,
            String status,
            long version,
            String resultRef,
            String resultDigest,
            Instant completedAt
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
