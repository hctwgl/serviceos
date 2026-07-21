package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.NetworkPortalAssignCandidateItem;
import com.serviceos.readmodel.api.NetworkPortalAssignCandidatePage;
import com.serviceos.readmodel.api.NetworkPortalQueryService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M412：分配候选推荐解释读模型（可见运营事实汇总；无内部评分公式）。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NetworkPortalAssignCandidateRecommendationPostgresIT {
    private static final String TENANT = "tenant-network-portal-m412";
    private static final UUID PRINCIPAL = UUID.fromString("019f9120-4112-7000-8000-000000000001");
    private static final UUID NETWORK = UUID.fromString("019f9120-4112-7000-8000-000000000010");
    private static final UUID PARTNER = UUID.fromString("019f9120-4112-7000-8000-000000000020");
    private static final UUID TECH_PROFILE = UUID.fromString("019f9120-4112-7000-8000-000000000030");
    private static final UUID TECH_PRINCIPAL = UUID.fromString("019f9120-4112-7000-8000-000000000031");
    private static final UUID WO = UUID.fromString("019f9120-4112-7000-8000-000000000040");
    private static final UUID TASK = UUID.fromString("019f9120-4112-7000-8000-000000000050");

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
        registry.add("serviceos.outbox.scheduling-enabled", () -> "false");
        registry.add("serviceos.task.scheduling-enabled", () -> "false");
    }

    @Autowired NetworkPortalQueryService portal;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.sql("""
                TRUNCATE TABLE dsp_assignment_command_result, dsp_capacity_command_result,
                    dsp_service_assignment_activation_saga, dsp_capacity_reservation,
                    dsp_service_assignment, dsp_capacity_counter,
                    tsk_task, wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    auth_delegation_capability, auth_delegation, auth_role_grant_event,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role,
                    net_service_network_coverage,
                    net_technician_qualification, net_network_technician_membership,
                    net_technician_profile, net_network_membership, net_service_network,
                    net_partner_organization, net_directory_event, net_clearance_work_item,
                    idn_principal_lifecycle_event, idn_principal_persona, idn_identity_link,
                    idn_person_profile, idn_security_principal,
                    rel_idempotency_record, aud_audit_record CASCADE
                """).update();
        assertThat(flyway.info().current().getVersion().getVersion()).isGreaterThanOrEqualTo("142");

        seedPrincipal(PRINCIPAL, "Portal Member");
        seedPrincipal(TECH_PRINCIPAL, "Technician");
        seedPersona(PRINCIPAL, "NETWORK_MEMBER");
        seedPartnerAndNetwork();
        seedNetworkMembership(PRINCIPAL, NETWORK);
        seedTechnician(TECH_PROFILE, TECH_PRINCIPAL, NETWORK, "青岛师傅");
        seedApprovedQualification(TECH_PROFILE);
        seedGrant(PRINCIPAL, "networkTask.read", "NETWORK", NETWORK.toString());
        seedGrant(PRINCIPAL, "technician.readOwnNetwork", "NETWORK", NETWORK.toString());
        seedHumanTask(TASK, WO);
        seedWorkOrderHeader(WO, TASK, "BYD_OCEAN", "HOME_CHARGING", "370000", "370200", "370203");
        seedActiveNetworkOnly(NETWORK, WO, TASK);
        seedCoverage(NETWORK, "BYD_OCEAN", "HOME_CHARGING", "370200");
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void assignCandidatesExposeRecommendationExplanation() {
        String context = "NETWORK|NETWORK|" + NETWORK;
        NetworkPortalAssignCandidatePage page = portal.listAssignCandidates(
                actor(PRINCIPAL), "corr-m412", context, TASK);

        assertThat(page.networkId()).isEqualTo(NETWORK);
        assertThat(page.taskId()).isEqualTo(TASK);
        assertThat(page.rankingExplanation()).contains("不含内部评分公式");
        assertThat(page.emptyReason()).isNull();
        assertThat(page.items()).isNotEmpty();

        NetworkPortalAssignCandidateItem tech = page.items().stream()
                .filter(item -> TECH_PROFILE.equals(item.technicianProfileId()))
                .findFirst()
                .orElseThrow();
        assertThat(tech.distanceTier()).isEqualTo("SAME_CITY");
        assertThat(tech.coverageMatched()).isTrue();
        assertThat(tech.assignable()).isTrue();
        assertThat(tech.recommendationTier()).isEqualTo("RECOMMENDED");
        assertThat(tech.recommendationSummary()).startsWith("建议优先：");
        assertThat(tech.recommendationReasons()).anyMatch(r -> r.contains("同城") || r.contains("已通过资质"));
    }

    @Test
    void cautionWhenCoverageMisses() {
        jdbc.sql("DELETE FROM net_service_network_coverage").update();
        seedCoverage(NETWORK, "BYD_OCEAN", "HOME_CHARGING", "370100");

        NetworkPortalAssignCandidatePage page = portal.listAssignCandidates(
                actor(PRINCIPAL), "corr-m412-caution", "NETWORK|NETWORK|" + NETWORK, TASK);
        NetworkPortalAssignCandidateItem tech = page.items().getFirst();
        assertThat(tech.recommendationTier()).isEqualTo("CAUTION");
        assertThat(tech.recommendationSummary()).startsWith("谨慎：");
        assertThat(tech.recommendationReasons()).anyMatch(r -> r.contains("覆盖未命中"));
    }

    @Test
    void emptyReasonWhenNoActiveTechnicians() {
        jdbc.sql("DELETE FROM net_network_technician_membership").update();
        jdbc.sql("DELETE FROM net_technician_qualification").update();
        jdbc.sql("DELETE FROM net_technician_profile").update();

        NetworkPortalAssignCandidatePage page = portal.listAssignCandidates(
                actor(PRINCIPAL), "corr-m412-empty", "NETWORK|NETWORK|" + NETWORK, TASK);
        assertThat(page.items()).isEmpty();
        assertThat(page.emptyReason()).contains("没有 ACTIVE 师傅");
        assertThat(page.rankingExplanation()).contains("排序");
    }

    private void seedApprovedQualification(UUID profileId) {
        Instant submittedAt = Instant.parse("2026-07-01T00:00:00Z");
        jdbc.sql("""
                INSERT INTO net_technician_qualification (
                    qualification_id, tenant_id, technician_profile_id, qualification_code,
                    qualification_status, valid_from, valid_to, submitted_by, submitted_at,
                    decided_by, decided_at, decision_reason, aggregate_version
                ) VALUES (
                    :id, :tenant, :profile, 'ELEC-A', 'APPROVED',
                    :validFrom, :validTo, 'submitter', :submittedAt,
                    'approver', :decidedAt, 'ok', 1
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("profile", profileId)
                .param("validFrom", Timestamp.from(submittedAt))
                .param("validTo", Timestamp.from(submittedAt.plusSeconds(86400L * 365)))
                .param("submittedAt", Timestamp.from(submittedAt))
                .param("decidedAt", Timestamp.from(submittedAt.plusSeconds(3600)))
                .update();
    }

    private void seedPrincipal(UUID principalId, String displayName) {
        jdbc.sql("""
                INSERT INTO idn_security_principal (
                    principal_id, tenant_id, principal_type, principal_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (:id, :tenant, 'USER', 'ACTIVE', 1, now(), now())
                """).param("id", principalId).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO idn_person_profile (
                    principal_id, tenant_id, display_name, employee_number,
                    profile_version, created_at, updated_at, updated_by
                ) VALUES (:id, :tenant, :name, :emp, 1, now(), now(), 'test')
                """)
                .param("id", principalId)
                .param("tenant", TENANT)
                .param("name", displayName)
                .param("emp", "E-" + principalId.toString().substring(24))
                .update();
    }

    private void seedPersona(UUID principalId, String type) {
        jdbc.sql("""
                INSERT INTO idn_principal_persona (
                    persona_id, tenant_id, principal_id, persona_type, persona_status,
                    valid_from, valid_to, persona_version, created_by, created_at
                ) VALUES (
                    :id, :tenant, :principal, :type, 'ACTIVE',
                    now() - interval '1 day', NULL, 1, 'test', now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("principal", principalId)
                .param("type", type)
                .update();
    }

    private void seedPartnerAndNetwork() {
        jdbc.sql("""
                INSERT INTO net_partner_organization (
                    partner_organization_id, tenant_id, partner_code, partner_name,
                    partner_status, aggregate_version, created_at, updated_at
                ) VALUES (:id, :tenant, 'P-410', 'Partner 410', 'ACTIVE', 1, now(), now())
                """).param("id", PARTNER).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO net_service_network (
                    service_network_id, tenant_id, partner_organization_id, network_code,
                    network_name, network_status, aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :partner, 'N-410', 'Network 410', 'ACTIVE', 1, now(), now()
                )
                """)
                .param("id", NETWORK)
                .param("tenant", TENANT)
                .param("partner", PARTNER)
                .update();
    }

    private void seedNetworkMembership(UUID principalId, UUID networkId) {
        jdbc.sql("""
                INSERT INTO net_network_membership (
                    membership_id, tenant_id, service_network_id, principal_id, membership_role,
                    membership_status, valid_from, invited_by, created_at, aggregate_version
                ) VALUES (
                    :id, :tenant, :network, :principal, 'STAFF',
                    'ACTIVE', now() - interval '1 day', 'test', now(), 1
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("network", networkId)
                .param("principal", principalId)
                .update();
    }

    private void seedTechnician(UUID profileId, UUID principalId, UUID networkId, String name) {
        jdbc.sql("""
                INSERT INTO net_technician_profile (
                    technician_profile_id, tenant_id, principal_id, display_name, profile_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :principal, :name, 'ACTIVE', 1, now(), now()
                )
                """)
                .param("id", profileId)
                .param("tenant", TENANT)
                .param("principal", principalId)
                .param("name", name)
                .update();
        jdbc.sql("""
                INSERT INTO net_network_technician_membership (
                    membership_id, tenant_id, service_network_id, technician_profile_id,
                    membership_status, valid_from, created_by, created_at, aggregate_version
                ) VALUES (
                    :id, :tenant, :network, :profile,
                    'ACTIVE', now() - interval '1 day', 'test', now(), 1
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("network", networkId)
                .param("profile", profileId)
                .update();
    }

    private void seedHumanTask(UUID taskId, UUID workOrderId) {
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id, tenant_id, task_type, task_kind, business_key, payload_digest,
                    priority, status, next_run_at, attempt_count, max_attempts, correlation_id,
                    version, created_at, updated_at, project_id, work_order_id,
                    workflow_instance_id, stage_instance_id, workflow_node_instance_id,
                    workflow_node_id, workflow_definition_version_id, workflow_definition_digest,
                    configuration_bundle_id, configuration_bundle_digest, stage_code
                ) VALUES (
                    :taskId, :tenantId, 'INSTALLATION', 'HUMAN', :businessKey, :digest,
                    500, 'READY', :now, 0, 3, 'corr-seed', 1, :now, :now, :projectId,
                    :workOrderId, :workflowInstanceId, :stageInstanceId, :workflowNodeInstanceId,
                    'INSTALL_NODE', :definitionId, :definitionDigest, :bundleId, :bundleDigest,
                    'INSTALL'
                )
                """)
                .param("taskId", taskId)
                .param("tenantId", TENANT)
                .param("businessKey", "m412:" + taskId)
                .param("digest", "a".repeat(64))
                .param("now", java.sql.Timestamp.from(now))
                .param("projectId", UUID.randomUUID())
                .param("workOrderId", workOrderId)
                .param("workflowInstanceId", UUID.randomUUID())
                .param("stageInstanceId", UUID.randomUUID())
                .param("workflowNodeInstanceId", UUID.randomUUID())
                .param("definitionId", UUID.randomUUID())
                .param("definitionDigest", "b".repeat(64))
                .param("bundleId", UUID.randomUUID())
                .param("bundleDigest", "c".repeat(64))
                .update();
    }

    private void seedWorkOrderHeader(
            UUID workOrderId,
            UUID taskId,
            String brandCode,
            String serviceProductCode,
            String provinceCode,
            String cityCode,
            String districtCode
    ) {
        var task = jdbc.sql("""
                SELECT project_id, configuration_bundle_id, configuration_bundle_digest
                  FROM tsk_task WHERE task_id=:id
                """)
                .param("id", taskId)
                .query((rs, rowNum) -> new Object[] {
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("configuration_bundle_id", UUID.class),
                        rs.getString("configuration_bundle_digest")
                })
                .single();
        UUID projectId = (UUID) task[0];
        UUID bundleId = (UUID) task[1];
        String bundleDigest = (String) task[2];
        OffsetDateTime scopeNow = OffsetDateTime.ofInstant(
                Instant.parse("2026-07-21T00:00:00Z"), ZoneOffset.UTC);
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at)
                VALUES (
                    :projectId, :tenantId, :code, 'BYD', 'M412 distance fixture',
                    DATE '2026-07-01', NULL, 'ACTIVE', 1, :createdAt)
                ON CONFLICT (project_id) DO NOTHING
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("code", "M412-" + workOrderId.toString().substring(24))
                .param("createdAt", scopeNow)
                .update();
        jdbc.sql("""
                INSERT INTO cfg_configuration_bundle (
                    bundle_id, tenant_id, project_id, bundle_code, bundle_version,
                    brand_code, service_product_code, province_code, effective_from, effective_until,
                    manifest_digest, status, published_at)
                VALUES (
                    :bundleId, :tenantId, :projectId, :bundleCode, '1.0.0',
                    :brandCode, :product, :province, :effectiveFrom, NULL,
                    :manifestDigest, 'PUBLISHED', :publishedAt)
                ON CONFLICT (bundle_id) DO NOTHING
                """)
                .param("bundleId", bundleId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("bundleCode", "M412-BUNDLE-" + workOrderId.toString().substring(24))
                .param("brandCode", brandCode)
                .param("product", serviceProductCode)
                .param("province", provinceCode)
                .param("effectiveFrom", scopeNow)
                .param("manifestDigest", bundleDigest)
                .param("publishedAt", scopeNow)
                .update();
        jdbc.sql("""
                INSERT INTO wo_work_order (
                    id, tenant_id, project_id, client_code, brand_code, service_product_code,
                    external_order_code, payload_digest, status,
                    configuration_bundle_id, configuration_bundle_code, configuration_bundle_version,
                    configuration_bundle_digest, province_code, city_code, district_code,
                    customer_name, customer_mobile, service_address, vehicle_vin,
                    external_dispatched_at, received_at, activated_at, version
                ) VALUES (
                    :id, :tenantId, :projectId, 'BYD', :brandCode, :product,
                    :externalOrderCode, :payloadDigest, 'ACTIVE',
                    :bundleId, 'M412-BUNDLE', '1.0.0', :bundleDigest,
                    :province, :city, :district,
                    '测试客户', '13800000000', '测试地址', 'VIN123456789012345',
                    :receivedAt, :receivedAt, :receivedAt, 1)
                ON CONFLICT (id) DO NOTHING
                """)
                .param("id", workOrderId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("brandCode", brandCode)
                .param("product", serviceProductCode)
                .param("externalOrderCode", "M412-" + workOrderId)
                .param("payloadDigest", "c".repeat(64))
                .param("bundleId", bundleId)
                .param("bundleDigest", bundleDigest)
                .param("province", provinceCode)
                .param("city", cityCode)
                .param("district", districtCode)
                .param("receivedAt", java.sql.Timestamp.from(Instant.parse("2026-07-21T02:00:00Z")))
                .update();
    }

    private void seedActiveNetworkOnly(UUID networkId, UUID workOrderId, UUID taskId) {
        UUID assignmentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T01:00:00Z");
        jdbc.sql("""
                INSERT INTO dsp_service_assignment (
                    service_assignment_id, tenant_id, work_order_id, task_id,
                    responsibility_level, assignee_id, business_type, source_decision_id,
                    status, activation_saga_id, effective_from, created_by, created_at,
                    authority_assignment_id, authority_version,
                    fence_decision_id, fence_policy_version
                ) VALUES (
                    :id, :tenant, :workOrderId, :taskId,
                    'NETWORK', :assignee, 'INSTALLATION', :decision,
                    'ACTIVE', :saga, :now, 'test', :now,
                    :authorityId, 1,
                    :fenceDecision, :fencePolicy
                )
                """)
                .param("id", assignmentId)
                .param("tenant", TENANT)
                .param("workOrderId", workOrderId)
                .param("taskId", taskId)
                .param("assignee", networkId.toString())
                .param("decision", "decision://" + assignmentId)
                .param("saga", UUID.randomUUID())
                .param("now", java.sql.Timestamp.from(now))
                .param("authorityId", "authority://" + assignmentId)
                .param("fenceDecision", "fence://" + assignmentId)
                .param("fencePolicy", "fence-policy-v1")
                .update();
    }

    private void seedCoverage(UUID networkId, String brand, String product, String regionCode) {
        jdbc.sql("""
                INSERT INTO net_service_network_coverage (
                    coverage_id, tenant_id, service_network_id, brand_code, business_type,
                    region_code, coverage_status, valid_from, valid_to, created_at
                ) VALUES (
                    :id, :tenant, :network, :brand, :product,
                    :region, 'ACTIVE', now() - interval '1 day', NULL, now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("network", networkId)
                .param("brand", brand)
                .param("product", product)
                .param("region", regionCode)
                .update();
    }

    private void seedGrant(UUID principalId, String capability, String scopeType, String scopeRef) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (
                    role_id, tenant_id, role_code, role_name, role_status, role_kind,
                    aggregate_version, created_at, updated_at
                ) VALUES (
                    :roleId, :tenant, :code, :code, 'ACTIVE', 'TENANT', 1, now(), now()
                )
                """)
                .param("roleId", roleId)
                .param("tenant", TENANT)
                .param("code", "m412-" + capability + "-" + UUID.randomUUID())
                .update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, :capability, now())
                """)
                .param("roleId", roleId)
                .param("capability", capability)
                .update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, grant_status, grant_effect,
                    aggregate_version, created_at, updated_at
                ) VALUES (
                    :grantId, :tenant, :principal, :roleId, :scopeType, :scopeRef,
                    now() - interval '1 day', 'TEST_FIXTURE', 'ACTIVE', 'ALLOW',
                    1, now(), now()
                )
                """)
                .param("grantId", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("principal", principalId.toString())
                .param("roleId", roleId)
                .param("scopeType", scopeType)
                .param("scopeRef", scopeRef)
                .update();
    }

    private static CurrentPrincipal actor(UUID principalId) {
        return new CurrentPrincipal(principalId.toString(), TENANT, CurrentPrincipal.PrincipalType.USER,
                "network-portal", Set.of());
    }
}
