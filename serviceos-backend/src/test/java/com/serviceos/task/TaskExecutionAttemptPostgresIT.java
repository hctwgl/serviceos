package com.serviceos.task;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskExecutionAttemptQueryService;
import com.serviceos.task.api.TaskExecutionAttemptView;
import org.flywaydb.core.Flyway;
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

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M72：真实 PostgreSQL 证明 Attempt 历史分页、信息边界和逐页实时授权。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TaskExecutionAttemptPostgresIT {
    private static final String TENANT = "tenant-task-attempt-it";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos")
            .withUsername("serviceos_test")
            .withPassword("serviceos_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TaskExecutionAttemptQueryService attempts;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Flyway flyway;

    private UUID automatedTask;
    private UUID humanTask;
    private UUID readerGrant;

    @BeforeEach
    void seed() {
        jdbc.sql("""
                TRUNCATE TABLE aud_audit_record, tsk_task_execution_attempt, tsk_task,
                    auth_role_field_policy, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        automatedTask = task("AUTOMATED", 3);
        humanTask = task("HUMAN", 0);
        attempt(automatedTask, 1, "SUCCEEDED", null, "result://first", null);
        attempt(automatedTask, 2, "RETRYABLE_FAILURE", "REMOTE_TIMEOUT", null,
                Instant.parse("2026-07-16T00:03:00Z"));
        attempt(automatedTask, 3, "RUNNING", null, null, null);
        readerGrant = seedReader("reader");
    }

    @Test
    void returnsSafeAttemptHistoryWithStableDescendingPagination() {
        CurrentPrincipal reader = principal("reader", TENANT);
        var first = attempts.list(reader, "corr-first", automatedTask, null, 2);

        assertThat(first.resourceVersion()).isEqualTo(1);
        assertThat(first.items()).extracting(TaskExecutionAttemptView::attemptNo)
                .containsExactly(3, 2);
        assertThat(first.items().get(1).resultCode()).isEqualTo("RETRYABLE_FAILURE");
        assertThat(first.items().get(1).errorCode()).isEqualTo("REMOTE_TIMEOUT");
        assertThat(first.items().get(1).nextRetryAt())
                .isEqualTo(Instant.parse("2026-07-16T00:03:00Z"));
        assertThat(first.nextCursor()).isNotBlank();

        var second = attempts.list(reader, "corr-second", automatedTask, first.nextCursor(), 2);
        assertThat(second.items()).extracting(TaskExecutionAttemptView::attemptNo)
                .containsExactly(1);
        assertThat(second.items().getFirst().resultRef()).isEqualTo("result://first");
        assertThat(second.nextCursor()).isNull();

        assertThatThrownBy(() -> attempts.list(
                reader, "corr-wrong-task", humanTask, first.nextCursor(), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor");
        assertThatThrownBy(() -> attempts.list(
                reader, "corr-limit", automatedTask, null, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void humanTaskIsExplicitlyEmptyAndEveryPageRechecksAuthorization() {
        CurrentPrincipal reader = principal("reader", TENANT);
        var empty = attempts.list(reader, "corr-human", humanTask, null, 50);
        assertThat(empty.resourceVersion()).isEqualTo(1);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.nextCursor()).isNull();

        var first = attempts.list(reader, "corr-before-revoke", automatedTask, null, 1);
        jdbc.sql("DELETE FROM auth_role_grant WHERE grant_id = :grantId")
                .param("grantId", readerGrant)
                .update();
        assertThatThrownBy(() -> attempts.list(
                reader, "corr-revoked", automatedTask, first.nextCursor(), 1))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThat(jdbc.sql("""
                SELECT decision_code FROM aud_audit_record
                 WHERE correlation_id = 'corr-revoked'
                """).query(String.class).single()).isEqualTo("DENY");
    }

    @Test
    void tenantIsolationHidesTaskAndMigrationBaselineStaysUnchanged() {
        assertThatThrownBy(() -> attempts.list(
                principal("reader", "another-tenant"),
                "corr-cross-tenant",
                automatedTask,
                null,
                50))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("117");
        assertThat(flyway.info().applied()).hasSize(119);
    }

    private UUID task(String kind, int attemptCount) {
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id, tenant_id, task_type, task_kind, business_key, payload_digest,
                    priority, status, next_run_at, attempt_count, max_attempts, correlation_id,
                    version, created_at, updated_at
                ) VALUES (
                    :taskId, :tenantId, 'M72_QUERY', :kind, :businessKey, :digest,
                    500, :status, :now, :attemptCount, 5, 'corr-seed', 1, :now, :now
                )
                """)
                .param("taskId", taskId)
                .param("tenantId", TENANT)
                .param("kind", kind)
                .param("businessKey", "m72:" + taskId)
                .param("digest", "a".repeat(64))
                .param("status", "AUTOMATED".equals(kind) ? "PENDING" : "READY")
                .param("now", java.sql.Timestamp.from(now))
                .param("attemptCount", attemptCount)
                .update();
        return taskId;
    }

    private void attempt(
            UUID taskId,
            int attemptNo,
            String resultCode,
            String errorCode,
            String resultRef,
            Instant nextRetryAt
    ) {
        Instant startedAt = Instant.parse("2026-07-16T00:00:00Z").plusSeconds(attemptNo * 60L);
        jdbc.sql("""
                INSERT INTO tsk_task_execution_attempt (
                    attempt_id, task_id, attempt_no, worker_id, started_at, finished_at,
                    result_code, error_code, result_ref, next_retry_at
                ) VALUES (
                    :attemptId, :taskId, :attemptNo, 'worker-private', :startedAt, :finishedAt,
                    :resultCode, :errorCode, :resultRef, :nextRetryAt
                )
                """)
                .param("attemptId", UUID.randomUUID())
                .param("taskId", taskId)
                .param("attemptNo", attemptNo)
                .param("startedAt", java.sql.Timestamp.from(startedAt))
                .param("finishedAt", "RUNNING".equals(resultCode)
                        ? null : java.sql.Timestamp.from(startedAt.plusSeconds(10)))
                .param("resultCode", resultCode)
                .param("errorCode", errorCode)
                .param("resultRef", resultRef)
                .param("nextRetryAt", nextRetryAt == null ? null : java.sql.Timestamp.from(nextRetryAt))
                .update();
    }

    private UUID seedReader(String principalId) {
        UUID roleId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (
                    role_id, tenant_id, role_code, role_name, role_status, created_at
                ) VALUES (:roleId, :tenantId, 'M72_READER', 'M72 Reader', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, 'task.read', now())
                """).param("roleId", roleId).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at
                ) VALUES (
                    :grantId, :tenantId, :principalId, :roleId, 'TENANT', :tenantId,
                    now() - interval '1 day', 'TEST', 'm72', now()
                )
                """)
                .param("grantId", grantId)
                .param("tenantId", TENANT)
                .param("principalId", principalId)
                .param("roleId", roleId)
                .update();
        return grantId;
    }

    private static CurrentPrincipal principal(String principalId, String tenantId) {
        return new CurrentPrincipal(
                principalId, tenantId, CurrentPrincipal.PrincipalType.USER, "m72", Set.of());
    }
}
