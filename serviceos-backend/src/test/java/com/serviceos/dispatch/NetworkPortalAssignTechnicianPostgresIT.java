package com.serviceos.dispatch;

import com.serviceos.ServiceOsApplication;
import com.serviceos.dispatch.api.ManualServiceAssignmentReceipt;
import com.serviceos.dispatch.api.NetworkPortalAssignTechnicianService;
import com.serviceos.identity.api.CurrentPrincipal;
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

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M196 Network Portal 指派师傅：成功指派、membership/capability 失败关闭、师傅不在网点、
 * 不同网点/不同师傅 ACTIVE 冲突、伪造上下文。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NetworkPortalAssignTechnicianPostgresIT {
    private static final String TENANT = "tenant-network-portal-m196";
    private static final String BUSINESS_TYPE = "INSTALLATION";
    private static final UUID PRINCIPAL = UUID.fromString("019f83b0-1111-7f8c-9505-36fe5c0e8801");
    private static final UUID OTHER_PRINCIPAL = UUID.fromString("019f83b0-1112-7f8c-9505-36fe5c0e8802");
    private static final UUID NETWORK_A = UUID.fromString("019f83b0-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID NETWORK_B = UUID.fromString("019f83b0-3333-7f8c-9505-36fe5c0e8804");
    private static final UUID PARTNER = UUID.fromString("019f83b0-4444-7f8c-9505-36fe5c0e8805");
    private static final UUID TECH_PROFILE = UUID.fromString("019f83b0-5555-7f8c-9505-36fe5c0e8806");
    private static final UUID TECH_PRINCIPAL = UUID.fromString("019f83b0-6666-7f8c-9505-36fe5c0e8807");
    private static final UUID TECH_PROFILE_B = UUID.fromString("019f83b0-5556-7f8c-9505-36fe5c0e8808");
    private static final UUID TECH_PRINCIPAL_B = UUID.fromString("019f83b0-6667-7f8c-9505-36fe5c0e8809");
    private static final UUID WO = UUID.fromString("019f83b0-7777-7f8c-9505-36fe5c0e880a");
    private static final UUID TASK = UUID.fromString("019f83b0-9999-7f8c-9505-36fe5c0e880b");

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

    @Autowired NetworkPortalAssignTechnicianService portalAssign;
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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("125");
        assertThat(flyway.info().applied()).hasSize(127);
        assertThat(jdbc.sql("""
                        SELECT risk_level FROM auth_capability
                         WHERE capability_code='networkPortal.assignTechnician'
                        """).query(String.class).single()).isEqualTo("HIGH");

        seedPrincipal(PRINCIPAL, "Portal Member");
        seedPrincipal(OTHER_PRINCIPAL, "Other Member");
        seedPrincipal(TECH_PRINCIPAL, "Technician A");
        seedPrincipal(TECH_PRINCIPAL_B, "Technician B");
        seedPersona(PRINCIPAL, "NETWORK_MEMBER");
        seedPartnerAndNetworks();
        seedNetworkMembership(PRINCIPAL, NETWORK_A);
        seedNetworkMembership(OTHER_PRINCIPAL, NETWORK_B);
        seedTechnician(TECH_PROFILE, TECH_PRINCIPAL, NETWORK_A, true);
        seedTechnician(TECH_PROFILE_B, TECH_PRINCIPAL_B, NETWORK_B, true);
        seedGrant(PRINCIPAL, "networkPortal.assignTechnician", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL, "dispatch.assignment.manage", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL, "dispatch.capacity.configure", "NETWORK", NETWORK_A.toString());
        seedHumanTask(TASK, WO);
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void successAssignsNetworkAndTechnicianFromTrustedContext() {
        // 本网点已有 ACTIVE NETWORK，尚无 TECHNICIAN —— 典型 Portal 初派
        seedActiveAssignment(NETWORK_A.toString(), "NETWORK", TASK, WO);
        String context = "NETWORK|NETWORK|" + NETWORK_A;

        ManualServiceAssignmentReceipt receipt = portalAssign.assignTechnician(
                actor(PRINCIPAL), metadata("m196-ok"), context, TASK,
                TECH_PROFILE.toString(), BUSINESS_TYPE);

        assertThat(receipt.networkAssigneeId()).isEqualTo(NETWORK_A.toString());
        assertThat(receipt.technicianAssigneeId()).isEqualTo(TECH_PROFILE.toString());
        assertThat(jdbc.sql("""
                        SELECT responsibility_level || ':' || assignee_id || ':' || status
                          FROM dsp_service_assignment
                         WHERE tenant_id = :tenant AND task_id = :task
                         ORDER BY responsibility_level
                        """)
                .param("tenant", TENANT).param("task", TASK)
                .query(String.class).list())
                .contains(
                        "NETWORK:" + NETWORK_A + ":ACTIVE",
                        "TECHNICIAN:" + TECH_PROFILE + ":ACTIVE");

        ManualServiceAssignmentReceipt replay = portalAssign.assignTechnician(
                actor(PRINCIPAL), metadata("m196-ok"), context, TASK,
                TECH_PROFILE.toString(), BUSINESS_TYPE);
        assertThat(replay.networkServiceAssignmentId()).isEqualTo(receipt.networkServiceAssignmentId());
        assertThat(replay.technicianServiceAssignmentId()).isEqualTo(receipt.technicianServiceAssignmentId());
    }

    @Test
    void wrongNetworkMembershipIsPortalContextInvalid() {
        assertThatThrownBy(() -> portalAssign.assignTechnician(
                actor(PRINCIPAL), metadata("m196-cross"), "NETWORK|NETWORK|" + NETWORK_B, TASK,
                TECH_PROFILE.toString(), BUSINESS_TYPE))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));
    }

    @Test
    void technicianNotOnNetworkIsRejected() {
        seedActiveAssignment(NETWORK_A.toString(), "NETWORK", TASK, WO);
        assertThatThrownBy(() -> portalAssign.assignTechnician(
                actor(PRINCIPAL), metadata("m196-tech"), "NETWORK|NETWORK|" + NETWORK_A, TASK,
                TECH_PROFILE_B.toString(), BUSINESS_TYPE))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void differentNetworkActiveConflicts() {
        seedActiveAssignment(NETWORK_B.toString(), "NETWORK", TASK, WO);
        assertThatThrownBy(() -> portalAssign.assignTechnician(
                actor(PRINCIPAL), metadata("m196-net-conflict"), "NETWORK|NETWORK|" + NETWORK_A, TASK,
                TECH_PROFILE.toString(), BUSINESS_TYPE))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.SERVICE_ASSIGNMENT_CONFLICT));
    }

    @Test
    void differentTechnicianActiveConflicts() {
        seedActiveAssignment(NETWORK_A.toString(), "NETWORK", TASK, WO);
        seedActiveAssignment("other-tech-assignee", "TECHNICIAN", TASK, WO);
        assertThatThrownBy(() -> portalAssign.assignTechnician(
                actor(PRINCIPAL), metadata("m196-tech-conflict"), "NETWORK|NETWORK|" + NETWORK_A, TASK,
                TECH_PROFILE.toString(), BUSINESS_TYPE))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.SERVICE_ASSIGNMENT_CONFLICT));
    }

    @Test
    void forgedContextIsPortalContextInvalid() {
        assertThatThrownBy(() -> portalAssign.assignTechnician(
                actor(PRINCIPAL), metadata("m196-forged"), "NETWORK|NETWORK|" + UUID.randomUUID(), TASK,
                TECH_PROFILE.toString(), BUSINESS_TYPE))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));
    }

    @Test
    void missingCapabilityIsAccessDeniedAfterMembership() {
        jdbc.sql("""
                UPDATE auth_role_grant SET grant_status='REVOKED', revoked_at=now(),
                       revoked_by='test', revoke_reason='m196',
                       aggregate_version = aggregate_version + 1, updated_at=now()
                 WHERE tenant_id=:tenant AND principal_id=:principal
                   AND scope_type='NETWORK' AND scope_ref=:network
                   AND role_id IN (
                     SELECT role_id FROM auth_role_capability
                      WHERE capability_code='networkPortal.assignTechnician'
                   )
                """)
                .param("tenant", TENANT)
                .param("principal", PRINCIPAL.toString())
                .param("network", NETWORK_A.toString())
                .update();

        assertThatThrownBy(() -> portalAssign.assignTechnician(
                actor(PRINCIPAL), metadata("m196-cap"), "NETWORK|NETWORK|" + NETWORK_A, TASK,
                TECH_PROFILE.toString(), BUSINESS_TYPE))
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
                ) VALUES (:id, :tenant, 'P-196', 'Partner 196', 'ACTIVE', 1, now(), now())
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

    private void seedTechnician(UUID profileId, UUID principalId, UUID networkId, boolean approvedQual) {
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
                .param("name", "师傅 " + profileId.toString().substring(24))
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
        if (approvedQual) {
            jdbc.sql("""
                    INSERT INTO net_technician_qualification (
                        qualification_id, tenant_id, technician_profile_id, qualification_code,
                        qualification_status, valid_from, valid_to, submitted_by, submitted_at,
                        decided_by, decided_at, decision_reason, aggregate_version
                    ) VALUES (
                        :id, :tenant, :profile, 'EV-INSTALL', 'APPROVED',
                        now() - interval '1 day', now() + interval '365 day',
                        'test', now(), 'test', now(), 'M196 fixture', 1
                    )
                    """)
                    .param("id", UUID.randomUUID())
                    .param("tenant", TENANT)
                    .param("profile", profileId)
                    .update();
        }
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
                .param("businessKey", "m196:" + taskId)
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

    private void seedActiveAssignment(String assigneeId, String level, UUID taskId, UUID workOrderId) {
        UUID assignmentId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        UUID counterId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-17T01:00:00Z");
        // ManualAssign 识别既有 ACTIVE 时 JOIN CONFIRMED reservation；冲突预检只看 assignment 行。
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
                .param("code", "m196-" + capability + "-" + UUID.randomUUID())
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
