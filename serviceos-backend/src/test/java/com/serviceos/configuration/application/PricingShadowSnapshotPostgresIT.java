package com.serviceos.configuration.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.PricingShadowSnapshotQueryService;
import com.serviceos.configuration.api.PricingShadowSnapshotView;
import com.serviceos.identity.api.CurrentPrincipal;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 影子定价试算只读：允许路径、空列表、缺能力拒绝。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PricingShadowSnapshotPostgresIT {
    private static final String TENANT = "tenant-pricing-shadow-read";
    private static final UUID PRINCIPAL = UUID.fromString("019f83d1-aaaa-7f8c-9505-36fe5c0e8801");
    private static final UUID PROJECT = UUID.fromString("019f83d1-bbbb-7f8c-9505-36fe5c0e8802");
    private static final UUID WORK_ORDER = UUID.fromString("019f83d1-cccc-7f8c-9505-36fe5c0e8803");
    private static final UUID BUNDLE = UUID.fromString("019f83d1-dddd-7f8c-9505-36fe5c0e8804");
    private static final UUID SNAPSHOT = UUID.fromString("019f83d1-eeee-7f8c-9505-36fe5c0e8805");

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

    @Autowired PricingShadowSnapshotQueryService queries;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.sql("""
                TRUNCATE TABLE cfg_calculation_snapshot, cfg_configuration_bundle, wo_work_order,
                    prj_project, auth_role_grant_event, auth_tenant_grant_generation,
                    auth_role_grant, auth_role_capability, auth_role, idn_security_principal CASCADE
                """).update();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("130");

        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at)
                VALUES (:projectId, :tenantId, 'SHADOW-READ', 'BYD', '影子试算只读',
                    CURRENT_DATE - 1, 'ACTIVE', 1, now())
                """).param("projectId", PROJECT).param("tenantId", TENANT).update();
        jdbc.sql("""
                INSERT INTO cfg_configuration_bundle (
                    bundle_id, tenant_id, project_id, bundle_code, bundle_version,
                    brand_code, service_product_code, province_code, effective_from, effective_until,
                    manifest_digest, status, published_at)
                VALUES (
                    :bundleId, :tenantId, :projectId, 'SHADOW-BUNDLE', '1.0.0',
                    'BYD', 'HOME_CHARGING', '370000', now() - interval '1 day', NULL,
                    :digest, 'PUBLISHED', now())
                """).param("bundleId", BUNDLE).param("tenantId", TENANT).param("projectId", PROJECT)
                .param("digest", "a".repeat(64)).update();
        jdbc.sql("""
                INSERT INTO wo_work_order (
                    id, tenant_id, project_id, client_code, brand_code, service_product_code,
                    external_order_code, payload_digest, status, configuration_bundle_id,
                    configuration_bundle_code, configuration_bundle_version,
                    configuration_bundle_digest, province_code, city_code, district_code,
                    customer_name, customer_mobile, service_address, vehicle_vin,
                    external_dispatched_at, received_at, activated_at, fulfilled_at, version)
                VALUES (
                    :workOrderId, :tenantId, :projectId, 'BYD', 'BYD', 'HOME_CHARGING',
                    'WO-SHADOW-READ', :payloadDigest, 'FULFILLED', :bundleId,
                    'SHADOW-BUNDLE', '1.0.0', :bundleDigest, '370000', '370100', '370102',
                    '测试用户', '13800000000', '测试地址', 'VINSHADOW000001',
                    now() - interval '2 hours', now() - interval '1 hour',
                    now() - interval '30 minutes', now() - interval '10 minutes', 1)
                """).param("workOrderId", WORK_ORDER).param("tenantId", TENANT)
                .param("projectId", PROJECT).param("payloadDigest", "d".repeat(64))
                .param("bundleId", BUNDLE).param("bundleDigest", "a".repeat(64)).update();
        jdbc.sql("""
                INSERT INTO cfg_calculation_snapshot (
                    snapshot_id, tenant_id, project_id, work_order_id, source_event_id,
                    source_event_type, bundle_id, bundle_digest, pricing_key, asset_version_id,
                    asset_content_digest, currency, total_amount_minor, matched_lines_json,
                    explanations_json, facts_digest, mode, correlation_id, created_at
                ) VALUES (
                    :snapshotId, :tenantId, :projectId, :workOrderId, :eventId,
                    'workorder.fulfilled', :bundleId, :bundleDigest, 'platform.demo.pricing',
                    :assetVersionId, :assetDigest, 'CNY', 28800,
                    '[]'::jsonb, '[]'::jsonb, :factsDigest, 'SHADOW', 'corr-shadow', now())
                """)
                .param("snapshotId", SNAPSHOT).param("tenantId", TENANT).param("projectId", PROJECT)
                .param("workOrderId", WORK_ORDER).param("eventId", UUID.randomUUID())
                .param("bundleId", BUNDLE).param("bundleDigest", "a".repeat(64))
                .param("assetVersionId", UUID.randomUUID()).param("assetDigest", "b".repeat(64))
                .param("factsDigest", "c".repeat(64)).update();

        seedPrincipal();
        seedRoleWithCapability("pricing.snapshot.read");
        seedProjectGrant(PRINCIPAL, "pricing.snapshot.read", PROJECT);
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void allowedPrincipalReadsShadowSnapshots() {
        var items = queries.listByWorkOrder(actor(), "corr-read", WORK_ORDER);
        assertThat(items).hasSize(1);
        PricingShadowSnapshotView view = items.getFirst();
        assertThat(view.snapshotId()).isEqualTo(SNAPSHOT);
        assertThat(view.mode()).isEqualTo("SHADOW");
        assertThat(view.totalAmountMinor()).isEqualTo(28800L);
    }

    @Test
    void missingCapabilityIsAccessDenied() {
        UUID denied = UUID.fromString("019f83d1-ffff-7f8c-9505-36fe5c0e8806");
        seedPrincipal(denied, "Denied Reader");
        assertThatThrownBy(() -> queries.listByWorkOrder(
                new CurrentPrincipal(
                        denied.toString(), TENANT, CurrentPrincipal.PrincipalType.USER, "denied", java.util.Set.of()),
                "corr-deny", WORK_ORDER))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    @Test
    void unknownWorkOrderIsNotFound() {
        assertThatThrownBy(() -> queries.listByWorkOrder(
                actor(), "corr-missing", UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(
                PRINCIPAL.toString(), TENANT, CurrentPrincipal.PrincipalType.USER, "reader", java.util.Set.of());
    }

    private void seedPrincipal() {
        seedPrincipal(PRINCIPAL, "Shadow Reader");
    }

    private void seedPrincipal(UUID principalId, String displayName) {
        jdbc.sql("""
                INSERT INTO idn_security_principal (
                    principal_id, tenant_id, principal_type, principal_status,
                    aggregate_version, created_at, updated_at)
                VALUES (:id, :tenant, 'USER', 'ACTIVE', 1, now(), now())
                ON CONFLICT (principal_id) DO NOTHING
                """).param("id", principalId).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO idn_person_profile (
                    principal_id, tenant_id, display_name, employee_number,
                    profile_version, created_at, updated_at, updated_by)
                VALUES (:id, :tenant, :name, :emp, 1, now(), now(), 'fixture')
                ON CONFLICT (principal_id) DO NOTHING
                """).param("id", principalId).param("tenant", TENANT)
                .param("name", displayName)
                .param("emp", "IT-" + principalId.toString().substring(24)).update();
    }

    private void seedRoleWithCapability(String capability) {
        UUID roleId = UUID.fromString("019f83d1-1212-7f8c-9505-36fe5c0e8807");
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenant, 'shadow-read', 'Shadow Read', 'ACTIVE', now())
                ON CONFLICT (tenant_id, role_code) DO NOTHING
                """).param("roleId", roleId).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, :capability, now())
                ON CONFLICT (role_id, capability_code) DO NOTHING
                """).param("roleId", roleId).param("capability", capability).update();
    }

    private void seedProjectGrant(UUID principalId, String capability, UUID projectId) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenant, :code, :name, 'ACTIVE', now())
                """).param("roleId", roleId).param("tenant", TENANT)
                .param("code", "grant-" + capability).param("name", capability).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, :capability, now())
                """).param("roleId", roleId).param("capability", capability).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (:grantId, :tenant, :principal, :roleId, 'PROJECT', :project,
                    now(), 'IT', 'it', now())
                """).param("grantId", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", principalId).param("roleId", roleId)
                .param("project", projectId.toString()).update();
    }
}
