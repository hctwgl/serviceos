package com.serviceos.project;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.CreateProjectCommand;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectView;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.application.OutboxQueue;
import com.serviceos.reliability.application.OutboxWorker;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
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

import java.time.LocalDate;
import java.time.Duration;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB-001/003、TX-001/002 的 PostgreSQL 证据。
 *
 * <p>测试只接受真实 PostgreSQL，不用 H2 模拟 JSONB、行锁和唯一约束语义。
 * 没有 Docker/兼容容器运行时时由 Testcontainers 明确跳过。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectCommandPostgresIT {
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
    ProjectCommandService commands;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Flyway flyway;

    @Autowired
    InboxService inbox;

    @Autowired
    OutboxQueue outboxQueue;

    @BeforeEach
    void cleanBusinessTables() {
        jdbc.sql("""
                        TRUNCATE TABLE prj_project, aud_audit_record,
                            rel_outbox_publish_attempt, rel_outbox_event,
                            rel_inbox_record, rel_idempotency_record,
                            auth_role_grant, auth_role_capability, auth_role
                        """)
                .update();
        seedProjectCreateGrant("actor-test");
    }

    @Test
    void createAndReplayCommitOneProjectAuditOutboxAndIdempotencyResult() {
        CommandMetadata context = context("idem-create-project-001");
        CreateProjectCommand command = command("BYD-2026", "比亚迪家充履约项目 2026");

        ProjectView first = commands.create(principal(), context, command);
        ProjectView replay = commands.create(principal(), context, command);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(count("prj_project")).isEqualTo(1);
        assertThat(count("aud_audit_record")).isEqualTo(1);
        assertThat(count("rel_outbox_event")).isEqualTo(1);
        assertThat(count("rel_idempotency_record")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM rel_idempotency_record")
                .query(String.class).single()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.sql("SELECT jsonb_array_length(matched_grant_ids) FROM aud_audit_record")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT authorization_policy_version FROM aud_audit_record")
                .query(String.class).single()).isEqualTo("role-grant-v1");
    }

    @Test
    void reusingIdempotencyKeyWithDifferentPayloadIsRejectedWithoutExtraWrites() {
        CommandMetadata context = context("idem-create-project-002");
        commands.create(principal(), context, command("GAC-2026", "广汽家充 2026"));

        assertThatThrownBy(() -> commands.create(principal(), context, command("GAC-2026-X", "不同请求")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.IDEMPOTENCY_KEY_REUSED));

        assertThat(count("prj_project")).isEqualTo(1);
        assertThat(count("aud_audit_record")).isEqualTo(1);
        assertThat(count("rel_outbox_event")).isEqualTo(1);
    }

    @Test
    void aggregateConflictRollsBackNewIdempotencyAuditAndOutbox() {
        commands.create(principal(), context("idem-create-project-003-a"), command("GEELY-2026", "吉利项目"));

        assertThatThrownBy(() -> commands.create(
                principal(), context("idem-create-project-003-b"), command("GEELY-2026", "重复编码")))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);

        assertThat(count("prj_project")).isEqualTo(1);
        assertThat(count("aud_audit_record")).isEqualTo(1);
        assertThat(count("rel_outbox_event")).isEqualTo(1);
        assertThat(count("rel_idempotency_record")).isEqualTo(1);
    }

    @Test
    void repeatedMigrationIsNoOp() {
        assertThat(flyway.info().applied().length).isEqualTo(9);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void deniedCommandPersistsSecurityAuditOutsideRolledBackBusinessTransaction() {
        CurrentPrincipal noCapability = new CurrentPrincipal(
                "actor-no-grant", "tenant-test", CurrentPrincipal.PrincipalType.USER,
                "test-client", java.util.Set.of("project.create"));

        assertThatThrownBy(() -> commands.create(
                noCapability, context("idem-denied-001"), command("DENIED-2026", "无权限项目")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));

        assertThat(count("prj_project")).isZero();
        assertThat(count("rel_idempotency_record")).isZero();
        assertThat(count("rel_outbox_event")).isZero();
        assertThat(count("aud_audit_record")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT decision_code FROM aud_audit_record")
                .query(String.class).single()).isEqualTo("DENY");
    }

    @Test
    void revokedRoleGrantOverridesStillAssertedTokenCapability() {
        jdbc.sql("""
                        UPDATE auth_role_grant
                           SET revoked_at = now(), revoked_by = 'security-admin',
                               revoke_reason = 'test revocation'
                        """).update();

        assertThatThrownBy(() -> commands.create(
                principal(), context("idem-revoked-001"), command("REVOKED-2026", "撤权项目")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));

        assertThat(count("prj_project")).isZero();
        assertThat(count("aud_audit_record")).isEqualTo(1);
    }

    @Test
    void inboxReplaysSamePayloadAndRejectsDigestMutation() {
        UUID eventId = UUID.randomUUID();
        String digest = "a".repeat(64);

        assertThat(inbox.begin("tenant-test", "projection-worker", eventId, 1, digest).kind())
                .isEqualTo(InboxDecision.Kind.NEW);
        inbox.complete("tenant-test", "projection-worker", eventId, "b".repeat(64));
        assertThat(inbox.begin("tenant-test", "projection-worker", eventId, 1, digest).kind())
                .isEqualTo(InboxDecision.Kind.REPLAY);

        assertThatThrownBy(() -> inbox.begin(
                "tenant-test", "projection-worker", eventId, 1, "c".repeat(64)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.EVENT_PAYLOAD_MISMATCH));
        assertThat(count("rel_inbox_record")).isEqualTo(1);
    }

    @Test
    void outboxWorkerClaimsPublishesAndRecordsAttempt() {
        commands.create(principal(), context("idem-outbox-worker-001"), command("WORKER-2026", "Worker 测试"));
        List<UUID> published = new ArrayList<>();
        OutboxWorker worker = new OutboxWorker(
                outboxQueue, message -> published.add(message.eventId()), Clock.systemUTC(),
                "integration-worker", Duration.ofSeconds(30), 8);

        assertThat(worker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        assertThat(published).hasSize(1);
        assertThat(jdbc.sql("SELECT status FROM rel_outbox_event")
                .query(String.class).single()).isEqualTo("PUBLISHED");
        assertThat(count("rel_outbox_publish_attempt")).isEqualTo(1);
    }

    @Test
    void activeLeasePreventsSecondWorkerFromClaimingTheSameEvent() {
        commands.create(principal(), context("idem-outbox-lease-001"), command("LEASE-2026", "租约测试"));

        var first = outboxQueue.claimNext("worker-a", Duration.ofSeconds(30));
        var second = outboxQueue.claimNext("worker-b", Duration.ofSeconds(30));

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        outboxQueue.markPublished(first.orElseThrow(), "worker-a", Clock.systemUTC().instant());
    }

    @Test
    void expiredLeaseCanBeRecoveredWithSameEventId() {
        commands.create(principal(), context("idem-outbox-recovery-001"), command("RECOVER-2026", "恢复测试"));
        UUID eventId = jdbc.sql("SELECT event_id FROM rel_outbox_event")
                .query(UUID.class).single();
        jdbc.sql("""
                        UPDATE rel_outbox_event
                           SET status = 'CLAIMED', claim_owner = 'crashed-worker',
                               claim_until = now() - interval '1 second', attempt_count = 1
                        """).update();
        List<UUID> republished = new ArrayList<>();
        OutboxWorker recovery = new OutboxWorker(
                outboxQueue, message -> republished.add(message.eventId()), Clock.systemUTC(),
                "recovery-worker", Duration.ofSeconds(30), 8);

        assertThat(recovery.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        assertThat(republished).containsExactly(eventId);
        assertThat(jdbc.sql("SELECT attempt_count FROM rel_outbox_event")
                .query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void exhaustedPublicationAttemptsMoveEventToDeadWithAttemptEvidence() {
        commands.create(principal(), context("idem-outbox-dead-001"), command("DEAD-2026", "死信测试"));
        OutboxWorker worker = new OutboxWorker(
                outboxQueue, message -> { throw new IllegalStateException("broker down"); },
                Clock.systemUTC(), "failing-worker", Duration.ofSeconds(30), 1);

        assertThat(worker.runOnce()).isEqualTo(OutboxWorker.RunResult.FAILED);
        assertThat(jdbc.sql("SELECT status FROM rel_outbox_event")
                .query(String.class).single()).isEqualTo("DEAD");
        assertThat(jdbc.sql("SELECT result_code FROM rel_outbox_publish_attempt")
                .query(String.class).single()).isEqualTo("DEAD");
    }

    private long count(String table) {
        // 表名来自测试内部常量，不接受外部输入；生产代码禁止拼接任意 SQL 标识符。
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private static CommandMetadata context(String key) {
        return new CommandMetadata("corr-" + key, key);
    }

    private static CreateProjectCommand command(String code, String name) {
        return new CreateProjectCommand(code, "client-demo", name, LocalDate.of(2026, 1, 1), null);
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(
                "actor-test", "tenant-test", CurrentPrincipal.PrincipalType.USER,
                "test-client", java.util.Set.of("project.create"));
    }

    private void seedProjectCreateGrant(String principalId) {
        UUID roleId = UUID.fromString("54144fc4-75d0-4457-bf3e-cd65158ff9bc");
        jdbc.sql("""
                        INSERT INTO auth_role (
                            role_id, tenant_id, role_code, role_name, role_status, created_at
                        ) VALUES (
                            :roleId, 'tenant-test', 'project-admin', '项目管理员', 'ACTIVE', now()
                        )
                        """).param("roleId", roleId).update();
        jdbc.sql("""
                        INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                        VALUES (:roleId, 'project.create', now())
                        """).param("roleId", roleId).update();
        jdbc.sql("""
                        INSERT INTO auth_role_grant (
                            grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                            valid_from, source_code, approval_ref, created_at
                        ) VALUES (
                            :grantId, 'tenant-test', :principalId, :roleId, 'TENANT', 'tenant-test',
                            now() - interval '1 day', 'TEST_FIXTURE', 'test-approval', now()
                        )
                        """)
                .param("grantId", UUID.randomUUID())
                .param("principalId", principalId)
                .param("roleId", roleId)
                .update();
    }
}
