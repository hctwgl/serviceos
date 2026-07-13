package com.serviceos.task.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.operations.api.OpenTaskFailureCommand;
import com.serviceos.operations.api.OperationalExceptionService;
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
                        TRUNCATE TABLE ops_operational_exception,
                            tsk_task_execution_attempt, tsk_task,
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
