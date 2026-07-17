package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.NetworkPortalCapacityItem;
import com.serviceos.readmodel.api.NetworkPortalPage;
import com.serviceos.readmodel.api.NetworkPortalQueryService;
import com.serviceos.readmodel.api.NetworkPortalTaskItem;
import com.serviceos.readmodel.api.NetworkPortalTechnicianItem;
import com.serviceos.readmodel.api.NetworkPortalWorkbenchView;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderItem;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M194 Network Portal 只读：本网点 ACTIVE 责任、跨网点隔离、伪造上下文、师傅与容量。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NetworkPortalReadPostgresIT {
    private static final String TENANT = "tenant-network-portal-m194";
    private static final UUID PRINCIPAL = UUID.fromString("019f83a0-1111-7f8c-9505-36fe5c0e8801");
    private static final UUID OTHER_PRINCIPAL = UUID.fromString("019f83a0-1112-7f8c-9505-36fe5c0e8802");
    private static final UUID NETWORK_A = UUID.fromString("019f83a0-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID NETWORK_B = UUID.fromString("019f83a0-3333-7f8c-9505-36fe5c0e8804");
    private static final UUID PARTNER = UUID.fromString("019f83a0-4444-7f8c-9505-36fe5c0e8805");
    private static final UUID TECH_PROFILE = UUID.fromString("019f83a0-5555-7f8c-9505-36fe5c0e8806");
    private static final UUID TECH_PRINCIPAL = UUID.fromString("019f83a0-6666-7f8c-9505-36fe5c0e8807");
    private static final UUID WO_A = UUID.fromString("019f83a0-7777-7f8c-9505-36fe5c0e8808");
    private static final UUID WO_B = UUID.fromString("019f83a0-8888-7f8c-9505-36fe5c0e8809");
    private static final UUID TASK_A = UUID.fromString("019f83a0-9999-7f8c-9505-36fe5c0e880a");
    private static final UUID TASK_B = UUID.fromString("019f83a0-aaaa-7f8c-9505-36fe5c0e880b");

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
                    tsk_task,
                    auth_delegation_capability, auth_delegation, auth_role_grant_event,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role,
                    net_technician_qualification, net_network_technician_membership,
                    net_technician_profile, net_network_membership, net_service_network,
                    net_partner_organization, net_directory_event, net_clearance_work_item,
                    idn_principal_lifecycle_event, idn_principal_persona, idn_identity_link,
                    idn_person_profile, idn_security_principal,
                    rel_idempotency_record, aud_audit_record CASCADE
                """).update();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("096");
        assertThat(flyway.info().applied()).hasSize(98);

        seedPrincipal(PRINCIPAL, "Portal Member");
        seedPrincipal(OTHER_PRINCIPAL, "Other Member");
        seedPrincipal(TECH_PRINCIPAL, "Technician");
        seedPersona(PRINCIPAL, "NETWORK_MEMBER");
        seedPersona(OTHER_PRINCIPAL, "NETWORK_MEMBER");
        seedPartnerAndNetworks();
        seedNetworkMembership(PRINCIPAL, NETWORK_A);
        seedNetworkMembership(OTHER_PRINCIPAL, NETWORK_B);
        seedTechnicianOn(NETWORK_A);
        seedGrant(PRINCIPAL, "networkTask.read", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL, "technician.readOwnNetwork", "NETWORK", NETWORK_A.toString());
        seedGrant(OTHER_PRINCIPAL, "networkTask.read", "NETWORK", NETWORK_B.toString());
        seedGrant(OTHER_PRINCIPAL, "technician.readOwnNetwork", "NETWORK", NETWORK_B.toString());
        seedHumanTask(TASK_A, WO_A);
        seedHumanTask(TASK_B, WO_B);
        seedActiveNetworkAssignment(NETWORK_A, WO_A, TASK_A, "tech-a");
        seedActiveNetworkAssignment(NETWORK_B, WO_B, TASK_B, "tech-b");
        seedCapacity(NETWORK_A, 10, 3);
        seedCapacity(NETWORK_B, 5, 1);
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void memberSeesOnlyOwnNetworkActiveAssignmentsAndWorkbench() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalPage<NetworkPortalWorkOrderItem> workOrders =
                portal.listWorkOrders(actor(PRINCIPAL), "corr-wo", context);
        assertThat(workOrders.networkId()).isEqualTo(NETWORK_A);
        assertThat(workOrders.items()).extracting(NetworkPortalWorkOrderItem::workOrderId)
                .containsExactly(WO_A);
        assertThat(workOrders.items()).noneMatch(item -> WO_B.equals(item.workOrderId()));

        NetworkPortalPage<NetworkPortalTaskItem> tasks =
                portal.listTasks(actor(PRINCIPAL), "corr-task", context);
        assertThat(tasks.items()).extracting(NetworkPortalTaskItem::taskId).containsExactly(TASK_A);
        assertThat(tasks.items().getFirst().status()).isEqualTo("READY");

        NetworkPortalWorkbenchView workbench = portal.workbench(actor(PRINCIPAL), "corr-wb", context);
        assertThat(workbench.activeWorkOrderCount()).isEqualTo(1);
        assertThat(workbench.activeTaskCount()).isEqualTo(1);
        assertThat(workbench.activeTechnicianCount()).isEqualTo(1);
        assertThat(workbench.capacity()).extracting(NetworkPortalCapacityItem::occupiedUnits)
                .containsExactly(3);

        // 纯 UUID 形态在 ACTIVE membership 下也可接受
        NetworkPortalPage<NetworkPortalWorkOrderItem> byUuid =
                portal.listWorkOrders(actor(PRINCIPAL), "corr-uuid", NETWORK_A.toString());
        assertThat(byUuid.items()).hasSize(1);
    }

    @Test
    void forgedOrNonMemberContextIsPortalContextInvalid() {
        assertThatThrownBy(() -> portal.listWorkOrders(
                actor(PRINCIPAL), "corr-missing", null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));

        assertThatThrownBy(() -> portal.listWorkOrders(
                actor(PRINCIPAL), "corr-forged", "NETWORK|NETWORK|" + UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));

        // 主体对 NETWORK_B 无 membership
        assertThatThrownBy(() -> portal.listWorkOrders(
                actor(PRINCIPAL), "corr-cross", "NETWORK|NETWORK|" + NETWORK_B))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));

        assertThatThrownBy(() -> portal.listTasks(
                actor(PRINCIPAL), "corr-tech-ctx", "TECHNICIAN|NETWORK|" + NETWORK_A))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));
    }

    @Test
    void techniciansAndCapacityAreNetworkScoped() {
        String contextA = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalPage<NetworkPortalTechnicianItem> techs =
                portal.listTechnicians(actor(PRINCIPAL), "corr-techs", contextA);
        assertThat(techs.items()).hasSize(1);
        assertThat(techs.items().getFirst().technicianProfileId()).isEqualTo(TECH_PROFILE);
        assertThat(techs.items().getFirst().displayName()).isEqualTo("网点师傅甲");

        NetworkPortalPage<NetworkPortalCapacityItem> capacity =
                portal.listCapacity(actor(PRINCIPAL), "corr-cap", contextA);
        assertThat(capacity.items()).hasSize(1);
        assertThat(capacity.items().getFirst().maxUnits()).isEqualTo(10);
        assertThat(capacity.items().getFirst().availableUnits()).isEqualTo(7);

        // B 网点成员看不到 A 的师傅/容量
        String contextB = "NETWORK|NETWORK|" + NETWORK_B;
        assertThat(portal.listTechnicians(actor(OTHER_PRINCIPAL), "corr-techs-b", contextB).items())
                .isEmpty();
        assertThat(portal.listCapacity(actor(OTHER_PRINCIPAL), "corr-cap-b", contextB).items())
                .extracting(NetworkPortalCapacityItem::maxUnits)
                .containsExactly(5);
    }

    @Test
    void missingCapabilityIsAccessDeniedAfterMembership() {
        // 撤掉 networkTask.read，保留 membership
        jdbc.sql("""
                UPDATE auth_role_grant SET grant_status='REVOKED', revoked_at=now(),
                       revoked_by='test', revoke_reason='m194',
                       aggregate_version = aggregate_version + 1, updated_at=now()
                 WHERE tenant_id=:tenant AND principal_id=:principal
                   AND scope_type='NETWORK' AND scope_ref=:network
                   AND role_id IN (
                     SELECT role_id FROM auth_role_capability WHERE capability_code='networkTask.read'
                   )
                """)
                .param("tenant", TENANT)
                .param("principal", PRINCIPAL.toString())
                .param("network", NETWORK_A.toString())
                .update();

        assertThatThrownBy(() -> portal.listWorkOrders(
                actor(PRINCIPAL), "corr-cap-missing", "NETWORK|NETWORK|" + NETWORK_A))
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
                ) VALUES (:id, :tenant, 'P-194', 'Partner 194', 'ACTIVE', 1, now(), now())
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

    private void seedTechnicianOn(UUID networkId) {
        jdbc.sql("""
                INSERT INTO net_technician_profile (
                    technician_profile_id, tenant_id, principal_id, display_name, profile_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :principal, '网点师傅甲', 'ACTIVE', 1, now(), now()
                )
                """)
                .param("id", TECH_PROFILE)
                .param("tenant", TENANT)
                .param("principal", TECH_PRINCIPAL)
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
                .param("profile", TECH_PROFILE)
                .update();
    }

    private void seedHumanTask(UUID taskId, UUID workOrderId) {
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
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
                .param("businessKey", "m194:" + taskId)
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

    private void seedActiveNetworkAssignment(
            UUID networkId, UUID workOrderId, UUID taskId, String technicianId
    ) {
        UUID networkAssignmentId = UUID.randomUUID();
        UUID techAssignmentId = UUID.randomUUID();
        UUID sagaNetwork = UUID.randomUUID();
        UUID sagaTech = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-17T01:00:00Z");
        insertAssignment(networkAssignmentId, workOrderId, taskId, "NETWORK", networkId.toString(),
                sagaNetwork, now);
        insertAssignment(techAssignmentId, workOrderId, taskId, "TECHNICIAN", technicianId,
                sagaTech, now);
    }

    private void insertAssignment(
            UUID assignmentId, UUID workOrderId, UUID taskId, String level, String assigneeId,
            UUID sagaId, Instant now
    ) {
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
    }

    private void seedCapacity(UUID networkId, int max, int occupied) {
        jdbc.sql("""
                INSERT INTO dsp_capacity_counter (
                    capacity_counter_id, tenant_id, responsibility_level, assignee_id,
                    business_type, max_units, occupied_units, version, updated_by, updated_at
                ) VALUES (
                    :id, :tenant, 'NETWORK', :assignee,
                    'INSTALLATION', :max, :occupied, 1, 'test', now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("assignee", networkId.toString())
                .param("max", max)
                .param("occupied", occupied)
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
                .param("code", "m194-" + capability + "-" + UUID.randomUUID())
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
