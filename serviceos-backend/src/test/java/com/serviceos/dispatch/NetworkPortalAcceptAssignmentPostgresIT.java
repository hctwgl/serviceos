package com.serviceos.dispatch;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.dispatch.api.NetworkPortalAcceptAssignmentReceipt;
import com.serviceos.dispatch.api.NetworkPortalAcceptAssignmentService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Network Portal 网点接单：成功激活 NETWORK、幂等回放、跨网点冲突、能力/上下文失败关闭。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NetworkPortalAcceptAssignmentPostgresIT {
    private static final String TENANT = "tenant-network-portal-accept";
    private static final String BUSINESS_TYPE = "INSTALLATION";
    private static final UUID PRINCIPAL = UUID.fromString("019f83c0-1111-7f8c-9505-36fe5c0e8801");
    private static final UUID NETWORK_A = UUID.fromString("019f83c0-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID NETWORK_B = UUID.fromString("019f83c0-3333-7f8c-9505-36fe5c0e8804");
    private static final UUID PARTNER = UUID.fromString("019f83c0-4444-7f8c-9505-36fe5c0e8805");
    private static final UUID PROJECT = UUID.fromString("019f83c0-5555-7f8c-9505-36fe5c0e8806");
    private static final UUID WO = UUID.fromString("019f83c0-7777-7f8c-9505-36fe5c0e880a");
    private static final UUID TASK = UUID.fromString("019f83c0-9999-7f8c-9505-36fe5c0e880b");

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

    @Autowired NetworkPortalAcceptAssignmentService portalAccept;
    @Autowired ConfigurationService configurations;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.sql("""
                TRUNCATE TABLE dsp_assignment_command_result, dsp_capacity_command_result,
                    dsp_service_assignment_activation_saga, dsp_capacity_reservation,
                    dsp_service_assignment, dsp_capacity_counter,
                    tsk_task,
                    wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version,
                    auth_delegation_capability, auth_delegation, auth_role_grant_event,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role,
                    net_technician_qualification, net_network_technician_membership,
                    net_technician_profile, net_network_membership,
                    net_service_network_coverage, net_service_network,
                    net_partner_organization, net_directory_event, net_clearance_work_item,
                    prj_project_network, prj_project,
                    idn_principal_lifecycle_event, idn_principal_persona, idn_identity_link,
                    idn_person_profile, idn_security_principal,
                    rel_idempotency_record, aud_audit_record CASCADE
                """).update();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("146");
        assertThat(flyway.info().applied()).hasSize(148);
        assertThat(jdbc.sql("""
                        SELECT risk_level FROM auth_capability
                         WHERE capability_code='networkPortal.acceptAssignment'
                        """).query(String.class).single()).isEqualTo("HIGH");

        seedPrincipal(PRINCIPAL, "Portal Member");
        seedPersona(PRINCIPAL, "NETWORK_MEMBER");
        seedProject();
        seedPartnerAndNetworks();
        ConfigurationBundleReference bundle = publishDispatchBundle();
        seedWorkOrder(bundle);
        seedNetworkMembership(PRINCIPAL, NETWORK_A);
        seedGrant(PRINCIPAL, "networkPortal.acceptAssignment", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL, "dispatch.assignment.manage", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL, "dispatch.capacity.configure", "NETWORK", NETWORK_A.toString());
        seedHumanTask(TASK, WO, bundle);
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void successActivatesNetworkOnly() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalAcceptAssignmentReceipt receipt = portalAccept.acceptAssignment(
                actor(PRINCIPAL), metadata("accept-1"), context, TASK, BUSINESS_TYPE);

        assertThat(receipt.taskId()).isEqualTo(TASK);
        assertThat(receipt.workOrderId()).isEqualTo(WO);
        assertThat(receipt.networkAssigneeId()).isEqualTo(NETWORK_A.toString());
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM dsp_service_assignment
                         WHERE tenant_id=:tenant AND task_id=:task
                           AND responsibility_level='NETWORK' AND status='ACTIVE'
                           AND assignee_id=:network
                        """)
                .param("tenant", TENANT)
                .param("task", TASK)
                .param("network", NETWORK_A.toString())
                .query(Long.class)
                .single()).isEqualTo(1L);
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM dsp_service_assignment
                         WHERE tenant_id=:tenant AND task_id=:task
                           AND responsibility_level='TECHNICIAN' AND status='ACTIVE'
                        """)
                .param("tenant", TENANT)
                .param("task", TASK)
                .query(Long.class)
                .single()).isZero();
    }

    @Test
    void idempotentReplayReturnsSameNetworkAssignment() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalAcceptAssignmentReceipt first = portalAccept.acceptAssignment(
                actor(PRINCIPAL), metadata("accept-idem"), context, TASK, BUSINESS_TYPE);
        NetworkPortalAcceptAssignmentReceipt second = portalAccept.acceptAssignment(
                actor(PRINCIPAL), metadata("accept-idem-2"), context, TASK, BUSINESS_TYPE);
        assertThat(second.networkServiceAssignmentId()).isEqualTo(first.networkServiceAssignmentId());
    }

    @Test
    void foreignActiveNetworkConflicts() {
        seedActiveAssignment(NETWORK_B.toString(), "NETWORK", TASK, WO);
        assertThatThrownBy(() -> portalAccept.acceptAssignment(
                actor(PRINCIPAL), metadata("accept-conflict"), "NETWORK|NETWORK|" + NETWORK_A,
                TASK, BUSINESS_TYPE))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.SERVICE_ASSIGNMENT_CONFLICT));
    }

    @Test
    void missingCapabilityFailsClosed() {
        jdbc.sql("""
                UPDATE auth_role_grant
                   SET grant_status='REVOKED', aggregate_version = aggregate_version + 1, updated_at=now()
                 WHERE tenant_id=:tenant AND principal_id=:principal
                   AND scope_type='NETWORK' AND scope_ref=:network
                   AND role_id IN (
                     SELECT role_id FROM auth_role_capability
                      WHERE capability_code='networkPortal.acceptAssignment'
                   )
                """)
                .param("tenant", TENANT)
                .param("principal", PRINCIPAL.toString())
                .param("network", NETWORK_A.toString())
                .update();

        assertThatThrownBy(() -> portalAccept.acceptAssignment(
                actor(PRINCIPAL), metadata("accept-cap"), "NETWORK|NETWORK|" + NETWORK_A,
                TASK, BUSINESS_TYPE))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
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

    private void seedPartnerAndNetworks() {
        jdbc.sql("""
                INSERT INTO net_partner_organization (
                    partner_organization_id, tenant_id, partner_code, partner_name,
                    partner_status, aggregate_version, created_at, updated_at
                ) VALUES (:id, :tenant, 'P-ACC', 'Partner Accept', 'ACTIVE', 1, now(), now())
                """).param("id", PARTNER).param("tenant", TENANT).update();
        for (UUID networkId : new UUID[]{NETWORK_A, NETWORK_B}) {
            jdbc.sql("""
                    INSERT INTO net_service_network (
                        service_network_id, tenant_id, partner_organization_id, network_code,
                        network_name, network_status, aggregate_version, created_at, updated_at
                    ) VALUES (
                        :id, :tenant, :partner, :code, :name, 'ACTIVE', 1, now(), now()
                    )
                    """)
                    .param("id", networkId)
                    .param("tenant", TENANT)
                    .param("partner", PARTNER)
                    .param("code", "N-" + networkId.toString().substring(24))
                    .param("name", "Network " + networkId)
                    .update();
        }
        jdbc.sql("""
                INSERT INTO prj_project_network (
                    project_network_id, tenant_id, project_id, network_id,
                    valid_from, created_by, created_at
                ) VALUES (
                    :id, :tenant, :project, :network,
                    now() - interval '1 day', 'test', now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("project", PROJECT)
                .param("network", NETWORK_A.toString())
                .update();
        jdbc.sql("""
                INSERT INTO net_service_network_coverage (
                    coverage_id, tenant_id, service_network_id, brand_code, business_type,
                    region_code, coverage_status, valid_from, created_at
                ) VALUES (
                    :id, :tenant, :network, 'TEST_BRAND', :businessType,
                    '370100', 'ACTIVE', now() - interval '1 day', now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("network", NETWORK_A)
                .param("businessType", BUSINESS_TYPE)
                .update();
        jdbc.sql("""
                INSERT INTO dsp_capacity_counter (
                    capacity_counter_id, tenant_id, responsibility_level, assignee_id,
                    business_type, max_units, occupied_units, version, updated_by, updated_at
                ) VALUES (
                    :id, :tenant, 'NETWORK', :network,
                    :businessType, 10, 0, 1, 'test', now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("network", NETWORK_A.toString())
                .param("businessType", BUSINESS_TYPE)
                .update();
    }

    private void seedProject() {
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :project, :tenant, 'NETWORK-ACCEPT', 'TEST', '网点接单测试项目',
                    :startsOn, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("project", PROJECT)
                .param("tenant", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
    }

    /**
     * 网点接单属于正式人工派网点命令，必须与生产路径一样引用冻结 DISPATCH，不能用缺失配置的
     * 悬空任务夹具绕过候选硬过滤。测试只建立本用例需要的最小工作流和派单策略。
     */
    private ConfigurationBundleReference publishDispatchBundle() {
        String workflow = """
                {"workflowKey":"NETWORK_ACCEPT","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"HUMAN","nodeType":"USER_TASK","name":"网点接单",
                    "stageCode":"INSTALL","taskType":"INSTALLATION",
                    "dispatchPolicyRef":"network-accept-dispatch"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"HUMAN"},
                   {"transitionId":"t2","from":"HUMAN","to":"END"}]}
                """.replaceAll("\\s+", "");
        String dispatch = """
                {"policyKey":"network-accept-dispatch","version":"1.0.0",
                 "scope":{"brandCodes":["TEST_BRAND"],"businessTypes":["INSTALLATION"],
                          "regionCodes":["370000"]},
                 "hardFilters":[
                   {"filterKey":"ENABLED","order":1,
                    "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"TEST_BRAND\\""},
                    "failureCode":"DISABLED"},
                   {"filterKey":"CAPACITY","order":2,
                    "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"TEST_BRAND\\""},
                    "failureCode":"NO_CAPACITY"},
                   {"filterKey":"BRAND_SCOPE","order":3,
                    "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"TEST_BRAND\\""},
                    "failureCode":"BRAND_MISMATCH"},
                   {"filterKey":"REGION_SCOPE","order":4,
                    "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"TEST_BRAND\\""},
                    "failureCode":"REGION_MISMATCH"}],
                 "scoring":[{"factorKey":"REMAINING_CAPACITY","weight":1.0,
                    "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"TEST_BRAND\\""}}],
                 "capacity":{"reservationRequired":true},
                 "fallback":{"onNoCandidate":"MANUAL_INTERVENTION","manualRole":"OPS","resolutionHours":4}}
                """.replaceAll("\\s+", "");
        var workflowAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "NETWORK_ACCEPT",
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        var dispatchAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.DISPATCH, "network-accept-dispatch",
                "1.0.0", "1.0.0", dispatch, Sha256.digest(dispatch)));
        return configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, PROJECT, "NETWORK-ACCEPT-BUNDLE", "1.0.0",
                "TEST_BRAND", BUSINESS_TYPE, "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowAsset.versionId(), dispatchAsset.versionId())));
    }

    private void seedWorkOrder(ConfigurationBundleReference bundle) {
        jdbc.sql("""
                INSERT INTO wo_work_order (
                    id, tenant_id, project_id, client_code, brand_code, service_product_code,
                    external_order_code, payload_digest, status, configuration_bundle_id,
                    configuration_bundle_code, configuration_bundle_version,
                    configuration_bundle_digest, province_code, city_code, district_code,
                    customer_name, customer_mobile, service_address, vehicle_vin,
                    external_dispatched_at, received_at, activated_at, version
                ) VALUES (
                    :id, :tenant, :project, 'TEST', 'TEST_BRAND', :businessType,
                    'NETWORK-ACCEPT-ORDER', :payloadDigest, 'ACTIVE', :bundleId,
                    :bundleCode, :bundleVersion, :bundleDigest, '370000', '370100', '370102',
                    '测试客户', '13800000000', '测试服务地址', 'LSLNETWORKACCEPT01',
                    now(), now(), now(), 1
                )
                """)
                .param("id", WO)
                .param("tenant", TENANT)
                .param("project", PROJECT)
                .param("businessType", BUSINESS_TYPE)
                .param("payloadDigest", "d".repeat(64))
                .param("bundleId", bundle.bundleId())
                .param("bundleCode", bundle.bundleCode())
                .param("bundleVersion", bundle.bundleVersion())
                .param("bundleDigest", bundle.manifestDigest())
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

    private void seedHumanTask(
            UUID taskId, UUID workOrderId, ConfigurationBundleReference bundle
    ) {
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id, tenant_id, task_type, task_kind, business_key, payload_digest,
                    priority, status, next_run_at, attempt_count, max_attempts, correlation_id,
                    version, created_at, updated_at, project_id, work_order_id,
                    workflow_instance_id, stage_instance_id, workflow_node_instance_id,
                    workflow_node_id, workflow_definition_version_id, workflow_definition_digest,
                    configuration_bundle_id, configuration_bundle_digest, stage_code,
                    dispatch_policy_ref
                ) VALUES (
                    :taskId, :tenantId, 'INSTALLATION', 'HUMAN', :businessKey, :digest,
                    500, 'READY', :now, 0, 3, 'corr-seed', 1, :now, :now, :projectId,
                    :workOrderId, :workflowInstanceId, :stageInstanceId, :workflowNodeInstanceId,
                    'INSTALL_NODE', :definitionId, :definitionDigest, :bundleId, :bundleDigest,
                    'INSTALL', 'network-accept-dispatch'
                )
                """)
                .param("taskId", taskId)
                .param("tenantId", TENANT)
                .param("businessKey", "accept:" + taskId)
                .param("digest", "a".repeat(64))
                .param("now", java.sql.Timestamp.from(now))
                .param("projectId", PROJECT)
                .param("workOrderId", workOrderId)
                .param("workflowInstanceId", UUID.randomUUID())
                .param("stageInstanceId", UUID.randomUUID())
                .param("workflowNodeInstanceId", UUID.randomUUID())
                .param("definitionId", UUID.randomUUID())
                .param("definitionDigest", "b".repeat(64))
                .param("bundleId", bundle.bundleId())
                .param("bundleDigest", bundle.manifestDigest())
                .update();
    }

    private void seedActiveAssignment(String assigneeId, String level, UUID taskId, UUID workOrderId) {
        UUID assignmentId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        UUID counterId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-17T01:00:00Z");
        jdbc.sql("""
                INSERT INTO dsp_capacity_counter (
                    capacity_counter_id, tenant_id, responsibility_level, assignee_id,
                    business_type, max_units, occupied_units, version, updated_by, updated_at
                ) VALUES (
                    :id, :tenant, :level, :assignee, 'INSTALLATION', 10, 1, 1, 'test', :now
                )
                ON CONFLICT (tenant_id, responsibility_level, assignee_id, business_type) DO NOTHING
                """)
                .param("id", counterId)
                .param("tenant", TENANT)
                .param("level", level)
                .param("assignee", assigneeId)
                .param("now", java.sql.Timestamp.from(now))
                .update();
        UUID resolvedCounterId = jdbc.sql("""
                        SELECT capacity_counter_id FROM dsp_capacity_counter
                         WHERE tenant_id = :tenant AND responsibility_level = :level
                           AND assignee_id = :assignee AND business_type = 'INSTALLATION'
                        """)
                .param("tenant", TENANT)
                .param("level", level)
                .param("assignee", assigneeId)
                .query(UUID.class)
                .single();
        jdbc.sql("""
                INSERT INTO dsp_service_assignment (
                    service_assignment_id, tenant_id, work_order_id, task_id,
                    responsibility_level, assignee_id, business_type, source_decision_id,
                    status, activation_saga_id, effective_from, created_by, created_at,
                    authority_assignment_id, authority_version,
                    fence_decision_id, fence_policy_version
                ) VALUES (
                    :id, :tenant, :workOrderId, :taskId,
                    :level, :assignee, 'INSTALLATION', :decision,
                    'ACTIVE', :saga, :now, 'test', :now,
                    :authorityId, 1,
                    :fenceDecision, :fencePolicy
                )
                """)
                .param("id", assignmentId)
                .param("tenant", TENANT)
                .param("workOrderId", workOrderId)
                .param("taskId", taskId)
                .param("level", level)
                .param("assignee", assigneeId)
                .param("decision", "decision://" + assignmentId)
                .param("saga", sagaId)
                .param("now", java.sql.Timestamp.from(now))
                .param("authorityId", "authority://" + assignmentId)
                .param("fenceDecision", "fence://" + assignmentId)
                .param("fencePolicy", "fence-policy-v1")
                .update();
        jdbc.sql("""
                INSERT INTO dsp_capacity_reservation (
                    capacity_reservation_id, tenant_id, service_assignment_id,
                    capacity_counter_id, units, status, held_at, confirmed_at
                ) VALUES (
                    :id, :tenant, :assignmentId, :counterId, 1, 'CONFIRMED', :now, :now
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("assignmentId", assignmentId)
                .param("counterId", resolvedCounterId)
                .param("now", java.sql.Timestamp.from(now))
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
                .param("code", "accept-" + capability + "-" + UUID.randomUUID())
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

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }
}
