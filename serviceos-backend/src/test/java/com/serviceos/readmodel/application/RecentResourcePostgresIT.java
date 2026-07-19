package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.RecentResourceCommandService;
import com.serviceos.readmodel.api.RecentResourceItem;
import com.serviceos.readmodel.api.RecentResourcePage;
import com.serviceos.readmodel.api.RecentResourceQueryService;
import com.serviceos.readmodel.api.RecentResourceTouch;
import com.serviceos.readmodel.api.RecentResourceType;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M193 Admin 最近访问：touch upsert、列表排序、失权过滤、跨租户隔离、上限裁剪。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RecentResourcePostgresIT {
    private static final String TENANT_A = "tenant-recent-a";
    private static final String TENANT_B = "tenant-recent-b";
    private static final String CLIENT = "admin-web";
    private static final String PRINCIPAL_A = "019f83a0-aaaa-7f8c-9505-36fe5c0e8801";
    private static final String PRINCIPAL_B = "019f83a0-bbbb-7f8c-9505-36fe5c0e8802";

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

    @Autowired RecentResourceQueryService queries;
    @Autowired RecentResourceCommandService commands;
    @Autowired WorkOrderCommandService workOrders;
    @Autowired ConfigurationService configurations;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_recent_resource,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record,
                    wo_work_order, cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("131");
        assertThat(flyway.info().applied()).hasSize(133);
    }

    @Test
    void touchUpsertsAndListOrdersByLastVisited() {
        seedRole(TENANT_A, PRINCIPAL_A, List.of("workOrder.read"));
        UUID older = seedWorkOrder(TENANT_A, "RECENT-OLD");
        UUID newer = seedWorkOrder(TENANT_A, "RECENT-NEW");

        commands.touch(actor(PRINCIPAL_A, TENANT_A), "c-1", "ADMIN",
                new RecentResourceTouch(RecentResourceType.WORK_ORDER, older.toString(),
                        "ADMIN.WORKORDER.WORKSPACE", "RECENT-OLD"));
        commands.touch(actor(PRINCIPAL_A, TENANT_A), "c-2", "ADMIN",
                new RecentResourceTouch(RecentResourceType.WORK_ORDER, newer.toString(),
                        "ADMIN.WORKORDER.WORKSPACE", "RECENT-NEW"));

        // 再次 touch older → 应排到最前
        RecentResourceItem retouched = commands.touch(actor(PRINCIPAL_A, TENANT_A), "c-3", "ADMIN",
                new RecentResourceTouch(RecentResourceType.WORK_ORDER, older.toString(),
                        "ADMIN.WORKORDER.WORKSPACE", "RECENT-OLD"));
        assertThat(retouched.resourceId()).isEqualTo(older.toString());

        RecentResourcePage page = queries.list(actor(PRINCIPAL_A, TENANT_A), "c-list", "ADMIN", null);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).resourceId()).isEqualTo(older.toString());
        assertThat(page.items().get(0).deepLink()).isEqualTo("/work-orders/" + older);
        assertThat(page.items().get(1).resourceId()).isEqualTo(newer.toString());

        long rowCount = jdbc.sql("""
                SELECT count(*) FROM rdm_recent_resource
                 WHERE tenant_id=:t AND principal_id=:p
                """).param("t", TENANT_A).param("p", PRINCIPAL_A)
                .query(Long.class).single();
        assertThat(rowCount).isEqualTo(2L);
    }

    @Test
    void unauthorizedItemIsFilteredAndDeletedOnRead() {
        seedRole(TENANT_A, PRINCIPAL_A, List.of("workOrder.read"));
        UUID visible = seedWorkOrder(TENANT_A, "RECENT-OK");
        commands.touch(actor(PRINCIPAL_A, TENANT_A), "c-ok", "ADMIN",
                new RecentResourceTouch(RecentResourceType.WORK_ORDER, visible.toString(), null, "RECENT-OK"));

        UUID ghost = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-17T10:00:00Z");
        jdbc.sql("""
                INSERT INTO rdm_recent_resource (
                    tenant_id, principal_id, portal, resource_type, resource_id,
                    page_id, display_ref, last_visited_at, created_at
                ) VALUES (
                    :tenant, :principal, 'ADMIN', 'WORK_ORDER', :resourceId,
                    null, 'GHOST', :visited, :visited
                )
                """).param("tenant", TENANT_A).param("principal", PRINCIPAL_A)
                .param("resourceId", ghost.toString())
                .param("visited", java.sql.Timestamp.from(now))
                .update();

        RecentResourcePage page = queries.list(actor(PRINCIPAL_A, TENANT_A), "c-filter", "ADMIN", null);
        assertThat(page.items()).extracting(RecentResourceItem::resourceId)
                .containsExactly(visible.toString());

        Long ghosts = jdbc.sql("""
                SELECT count(*) FROM rdm_recent_resource
                 WHERE resource_id = :id
                """).param("id", ghost.toString()).query(Long.class).single();
        assertThat(ghosts).isZero();
    }

    @Test
    void crossTenantAndPrincipalIsolation() {
        seedRole(TENANT_A, PRINCIPAL_A, List.of("workOrder.read"));
        seedRole(TENANT_A, PRINCIPAL_B, List.of("workOrder.read"));
        seedRole(TENANT_B, PRINCIPAL_A, List.of("workOrder.read"));
        UUID wo = seedWorkOrder(TENANT_A, "RECENT-ISO");
        commands.touch(actor(PRINCIPAL_A, TENANT_A), "c-iso", "ADMIN",
                new RecentResourceTouch(RecentResourceType.WORK_ORDER, wo.toString(), null, "RECENT-ISO"));

        assertThat(queries.list(actor(PRINCIPAL_B, TENANT_A), "c-other", "ADMIN", null).items())
                .isEmpty();
        assertThat(queries.list(actor(PRINCIPAL_A, TENANT_B), "c-tenant", "ADMIN", null).items())
                .isEmpty();
    }

    @Test
    void listIsCappedAndTrimOnTouch() {
        seedRole(TENANT_A, PRINCIPAL_A, List.of("workOrder.read"));
        for (int i = 0; i < 21; i++) {
            UUID id = UUID.randomUUID();
            commands.touch(actor(PRINCIPAL_A, TENANT_A), "c-cap-" + i, "ADMIN",
                    new RecentResourceTouch(RecentResourceType.TASK, id.toString(), null, "T-" + i));
        }
        Long stored = jdbc.sql("""
                SELECT count(*) FROM rdm_recent_resource
                 WHERE tenant_id=:t AND principal_id=:p
                """).param("t", TENANT_A).param("p", PRINCIPAL_A)
                .query(Long.class).single();
        assertThat(stored).isEqualTo(20L);

        // TASK 行对当前主体均不可访问 → 列表为空（且读路径清理）
        RecentResourcePage page = queries.list(actor(PRINCIPAL_A, TENANT_A), "c-cap-list", "ADMIN", 20);
        assertThat(page.items()).isEmpty();
        Long after = jdbc.sql("""
                SELECT count(*) FROM rdm_recent_resource
                 WHERE tenant_id=:t AND principal_id=:p
                """).param("t", TENANT_A).param("p", PRINCIPAL_A)
                .query(Long.class).single();
        assertThat(after).isZero();
    }

    @Test
    void sensitiveDisplayRefRejectedAndNonAdminPortalRejected() {
        assertThatThrownBy(() -> commands.touch(actor(PRINCIPAL_A, TENANT_A), "c-phone", "ADMIN",
                new RecentResourceTouch(RecentResourceType.WORK_ORDER, UUID.randomUUID().toString(),
                        null, "客户 13800138000")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        assertThatThrownBy(() -> queries.list(actor(PRINCIPAL_A, TENANT_A), "c-portal", "NETWORK", null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    private UUID seedWorkOrder(String tenant, String externalOrderCode) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project
                (project_id,tenant_id,project_code,client_id,project_name,starts_on,project_status,aggregate_version,created_at)
                VALUES (:id,:tenant,:code,'BYD',:name,current_date,'ACTIVE',1,now())
                """).param("id", projectId).param("tenant", tenant)
                .param("code", "P-" + externalOrderCode).param("name", "项目" + externalOrderCode).update();
        String definition = "{\"workflowCode\":\"WF-" + externalOrderCode + "\"}";
        UUID asset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                tenant, ConfigurationAssetType.WORKFLOW, "WF-" + externalOrderCode, "1.0.0", "1.0.0",
                definition, Sha256.digest(definition))).versionId();
        var bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                tenant, projectId, "B-" + externalOrderCode, "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60), null, List.of(asset)));
        return workOrders.receive(new ReceiveExternalWorkOrderCommand(
                tenant, projectId, "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                externalOrderCode, "d".repeat(64), bundle.bundleId(), bundle.bundleCode(),
                bundle.bundleVersion(), bundle.manifestDigest(), "370000", "370100", "370102",
                "敏感姓名", "13800000000", "敏感地址", "VIN123456789",
                LocalDateTime.of(2026, 7, 15, 10, 0), "corr-" + externalOrderCode, "cause")).workOrderId();
    }

    private void seedRole(String tenantId, String principal, List<String> capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenant, :code, :name, 'ACTIVE', now())
                """).param("roleId", roleId).param("tenant", tenantId)
                .param("code", principal + "-role-" + UUID.randomUUID()).param("name", principal + "-role").update();
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
                    :grantId, :tenant, :principal, :roleId, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST_FIXTURE', 'm193', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenant", tenantId)
                .param("principal", principal).param("roleId", roleId).update();
    }

    private static CurrentPrincipal actor(String principalId, String tenantId) {
        return new CurrentPrincipal(principalId, tenantId, CurrentPrincipal.PrincipalType.USER, CLIENT, Set.of());
    }
}
