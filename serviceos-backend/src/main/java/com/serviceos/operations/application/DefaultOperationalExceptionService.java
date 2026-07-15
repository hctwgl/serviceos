package com.serviceos.operations.application;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * 最终失败消费者：Inbox、异常记录和人工处理 Task 在同一事务提交。
 */
@Service
final class DefaultOperationalExceptionService implements OperationalExceptionService {
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
    private final JdbcClient jdbc;
    private final Clock clock;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;

    DefaultOperationalExceptionService(
            InboxService inbox,
            TaskSchedulingService tasks,
            JdbcClient jdbc,
            Clock clock,
            OutboxAppender outbox,
            ObjectMapper objectMapper
    ) {
        this.inbox = inbox;
        this.tasks = tasks;
        this.jdbc = jdbc;
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
        jdbc.sql("""
                        INSERT INTO ops_operational_exception (
                            exception_id, tenant_id, source_type, source_id, source_attempt_id, source_task_type,
                            category_code, severity_code, error_code, status, correlation_id,
                            opened_at, last_detected_at
                        ) VALUES (
                            :exceptionId, :tenantId, 'TASK', :sourceId, :sourceAttemptId, :sourceTaskType,
                            'AUTOMATION_FINAL_FAILURE', 'P1', :errorCode, 'OPEN', :correlationId,
                            :openedAt, :openedAt
                        )
                        ON CONFLICT (tenant_id, source_type, source_id, source_attempt_id) DO NOTHING
                        """)
                .params(Map.of(
                        "exceptionId", exceptionId,
                        "tenantId", command.tenantId(),
                        "sourceId", command.sourceTaskId().toString(),
                        "sourceAttemptId", command.sourceAttemptId(),
                        "sourceTaskType", command.sourceTaskType(),
                        "errorCode", truncate(command.errorCode(), 100),
                        "correlationId", command.correlationId(),
                        "openedAt", timestamptz(now)))
                .update();

        OperationalExceptionView opened = findBySource(
                command.tenantId(), command.sourceTaskId(), command.sourceAttemptId());
        String handlingPayloadDigest = Sha256.digest(
                opened.exceptionId() + "|" + command.sourceTaskId() + "|" + command.errorCode());
        ScheduledTaskView handlingTask = tasks.createHandlingTask(new CreateHandlingTaskCommand(
                command.tenantId(), HANDLING_TASK_TYPE, opened.exceptionId().toString(),
                "operational-exception:" + opened.exceptionId(), handlingPayloadDigest,
                900, now, command.correlationId()));
        jdbc.sql("""
                        UPDATE ops_operational_exception
                           SET handling_task_id = :handlingTaskId
                         WHERE exception_id = :exceptionId AND handling_task_id IS NULL
                        """)
                .params(Map.of(
                        "handlingTaskId", handlingTask.taskId(),
                        "exceptionId", opened.exceptionId()))
                .update();
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
                int updated = jdbc.sql("""
                                UPDATE ops_operational_exception
                                   SET status='RESOLVED', resolved_at=:resolvedAt,
                                       resolution_code=:resolutionCode,
                                       resolution_action_ref=:actionRef,
                                       resolution_event_id=:resolutionEventId,
                                       aggregate_version=aggregate_version+1
                                 WHERE exception_id=:exceptionId
                                   AND status IN ('OPEN','ACKNOWLEDGED')
                                """)
                        .param("resolvedAt", timestamptz(command.recoveredAt()))
                        .param("resolutionCode", resolutionCode).param("actionRef", actionRef)
                        .param("resolutionEventId", command.eventId())
                        .param("exceptionId", state.exceptionId()).update();
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
        jdbc.sql("""
                        INSERT INTO ops_operational_exception (
                            exception_id, tenant_id, source_type, source_id, source_attempt_id,
                            source_task_type, category_code, severity_code, error_code, status,
                            work_order_id, task_id, correlation_id, opened_at, last_detected_at
                        ) VALUES (
                            :exceptionId, :tenantId, 'SERVICE_ASSIGNMENT_ACTIVATION_SAGA',
                            :sourceId, :sourceAttemptId, :sourceTaskType, 'DISPATCH', 'P1',
                            :errorCode, 'OPEN', :workOrderId, :taskId, :correlationId,
                            :openedAt, :detectedAt
                        )
                        ON CONFLICT (tenant_id, source_type, source_id, source_attempt_id)
                        DO UPDATE SET occurrence_count = ops_operational_exception.occurrence_count + 1,
                                      last_detected_at = GREATEST(
                                          ops_operational_exception.last_detected_at,
                                          EXCLUDED.last_detected_at),
                                      error_code = EXCLUDED.error_code,
                                      status = 'OPEN', resolved_at = NULL,
                                      acknowledged_at = NULL,
                                      acknowledged_by = NULL,
                                      acknowledgement_note = NULL,
                                      resolution_code = NULL,
                                      resolution_action_ref = NULL,
                                      resolution_event_id = NULL,
                                      aggregate_version = ops_operational_exception.aggregate_version + 1
                        """)
                .param("exceptionId", exceptionId).param("tenantId", command.tenantId())
                .param("sourceId", command.sagaId().toString())
                // 现有唯一键要求 occurrence UUID；以 sagaId 聚合同一 saga 的多阶段超时。
                .param("sourceAttemptId", command.sagaId())
                .param("sourceTaskType", DISPATCH_TIMEOUT_TASK_TYPE)
                .param("errorCode", truncate(command.errorCode(), 100))
                .param("workOrderId", command.workOrderId()).param("taskId", command.taskId())
                .param("correlationId", command.correlationId())
                .param("openedAt", timestamptz(now))
                .param("detectedAt", timestamptz(command.detectedAt())).update();

        OperationalExceptionView opened = findSagaTimeout(command.tenantId(), command.sagaId());
        String handlingPayloadDigest = Sha256.digest(
                opened.exceptionId() + "|" + command.sagaId() + "|" + command.errorCode());
        ScheduledTaskView handlingTask = tasks.createHandlingTask(new CreateHandlingTaskCommand(
                command.tenantId(), DISPATCH_TIMEOUT_TASK_TYPE, opened.exceptionId().toString(),
                "service-assignment-saga:" + command.sagaId(), handlingPayloadDigest,
                950, now, command.correlationId()));
        jdbc.sql("""
                        UPDATE ops_operational_exception
                           SET handling_task_id = :handlingTaskId
                         WHERE exception_id = :exceptionId AND handling_task_id IS NULL
                        """)
                .param("handlingTaskId", handlingTask.taskId())
                .param("exceptionId", opened.exceptionId()).update();
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

        SagaTimeoutException state = jdbc.sql("""
                        SELECT exception_id, work_order_id, task_id, handling_task_id,
                               status, occurrence_count, aggregate_version, resolution_event_id
                          FROM ops_operational_exception
                         WHERE tenant_id = :tenantId
                           AND source_type = 'SERVICE_ASSIGNMENT_ACTIVATION_SAGA'
                           AND source_id = :sourceId AND source_attempt_id = :sourceAttemptId
                         FOR UPDATE
                        """)
                .param("tenantId", command.tenantId()).param("sourceId", command.sagaId().toString())
                .param("sourceAttemptId", command.sagaId())
                .query(SagaTimeoutException.class).optional().orElse(null);
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
        int updated = jdbc.sql("""
                        UPDATE ops_operational_exception
                           SET status = 'RESOLVED', resolved_at = :resolvedAt,
                               resolution_code = :resolutionCode,
                               resolution_action_ref = :actionRef,
                               resolution_event_id = :resolutionEventId,
                               aggregate_version = aggregate_version + 1
                         WHERE exception_id = :exceptionId AND status IN ('OPEN', 'ACKNOWLEDGED')
                        """)
                .param("resolvedAt", timestamptz(command.completedAt()))
                .param("resolutionCode", resolutionCode).param("actionRef", actionRef)
                .param("resolutionEventId", command.eventId())
                .param("exceptionId", state.exceptionId()).update();
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
        int inserted = jdbc.sql("""
                        INSERT INTO ops_operational_exception (
                            exception_id, tenant_id, source_type, source_id, source_attempt_id,
                            source_task_type, category_code, severity_code, error_code, status,
                            correlation_id, opened_at, last_detected_at, resolved_at,
                            resolution_code, resolution_action_ref, resolution_event_id)
                        VALUES (
                            :exceptionId, :tenantId, 'TASK', :sourceId, :sourceAttemptId,
                            :sourceTaskType, 'AUTOMATION_FINAL_FAILURE', 'P1', :errorCode, 'RESOLVED',
                            :correlationId, :detectedAt, :detectedAt, :resolvedAt,
                            :resolutionCode, :actionRef, :resolutionEventId)
                        ON CONFLICT (tenant_id, source_type, source_id, source_attempt_id) DO NOTHING
                        """)
                .param("exceptionId", exceptionId).param("tenantId", command.tenantId())
                .param("sourceId", command.sourceTaskId().toString())
                .param("sourceAttemptId", command.sourceAttemptId())
                .param("sourceTaskType", command.sourceTaskType())
                .param("errorCode", truncate(command.errorCode(), 100))
                .param("correlationId", command.correlationId())
                .param("detectedAt", timestamptz(command.detectedAt()))
                .param("resolvedAt", timestamptz(recovery.recoveredAt()))
                .param("resolutionCode", resolutionCode).param("actionRef", actionRef)
                .param("resolutionEventId", recovery.recoveryEventId()).update();
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
        int inserted = jdbc.sql("""
                        INSERT INTO ops_task_failure_recovery (
                            tenant_id, source_task_id, source_task_type, recovery_type,
                            recovery_ref, recovery_event_id, recovered_at, correlation_id)
                        VALUES (
                            :tenantId, :sourceTaskId, :sourceTaskType, :recoveryType,
                            :recoveryRef, :recoveryEventId, :recoveredAt, :correlationId)
                        ON CONFLICT (tenant_id, source_task_id) DO NOTHING
                        """)
                .param("tenantId", command.tenantId()).param("sourceTaskId", sourceTaskId)
                .param("sourceTaskType", command.sourceTaskType())
                .param("recoveryType", command.recoveryType()).param("recoveryRef", command.recoveryRef())
                .param("recoveryEventId", command.eventId())
                .param("recoveredAt", timestamptz(command.recoveredAt()))
                .param("correlationId", command.correlationId()).update();
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
        return jdbc.sql("""
                        SELECT source_task_type, recovery_type, recovery_ref,
                               recovery_event_id, recovered_at
                          FROM ops_task_failure_recovery
                         WHERE tenant_id=:tenantId AND source_task_id=:sourceTaskId
                        """)
                .param("tenantId", tenantId).param("sourceTaskId", sourceTaskId)
                .query((rs, row) -> new TaskFailureRecovery(
                        rs.getString("source_task_type"), rs.getString("recovery_type"),
                        rs.getString("recovery_ref"), rs.getObject("recovery_event_id", UUID.class),
                        rs.getObject("recovered_at", java.time.OffsetDateTime.class).toInstant()))
                .optional().orElse(null);
    }

