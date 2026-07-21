package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.NetworkPortalAppointmentCalendarView;
import com.serviceos.readmodel.api.NetworkPortalQueryService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M413：本网点预约日历（Asia/Shanghai 运营日 + manageAppointment 硬门禁）。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NetworkPortalAppointmentCalendarPostgresIT {
    private static final String TENANT = "tenant-network-portal-m413";
    private static final UUID PRINCIPAL = UUID.fromString("019f9130-4113-7000-8000-000000000001");
    private static final UUID NETWORK = UUID.fromString("019f9130-4113-7000-8000-000000000010");
    private static final UUID PARTNER = UUID.fromString("019f9130-4113-7000-8000-000000000020");
    private static final UUID TECH_PROFILE = UUID.fromString("019f9130-4113-7000-8000-000000000030");
    private static final UUID TECH_PRINCIPAL = UUID.fromString("019f9130-4113-7000-8000-000000000031");
    private static final UUID WO = UUID.fromString("019f9130-4113-7000-8000-000000000040");
    private static final UUID TASK = UUID.fromString("019f9130-4113-7000-8000-000000000050");
    private static final UUID APPOINTMENT = UUID.fromString("019f9130-4113-7000-8000-000000000060");
    private static final UUID REVISION = UUID.fromString("019f9130-4113-7000-8000-000000000061");
    private static final UUID PROJECT = UUID.fromString("019f9130-4113-7000-8000-000000000070");

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
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.sql("""
                TRUNCATE TABLE dsp_assignment_command_result, dsp_capacity_command_result,
                    dsp_service_assignment_activation_saga, dsp_capacity_reservation,
                    dsp_service_assignment, dsp_capacity_counter,
                    apt_appointment_revision, apt_appointment,
                    tsk_task, wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    auth_delegation_capability, auth_delegation, auth_role_grant_event,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role,
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
        seedGrant(PRINCIPAL, "networkTask.read", "NETWORK", NETWORK.toString());
        seedGrant(PRINCIPAL, "technician.readOwnNetwork", "NETWORK", NETWORK.toString());
        seedGrant(PRINCIPAL, "networkPortal.manageAppointment", "NETWORK", NETWORK.toString());
        seedHumanTask(TASK, WO);
        seedActiveNetworkWithTech(NETWORK, WO, TASK, TECH_PROFILE.toString());
        seedTodayAppointment();
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void calendarReturnsDefaultFourteenDaysWithTodayAppointment() {
        LocalDate today = Instant.now().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        NetworkPortalAppointmentCalendarView calendar = portal.appointmentCalendar(
                actor(PRINCIPAL), "corr-m413", "NETWORK|NETWORK|" + NETWORK, null, null);

        assertThat(calendar.networkId()).isEqualTo(NETWORK);
        assertThat(calendar.timezone()).isEqualTo("Asia/Shanghai");
        assertThat(calendar.rangeStart()).isEqualTo(today);
        assertThat(calendar.rangeEnd()).isEqualTo(today.plusDays(13));
        assertThat(calendar.days()).hasSize(14);
        assertThat(calendar.totalAppointmentCount()).isEqualTo(1);
        assertThat(calendar.truncated()).isFalse();
        assertThat(calendar.days().getFirst().date()).isEqualTo(today);
        assertThat(calendar.days().getFirst().appointmentCount()).isEqualTo(1);
        assertThat(calendar.days().getFirst().items().getFirst().appointmentId()).isEqualTo(APPOINTMENT);
        assertThat(calendar.days().getFirst().items().getFirst().technicianDisplayName())
                .isEqualTo("青岛师傅");
    }

    @Test
    void rejectsRangeLongerThan31Days() {
        LocalDate today = Instant.now().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        assertThatThrownBy(() -> portal.appointmentCalendar(
                actor(PRINCIPAL),
                "corr-m413-span",
                "NETWORK|NETWORK|" + NETWORK,
                today,
                today.plusDays(31)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    private void seedTodayAppointment() {
        // 固定在 Asia/Shanghai 当日上午，避免随测试运行时刻漂移到昨日。
        // appointment ↔ revision 循环外键为 DEFERRABLE，必须同事务写入。
        Instant windowStart = Instant.now().atZone(java.time.ZoneId.of("Asia/Shanghai"))
                .toLocalDate().atTime(10, 0).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
        Instant windowEnd = windowStart.plusSeconds(3600);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.sql("""
                    INSERT INTO apt_appointment (
                        appointment_id, tenant_id, project_id, work_order_id, task_id,
                        appointment_type, status, current_revision_id, current_revision_no,
                        assigned_network_id, technician_id, aggregate_version, created_by, created_at
                    ) VALUES (
                        :id, :tenant, :project, :wo, :task,
                        'INSTALLATION', 'CONFIRMED', :revision, 1,
                        :network, :tech, 1, 'test', now()
                    )
                    """)
                    .param("id", APPOINTMENT)
                    .param("tenant", TENANT)
                    .param("project", PROJECT)
                    .param("wo", WO)
                    .param("task", TASK)
                    .param("network", NETWORK.toString())
                    .param("tech", TECH_PROFILE.toString())
                    .param("revision", REVISION)
                    .update();
            jdbc.sql("""
                    INSERT INTO apt_appointment_revision (
                        revision_id, tenant_id, appointment_id, revision_no, previous_revision_id,
                        window_start, window_end, timezone, estimated_duration_minutes,
                        address_ref, address_version,
                        confirmed_party_type, confirmed_party_ref, confirmation_channel, confirmed_at,
                        reason_code, note, revision_kind, created_by, created_at
                    ) VALUES (
                        :id, :tenant, :appointment, 1, NULL,
                        :start, :end, 'Asia/Shanghai', 60,
                        'address-ref', 'address-v1',
                        'CUSTOMER', 'customer-ref', 'PHONE', now(),
                        NULL, NULL, 'CONFIRM', 'test', now()
                    )
                    """)
                    .param("id", REVISION)
                    .param("appointment", APPOINTMENT)
                    .param("tenant", TENANT)
                    .param("start", java.sql.Timestamp.from(windowStart))
                    .param("end", java.sql.Timestamp.from(windowEnd))
                    .update();
        });
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
                ) VALUES (:id, :tenant, 'P-411', 'Partner 411', 'ACTIVE', 1, now(), now())
                """).param("id", PARTNER).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO net_service_network (
                    service_network_id, tenant_id, partner_organization_id, network_code,
                    network_name, network_status, aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :partner, 'N-411', 'Network 411', 'ACTIVE', 1, now(), now()
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
                .param("businessKey", "m413:" + taskId)
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
        OffsetDateTime scopeNow = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at)
                VALUES (
                    :projectId, :tenantId, 'M413-P', 'BYD', 'M413 today appointment',
                    DATE '2026-07-01', NULL, 'ACTIVE', 1, :createdAt)
                ON CONFLICT (project_id) DO NOTHING
                """)
                .param("projectId", PROJECT)
                .param("tenantId", TENANT)
                .param("createdAt", scopeNow)
                .update();
    }

    private void seedActiveNetworkWithTech(
            UUID networkId, UUID workOrderId, UUID taskId, String technicianId
    ) {
        Instant now = Instant.parse("2026-07-21T01:00:00Z");
        insertAssignment(UUID.randomUUID(), workOrderId, taskId, "NETWORK", networkId.toString(), now);
        insertAssignment(UUID.randomUUID(), workOrderId, taskId, "TECHNICIAN", technicianId, now);
    }

    private void insertAssignment(
            UUID assignmentId, UUID workOrderId, UUID taskId, String level, String assigneeId, Instant now
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
                .param("code", "m413-" + capability + "-" + UUID.randomUUID())
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
