package com.serviceos.operations.application;

import com.serviceos.jooq.generated.tables.OpsOperationalException;
import com.serviceos.jooq.generated.tables.OpsTaskFailureRecovery;
import com.serviceos.operations.api.OpenTaskFailureCommand;
import com.serviceos.operations.api.OpenServiceAssignmentTimeoutCommand;
import com.serviceos.operations.api.ResolveServiceAssignmentTimeoutCommand;
import com.serviceos.operations.api.OperationalExceptionService;
import com.serviceos.operations.api.OperationalExceptionResolvedPayload;
import com.serviceos.operations.api.OperationalExceptionView;
import com.serviceos.operations.api.ResolveTaskFailureExceptionsCommand;
import com.serviceos.operations.api.TaskFailureExceptionResolvedPayload;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CancelHandlingTaskCommand;
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskSchedulingService;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.OpsOperationalException.OPS_OPERATIONAL_EXCEPTION;
import static com.serviceos.jooq.generated.tables.OpsTaskFailureRecovery.OPS_TASK_FAILURE_RECOVERY;
import static org.jooq.impl.DSL.excluded;

/**
 * 最终失败消费者：Inbox、异常记录和人工处理 Task 在同一事务提交。
 */
@Service
final class JooqOperationalExceptionService implements OperationalExceptionService {
    private static final String CONSUMER_NAME = "operations.task-final-failure.v1";
    private static final String SAGA_TIMEOUT_CONSUMER = "operations.service-assignment-timeout.v1";
    private static final String SAGA_RECOVERY_CONSUMER =
            "operations.service-assignment-activation-completed.v1";
    private static final String TASK_RECOVERY_CONSUMER =
            "operations.outbound-delivery-recovered.v1";
    private static final String HANDLING_TASK_TYPE = "operations.resolve-exception";
    private static final String DISPATCH_TIMEOUT_TASK_TYPE = "operations.resolve-dispatch-timeout";

    private final InboxService inbox;
    private final TaskSchedulingService tasks;
    private final DSLContext dsl;
    private final Clock clock;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;

