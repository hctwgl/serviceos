package com.serviceos.operations.application;

import com.serviceos.operations.api.OpenTaskFailureCommand;
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
    private static final String HANDLING_TASK_TYPE = "operations.resolve-exception";

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
                            category_code, severity_code, error_code, status, correlation_id, opened_at
                        ) VALUES (
                            :exceptionId, :tenantId, 'TASK', :sourceId, :sourceAttemptId, :sourceTaskType,
                            'AUTOMATION_FINAL_FAILURE', 'P1', :errorCode, 'OPEN', :correlationId, :openedAt
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

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
