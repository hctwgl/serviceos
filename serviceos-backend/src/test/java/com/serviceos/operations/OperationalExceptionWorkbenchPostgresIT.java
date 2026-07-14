package com.serviceos.operations;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.operations.api.AcknowledgeOperationalExceptionCommand;
import com.serviceos.operations.api.OperationalExceptionQuery;
import com.serviceos.operations.api.OperationalExceptionWorkbenchService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
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
import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/** M29：真实 PostgreSQL 验证 MyBatis 动态查询、租户隔离和确认动作事务闭环。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OperationalExceptionWorkbenchPostgresIT {
    private static final String TENANT = "tenant-ops-it";
    private static final String OTHER_TENANT = "tenant-ops-other";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos").withUsername("serviceos_test").withPassword("serviceos_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired OperationalExceptionWorkbenchService workbench;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE ops_exception_ack_result, ops_operational_exception,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        seedGrant(TENANT, "ops-user");
    }

    @Test
    void filtersStableCursorAndDetailNeverCrossTenant() {
        Instant openedAt = Instant.parse("2026-07-14T03:00:00Z");
        UUID first = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000001");
        insert(first, TENANT, "DISPATCH", "P1", openedAt);
        insert(second, TENANT, "AUTOMATION_FINAL_FAILURE", "P2", openedAt);
        insert(UUID.randomUUID(), OTHER_TENANT, "DISPATCH", "P1", openedAt.plusSeconds(60));

        var firstPage = workbench.list(principal(), "corr-list-1",
                new OperationalExceptionQuery(null, null, null, null, null, null, 1));
        var secondPage = workbench.list(principal(), "corr-list-2",
                new OperationalExceptionQuery(null, null, null, null, null, firstPage.nextCursor(), 1));
        var filtered = workbench.list(principal(), "corr-filter",
                new OperationalExceptionQuery("open", "dispatch", "p1", null, null, null, 10));

        assertThat(firstPage.items()).extracting(item -> item.exceptionId()).containsExactly(first);
        assertThat(secondPage.items()).extracting(item -> item.exceptionId()).containsExactly(second);
        assertThat(secondPage.nextCursor()).isNull();
        assertThat(filtered.items()).extracting(item -> item.exceptionId()).containsExactly(first);
        assertThat(workbench.get(principal(), "corr-detail", first).allowedActions())
                .containsExactly("ACKNOWLEDGE");
        assertThatThrownBy(() -> workbench.get(
                principal(), "corr-cross-tenant",
                jdbc.sql("SELECT exception_id FROM ops_operational_exception WHERE tenant_id = :tenant")
                        .param("tenant", OTHER_TENANT).query(UUID.class).single()))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void acknowledgeFreezesReplayAndWritesAuditOutboxAtomically() {
        UUID exceptionId = UUID.randomUUID();
        insert(exceptionId, TENANT, "DISPATCH", "P1", Instant.parse("2026-07-14T04:00:00Z"));
        CommandMetadata metadata = new CommandMetadata("corr-ack", "ack-001");
        var command = new AcknowledgeOperationalExceptionCommand(exceptionId, 1, "已通知值班负责人");

        var acknowledged = workbench.acknowledge(principal(), metadata, command);
        var replay = workbench.acknowledge(principal(), metadata, command);

        assertThat(replay).isEqualTo(acknowledged);
        assertThat(acknowledged.status()).isEqualTo("ACKNOWLEDGED");
        assertThat(acknowledged.aggregateVersion()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT status || ':' || aggregate_version FROM ops_operational_exception")
                .query(String.class).single()).isEqualTo("ACKNOWLEDGED:2");
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event").query(String.class).single())
                .isEqualTo("operational.exception.acknowledged");
        assertThat(jdbc.sql("SELECT action_name FROM aud_audit_record").query(String.class).single())
                .isEqualTo("OPERATIONAL_EXCEPTION_ACKNOWLEDGE");
        assertThat(jdbc.sql("SELECT count(*) FROM ops_exception_ack_result").query(Long.class).single())
                .isEqualTo(1);

        assertThatThrownBy(() -> workbench.acknowledge(
                principal(), metadata,
                new AcknowledgeOperationalExceptionCommand(exceptionId, 1, "变造请求")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    void staleVersionCannotAcknowledgeTwice() {
        UUID exceptionId = UUID.randomUUID();
        insert(exceptionId, TENANT, "DISPATCH", "P1", Instant.now());
        workbench.acknowledge(principal(), new CommandMetadata("corr-first", "ack-first"),
                new AcknowledgeOperationalExceptionCommand(exceptionId, 1, null));

        assertThatThrownBy(() -> workbench.acknowledge(
                principal(), new CommandMetadata("corr-stale", "ack-stale"),
                new AcknowledgeOperationalExceptionCommand(exceptionId, 1, null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VERSION_CONFLICT));
    }

    private void insert(UUID id, String tenant, String category, String severity, Instant openedAt) {
        jdbc.sql("""
                INSERT INTO ops_operational_exception (
                    exception_id, tenant_id, source_type, source_id, source_attempt_id,
                    source_task_type, category_code, severity_code, error_code, status,
                    correlation_id, opened_at, last_detected_at
                ) VALUES (
                    :id, :tenant, 'TEST', :sourceId, :attemptId,
                    'operations.test', :category, :severity, 'TEST_FAILURE', 'OPEN',
                    'corr-fixture', :openedAt, :openedAt
                )
                """).param("id", id).param("tenant", tenant).param("sourceId", id.toString())
                .param("attemptId", UUID.randomUUID()).param("category", category)
                .param("severity", severity).param("openedAt", timestamptz(openedAt)).update();
    }

    private void seedGrant(String tenant, String actor) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:role, :tenant, 'ops-handler', '异常处理人', 'ACTIVE', now())
                """).param("role", roleId).param("tenant", tenant).update();
        for (String capability : Set.of(
                "operations.exception.read", "operations.exception.acknowledge")) {
            jdbc.sql("""
                    INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                    VALUES (:role, :capability, now())
                    """).param("role", roleId).param("capability", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at
                ) VALUES (
                    :grant, :tenant, :actor, :role, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M29-TEST', now()
                )
                """).param("grant", UUID.randomUUID()).param("tenant", tenant)
                .param("actor", actor).param("role", roleId).update();
    }

    private CurrentPrincipal principal() {
        return new CurrentPrincipal(
                "ops-user", TENANT, CurrentPrincipal.PrincipalType.USER, "ops-it", Set.of());
    }
}
