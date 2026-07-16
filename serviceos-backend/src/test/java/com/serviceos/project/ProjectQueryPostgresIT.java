package com.serviceos.project;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.CreateProjectCommand;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectQuery;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.project.api.ProjectView;
import com.serviceos.project.api.ReviseProjectScopeRelationsCommand;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M67 项目授权目录的真实 PostgreSQL 证据。范围必须进入 SQL，cursor 必须绑定实时范围与筛选，
 * 详情和历史必须先按 tenant 隔离再鉴权，禁止在无权或跨租户时返回项目事实。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectQueryPostgresIT {
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
    ProjectQueryService queries;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Flyway flyway;

    @BeforeEach
    void cleanAndSeedOperator() {
        jdbc.sql("""
                        TRUNCATE TABLE prj_project, aud_audit_record,
                            rel_outbox_publish_attempt, rel_outbox_event,
                            rel_inbox_record, rel_idempotency_record,
                            auth_role_field_policy, auth_role_grant,
                            auth_role_capability, auth_role CASCADE
                        """).update();
        seedRole("operator", "operator-role", "TENANT", "tenant-test",
                List.of("project.create", "project.reviseScopeRelations", "project.read"));
    }

    @Test
    void tenantWideDirectoryAppliesExactFiltersAndStableKeysetPagination() {
        ProjectView alpha = create("ALPHA", "client-a", "Alpha", LocalDate.of(2026, 1, 1), null,
                List.of("CN-3702"), List.of("network-a"));
        create("BRAVO", "client-a", "Bravo", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30),
                List.of("CN-3100"), List.of("network-b"));
        ProjectView charlie = create("CHARLIE", "client-b", "Charlie", LocalDate.of(2026, 1, 1), null,
                List.of("CN-4403"), List.of("network-c"));

        var first = queries.list(operator(), "corr-m67-page-1",
                new ProjectQuery(null, "DRAFT", null, null, 2));
        var second = queries.list(operator(), "corr-m67-page-2",
                new ProjectQuery(null, "DRAFT", null, first.nextCursor(), 2));

        assertThat(first.items()).extracting(ProjectView::code).containsExactly("ALPHA", "BRAVO");
        assertThat(first.nextCursor()).isNotBlank();
        assertThat(second.items()).extracting(ProjectView::code).containsExactly("CHARLIE");
        assertThat(second.nextCursor()).isNull();
        assertThat(first.items().getFirst().regionCodes()).containsExactly("CN-3702");
        assertThat(first.items().getFirst().networkIds()).containsExactly("network-a");

        var activeClient = queries.list(operator(), "corr-m67-filter",
                new ProjectQuery("client-a", null, LocalDate.of(2026, 7, 1), null, 10));
        assertThat(activeClient.items()).extracting(ProjectView::id).containsExactly(alpha.id());

        assertThatThrownBy(() -> queries.list(operator(), "corr-m67-filter-cursor",
                new ProjectQuery("client-b", "DRAFT", null, first.nextCursor(), 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor");
        assertThatThrownBy(() -> queries.list(operator(), "corr-m67-invalid-status",
                new ProjectQuery(null, "UNKNOWN", null, null, 20)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("status");
        assertThatThrownBy(() -> queries.list(operator(), "corr-m67-invalid-client",
                new ProjectQuery(" client-a", null, null, null, 20)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("clientId");
        assertThatThrownBy(() -> queries.list(operator(), "corr-m67-invalid-limit",
                new ProjectQuery(null, null, null, null, 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("limit");
        assertThat(charlie.clientId()).isEqualTo("client-b");
    }

    @Test
    void projectRegionAndNetworkGrantsResolveAsOneExactUnion() {
        ProjectView direct = create("UNION-A", "client-a", "Direct", LocalDate.of(2026, 1, 1), null,
                List.of("CN-1100"), List.of("network-a"));
        ProjectView regional = create("UNION-B", "client-b", "Regional", LocalDate.of(2026, 1, 1), null,
                List.of("CN-3702"), List.of("network-b"));
        ProjectView network = create("UNION-C", "client-c", "Network", LocalDate.of(2026, 1, 1), null,
                List.of("CN-4403"), List.of("network-target"));
        create("UNION-D", "client-d", "Denied", LocalDate.of(2026, 1, 1), null,
                List.of("CN-5100"), List.of("network-denied"));
        seedRole("union-reader", "union-project-role", "PROJECT", direct.id().toString(),
                List.of("project.read"));
        seedRole("union-reader", "union-region-role", "REGION", "CN-3702", List.of("project.read"));
        seedRole("union-reader", "union-network-role", "NETWORK", "network-target", List.of("project.read"));

        var page = queries.list(principal("union-reader", "tenant-test"), "corr-m67-union",
                new ProjectQuery(null, null, null, null, 20));

        assertThat(page.items()).extracting(ProjectView::id)
                .containsExactly(direct.id(), regional.id(), network.id());
    }

    @Test
    void regionGrantRestrictsListDetailAndImmutableRevisionHistory() {
        ProjectView allowed = create("REGION-A", "client-a", "Region A", LocalDate.of(2026, 1, 1), null,
                List.of("CN-3702"), List.of("network-a"));
        ProjectView denied = create("REGION-B", "client-b", "Region B", LocalDate.of(2026, 1, 1), null,
                List.of("CN-4403"), List.of("network-b"));
        commands.reviseScopeRelations(operator(), metadata("revise-a-1"),
                new ReviseProjectScopeRelationsCommand(allowed.id(), 1, List.of("CN-3702", "CN-3100"),
                        List.of("network-a"), "新增上海服务范围"));
        commands.reviseScopeRelations(operator(), metadata("revise-a-2"),
                new ReviseProjectScopeRelationsCommand(allowed.id(), 2, List.of("CN-3702"),
                        List.of("network-a", "network-c"), "调整服务网点"));
        seedRole("region-reader", "region-reader-role", "REGION", "CN-3702", List.of("project.read"));

        CurrentPrincipal reader = principal("region-reader", "tenant-test");
        var page = queries.list(reader, "corr-m67-region", new ProjectQuery(null, null, null, null, 20));
        assertThat(page.items()).extracting(ProjectView::id).containsExactly(allowed.id());
        assertThat(queries.get(reader, "corr-m67-detail", allowed.id()).project().version()).isEqualTo(3);

        var history1 = queries.listScopeRevisions(reader, "corr-m67-history-1", allowed.id(), null, 1);
        var history2 = queries.listScopeRevisions(
                reader, "corr-m67-history-2", allowed.id(), history1.nextCursor(), 1);
        assertThat(history1.items()).extracting(item -> item.aggregateVersion()).containsExactly(3L);
        assertThat(history2.items()).extracting(item -> item.aggregateVersion()).containsExactly(2L);
        assertThat(history1.items().getFirst().reason()).isEqualTo("调整服务网点");
        assertThat(history2.nextCursor()).isNull();
        assertThatThrownBy(() -> queries.listScopeRevisions(
                operator(), "corr-m67-history-cross-project", denied.id(), history1.nextCursor(), 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cursor");

        assertThatThrownBy(() -> queries.get(reader, "corr-m67-denied-detail", denied.id()))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThat(jdbc.sql("SELECT decision_code FROM aud_audit_record ORDER BY occurred_at DESC LIMIT 1")
                .query(String.class).single()).isEqualTo("DENY");
    }

    @Test
    void cursorCannotSurviveAuthorizationScopeChangeAndCrossTenantLookupIsHidden() {
        create("SCOPE-A", "client-a", "Scope A", LocalDate.of(2026, 1, 1), null,
                List.of("CN-3702"), List.of());
        ProjectView second = create("SCOPE-B", "client-b", "Scope B", LocalDate.of(2026, 1, 1), null,
                List.of("CN-3702"), List.of());
        seedRole("scope-reader", "scope-reader-role", "REGION", "CN-3702", List.of("project.read"));
        CurrentPrincipal reader = principal("scope-reader", "tenant-test");
        var first = queries.list(reader, "corr-m67-scope-1", new ProjectQuery(null, null, null, null, 1));
        assertThat(first.nextCursor()).isNotBlank();

        jdbc.sql("""
                        UPDATE auth_role_grant SET scope_type='PROJECT', scope_ref=:projectId
                         WHERE principal_id='scope-reader'
                        """).param("projectId", second.id().toString()).update();
        assertThatThrownBy(() -> queries.list(reader, "corr-m67-scope-2",
                new ProjectQuery(null, null, null, first.nextCursor(), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor");

        CurrentPrincipal otherTenant = principal("operator", "tenant-other");
        assertThatThrownBy(() -> queries.get(otherTenant, "corr-m67-cross-tenant", second.id()))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void revokedReadGrantFailsClosedAndMigrationRegistersCapabilityAndIndex() {
        create("REVOKED", "client-a", "Revoked", LocalDate.of(2026, 1, 1), null, List.of(), List.of());
        jdbc.sql("""
                        UPDATE auth_role_grant
                           SET revoked_at=now(), revoked_by='security-admin', revoke_reason='test revocation'
                         WHERE principal_id='operator'
                        """).update();

        assertThatThrownBy(() -> queries.list(operator(), "corr-m67-revoked",
                new ProjectQuery(null, null, null, null, 20)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThat(jdbc.sql("SELECT decision_code FROM aud_audit_record ORDER BY occurred_at DESC LIMIT 1")
                .query(String.class).single()).isEqualTo("DENY");
        assertThat(jdbc.sql("SELECT risk_level FROM auth_capability WHERE capability_code='project.read'")
                .query(String.class).single()).isEqualTo("NORMAL");
        assertThat(jdbc.sql("SELECT count(*) FROM pg_indexes WHERE indexname='ix_prj_project_directory_cursor'")
                .query(Long.class).single()).isOne();
        assertThat(flyway.info().applied()).hasSize(80);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private ProjectView create(
            String code, String clientId, String name, LocalDate startsOn, LocalDate endsOn,
            List<String> regions, List<String> networks
    ) {
        return commands.create(operator(), metadata("create-" + code),
                new CreateProjectCommand(code, clientId, name, startsOn, endsOn, regions, networks));
    }

    private void seedRole(
            String principalId, String roleCode, String scopeType, String scopeRef, List<String> capabilities
    ) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO auth_role (
                            role_id, tenant_id, role_code, role_name, role_status, created_at
                        ) VALUES (:roleId, 'tenant-test', :roleCode, :roleCode, 'ACTIVE', now())
                        """).param("roleId", roleId).param("roleCode", roleCode).update();
        for (String capability : capabilities) {
            jdbc.sql("""
                            INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                            VALUES (:roleId, :capability, now())
                            """).param("roleId", roleId).param("capability", capability).update();
        }
        jdbc.sql("""
                        INSERT INTO auth_role_grant (
                            grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                            valid_from, source_code, approval_ref, created_at
                        ) VALUES (
                            :grantId, 'tenant-test', :principalId, :roleId, :scopeType, :scopeRef,
                            now() - interval '1 day', 'TEST_FIXTURE', 'm67-test', now()
                        )
                        """)
                .param("grantId", UUID.randomUUID()).param("principalId", principalId)
                .param("roleId", roleId).param("scopeType", scopeType).param("scopeRef", scopeRef).update();
    }

    private static CurrentPrincipal operator() {
        return principal("operator", "tenant-test");
    }

    private static CurrentPrincipal principal(String principalId, String tenantId) {
        return new CurrentPrincipal(principalId, tenantId, CurrentPrincipal.PrincipalType.USER,
                "m67-test", Set.of());
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-m67-" + key, "idem-m67-" + key);
    }
}