    JooqOperationalExceptionService(
            InboxService inbox,
            TaskSchedulingService tasks,
            DSLContext dsl,
            Clock clock,
            OutboxAppender outbox,
            ObjectMapper objectMapper
    ) {
        this.inbox = inbox;
        this.tasks = tasks;
        this.dsl = dsl;
        this.clock = clock;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public OperationalExceptionView openFromTaskFailure(OpenTaskFailureCommand command) {
        validate(command);
        InboxDecision decision = inbox.begin(
                command.tenantId(), CONSUMER_NAME, command.eventId(),
                command.schemaVersion(), command.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return findBySource(command.tenantId(), command.sourceTaskId(), command.sourceAttemptId());
        }

        lockTaskFailureStream(command.tenantId(), command.sourceTaskId());
        TaskFailureRecovery recovery = findTaskFailureRecovery(
                command.tenantId(), command.sourceTaskId());
        if (recovery != null) {
            if (!command.sourceTaskType().equals(recovery.sourceTaskType())) {
                throw new IllegalArgumentException("Task failure recovery source type mismatch");
            }
            return recordLateRecoveredFailure(command, recovery);
        }

        Instant now = command.detectedAt();
        UUID exceptionId = UUID.randomUUID();
        OpsOperationalException exception = OPS_OPERATIONAL_EXCEPTION;
        dsl.insertInto(exception)
                .set(exception.EXCEPTION_ID, exceptionId)
                .set(exception.TENANT_ID, command.tenantId())
                .set(exception.SOURCE_TYPE, "TASK")
                .set(exception.SOURCE_ID, command.sourceTaskId().toString())
                .set(exception.SOURCE_ATTEMPT_ID, command.sourceAttemptId())
                .set(exception.SOURCE_TASK_TYPE, command.sourceTaskType())
                .set(exception.CATEGORY_CODE, "AUTOMATION_FINAL_FAILURE")
                .set(exception.SEVERITY_CODE, "P1")
                .set(exception.ERROR_CODE, truncate(command.errorCode(), 100))
                .set(exception.STATUS, "OPEN")
                .set(exception.CORRELATION_ID, command.correlationId())
                .set(exception.OPENED_AT, now)
                .set(exception.LAST_DETECTED_AT, now)
                .onConflict(exception.TENANT_ID, exception.SOURCE_TYPE,
                        exception.SOURCE_ID, exception.SOURCE_ATTEMPT_ID)
                .doNothing()
                .execute();

        OperationalExceptionView opened = findBySource(
                command.tenantId(), command.sourceTaskId(), command.sourceAttemptId());
        String handlingPayloadDigest = Sha256.digest(
                opened.exceptionId() + "|" + command.sourceTaskId() + "|" + command.errorCode());
        ScheduledTaskView handlingTask = tasks.createHandlingTask(new CreateHandlingTaskCommand(
                command.tenantId(), HANDLING_TASK_TYPE, opened.exceptionId().toString(),
                "operational-exception:" + opened.exceptionId(), handlingPayloadDigest,
                900, now, command.correlationId()));
        dsl.update(exception)
                .set(exception.HANDLING_TASK_ID, handlingTask.taskId())
                .where(exception.EXCEPTION_ID.eq(opened.exceptionId()))
                .and(exception.HANDLING_TASK_ID.isNull())
                .execute();
        inbox.complete(
                command.tenantId(), CONSUMER_NAME, command.eventId(),
                Sha256.digest(opened.exceptionId() + "|" + handlingTask.taskId()));
        return findBySource(command.tenantId(), command.sourceTaskId(), command.sourceAttemptId());
    }

    @Override
    @Transactional
    public void resolveTaskFailures(ResolveTaskFailureExceptionsCommand command) {
        validate(command);
        InboxDecision decision = inbox.begin(
                command.tenantId(), TASK_RECOVERY_CONSUMER, command.eventId(),
                command.schemaVersion(), command.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }

        List<UUID> orderedTaskIds = command.sourceTaskIds().stream()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        List<String> results = new ArrayList<>();
        for (UUID sourceTaskId : orderedTaskIds) {
            // 每个 Task 使用固定 advisory lock；恢复与失败事件无论谁先到，都在同一流上串行判定。
            lockTaskFailureStream(command.tenantId(), sourceTaskId);
            registerTaskFailureRecovery(command, sourceTaskId);
            List<TaskFailureExceptionState> states = findTaskFailureExceptions(
                    command.tenantId(), sourceTaskId, command.sourceTaskType());
            if (states.isEmpty()) {
                results.add(sourceTaskId + ":MARKED");
                continue;
            }
            for (TaskFailureExceptionState state : states) {
                if ("RESOLVED".equals(state.status())) {
                    results.add(state.exceptionId() + ":ALREADY_RESOLVED");
                    continue;
                }
                if (state.handlingTaskId() == null) {
                    throw new IllegalStateException("Open Task failure exception has no handling Task");
                }
                String resolutionCode = "OUTBOUND_DELIVERY_RECOVERED";
                String actionRef = recoveryActionRef(command.eventId());
                var cancellation = tasks.cancelHandlingTask(new CancelHandlingTaskCommand(
                        command.tenantId(), state.handlingTaskId(), HANDLING_TASK_TYPE,
                        state.exceptionId().toString(), resolutionCode,
                        command.eventId(), command.recoveredAt(), command.correlationId()));
                OpsOperationalException exception = OPS_OPERATIONAL_EXCEPTION;
                int updated = dsl.update(exception)
                        .set(exception.STATUS, "RESOLVED")
                        .set(exception.RESOLVED_AT, command.recoveredAt())
                        .set(exception.RESOLUTION_CODE, resolutionCode)
                        .set(exception.RESOLUTION_ACTION_REF, actionRef)
                        .set(exception.RESOLUTION_EVENT_ID, command.eventId())
                        .set(exception.AGGREGATE_VERSION, exception.AGGREGATE_VERSION.plus(1))
                        .where(exception.EXCEPTION_ID.eq(state.exceptionId()))
                        .and(exception.STATUS.in("OPEN", "ACKNOWLEDGED"))
                        .execute();
                if (updated != 1) {
                    throw new IllegalStateException("Task failure exception changed during recovery");
                }
                appendTaskFailureResolvedEvent(
                        command.tenantId(), command.correlationId(), state.exceptionId(),
                        sourceTaskId, command.sourceTaskType(), command.recoveryType(),
                        command.recoveryRef(), state.handlingTaskId(), cancellation.status(),
                        state.aggregateVersion() + 1, command.eventId(), command.recoveredAt());
                results.add(state.exceptionId() + ":" + cancellation.status());
            }
        }
        inbox.complete(command.tenantId(), TASK_RECOVERY_CONSUMER, command.eventId(),
                Sha256.digest(String.join("|", results)));
    }

    @Override
    @Transactional
    public OperationalExceptionView openFromServiceAssignmentTimeout(
            OpenServiceAssignmentTimeoutCommand command
    ) {
        validate(command);
        InboxDecision decision = inbox.begin(
                command.tenantId(), SAGA_TIMEOUT_CONSUMER, command.eventId(),
                command.schemaVersion(), command.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return findSagaTimeout(command.tenantId(), command.sagaId());
        }

        Instant now = clock.instant();
        UUID exceptionId = UUID.randomUUID();
        OpsOperationalException exception = OPS_OPERATIONAL_EXCEPTION;
        // 冲突即同一 saga 另一阶段超时再次到达：发生次数 +1、最后检测时间取较大值，
        // 已解决/已确认字段清空并重新回到 OPEN，版本 +1。
        dsl.insertInto(exception)
                .set(exception.EXCEPTION_ID, exceptionId)
                .set(exception.TENANT_ID, command.tenantId())
                .set(exception.SOURCE_TYPE, "SERVICE_ASSIGNMENT_ACTIVATION_SAGA")
                .set(exception.SOURCE_ID, command.sagaId().toString())
                // 现有唯一键要求 occurrence UUID；以 sagaId 聚合同一 saga 的多阶段超时。
                .set(exception.SOURCE_ATTEMPT_ID, command.sagaId())
                .set(exception.SOURCE_TASK_TYPE, DISPATCH_TIMEOUT_TASK_TYPE)
                .set(exception.CATEGORY_CODE, "DISPATCH")
                .set(exception.SEVERITY_CODE, "P1")
                .set(exception.ERROR_CODE, truncate(command.errorCode(), 100))
                .set(exception.STATUS, "OPEN")
                .set(exception.WORK_ORDER_ID, command.workOrderId())
                .set(exception.TASK_ID, command.taskId())
                .set(exception.CORRELATION_ID, command.correlationId())
                .set(exception.OPENED_AT, now)
                .set(exception.LAST_DETECTED_AT, command.detectedAt())
                .onConflict(exception.TENANT_ID, exception.SOURCE_TYPE,
                        exception.SOURCE_ID, exception.SOURCE_ATTEMPT_ID)
                .doUpdate()
                .set(exception.OCCURRENCE_COUNT, exception.OCCURRENCE_COUNT.plus(1))
                .set(exception.LAST_DETECTED_AT, DSL.greatest(
                        exception.LAST_DETECTED_AT, excluded(exception.LAST_DETECTED_AT)))
                .set(exception.ERROR_CODE, excluded(exception.ERROR_CODE))
                .set(exception.STATUS, "OPEN")
                .setNull(exception.RESOLVED_AT)
                .setNull(exception.ACKNOWLEDGED_AT)
                .setNull(exception.ACKNOWLEDGED_BY)
                .setNull(exception.ACKNOWLEDGEMENT_NOTE)
                .setNull(exception.RESOLUTION_CODE)
                .setNull(exception.RESOLUTION_ACTION_REF)
                .setNull(exception.RESOLUTION_EVENT_ID)
                .set(exception.AGGREGATE_VERSION, exception.AGGREGATE_VERSION.plus(1))
                .execute();

        OperationalExceptionView opened = findSagaTimeout(command.tenantId(), command.sagaId());
        String handlingPayloadDigest = Sha256.digest(
                opened.exceptionId() + "|" + command.sagaId() + "|" + command.errorCode());
        ScheduledTaskView handlingTask = tasks.createHandlingTask(new CreateHandlingTaskCommand(
                command.tenantId(), DISPATCH_TIMEOUT_TASK_TYPE, opened.exceptionId().toString(),
                "service-assignment-saga:" + command.sagaId(), handlingPayloadDigest,
                950, now, command.correlationId()));
        dsl.update(exception)
                .set(exception.HANDLING_TASK_ID, handlingTask.taskId())
                .where(exception.EXCEPTION_ID.eq(opened.exceptionId()))
                .and(exception.HANDLING_TASK_ID.isNull())
                .execute();
        inbox.complete(
                command.tenantId(), SAGA_TIMEOUT_CONSUMER, command.eventId(),
                Sha256.digest(opened.exceptionId() + "|" + handlingTask.taskId()
                        + "|" + command.timeoutId()));
        return findSagaTimeout(command.tenantId(), command.sagaId());
    }

    @Override
    @Transactional
    public void resolveServiceAssignmentTimeout(ResolveServiceAssignmentTimeoutCommand command) {
        validate(command);
        InboxDecision decision = inbox.begin(
                command.tenantId(), SAGA_RECOVERY_CONSUMER, command.eventId(),
                command.schemaVersion(), command.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }

        OpsOperationalException exception = OPS_OPERATIONAL_EXCEPTION;
        SagaTimeoutException state = dsl.select(
                        exception.EXCEPTION_ID, exception.WORK_ORDER_ID, exception.TASK_ID,
                        exception.HANDLING_TASK_ID, exception.STATUS, exception.OCCURRENCE_COUNT,
                        exception.AGGREGATE_VERSION, exception.RESOLUTION_EVENT_ID)
                .from(exception)
                .where(exception.TENANT_ID.eq(command.tenantId()))
                .and(exception.SOURCE_TYPE.eq("SERVICE_ASSIGNMENT_ACTIVATION_SAGA"))
                .and(exception.SOURCE_ID.eq(command.sagaId().toString()))
                .and(exception.SOURCE_ATTEMPT_ID.eq(command.sagaId()))
                .forUpdate()
                .fetchOptional(this::mapSagaTimeoutException)
                .orElse(null);
        // 未发生过超时是合法正常路径；仍冻结 Inbox，阻止同一恢复事件被变造重放。
        if (state == null) {
            inbox.complete(command.tenantId(), SAGA_RECOVERY_CONSUMER, command.eventId(),
                    Sha256.digest("NO_TIMEOUT_EXCEPTION|" + command.sagaId()));
            return;
        }
        if (!command.workOrderId().equals(state.workOrderId())
                || !command.taskId().equals(state.taskId())) {
            throw new IllegalArgumentException("ServiceAssignment recovery source identity mismatch");
        }
        if ("RESOLVED".equals(state.status())) {
            if (!command.eventId().equals(state.resolutionEventId())) {
                throw new IllegalStateException("OperationalException was resolved by another action");
            }
            inbox.complete(command.tenantId(), SAGA_RECOVERY_CONSUMER, command.eventId(),
                    Sha256.digest(state.exceptionId() + "|ALREADY_RESOLVED"));
            return;
        }
        if (state.handlingTaskId() == null) {
            throw new IllegalStateException("Open saga timeout exception has no handling task");
        }

        String resolutionCode = "SERVICE_ASSIGNMENT_ACTIVATION_RECOVERED";
        String actionRef = "event:service.assignment.activation-completed:" + command.eventId();
        var cancellation = tasks.cancelHandlingTask(new CancelHandlingTaskCommand(
                command.tenantId(), state.handlingTaskId(), DISPATCH_TIMEOUT_TASK_TYPE,
                state.exceptionId().toString(), resolutionCode,
                command.eventId(), command.completedAt(), command.correlationId()));
        int updated = dsl.update(exception)
                .set(exception.STATUS, "RESOLVED")
                .set(exception.RESOLVED_AT, command.completedAt())
                .set(exception.RESOLUTION_CODE, resolutionCode)
                .set(exception.RESOLUTION_ACTION_REF, actionRef)
                .set(exception.RESOLUTION_EVENT_ID, command.eventId())
                .set(exception.AGGREGATE_VERSION, exception.AGGREGATE_VERSION.plus(1))
                .where(exception.EXCEPTION_ID.eq(state.exceptionId()))
                .and(exception.STATUS.in("OPEN", "ACKNOWLEDGED"))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("OperationalException changed during automatic recovery");
        }

        OperationalExceptionResolvedPayload payload = new OperationalExceptionResolvedPayload(
                state.exceptionId(), command.sagaId(), command.serviceAssignmentId(),
                state.handlingTaskId(), cancellation.status(), resolutionCode, actionRef,
                command.eventId(), command.completedAt());
        String json = serialize(payload);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "operations", "operational.exception.resolved", 1,
                "OperationalException", state.exceptionId().toString(), state.aggregateVersion() + 1L,
                command.tenantId(), command.correlationId(), command.eventId().toString(),
                state.exceptionId().toString(), json, Sha256.digest(json), command.completedAt()));
        inbox.complete(command.tenantId(), SAGA_RECOVERY_CONSUMER, command.eventId(),
                Sha256.digest(state.exceptionId() + "|" + cancellation.status() + "|" + actionRef));
    }

    private OperationalExceptionView recordLateRecoveredFailure(
            OpenTaskFailureCommand command,
            TaskFailureRecovery recovery
    ) {
        UUID exceptionId = UUID.randomUUID();
        String resolutionCode = "OUTBOUND_DELIVERY_RECOVERED";
        String actionRef = recoveryActionRef(recovery.recoveryEventId());
        OpsOperationalException exception = OPS_OPERATIONAL_EXCEPTION;
        int inserted = dsl.insertInto(exception)
                .set(exception.EXCEPTION_ID, exceptionId)
                .set(exception.TENANT_ID, command.tenantId())
                .set(exception.SOURCE_TYPE, "TASK")
                .set(exception.SOURCE_ID, command.sourceTaskId().toString())
                .set(exception.SOURCE_ATTEMPT_ID, command.sourceAttemptId())
                .set(exception.SOURCE_TASK_TYPE, command.sourceTaskType())
                .set(exception.CATEGORY_CODE, "AUTOMATION_FINAL_FAILURE")
                .set(exception.SEVERITY_CODE, "P1")
                .set(exception.ERROR_CODE, truncate(command.errorCode(), 100))
                .set(exception.STATUS, "RESOLVED")
                .set(exception.CORRELATION_ID, command.correlationId())
                .set(exception.OPENED_AT, command.detectedAt())
                .set(exception.LAST_DETECTED_AT, command.detectedAt())
                .set(exception.RESOLVED_AT, recovery.recoveredAt())
                .set(exception.RESOLUTION_CODE, resolutionCode)
                .set(exception.RESOLUTION_ACTION_REF, actionRef)
                .set(exception.RESOLUTION_EVENT_ID, recovery.recoveryEventId())
                .onConflict(exception.TENANT_ID, exception.SOURCE_TYPE,
                        exception.SOURCE_ID, exception.SOURCE_ATTEMPT_ID)
                .doNothing()
                .execute();
        OperationalExceptionView resolved = findBySource(
                command.tenantId(), command.sourceTaskId(), command.sourceAttemptId());
        if (inserted == 1) {
            appendTaskFailureResolvedEvent(
                    command.tenantId(), command.correlationId(), resolved.exceptionId(),
                    command.sourceTaskId(), command.sourceTaskType(), recovery.recoveryType(),
                    recovery.recoveryRef(), null, "NOT_CREATED", 1,
                    recovery.recoveryEventId(), recovery.recoveredAt());
        }
        inbox.complete(command.tenantId(), CONSUMER_NAME, command.eventId(),
                Sha256.digest(resolved.exceptionId() + "|RECOVERED_BEFORE_FAILURE"));
        return resolved;
    }

    private void registerTaskFailureRecovery(
            ResolveTaskFailureExceptionsCommand command,
            UUID sourceTaskId
    ) {
        OpsTaskFailureRecovery recovery = OPS_TASK_FAILURE_RECOVERY;
        int inserted = dsl.insertInto(recovery)
                .set(recovery.TENANT_ID, command.tenantId())
                .set(recovery.SOURCE_TASK_ID, sourceTaskId)
                .set(recovery.SOURCE_TASK_TYPE, command.sourceTaskType())
                .set(recovery.RECOVERY_TYPE, command.recoveryType())
                .set(recovery.RECOVERY_REF, command.recoveryRef())
                .set(recovery.RECOVERY_EVENT_ID, command.eventId())
                .set(recovery.RECOVERED_AT, command.recoveredAt())
                .set(recovery.CORRELATION_ID, command.correlationId())
                .onConflict(recovery.TENANT_ID, recovery.SOURCE_TASK_ID)
                .doNothing()
                .execute();
        TaskFailureRecovery stored = findTaskFailureRecovery(command.tenantId(), sourceTaskId);
        if (stored == null) {
            throw new IllegalStateException("Task failure recovery marker was not persisted");
        }
        if (inserted == 0 && (!command.sourceTaskType().equals(stored.sourceTaskType())
                || !command.recoveryType().equals(stored.recoveryType())
                || !command.recoveryRef().equals(stored.recoveryRef())
                || !command.eventId().equals(stored.recoveryEventId())
                || !command.recoveredAt().equals(stored.recoveredAt()))) {
            throw new IllegalStateException("Task failure was already bound to another recovery fact");
        }
    }

    private TaskFailureRecovery findTaskFailureRecovery(String tenantId, UUID sourceTaskId) {
        OpsTaskFailureRecovery recovery = OPS_TASK_FAILURE_RECOVERY;
        return dsl.select(
                        recovery.SOURCE_TASK_TYPE, recovery.RECOVERY_TYPE, recovery.RECOVERY_REF,
                        recovery.RECOVERY_EVENT_ID, recovery.RECOVERED_AT)
                .from(recovery)
                .where(recovery.TENANT_ID.eq(tenantId))
                .and(recovery.SOURCE_TASK_ID.eq(sourceTaskId))
                .fetchOptional(row -> new TaskFailureRecovery(
                        row.get(recovery.SOURCE_TASK_TYPE),
                        row.get(recovery.RECOVERY_TYPE),
                        row.get(recovery.RECOVERY_REF),
                        row.get(recovery.RECOVERY_EVENT_ID),
                        row.get(recovery.RECOVERED_AT)))
                .orElse(null);
    }

    private List<TaskFailureExceptionState> findTaskFailureExceptions(
            String tenantId,
            UUID sourceTaskId,
            String sourceTaskType
    ) {
        OpsOperationalException exception = OPS_OPERATIONAL_EXCEPTION;
        return dsl.select(
                        exception.EXCEPTION_ID, exception.HANDLING_TASK_ID,
                        exception.STATUS, exception.AGGREGATE_VERSION)
                .from(exception)
                .where(exception.TENANT_ID.eq(tenantId))
                .and(exception.SOURCE_TYPE.eq("TASK"))
                .and(exception.SOURCE_ID.eq(sourceTaskId.toString()))
                .and(exception.SOURCE_TASK_TYPE.eq(sourceTaskType))
                .orderBy(exception.OPENED_AT, exception.EXCEPTION_ID)
                .forUpdate()
                .fetch(row -> new TaskFailureExceptionState(
                        row.get(exception.EXCEPTION_ID),
                        row.get(exception.HANDLING_TASK_ID),
                        row.get(exception.STATUS),
                        row.get(exception.AGGREGATE_VERSION)));
    }

    private void lockTaskFailureStream(String tenantId, UUID sourceTaskId) {
        // advisory lock 随事务释放；锁键与失败流一一对应。
        dsl.select(DSL.field("pg_advisory_xact_lock(hashtextextended({0}, {1}))", Object.class,
                        DSL.val(tenantId + "|TASK_FAILURE|" + sourceTaskId), DSL.val(0L)))
                .fetchSingle();
    }

    private void appendTaskFailureResolvedEvent(
            String tenantId,
            String correlationId,
            UUID exceptionId,
            UUID sourceTaskId,
            String sourceTaskType,
            String recoveryType,
            String recoveryRef,
            UUID handlingTaskId,
            String handlingTaskStatus,
            long aggregateVersion,
            UUID recoveryEventId,
            Instant resolvedAt
    ) {
        String resolutionCode = "OUTBOUND_DELIVERY_RECOVERED";
        String actionRef = recoveryActionRef(recoveryEventId);
        String payload = serialize(new TaskFailureExceptionResolvedPayload(
                exceptionId, sourceTaskId, sourceTaskType, recoveryType, recoveryRef,
                handlingTaskId, handlingTaskStatus, resolutionCode, actionRef,
                recoveryEventId, resolvedAt));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "operations",
                "operational.exception.resolved", 2,
                "OperationalException", exceptionId.toString(), aggregateVersion,
                tenantId, correlationId, recoveryEventId.toString(), exceptionId.toString(),
                payload, Sha256.digest(payload), resolvedAt));
    }

    private static String recoveryActionRef(UUID recoveryEventId) {
        return "event:integration.outbound-delivery-recovered:" + recoveryEventId;
    }

    private OperationalExceptionView findBySource(
            String tenantId,
            UUID sourceTaskId,
            UUID sourceAttemptId
    ) {
        OpsOperationalException exception = OPS_OPERATIONAL_EXCEPTION;
        return dsl.select(
                        exception.EXCEPTION_ID, exception.TENANT_ID, exception.SOURCE_ID,
                        exception.SOURCE_ATTEMPT_ID, exception.SOURCE_TASK_TYPE, exception.ERROR_CODE,
                        exception.STATUS, exception.HANDLING_TASK_ID, exception.OPENED_AT)
                .from(exception)
                .where(exception.TENANT_ID.eq(tenantId))
                .and(exception.SOURCE_TYPE.eq("TASK"))
                .and(exception.SOURCE_ID.eq(sourceTaskId.toString()))
                .and(exception.SOURCE_ATTEMPT_ID.eq(sourceAttemptId))
                .fetchSingle(this::mapExceptionView);
    }

    private OperationalExceptionView findSagaTimeout(String tenantId, UUID sagaId) {
        OpsOperationalException exception = OPS_OPERATIONAL_EXCEPTION;
        return dsl.select(
                        exception.EXCEPTION_ID, exception.TENANT_ID, exception.SOURCE_ID,
                        exception.SOURCE_ATTEMPT_ID, exception.SOURCE_TASK_TYPE, exception.ERROR_CODE,
                        exception.STATUS, exception.HANDLING_TASK_ID, exception.OPENED_AT)
                .from(exception)
                .where(exception.TENANT_ID.eq(tenantId))
                .and(exception.SOURCE_TYPE.eq("SERVICE_ASSIGNMENT_ACTIVATION_SAGA"))
                .and(exception.SOURCE_ID.eq(sagaId.toString()))
                .and(exception.SOURCE_ATTEMPT_ID.eq(sagaId))
                .fetchSingle(this::mapExceptionView);
    }

    private OperationalExceptionView mapExceptionView(Record row) {
        OpsOperationalException exception = OPS_OPERATIONAL_EXCEPTION;
        return new OperationalExceptionView(
                row.get(exception.EXCEPTION_ID),
                row.get(exception.TENANT_ID),
                UUID.fromString(row.get(exception.SOURCE_ID)),
                row.get(exception.SOURCE_ATTEMPT_ID),
                row.get(exception.SOURCE_TASK_TYPE),
                row.get(exception.ERROR_CODE),
                row.get(exception.STATUS),
                row.get(exception.HANDLING_TASK_ID),
                row.get(exception.OPENED_AT));
    }

    private SagaTimeoutException mapSagaTimeoutException(Record row) {
        OpsOperationalException exception = OPS_OPERATIONAL_EXCEPTION;
        return new SagaTimeoutException(
                row.get(exception.EXCEPTION_ID),
                row.get(exception.WORK_ORDER_ID),
                row.get(exception.TASK_ID),
                row.get(exception.HANDLING_TASK_ID),
                row.get(exception.STATUS),
                row.get(exception.OCCURRENCE_COUNT),
                row.get(exception.AGGREGATE_VERSION),
                row.get(exception.RESOLUTION_EVENT_ID));
    }

    private static void validate(OpenTaskFailureCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId");
        Objects.requireNonNull(command.eventId(), "eventId must not be null");
        Objects.requireNonNull(command.sourceTaskId(), "sourceTaskId must not be null");
        Objects.requireNonNull(command.sourceAttemptId(), "sourceAttemptId must not be null");
        requireText(command.payloadDigest(), "payloadDigest");
        requireText(command.sourceTaskType(), "sourceTaskType");
        requireText(command.errorCode(), "errorCode");
        Objects.requireNonNull(command.detectedAt(), "detectedAt must not be null");
        requireText(command.correlationId(), "correlationId");
        if (!command.payloadDigest().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadDigest must be a SHA-256 hex digest");
        }
        if (command.schemaVersion() < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
    }

    private static void validate(OpenServiceAssignmentTimeoutCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId");
        Objects.requireNonNull(command.eventId(), "eventId must not be null");
        Objects.requireNonNull(command.timeoutId(), "timeoutId must not be null");
        Objects.requireNonNull(command.sagaId(), "sagaId must not be null");
        Objects.requireNonNull(command.serviceAssignmentId(), "serviceAssignmentId must not be null");
        Objects.requireNonNull(command.workOrderId(), "workOrderId must not be null");
        Objects.requireNonNull(command.taskId(), "taskId must not be null");
        Objects.requireNonNull(command.detectedAt(), "detectedAt must not be null");
        requireText(command.payloadDigest(), "payloadDigest");
        requireText(command.stage(), "stage");
        requireText(command.errorCode(), "errorCode");
        requireText(command.correlationId(), "correlationId");
        if (!command.payloadDigest().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadDigest must be a SHA-256 hex digest");
        }
        if (command.schemaVersion() < 1 || command.sagaVersion() < 1) {
            throw new IllegalArgumentException("schemaVersion and sagaVersion must be positive");
        }
    }

    private static void validate(ResolveServiceAssignmentTimeoutCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId");
        Objects.requireNonNull(command.eventId(), "eventId must not be null");
        Objects.requireNonNull(command.sagaId(), "sagaId must not be null");
        Objects.requireNonNull(command.serviceAssignmentId(), "serviceAssignmentId must not be null");
        Objects.requireNonNull(command.workOrderId(), "workOrderId must not be null");
        Objects.requireNonNull(command.taskId(), "taskId must not be null");
        Objects.requireNonNull(command.completedAt(), "completedAt must not be null");
        requireText(command.payloadDigest(), "payloadDigest");
        requireText(command.correlationId(), "correlationId");
        if (!command.payloadDigest().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadDigest must be a SHA-256 hex digest");
        }
        if (command.schemaVersion() != 1 || command.sagaVersion() < 1) {
            throw new IllegalArgumentException("unsupported recovery schema or saga version");
        }
    }

    private static void validate(ResolveTaskFailureExceptionsCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId");
        Objects.requireNonNull(command.eventId(), "eventId must not be null");
        requireText(command.payloadDigest(), "payloadDigest");
        requireText(command.sourceTaskType(), "sourceTaskType");
        Objects.requireNonNull(command.sourceTaskIds(), "sourceTaskIds must not be null");
        requireText(command.recoveryType(), "recoveryType");
        requireText(command.recoveryRef(), "recoveryRef");
        Objects.requireNonNull(command.recoveredAt(), "recoveredAt must not be null");
        requireText(command.correlationId(), "correlationId");
        if (command.schemaVersion() != 1
                || !command.payloadDigest().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("unsupported recovery schema or payload digest");
        }
        if (!"integration.byd.submit-review".equals(command.sourceTaskType())
                || !"OUTBOUND_DELIVERY_ACKNOWLEDGED".equals(command.recoveryType())) {
            throw new IllegalArgumentException("unsupported Task failure recovery type");
        }
        if (command.sourceTaskIds().isEmpty()
                || command.sourceTaskIds().stream().anyMatch(Objects::isNull)
                || command.sourceTaskIds().stream().distinct().count() != command.sourceTaskIds().size()) {
            throw new IllegalArgumentException("sourceTaskIds must be non-empty and unique");
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("OperationalException event serialization failed", exception);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record SagaTimeoutException(
            UUID exceptionId,
            UUID workOrderId,
            UUID taskId,
            UUID handlingTaskId,
            String status,
            int occurrenceCount,
            long aggregateVersion,
            UUID resolutionEventId
    ) {
    }

    private record TaskFailureRecovery(
            String sourceTaskType,
            String recoveryType,
            String recoveryRef,
            UUID recoveryEventId,
            Instant recoveredAt
    ) {
    }

    private record TaskFailureExceptionState(
            UUID exceptionId,
            UUID handlingTaskId,
            String status,
            long aggregateVersion
    ) {
    }
}
