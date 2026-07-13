package com.serviceos.project;

import com.serviceos.ServiceOsApplication;
import com.serviceos.project.api.CreateProjectCommand;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
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

import java.time.LocalDate;

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
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
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

    @BeforeEach
    void cleanBusinessTables() {
        jdbc.sql("TRUNCATE TABLE prj_project, aud_audit_record, rel_outbox_event, rel_idempotency_record")
                .update();
    }

    @Test
    void createAndReplayCommitOneProjectAuditOutboxAndIdempotencyResult() {
        CommandContext context = context("idem-create-project-001");
        CreateProjectCommand command = command("BYD-2026", "比亚迪家充履约项目 2026");

        ProjectView first = commands.create(context, command);
        ProjectView replay = commands.create(context, command);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(count("prj_project")).isEqualTo(1);
        assertThat(count("aud_audit_record")).isEqualTo(1);
        assertThat(count("rel_outbox_event")).isEqualTo(1);
        assertThat(count("rel_idempotency_record")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM rel_idempotency_record")
                .query(String.class).single()).isEqualTo("SUCCEEDED");
    }

    @Test
    void reusingIdempotencyKeyWithDifferentPayloadIsRejectedWithoutExtraWrites() {
        CommandContext context = context("idem-create-project-002");
        commands.create(context, command("GAC-2026", "广汽家充 2026"));

        assertThatThrownBy(() -> commands.create(context, command("GAC-2026-X", "不同请求")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.IDEMPOTENCY_KEY_REUSED));

        assertThat(count("prj_project")).isEqualTo(1);
        assertThat(count("aud_audit_record")).isEqualTo(1);
        assertThat(count("rel_outbox_event")).isEqualTo(1);
    }

    @Test
    void aggregateConflictRollsBackNewIdempotencyAuditAndOutbox() {
        commands.create(context("idem-create-project-003-a"), command("GEELY-2026", "吉利项目"));

        assertThatThrownBy(() -> commands.create(
                context("idem-create-project-003-b"), command("GEELY-2026", "重复编码")))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);

        assertThat(count("prj_project")).isEqualTo(1);
        assertThat(count("aud_audit_record")).isEqualTo(1);
        assertThat(count("rel_outbox_event")).isEqualTo(1);
        assertThat(count("rel_idempotency_record")).isEqualTo(1);
    }

    @Test
    void repeatedMigrationIsNoOp() {
        assertThat(flyway.info().applied().length).isEqualTo(3);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private long count(String table) {
        // 表名来自测试内部常量，不接受外部输入；生产代码禁止拼接任意 SQL 标识符。
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private static CommandContext context(String key) {
        return new CommandContext("tenant-test", "actor-test", "corr-" + key, key);
    }

    private static CreateProjectCommand command(String code, String name) {
        return new CreateProjectCommand(code, "client-demo", name, LocalDate.of(2026, 1, 1), null);
    }
}