    private List<TaskFailureExceptionState> findTaskFailureExceptions(
            String tenantId,
            UUID sourceTaskId,
            String sourceTaskType
    ) {
        return jdbc.sql("""
                        SELECT exception_id, handling_task_id, status, aggregate_version
                          FROM ops_operational_exception
                         WHERE tenant_id=:tenantId AND source_type='TASK'
                           AND source_id=:sourceId AND source_task_type=:sourceTaskType
                         ORDER BY opened_at, exception_id
                         FOR UPDATE
                        """)
                .param("tenantId", tenantId).param("sourceId", sourceTaskId.toString())
                .param("sourceTaskType", sourceTaskType)
                .query((rs, row) -> new TaskFailureExceptionState(
                        rs.getObject("exception_id", UUID.class),
                        rs.getObject("handling_task_id", UUID.class),
                        rs.getString("status"), rs.getLong("aggregate_version")))
                .list();
    }

    private void lockTaskFailureStream(String tenantId, UUID sourceTaskId) {
        jdbc.sql("""
                        SELECT 1
                          FROM (SELECT pg_advisory_xact_lock(
                                   hashtextextended(:lockKey, 0))) AS acquired
                        """)
                .param("lockKey", tenantId + "|TASK_FAILURE|" + sourceTaskId)
                .query(Integer.class).single();
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
        return jdbc.sql("""
                        SELECT exception_id, tenant_id, source_id, source_attempt_id, source_task_type,
                               error_code, status, handling_task_id, opened_at
                          FROM ops_operational_exception
                         WHERE tenant_id = :tenantId AND source_type = 'TASK'
                           AND source_id = :sourceId AND source_attempt_id = :sourceAttemptId
                        """)
                .params(Map.of(
                        "tenantId", tenantId,
                        "sourceId", sourceTaskId.toString(),
                        "sourceAttemptId", sourceAttemptId))
                .query((rs, rowNum) -> new OperationalExceptionView(
                        rs.getObject("exception_id", UUID.class), rs.getString("tenant_id"),
                        UUID.fromString(rs.getString("source_id")),
                        rs.getObject("source_attempt_id", UUID.class), rs.getString("source_task_type"),
                        rs.getString("error_code"),
                        rs.getString("status"), rs.getObject("handling_task_id", UUID.class),
                        rs.getTimestamp("opened_at").toInstant()))
                .single();
    }

    private OperationalExceptionView findSagaTimeout(String tenantId, UUID sagaId) {
        return jdbc.sql("""
                        SELECT exception_id, tenant_id, source_id, source_attempt_id, source_task_type,
                               error_code, status, handling_task_id, opened_at
                          FROM ops_operational_exception
                         WHERE tenant_id = :tenantId
                           AND source_type = 'SERVICE_ASSIGNMENT_ACTIVATION_SAGA'
                           AND source_id = :sourceId AND source_attempt_id = :sourceAttemptId
                        """)
                .param("tenantId", tenantId).param("sourceId", sagaId.toString())
                .param("sourceAttemptId", sagaId)
                .query((rs, rowNum) -> new OperationalExceptionView(
                        rs.getObject("exception_id", UUID.class), rs.getString("tenant_id"),
                        UUID.fromString(rs.getString("source_id")),
                        rs.getObject("source_attempt_id", UUID.class), rs.getString("source_task_type"),
                        rs.getString("error_code"), rs.getString("status"),
                        rs.getObject("handling_task_id", UUID.class),
                        rs.getTimestamp("opened_at").toInstant()))
                .single();
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
