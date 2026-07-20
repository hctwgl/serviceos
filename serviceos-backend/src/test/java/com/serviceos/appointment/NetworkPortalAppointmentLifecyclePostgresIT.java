package com.serviceos.appointment;

import com.serviceos.ServiceOsApplication;
import com.serviceos.appointment.api.AppointmentCommandReceipt;
import com.serviceos.appointment.api.AppointmentType;
import com.serviceos.appointment.api.AppointmentWindow;
import com.serviceos.appointment.api.NetworkPortalAppointmentService;
import com.serviceos.appointment.api.ProposeAppointmentCommand;
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
 * M198 Network Portal 预约生命周期：本网点改约+取消成功；跨网点拒绝；伪造上下文拒绝；版本冲突。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NetworkPortalAppointmentLifecyclePostgresIT {
    private static final String TENANT = "tenant-network-portal-m198";
    private static final UUID PRINCIPAL = UUID.fromString("019f83c1-1111-7f8c-9505-36fe5c0e8801");
    private static final UUID OTHER_PRINCIPAL = UUID.fromString("019f83c1-1112-7f8c-9505-36fe5c0e8802");
    private static final UUID NETWORK_A = UUID.fromString("019f83c1-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID NETWORK_B = UUID.fromString("019f83c1-3333-7f8c-9505-36fe5c0e8804");
    private static final UUID PARTNER = UUID.fromString("019f83c1-4444-7f8c-9505-36fe5c0e8805");
    private static final UUID PROJECT = UUID.fromString("019f83c1-5555-7f8c-9505-36fe5c0e8806");
    private static final UUID WO = UUID.fromString("019f83c1-7777-7f8c-9505-36fe5c0e880a");
    private static final UUID TASK_A = UUID.fromString("019f83c1-9999-7f8c-9505-36fe5c0e880b");
    private static final UUID TASK_B = UUID.fromString("019f83c1-999a-7f8c-9505-36fe5c0e880c");

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

    @Autowired NetworkPortalAppointmentService portalAppointments;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.sql("""
                TRUNCATE TABLE apt_contact_attempt_command_result, apt_contact_attempt,
                    apt_appointment_command_result, apt_appointment_status_history,
                    apt_appointment_revision, apt_appointment,
                    dsp_service_assignment, tsk_task,
                    auth_delegation_capability, auth_delegation, auth_role_grant_event,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role,
                    net_network_membership, net_service_network, net_partner_organization,
                    net_directory_event, net_clearance_work_item,
                    idn_principal_lifecycle_event, idn_principal_persona, idn_identity_link,
                    idn_person_profile, idn_security_principal,
                    rel_idempotency_record, rel_outbox_event, aud_audit_record CASCADE
                """).update();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("135");
        assertThat(flyway.info().applied()).hasSize(137);

        seedPrincipal(PRINCIPAL, "Portal Member");
        seedPrincipal(OTHER_PRINCIPAL, "Other Member");
        seedPersona(PRINCIPAL, "NETWORK_MEMBER");
        seedPartnerAndNetworks();
        seedNetworkMembership(PRINCIPAL, NETWORK_A);
        seedNetworkMembership(OTHER_PRINCIPAL, NETWORK_B);
        seedGrant(PRINCIPAL, "networkPortal.manageAppointment", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL, "appointment.read", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL, "appointment.propose", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL, "appointment.manage", "NETWORK", NETWORK_A.toString());
        // cancel 委托 AppointmentService.cancel，底层能力为 appointment.cancel（非 manage）
        seedGrant(PRINCIPAL, "appointment.cancel", "NETWORK", NETWORK_A.toString());
        seedGrant(OTHER_PRINCIPAL, "networkPortal.manageAppointment", "NETWORK", NETWORK_B.toString());
        seedGrant(OTHER_PRINCIPAL, "appointment.read", "NETWORK", NETWORK_B.toString());
        seedGrant(OTHER_PRINCIPAL, "appointment.propose", "NETWORK", NETWORK_B.toString());
        seedGrant(OTHER_PRINCIPAL, "appointment.manage", "NETWORK", NETWORK_B.toString());
        seedGrant(OTHER_PRINCIPAL, "appointment.cancel", "NETWORK", NETWORK_B.toString());
        seedHumanTask(TASK_A, WO);
        seedHumanTask(TASK_B, WO);
        seedActiveAssignment(NETWORK_A.toString(), "NETWORK", TASK_A, WO);
        seedActiveAssignment(NETWORK_B.toString(), "NETWORK", TASK_B, WO);
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void rescheduleAndCancelSucceedOnNetworkOwnedAppointment() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        AppointmentCommandReceipt proposed = portalAppointments.propose(
                actor(PRINCIPAL), metadata("m198-propose"), context, TASK_A,
                new ProposeAppointmentCommand(
                        TASK_A, AppointmentType.SURVEY, window("2026-09-10T01:00:00Z"),
                        "addr-ref-m198", "addr-v1"));
        AppointmentCommandReceipt confirmed = portalAppointments.confirm(
                actor(PRINCIPAL), metadata("m198-confirm"), context,
                proposed.appointmentId(), proposed.aggregateVersion(),
                "NETWORK_MEMBER", PRINCIPAL.toString(), "PHONE");
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");

        AppointmentCommandReceipt rescheduled = portalAppointments.reschedule(
                actor(PRINCIPAL), metadata("m198-reschedule"), context,
                confirmed.appointmentId(), confirmed.aggregateVersion(),
                window("2026-09-11T02:00:00Z"), "CUSTOMER_REQUESTED_LATER", "网点改约");
        assertThat(rescheduled.status()).isEqualTo("PROPOSED");
        assertThat(rescheduled.aggregateVersion()).isEqualTo(confirmed.aggregateVersion() + 1);

        AppointmentCommandReceipt cancelled = portalAppointments.cancel(
                actor(PRINCIPAL), metadata("m198-cancel"), context,
                rescheduled.appointmentId(), rescheduled.aggregateVersion(),
                "CUSTOMER_CANCELLED", "网点取消");
        assertThat(cancelled.status()).isEqualTo("CANCELLED");

        AppointmentCommandReceipt replay = portalAppointments.cancel(
                actor(PRINCIPAL), metadata("m198-cancel"), context,
                rescheduled.appointmentId(), rescheduled.aggregateVersion(),
                "CUSTOMER_CANCELLED", "网点取消");
        assertThat(replay.appointmentId()).isEqualTo(cancelled.appointmentId());
        assertThat(replay.aggregateVersion()).isEqualTo(cancelled.aggregateVersion());
    }

    @Test
    void crossNetworkAppointmentIsForbidden() {
        String contextB = "NETWORK|NETWORK|" + NETWORK_B;
        AppointmentCommandReceipt proposed = portalAppointments.propose(
                actor(OTHER_PRINCIPAL), metadata("m198-b-propose"), contextB, TASK_B,
                new ProposeAppointmentCommand(
                        TASK_B, AppointmentType.SURVEY, window("2026-09-12T01:00:00Z"),
                        "addr-ref", "addr-v1"));
        AppointmentCommandReceipt confirmed = portalAppointments.confirm(
                actor(OTHER_PRINCIPAL), metadata("m198-b-confirm"), contextB,
                proposed.appointmentId(), proposed.aggregateVersion(),
                "NETWORK_MEMBER", OTHER_PRINCIPAL.toString(), "PHONE");

        String contextA = "NETWORK|NETWORK|" + NETWORK_A;
        assertThatThrownBy(() -> portalAppointments.reschedule(
                actor(PRINCIPAL), metadata("m198-cross"), contextA,
                confirmed.appointmentId(), confirmed.aggregateVersion(),
                window("2026-09-13T01:00:00Z"), "CUSTOMER_REQUESTED_LATER", null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThatThrownBy(() -> portalAppointments.cancel(
                actor(PRINCIPAL), metadata("m198-cross-cancel"), contextA,
                confirmed.appointmentId(), confirmed.aggregateVersion(),
                "CUSTOMER_CANCELLED", null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    @Test
    void forgedContextIsPortalContextInvalid() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        AppointmentCommandReceipt proposed = portalAppointments.propose(
                actor(PRINCIPAL), metadata("m198-forge-propose"), context, TASK_A,
                new ProposeAppointmentCommand(
                        TASK_A, AppointmentType.SURVEY, window("2026-09-14T01:00:00Z"),
                        "addr-ref", "addr-v1"));

        assertThatThrownBy(() -> portalAppointments.cancel(
                actor(PRINCIPAL), metadata("m198-forged"),
                "NETWORK|NETWORK|" + UUID.randomUUID(),
                proposed.appointmentId(), proposed.aggregateVersion(),
                "CUSTOMER_CANCELLED", null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));
    }

    @Test
    void staleVersionConflictsWithoutOverwrite() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        AppointmentCommandReceipt proposed = portalAppointments.propose(
                actor(PRINCIPAL), metadata("m198-ver-propose"), context, TASK_A,
                new ProposeAppointmentCommand(
                        TASK_A, AppointmentType.SURVEY, window("2026-09-15T01:00:00Z"),
                        "addr-ref", "addr-v1"));
        AppointmentCommandReceipt confirmed = portalAppointments.confirm(
                actor(PRINCIPAL), metadata("m198-ver-confirm"), context,
                proposed.appointmentId(), proposed.aggregateVersion(),
                "NETWORK_MEMBER", PRINCIPAL.toString(), "PHONE");

        portalAppointments.reschedule(
                actor(PRINCIPAL), metadata("m198-ver-winner"), context,
                confirmed.appointmentId(), confirmed.aggregateVersion(),
                window("2026-09-16T01:00:00Z"), "CUSTOMER_REQUESTED_LATER", null);

        assertThatThrownBy(() -> portalAppointments.reschedule(
                actor(PRINCIPAL), metadata("m198-ver-loser"), context,
                confirmed.appointmentId(), confirmed.aggregateVersion(),
                window("2026-09-17T01:00:00Z"), "NETWORK_CONFLICT", null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.APPOINTMENT_VERSION_CONFLICT));
    }

    private AppointmentWindow window(String start) {
        Instant from = Instant.parse(start);
        return new AppointmentWindow(from, from.plusSeconds(3 * 60 * 60), "Asia/Shanghai", 120);
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
                ) VALUES (:id, :tenant, 'P-198', 'Partner 198', 'ACTIVE', 1, now(), now())
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
                    :taskId, :tenantId, 'SURVEY_TASK', 'HUMAN', :businessKey, :digest,
                    500, 'READY', :now, 0, 3, 'corr-seed', 1, :now, :now, :projectId,
                    :workOrderId, :workflowInstanceId, :stageInstanceId, :workflowNodeInstanceId,
                    'SURVEY_NODE', :definitionId, :definitionDigest, :bundleId, :bundleDigest,
                    'SURVEY'
                )
                """)
                .param("taskId", taskId)
                .param("tenantId", TENANT)
                .param("businessKey", "m198:" + taskId)
                .param("digest", "a".repeat(64))
                .param("now", java.sql.Timestamp.from(now))
                .param("projectId", PROJECT)
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
        Instant now = Instant.parse("2026-07-17T01:00:00Z");
        jdbc.sql("""
                INSERT INTO dsp_service_assignment (
                    service_assignment_id, tenant_id, work_order_id, task_id,
                    responsibility_level, assignee_id, business_type, source_decision_id,
                    status, activation_saga_id, effective_from, created_by, created_at,
                    authority_assignment_id, authority_version,
                    fence_decision_id, fence_policy_version
                ) VALUES (
                    :id, :tenant, :workOrderId, :taskId,
                    :level, :assignee, 'SURVEY', :decision,
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
                .param("saga", UUID.randomUUID())
                .param("now", java.sql.Timestamp.from(now))
                .param("authorityId", "authority://" + assignmentId)
                .param("fenceDecision", "fence://" + assignmentId)
                .param("fencePolicy", "fence-policy-v1")
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
                .param("code", "m198-" + capability + "-" + UUID.randomUUID())
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
