package com.serviceos.task.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.operations.api.OpenTaskFailureCommand;
import com.serviceos.operations.api.OpenServiceAssignmentTimeoutCommand;
import com.serviceos.operations.api.OperationalExceptionService;
import com.serviceos.operations.api.ResolveServiceAssignmentTimeoutCommand;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.ScheduleAutomatedTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.task.spi.AutomatedTaskHandler;
import com.serviceos.task.spi.TaskExecutionException;
import com.serviceos.task.spi.TaskExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * E1-07/TX-007：使用真实 PostgreSQL 验证 SKIP LOCKED、租约恢复与执行事实原子落库。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TaskExecutionPostgresIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos")
            .withUsername("serviceos_test")
            .withPassword("serviceos_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    TaskSchedulingService scheduling;

    @Autowired
    TaskExecutionQueue queue;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    OperationalExceptionService operationalExceptions;

    @BeforeEach
    void cleanTables() {
        jdbc.sql("""
                        TRUNCATE TABLE ops_exception_ack_result, ops_operational_exception,
                            tsk_task_reassignment_command_result,
                            tsk_task_execution_guard, tsk_task_assignment, tsk_task_assignment_batch,
                            tsk_human_task_command_result, tsk_task_execution_attempt, tsk_task,
                            rel_outbox_publish_attempt, rel_outbox_event
                        """).update();
        jdbc.sql("TRUNCATE TABLE rel_inbox_record").update();
    }

    @Test
    void sameBusinessKeyAndDigestReplayWhileDigestMutationIsRejected() {
        ScheduleAutomatedTaskCommand command = command("business-1", "a".repeat(64), 3);

        ScheduledTaskView first = scheduling.schedule(command);
        ScheduledTaskView replay = scheduling.schedule(command);

        assertThat(replay.taskId()).isEqualTo(first.taskId());
        assertThat(count("tsk_task")).isEqualTo(1);
        assertThatThrownBy(() -> scheduling.schedule(command("business-1", "b".repeat(64), 3)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_SCHEDULE_CONFLICT));
        assertThat(count("tsk_task")).isEqualTo(1);
    }

    @Test
    void onlyOneWorkerOwnsAnActiveLeaseAndStaleCompletionIsRejected() {
        scheduling.schedule(command("business-lease", "c".repeat(64), 2));

        ClaimedTask first = queue.claimNext("worker-a", Duration.ofSeconds(30)).orElseThrow();
        assertThat(queue.claimNext("worker-b", Duration.ofSeconds(30))).isEmpty();

        jdbc.sql("UPDATE tsk_task SET claim_until = now() - interval '1 second'").update();
        ClaimedTask recovered = queue.claimNext("worker-b", Duration.ofSeconds(30)).orElseThrow();
        assertThat(recovered.attemptNo()).isEqualTo(2);
        assertThat(jdbc.sql("""
                        SELECT result_code FROM tsk_task_execution_attempt WHERE attempt_no = 1
                        """).query(String.class).single()).isEqualTo("LEASE_EXPIRED");

        assertThatThrownBy(() -> queue.resolve(
                first, "worker-a", TaskExecutionOutcome.success("stale-result")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_LEASE_LOST));
    }

    @Test
    void successfulHandlerCommitsTaskAttemptAndOutboxEvent() {
        scheduling.schedule(command("business-success", "d".repeat(64), 3));
        AutomatedTaskHandler handler = handler(context -> TaskExecutionResult.succeeded("remote-result-1"));
        TaskExecutionWorker worker = new TaskExecutionWorker(
                queue, new TaskHandlerRegistry(List.of(handler)), "worker-success", Duration.ofSeconds(30));

        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        assertThat(taskStatus()).isEqualTo("SUCCEEDED");
        assertThat(attemptResult()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event")
                .query(String.class).single()).isEqualTo("task.execution.succeeded");
    }

    @Test
    void retryAtMaximumAttemptsTransitionsToManualIntervention() {
        scheduling.schedule(command("business-max", "e".repeat(64), 1));
        AutomatedTaskHandler handler = handler(context -> {
            throw TaskExecutionException.retryable(
                    "DEPENDENCY_DOWN", Instant.parse("2026-07-14T00:00:00Z"), null);
        });
        TaskExecutionWorker worker = new TaskExecutionWorker(
                queue, new TaskHandlerRegistry(List.of(handler)), "worker-max", Duration.ofSeconds(30));

        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.MANUAL_INTERVENTION);
        assertThat(taskStatus()).isEqualTo("MANUAL_INTERVENTION");
        assertThat(attemptResult()).isEqualTo("FINAL_FAILURE");
        assertThat(jdbc.sql("SELECT last_error_code FROM tsk_task")
                .query(String.class).single()).isEqualTo("TASK_MAX_ATTEMPTS_EXHAUSTED");
    }

    @Test
    void crashOnLastAttemptIsRecoveredToManualInterventionWithoutExtraExecution() {
        scheduling.schedule(command("business-crash", "f".repeat(64), 1));
        queue.claimNext("crashed-worker", Duration.ofSeconds(30)).orElseThrow();
        jdbc.sql("UPDATE tsk_task SET claim_until = now() - interval '1 second'").update();

        assertThat(queue.recoverOneExhaustedLease("recovery-worker")).isTrue();
        assertThat(taskStatus()).isEqualTo("MANUAL_INTERVENTION");
        assertThat(attemptResult()).isEqualTo("LEASE_EXPIRED");
        assertThat(count("tsk_task_execution_attempt")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event")
                .query(String.class).single()).isEqualTo("task.execution.manual-intervention-required");
    }

    @Test
    void finalFailureEventCreatesOneOperationalExceptionAndOneHumanHandlingTask() {
        UUID eventId = UUID.randomUUID();
        UUID sourceTaskId = UUID.randomUUID();
        UUID sourceAttemptId = UUID.randomUUID();
        OpenTaskFailureCommand command = new OpenTaskFailureCommand(
                "tenant-test", eventId, 1, "1".repeat(64), sourceTaskId, sourceAttemptId,
                "test.task", "REMOTE_RESULT_UNKNOWN", "corr-final-failure");

        var first = operationalExceptions.openFromTaskFailure(command);
        var replay = operationalExceptions.openFromTaskFailure(command);

        assertThat(replay.exceptionId()).isEqualTo(first.exceptionId());
        assertThat(first.handlingTaskId()).isNotNull();
        assertThat(count("ops_operational_exception")).isEqualTo(1);
        assertThat(count("tsk_task")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT task_kind FROM tsk_task")
                .query(String.class).single()).isEqualTo("HUMAN");
        assertThat(jdbc.sql("SELECT status FROM tsk_task")
                .query(String.class).single()).isEqualTo("READY");
        assertThat(count("rel_inbox_record")).isEqualTo(1);

        assertThatThrownBy(() -> operationalExceptions.openFromTaskFailure(
                new OpenTaskFailureCommand(
                        "tenant-test", eventId, 1, "2".repeat(64), sourceTaskId, sourceAttemptId,
                        "test.task", "MUTATED", "corr-final-failure")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.EVENT_PAYLOAD_MISMATCH));
    }

    @Test
    void repeatedSagaTimeoutsAggregateOccurrencesAndReuseOneHandlingTask() {
        UUID sagaId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        OpenServiceAssignmentTimeoutCommand first = new OpenServiceAssignmentTimeoutCommand(
                "tenant-test", UUID.randomUUID(), 1, "3".repeat(64), UUID.randomUUID(),
                sagaId, assignmentId, workOrderId, taskId, "TASK_PREPARED", 2,
                "ACTIVATION_SAGA_TIMEOUT", Instant.parse("2026-07-14T01:00:00Z"), "corr-timeout");
        OpenServiceAssignmentTimeoutCommand second = new OpenServiceAssignmentTimeoutCommand(
                "tenant-test", UUID.randomUUID(), 1, "4".repeat(64), UUID.randomUUID(),
                sagaId, assignmentId, workOrderId, taskId, "SERVICE_SWITCHED", 3,
                "ACTIVATION_SAGA_TIMEOUT", Instant.parse("2026-07-14T01:15:00Z"), "corr-timeout");

        var opened = operationalExceptions.openFromServiceAssignmentTimeout(first);
        var repeated = operationalExceptions.openFromServiceAssignmentTimeout(second);

        assertThat(repeated.exceptionId()).isEqualTo(opened.exceptionId());
        assertThat(repeated.handlingTaskId()).isEqualTo(opened.handlingTaskId());
        assertThat(jdbc.sql("SELECT occurrence_count FROM ops_operational_exception")
                .query(Integer.class).single()).isEqualTo(2);
        assertThat(count("tsk_task")).isEqualTo(1);
        assertThat(count("rel_inbox_record")).isEqualTo(2);
    }

    @Test
    void completedSagaResolvesTimeoutExceptionAndCancelsHandlingTaskExactlyOnce() {
        UUID sagaId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        var opened = operationalExceptions.openFromServiceAssignmentTimeout(
                timeoutCommand(sagaId, assignmentId, workOrderId, taskId));
        UUID recoveryEventId = UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-07-14T02:00:00Z");
        ResolveServiceAssignmentTimeoutCommand recovery = new ResolveServiceAssignmentTimeoutCommand(
                "tenant-test", recoveryEventId, 1, "5".repeat(64), sagaId, assignmentId,
                workOrderId, taskId, 4, completedAt, "corr-recovery");

        operationalExceptions.resolveServiceAssignmentTimeout(recovery);
        operationalExceptions.resolveServiceAssignmentTimeout(recovery);

        assertThat(jdbc.sql("""
                        SELECT status, resolution_code, resolution_action_ref, resolution_event_id
                          FROM ops_operational_exception WHERE exception_id = :exceptionId
                        """).param("exceptionId", opened.exceptionId())
                .query((rs, rowNum) -> List.of(
                        rs.getString("status"), rs.getString("resolution_code"),
                        rs.getString("resolution_action_ref"),
                        rs.getObject("resolution_event_id", UUID.class).toString())).single())
                .containsExactly(
                        "RESOLVED", "SERVICE_ASSIGNMENT_ACTIVATION_RECOVERED",
                        "event:service.assignment.activation-completed:" + recoveryEventId,
                        recoveryEventId.toString());
        assertThat(jdbc.sql("""
                        SELECT status, cancellation_reason_code, cancellation_source_event_id
                          FROM tsk_task WHERE task_id = :taskId
                        """).param("taskId", opened.handlingTaskId())
                .query((rs, rowNum) -> List.of(
                        rs.getString("status"), rs.getString("cancellation_reason_code"),
                        rs.getObject("cancellation_source_event_id", UUID.class).toString())).single())
                .containsExactly(
                        "CANCELLED", "SERVICE_ASSIGNMENT_ACTIVATION_RECOVERED",
                        recoveryEventId.toString());
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event ORDER BY created_at, outbox_id")
                .query(String.class).list())
                .containsExactlyInAnyOrder("task.cancelled", "operational.exception.resolved");
        assertThat(count("rel_inbox_record")).isEqualTo(2);

        assertThatThrownBy(() -> operationalExceptions.resolveServiceAssignmentTimeout(
                new ResolveServiceAssignmentTimeoutCommand(
                        "tenant-test", recoveryEventId, 1, "6".repeat(64), sagaId, assignmentId,
                        workOrderId, taskId, 4, completedAt, "corr-recovery")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.EVENT_PAYLOAD_MISMATCH));
    }

    @Test
    void handlingTaskCancellationFailureRollsBackExceptionResolutionAndInbox() {
        UUID sagaId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        var opened = operationalExceptions.openFromServiceAssignmentTimeout(
                timeoutCommand(sagaId, assignmentId, workOrderId, taskId));
        jdbc.sql("UPDATE tsk_task SET business_key = 'mutated' WHERE task_id = :taskId")
                .param("taskId", opened.handlingTaskId()).update();
        UUID recoveryEventId = UUID.randomUUID();

        assertThatThrownBy(() -> operationalExceptions.resolveServiceAssignmentTimeout(
                new ResolveServiceAssignmentTimeoutCommand(
                        "tenant-test", recoveryEventId, 1, "7".repeat(64), sagaId, assignmentId,
                        workOrderId, taskId, 4, Instant.parse("2026-07-14T02:10:00Z"),
                        "corr-recovery-failure")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_SCHEDULE_CONFLICT));

        assertThat(jdbc.sql("SELECT status FROM ops_operational_exception")
                .query(String.class).single()).isEqualTo("OPEN");
        assertThat(jdbc.sql("SELECT status FROM tsk_task")
                .query(String.class).single()).isEqualTo("READY");
        assertThat(count("rel_inbox_record")).isEqualTo(1);
        assertThat(count("rel_outbox_event")).isZero();
    }

    @Test
    void automaticRecoveryCanResolveAcknowledgedExceptionAndPreservesAcknowledgement() {
        UUID sagaId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        var opened = operationalExceptions.openFromServiceAssignmentTimeout(
                timeoutCommand(sagaId, assignmentId, workOrderId, taskId));
        Instant acknowledgedAt = Instant.parse("2026-07-14T01:30:00Z");
        jdbc.sql("""
                        UPDATE ops_operational_exception
                           SET status = 'ACKNOWLEDGED', acknowledged_at = :acknowledgedAt,
                               acknowledged_by = 'operator-1', aggregate_version = 2
                         WHERE exception_id = :exceptionId
                        """).param("acknowledgedAt", timestamptz(acknowledgedAt))
                .param("exceptionId", opened.exceptionId()).update();

        operationalExceptions.resolveServiceAssignmentTimeout(new ResolveServiceAssignmentTimeoutCommand(
                "tenant-test", UUID.randomUUID(), 1, "f".repeat(64), sagaId, assignmentId,
                workOrderId, taskId, 4, Instant.parse("2026-07-14T02:00:00Z"),
                "corr-acknowledged-recovery"));

        assertThat(jdbc.sql("""
                        SELECT status || ':' || acknowledged_by || ':' || aggregate_version
                          FROM ops_operational_exception WHERE exception_id = :exceptionId
                        """).param("exceptionId", opened.exceptionId()).query(String.class).single())
                .isEqualTo("RESOLVED:operator-1:3");
        assertThat(jdbc.sql("""
                        SELECT aggregate_version FROM rel_outbox_event
                         WHERE event_type = 'operational.exception.resolved'
                        """).query(Long.class).single()).isEqualTo(3);
    }

    @Test
    void automaticRecoveryPreservesAlreadyCompletedHumanHandlingFact() {
        UUID sagaId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        var opened = operationalExceptions.openFromServiceAssignmentTimeout(
                timeoutCommand(sagaId, assignmentId, workOrderId, taskId));
        Instant humanCompletedAt = Instant.parse("2026-07-14T01:50:00Z");
        jdbc.sql("""
                        UPDATE tsk_task
                           SET status = 'COMPLETED', claimed_by = 'operator-1',
                               claimed_at = :completedAt, started_at = :completedAt,
                               result_ref = 'resolution://manual', result_digest = :resultDigest,
                               completed_at = :completedAt, updated_at = :completedAt,
                               version = version + 1
                         WHERE task_id = :taskId
                        """)
                .param("completedAt", timestamptz(humanCompletedAt))
                .param("resultDigest", "9".repeat(64))
                .param("taskId", opened.handlingTaskId()).update();

        operationalExceptions.resolveServiceAssignmentTimeout(new ResolveServiceAssignmentTimeoutCommand(
                "tenant-test", UUID.randomUUID(), 1, "a".repeat(64), sagaId, assignmentId,
                workOrderId, taskId, 4, Instant.parse("2026-07-14T02:00:00Z"),
                "corr-completed-human"));

        assertThat(jdbc.sql("SELECT status FROM tsk_task")
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT status FROM ops_operational_exception")
                .query(String.class).single()).isEqualTo("RESOLVED");
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event")
                .query(String.class).list()).containsExactly("operational.exception.resolved");
    }

    @Test
    void normalSagaCompletionWithoutTimeoutOnlyFreezesRecoveryInbox() {
        operationalExceptions.resolveServiceAssignmentTimeout(new ResolveServiceAssignmentTimeoutCommand(
                "tenant-test", UUID.randomUUID(), 1, "b".repeat(64), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 4,
                Instant.parse("2026-07-14T02:00:00Z"), "corr-no-timeout"));

        assertThat(count("ops_operational_exception")).isZero();
        assertThat(count("tsk_task")).isZero();
        assertThat(count("rel_outbox_event")).isZero();
        assertThat(count("rel_inbox_record")).isEqualTo(1);
    }

    @Test
    void terminalCancellationAndResolutionWithoutEvidenceAreRejectedByPostgres() {
        var handling = scheduling.createHandlingTask(new com.serviceos.task.api.CreateHandlingTaskCommand(
                "tenant-test", "operations.test", "exception-1", "payload://exception-1",
                "c".repeat(64), 500, Instant.parse("2026-07-14T01:00:00Z"), "corr-constraint"));
        assertThatThrownBy(() -> jdbc.sql(
                        "UPDATE tsk_task SET status = 'CANCELLED' WHERE task_id = :taskId")
                .param("taskId", handling.taskId()).update())
                .isInstanceOf(DataIntegrityViolationException.class);

        var opened = operationalExceptions.openFromServiceAssignmentTimeout(timeoutCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        assertThatThrownBy(() -> jdbc.sql("""
                        UPDATE ops_operational_exception
                           SET status = 'RESOLVED', resolved_at = now()
                         WHERE exception_id = :exceptionId
                        """).param("exceptionId", opened.exceptionId()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static OpenServiceAssignmentTimeoutCommand timeoutCommand(
            UUID sagaId, UUID assignmentId, UUID workOrderId, UUID taskId) {
        return new OpenServiceAssignmentTimeoutCommand(
                "tenant-test", UUID.randomUUID(), 1, "8".repeat(64), UUID.randomUUID(),
                sagaId, assignmentId, workOrderId, taskId, "SERVICE_SWITCHED", 3,
                "ACTIVATION_SAGA_TIMEOUT", Instant.parse("2026-07-14T01:15:00Z"), "corr-timeout");
    }

    private static ScheduleAutomatedTaskCommand command(String businessKey, String digest, int maxAttempts) {
        return new ScheduleAutomatedTaskCommand(
                "tenant-test", "test.task", businessKey, "payload://" + businessKey,
                digest, 100, Instant.parse("2026-01-01T00:00:00Z"), maxAttempts, "corr-" + businessKey);
    }

    private static AutomatedTaskHandler handler(Executing function) {
        return new AutomatedTaskHandler() {
            @Override
            public String taskType() {
                return "test.task";
            }

            @Override
            public TaskExecutionResult execute(com.serviceos.task.spi.TaskExecutionContext context) throws Exception {
                return function.execute(context);
            }
        };
    }

    private long count(String table) {
        // 表名是测试内固定值，不接受任何外部输入。
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private String taskStatus() {
        return jdbc.sql("SELECT status FROM tsk_task").query(String.class).single();
    }

    private String attemptResult() {
        return jdbc.sql("SELECT result_code FROM tsk_task_execution_attempt")
                .query(String.class).single();
    }

    @FunctionalInterface
    private interface Executing {
        TaskExecutionResult execute(com.serviceos.task.spi.TaskExecutionContext context) throws Exception;
    }
}
