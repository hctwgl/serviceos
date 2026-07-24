package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.SavedView;
import com.serviceos.readmodel.api.SavedViewCommandService;
import com.serviceos.readmodel.api.SavedViewFilterAst;
import com.serviceos.readmodel.api.SavedViewFilterClause;
import com.serviceos.readmodel.api.SavedViewPage;
import com.serviceos.readmodel.api.SavedViewQueryService;
import com.serviceos.readmodel.api.SavedViewVisibility;
import com.serviceos.shared.BusinessProblem;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M191 Admin 共享 SavedView：ROLE/TENANT 可见性、能力门禁、收回共享与跨租户隔离。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SharedSavedViewPostgresIT {
    private static final String TENANT_A = "tenant-share-a";
    private static final String TENANT_B = "tenant-share-b";
    private static final String OWNER = "019f81a1-1111-7f8c-9505-36fe5c0e8801";
    private static final String VIEWER_WITH_ROLE = "019f81a1-2222-7f8c-9505-36fe5c0e8802";
    private static final String VIEWER_NO_ROLE = "019f81a1-3333-7f8c-9505-36fe5c0e8803";
    private static final String OTHER_TENANT = "019f81a1-4444-7f8c-9505-36fe5c0e8804";

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

    @Autowired SavedViewQueryService queries;
    @Autowired SavedViewCommandService commands;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE TABLE rdm_saved_view CASCADE").update();
        jdbc.sql("""
                TRUNCATE TABLE auth_delegation_capability, auth_delegation, auth_role_grant_event,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("151");
        assertThat(flyway.info().applied()).hasSize(153);
        assertThat(jdbc.sql(
                        "SELECT risk_level FROM auth_capability WHERE capability_code='preference.shareSavedView'")
                .query(String.class).single()).isEqualTo("HIGH");
    }

    @Test
    void shareTenantVisibleToPeerAndUnshare() {
        UUID shareRole = seedRoleWithCapability(TENANT_A, "share-ops", "preference.shareSavedView");
        seedGrant(TENANT_A, OWNER, shareRole);

        SavedView created = commands.create(
                actor(OWNER, TENANT_A), "c1", "ADMIN.TASK.QUEUE", "租户共享视图", 1,
                filter("status", "READY"), null, null, false);

        SavedView shared = commands.share(
                actor(OWNER, TENANT_A), "c-share", created.id(), created.aggregateVersion(),
                SavedViewVisibility.TENANT, null);
        assertThat(shared.visibility()).isEqualTo(SavedViewVisibility.TENANT);
        assertThat(shared.sharedScopeRef()).isNull();

        SavedViewPage peer = queries.list(actor(VIEWER_NO_ROLE, TENANT_A), "c-list", "ADMIN.TASK.QUEUE");
        assertThat(peer.items()).extracting(SavedView::id).contains(shared.id());
        assertThat(peer.items().stream().filter(v -> v.id().equals(shared.id())).findFirst())
                .get()
                .extracting(SavedView::ownerPrincipalId, SavedView::visibility)
                .containsExactly(OWNER, SavedViewVisibility.TENANT);

        assertThat(queries.list(actor(OTHER_TENANT, TENANT_B), "c-x", "ADMIN.TASK.QUEUE").items())
                .isEmpty();

        SavedView privateAgain = commands.share(
                actor(OWNER, TENANT_A), "c-unshare", shared.id(), shared.aggregateVersion(),
                SavedViewVisibility.PRIVATE, null);
        assertThat(privateAgain.visibility()).isEqualTo(SavedViewVisibility.PRIVATE);
        assertThat(queries.list(actor(VIEWER_NO_ROLE, TENANT_A), "c-list2", "ADMIN.TASK.QUEUE").items())
                .extracting(SavedView::id)
                .doesNotContain(shared.id());
    }

    @Test
    void shareRoleVisibleOnlyWithMatchingGrant() {
        UUID shareCapRole = seedRoleWithCapability(TENANT_A, "share-admin", "preference.shareSavedView");
        seedGrant(TENANT_A, OWNER, shareCapRole);
        UUID targetRole = seedRoleWithCapability(TENANT_A, "queue-reader", "task.read");
        seedGrant(TENANT_A, VIEWER_WITH_ROLE, targetRole);

        SavedView created = commands.create(
                actor(OWNER, TENANT_A), "c1", "ADMIN.TASK.QUEUE", "角色共享视图", 1,
                filter("status", "CLAIMED"), null, null, false);

        SavedView shared = commands.share(
                actor(OWNER, TENANT_A), "c-role", created.id(), created.aggregateVersion(),
                SavedViewVisibility.ROLE, targetRole.toString());
        assertThat(shared.visibility()).isEqualTo(SavedViewVisibility.ROLE);
        assertThat(shared.sharedScopeRef()).isEqualTo(targetRole.toString());

        assertThat(queries.list(actor(VIEWER_WITH_ROLE, TENANT_A), "c-yes", "ADMIN.TASK.QUEUE").items())
                .extracting(SavedView::id)
                .contains(shared.id());
        assertThat(queries.list(actor(VIEWER_NO_ROLE, TENANT_A), "c-no", "ADMIN.TASK.QUEUE").items())
                .extracting(SavedView::id)
                .doesNotContain(shared.id());
        // owner 始终可见自己的共享视图
        assertThat(queries.list(actor(OWNER, TENANT_A), "c-own", "ADMIN.TASK.QUEUE").items())
                .extracting(SavedView::id)
                .contains(shared.id());
    }

    @Test
    void shareWithoutCapabilityDenied() {
        SavedView created = commands.create(
                actor(OWNER, TENANT_A), "c1", "ADMIN.TASK.QUEUE", "无能力分享", 1,
                filter("status", "READY"), null, null, false);

        assertThatThrownBy(() -> commands.share(
                actor(OWNER, TENANT_A), "c-deny", created.id(), created.aggregateVersion(),
                SavedViewVisibility.TENANT, null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));

        assertThat(jdbc.sql("SELECT visibility FROM rdm_saved_view WHERE saved_view_id = :id")
                .param("id", created.id())
                .query(String.class)
                .single()).isEqualTo("PRIVATE");
    }

    @Test
    void ownerCanUnshareWithoutShareCapability() {
        UUID shareRole = seedRoleWithCapability(TENANT_A, "share-ops", "preference.shareSavedView");
        seedGrant(TENANT_A, OWNER, shareRole);
        SavedView created = commands.create(
                actor(OWNER, TENANT_A), "c1", "ADMIN.TASK.QUEUE", "先分享后撤权", 1,
                filter("status", "READY"), null, null, false);
        SavedView shared = commands.share(
                actor(OWNER, TENANT_A), "c-share", created.id(), created.aggregateVersion(),
                SavedViewVisibility.TENANT, null);

        jdbc.sql("DELETE FROM auth_role_grant WHERE principal_id = :p AND tenant_id = :t")
                .param("p", OWNER)
                .param("t", TENANT_A)
                .update();

        SavedView unshared = commands.share(
                actor(OWNER, TENANT_A), "c-unshare", shared.id(), shared.aggregateVersion(),
                SavedViewVisibility.PRIVATE, null);
        assertThat(unshared.visibility()).isEqualTo(SavedViewVisibility.PRIVATE);
    }

    @Test
    void foreignOwnerShareReturnsNotFound() {
        UUID shareRole = seedRoleWithCapability(TENANT_A, "share-ops", "preference.shareSavedView");
        seedGrant(TENANT_A, VIEWER_WITH_ROLE, shareRole);
        SavedView created = commands.create(
                actor(OWNER, TENANT_A), "c1", "ADMIN.TASK.QUEUE", "他人视图", 1,
                filter("status", "READY"), null, null, false);

        assertThatThrownBy(() -> commands.share(
                actor(VIEWER_WITH_ROLE, TENANT_A), "c-x", created.id(), 1L,
                SavedViewVisibility.TENANT, null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    private UUID seedRoleWithCapability(String tenantId, String roleCode, String capability) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (
                    role_id, tenant_id, role_code, role_name, role_status, role_kind, aggregate_version, created_at, updated_at
                ) VALUES (
                    :roleId, :tenantId, :code, :code, 'ACTIVE', 'TENANT', 1, now(), now()
                )
                """)
                .param("roleId", roleId)
                .param("tenantId", tenantId)
                .param("code", roleCode)
                .update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, :capability, now())
                """)
                .param("roleId", roleId)
                .param("capability", capability)
                .update();
        return roleId;
    }

    private void seedGrant(String tenantId, String principalId, UUID roleId) {
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, grant_status, grant_effect, created_at, updated_at, aggregate_version
                ) VALUES (
                    :grantId, :tenantId, :principalId, :roleId, 'TENANT', :tenantId,
                    now() - interval '1 day', 'TEST_FIXTURE', 'ACTIVE', 'ALLOW', now(), now(), 1
                )
                """)
                .param("grantId", UUID.randomUUID())
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("roleId", roleId)
                .update();
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenantId, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = auth_tenant_grant_generation.generation + 1,
                    updated_at = now()
                """)
                .param("tenantId", tenantId)
                .update();
    }

    private static SavedViewFilterAst filter(String field, String value) {
        return new SavedViewFilterAst(List.of(new SavedViewFilterClause(field, "EQ", value)));
    }

    private static CurrentPrincipal actor(String principalId, String tenantId) {
        return new CurrentPrincipal(
                principalId, tenantId, CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
    }
}
