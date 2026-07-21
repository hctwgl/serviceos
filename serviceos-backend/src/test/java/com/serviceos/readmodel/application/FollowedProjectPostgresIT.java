package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.CreateProjectCommand;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectView;
import com.serviceos.readmodel.api.FollowedProjectCommandService;
import com.serviceos.readmodel.api.FollowedProjectItem;
import com.serviceos.readmodel.api.FollowedProjectPage;
import com.serviceos.readmodel.api.FollowedProjectQueryService;
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
 * M401 Admin 关注项目：follow 幂等、列表授权过滤、失权清理、跨主体隔离。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FollowedProjectPostgresIT {
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

    @Autowired FollowedProjectQueryService queries;
    @Autowired FollowedProjectCommandService commands;
    @Autowired ProjectCommandService projects;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_followed_project, aud_audit_record,
                    rel_outbox_publish_attempt, rel_outbox_event, rel_idempotency_record,
                    prj_project_scope_revision, prj_project_network, prj_project_region, prj_project,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        seedRole("operator", "follow-op", "TENANT", "tenant-test",
                List.of("project.create", "project.read"));
        assertThat(flyway.info().current().getVersion().getVersion()).isGreaterThanOrEqualTo("139");
    }

    @Test
    void followIsIdempotentAndListReturnsAuthorizedProjects() {
        ProjectView alpha = create("FOLLOW-A", "client-a", "Alpha");
        ProjectView bravo = create("FOLLOW-B", "client-b", "Bravo");

        FollowedProjectItem first = commands.follow(operator(), "c1", "ADMIN", alpha.id(), null);
        FollowedProjectItem second = commands.follow(operator(), "c2", "ADMIN", bravo.id(), "自定义标签");
        FollowedProjectItem again = commands.follow(operator(), "c3", "ADMIN", alpha.id(), null);

        assertThat(first.projectId()).isEqualTo(alpha.id());
        assertThat(first.deepLink()).isEqualTo("/projects/" + alpha.id());
        assertThat(second.displayRef()).isEqualTo("自定义标签");
        assertThat(again.followedAt()).isAfterOrEqualTo(first.followedAt());
        assertThat(queries.isFollowed(operator(), "c4", "ADMIN", alpha.id())).isTrue();

        FollowedProjectPage page = queries.list(operator(), "c5", "ADMIN", 10);
        assertThat(page.items()).extracting(FollowedProjectItem::projectId)
                .containsExactly(alpha.id(), bravo.id());
        // 仅 project.read 时角标 soft-gate 为 null，不伪造 0
        assertThat(page.items()).allSatisfy(item -> {
            assertThat(item.activeWorkOrderCount()).isNull();
            assertThat(item.openReviewCount()).isNull();
            assertThat(item.openCorrectionCount()).isNull();
            assertThat(item.slaBreachedCount()).isNull();
            assertThat(item.openTodoCount()).isNull();
        });

        long rows = jdbc.sql("""
                SELECT count(*) FROM rdm_followed_project
                 WHERE tenant_id='tenant-test' AND principal_id='operator'
                """).query(Long.class).single();
        assertThat(rows).isEqualTo(2L);
    }

    @Test
    void listEnrichesZeroBadgesWhenReadCapabilitiesPresent() {
        ProjectView project = create("FOLLOW-BADGE", "client-a", "Badge Project");
        commands.follow(operator(), "c-badge-1", "ADMIN", project.id(), null);

        seedRole(
                "badge-reader",
                "follow-badge",
                "TENANT",
                "tenant-test",
                List.of(
                        "project.read",
                        "workOrder.read",
                        "evidence.review",
                        "evidence.read",
                        "sla.read"));
        CurrentPrincipal reader = principal("badge-reader");
        commands.follow(reader, "c-badge-2", "ADMIN", project.id(), null);

        FollowedProjectPage page = queries.list(reader, "c-badge-3", "ADMIN", 10);
        assertThat(page.items()).hasSize(1);
        FollowedProjectItem item = page.items().getFirst();
        assertThat(item.activeWorkOrderCount()).isZero();
        assertThat(item.activeWorkOrderCountTruncated()).isFalse();
        assertThat(item.openReviewCount()).isZero();
        assertThat(item.openReviewCountTruncated()).isFalse();
        assertThat(item.openCorrectionCount()).isZero();
        assertThat(item.openCorrectionCountTruncated()).isFalse();
        assertThat(item.slaBreachedCount()).isZero();
        assertThat(item.slaBreachedCountTruncated()).isFalse();
        assertThat(item.openTodoCount()).isZero();
    }

    @Test
    void listDropsUnauthorizedProjectsAndUnfollowIsIdempotent() {
        ProjectView allowed = create("FOLLOW-OK", "client-a", "Allowed");
        ProjectView denied = create("FOLLOW-DENY", "client-b", "Denied");
        commands.follow(operator(), "c1", "ADMIN", allowed.id(), null);
        commands.follow(operator(), "c2", "ADMIN", denied.id(), null);

        seedRole("scoped", "follow-scoped", "PROJECT", allowed.id().toString(), List.of("project.read"));
        CurrentPrincipal scoped = principal("scoped");

        FollowedProjectPage page = queries.list(scoped, "c3", "ADMIN", 20);
        // scoped 主体没有关注行——关注归属个人，需先 follow
        assertThat(page.items()).isEmpty();

        commands.follow(scoped, "c4", "ADMIN", allowed.id(), null);
        assertThatThrownBy(() -> commands.follow(scoped, "c5", "ADMIN", denied.id(), null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));

        // 直接插入失权关注行，列表应清理
        jdbc.sql("""
                INSERT INTO rdm_followed_project (
                    tenant_id, principal_id, portal, project_id, display_ref, followed_at, created_at
                ) VALUES (
                    'tenant-test', 'scoped', 'ADMIN', :projectId, '幽灵项目', now(), now()
                )
                """).param("projectId", denied.id()).update();

        FollowedProjectPage cleaned = queries.list(scoped, "c6", "ADMIN", 20);
        assertThat(cleaned.items()).extracting(FollowedProjectItem::projectId)
                .containsExactly(allowed.id());
        Long ghost = jdbc.sql("""
                SELECT count(*) FROM rdm_followed_project
                 WHERE principal_id='scoped' AND project_id=:projectId
                """).param("projectId", denied.id()).query(Long.class).single();
        assertThat(ghost).isZero();

        commands.unfollow(scoped, "c7", "ADMIN", allowed.id());
        commands.unfollow(scoped, "c8", "ADMIN", allowed.id());
        assertThat(queries.isFollowed(scoped, "c9", "ADMIN", allowed.id())).isFalse();
    }

    private ProjectView create(String code, String clientId, String name) {
        return projects.create(operator(), metadata("create-" + code),
                new CreateProjectCommand(
                        code, clientId, name, LocalDate.of(2026, 1, 1), null,
                        List.of("CN-3702"), List.of()));
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
                    now() - interval '1 day', 'TEST_FIXTURE', 'm401-test', now()
                )
                """)
                .param("grantId", UUID.randomUUID()).param("principalId", principalId)
                .param("roleId", roleId).param("scopeType", scopeType).param("scopeRef", scopeRef)
                .update();
    }

    private static CurrentPrincipal operator() {
        return principal("operator");
    }

    private static CurrentPrincipal principal(String principalId) {
        return new CurrentPrincipal(
                principalId, "tenant-test", CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }
}
