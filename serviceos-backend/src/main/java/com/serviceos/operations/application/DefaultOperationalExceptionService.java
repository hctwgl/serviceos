package com.serviceos.operations.application;

import com.serviceos.operations.api.OpenTaskFailureCommand;
import com.serviceos.operations.api.OpenServiceAssignmentTimeoutCommand;
import com.serviceos.operations.api.OperationalExceptionService;
import com.serviceos.operations.api.OperationalExceptionView;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskSchedulingService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
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
    private static final String HANDLING_TASK_TYPE = "operations.resolve-exception";
    private static final String DISPATCH_TIMEOUT_TASK_TYPE = "operations.resolve-dispatch-timeout";

    private final InboxService inbox;
    private final TaskSchedulingService tasks;
    private final JdbcClient jdbc;
    private final Clock clock;

    DefaultOperationalExceptionService(
            InboxService inbox,
            TaskSchedulingService tasks,
            JdbcClient jdbc,
            Clock clock
    ) {
        this.inbox = inbox;
        this.tasks = tasks;
        this.jdbc = jdbc;
        this.clock = clock;
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

        Instant now = clock.instant();
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
                                      status = 'OPEN', resolved_at = NULL
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

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
