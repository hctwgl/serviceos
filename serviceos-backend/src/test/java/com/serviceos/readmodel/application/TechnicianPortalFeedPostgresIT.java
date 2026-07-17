package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.TechnicianPortalFeedItem;
import com.serviceos.readmodel.api.TechnicianPortalFeedPage;
import com.serviceos.readmodel.api.TechnicianPortalQueryService;
import com.serviceos.readmodel.api.TechnicianPortalSchedulePage;
import com.serviceos.readmodel.api.TechnicianPortalSyncSummary;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M195 Technician Portal Feed：本人 ACTIVE 责任、tombstone、日程、sync-summary、跨网点与非师傅拒绝。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TechnicianPortalFeedPostgresIT {
    private static final String TENANT = "tenant-technician-portal-m195";
    private static final UUID TECH_PRINCIPAL = UUID.fromString("019f83b0-1111-7f8c-9505-36fe5c0e8801");
    private static final UUID OTHER_TECH_PRINCIPAL = UUID.fromString("019f83b0-1112-7f8c-9505-36fe5c0e8802");
    private static final UUID NON_TECH_PRINCIPAL = UUID.fromString("019f83b0-1113-7f8c-9505-36fe5c0e8803");
    private static final UUID NETWORK_A = UUID.fromString("019f83b0-2222-7f8c-9505-36fe5c0e8804");
    private static final UUID NETWORK_B = UUID.fromString("019f83b0-3333-7f8c-9505-36fe5c0e8805");
    private static final UUID PARTNER = UUID.fromString("019f83b0-4444-7f8c-9505-36fe5c0e8806");
    private static final UUID TECH_PROFILE_A = UUID.fromString("019f83b0-5555-7f8c-9505-36fe5c0e8807");
    private static final UUID TECH_PROFILE_B = UUID.fromString("019f83b0-5556-7f8c-9505-36fe5c0e8808");
    private static final UUID WO_A = UUID.fromString("019f83b0-7777-7f8c-9505-36fe5c0e8809");
    private static final UUID WO_B = UUID.fromString("019f83b0-8888-7f8c-9505-36fe5c0e880a");
    private static final UUID TASK_A = UUID.fromString("019f83b0-9999-7f8c-9505-36fe5c0e880b");
    private static final UUID TASK_B = UUID.fromString("019f83b0-aaaa-7f8c-9505-36fe5c0e880c");
    private static final UUID TASK_OTHER = UUID.fromString("019f83b0-bbbb-7f8c-9505-36fe5c0e880d");
    private static final UUID PROJECT_A = UUID.fromString("019f83b0-cccc-7f8c-9505-36fe5c0e880e");
    private static final UUID APPOINTMENT_A = UUID.fromString("019f83b0-dddd-7f8c-9505-36fe5c0e880f");
    private static final UUID TECH_ASSIGNMENT_A = UUID.fromString("019f83b0-eeee-7f8c-9505-36fe5c0e8810");

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

    @Autowired TechnicianPortalQueryService portal;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.sql("""
                TRUNCATE TABLE apt_appointment_command_result, apt_contact_attempt_command_result,
                    apt_contact_attempt, apt_appointment_status_history, apt_appointment,
                    apt_appointment_revision,
                    dsp_assignment_command_result, dsp_capacity_command_result,
                    dsp_service_assignment_activation_saga, dsp_capacity_reservation,
                    dsp_service_assignment, dsp_capacity_counter,
                    tsk_task_assignment, tsk_task_assignment_batch, tsk_task,
                    auth_delegation_capability, auth_delegation, auth_role_grant_event,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role,
                    net_technician_qualification, net_network_technician_membership,
                    net_technician_profile, net_network_membership, net_service_network,
                    net_partner_organization, net_directory_event, net_clearance_work_item,
                    idn_principal_lifecycle_event, idn_principal_persona, idn_identity_link,
                    idn_person_profile, idn_security_principal,
                    rel_idempotency_record, aud_audit_record CASCADE
                """).update();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("099");
        assertThat(flyway.info().applied()).hasSize(101);

        seedPrincipal(TECH_PRINCIPAL, "Technician A");
        seedPrincipal(OTHER_TECH_PRINCIPAL, "Technician B");
        seedPrincipal(NON_TECH_PRINCIPAL, "Staff Only");
        seedPersona(TECH_PRINCIPAL, "TECHNICIAN");
        seedPersona(OTHER_TECH_PRINCIPAL, "TECHNICIAN");
        seedPersona(NON_TECH_PRINCIPAL, "NETWORK_MEMBER");
        seedPartnerAndNetworks();
        seedTechnician(TECH_PROFILE_A, TECH_PRINCIPAL, NETWORK_A, "师傅甲");
        seedTechnician(TECH_PROFILE_B, OTHER_TECH_PRINCIPAL, NETWORK_B, "师傅乙");
        seedGrant(TECH_PRINCIPAL, "task.readAssigned", "NETWORK", NETWORK_A.toString());
        seedGrant(OTHER_TECH_PRINCIPAL, "task.readAssigned", "NETWORK", NETWORK_B.toString());
        seedHumanTask(TASK_A, WO_A, PROJECT_A);
        seedHumanTask(TASK_B, WO_B, UUID.randomUUID());
        seedHumanTask(TASK_OTHER, UUID.randomUUID(), UUID.randomUUID());
        // 本人 ACTIVE：assignee = principalId
        seedActivePair(NETWORK_A, WO_A, TASK_A, TECH_PRINCIPAL.toString(), TECH_ASSIGNMENT_A);
        // 同网点另一师傅责任（不应出现在本人 feed）
        seedActivePair(NETWORK_A, UUID.randomUUID(), TASK_OTHER, "other-assignee", UUID.randomUUID());
        // 跨网点师傅责任
        seedActivePair(NETWORK_B, WO_B, TASK_B, OTHER_TECH_PRINCIPAL.toString(), UUID.randomUUID());
        seedAppointment(APPOINTMENT_A, TASK_A, WO_A, PROJECT_A);
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void feedContainsOnlyCurrentTechnicianAssignments() {
        String context = "TECHNICIAN|NETWORK|" + NETWORK_A;
        TechnicianPortalFeedPage feed = portal.taskFeed(actor(TECH_PRINCIPAL), "corr-feed", context, null);
        assertThat(feed.networkId()).isEqualTo(NETWORK_A);
        assertThat(feed.items()).extracting(TechnicianPortalFeedItem::taskId).containsExactly(TASK_A);
        assertThat(feed.items()).noneMatch(item -> TASK_B.equals(item.taskId()) || TASK_OTHER.equals(item.taskId()));
        assertThat(feed.items().getFirst().itemType()).isEqualTo("ASSIGNMENT");
        assertThat(feed.items().getFirst().taskStatus()).isEqualTo("READY");
        assertThat(feed.items().getFirst().invalidationReason()).isNull();

        // 纯 UUID 形态在 ACTIVE 师傅成员下也可接受
        TechnicianPortalFeedPage byUuid =
                portal.taskFeed(actor(TECH_PRINCIPAL), "corr-uuid", NETWORK_A.toString(), null);
        assertThat(byUuid.items()).hasSize(1);
    }

    @Test
    void tombstoneAppearsAfterAssignmentEndedWithCursor() {
        String context = "TECHNICIAN|NETWORK|" + NETWORK_A;
        TechnicianPortalFeedPage before = portal.taskFeed(actor(TECH_PRINCIPAL), "corr-before", context, null);
        String cursor = before.items().getFirst().cursor();

        Instant endedAt = Instant.parse("2026-07-17T03:00:00Z");
        jdbc.sql("""
                UPDATE dsp_service_assignment
                   SET status='ENDED',
                       effective_to=:endedAt,
                       ended_by='test',
                       end_reason_code='REASSIGNED'
                 WHERE service_assignment_id=:id
                """)
                .param("endedAt", java.sql.Timestamp.from(endedAt))
                .param("id", TECH_ASSIGNMENT_A)
                .update();

        TechnicianPortalFeedPage delta =
                portal.taskFeed(actor(TECH_PRINCIPAL), "corr-tomb", context, cursor);
        assertThat(delta.items()).hasSize(1);
        assertThat(delta.items().getFirst().itemType()).isEqualTo("TOMBSTONE");
        assertThat(delta.items().getFirst().taskId()).isEqualTo(TASK_A);
        assertThat(delta.items().getFirst().invalidationReason()).isEqualTo("REASSIGNED");
        assertThat(delta.items().getFirst().workOrderId()).isNull();
        assertThat(delta.items().getFirst().taskStatus()).isNull();
    }

    @Test
    void scheduleAndSyncSummaryFanInActiveTasks() {
        String context = "TECHNICIAN|NETWORK|" + NETWORK_A;
        TechnicianPortalSchedulePage schedule =
                portal.schedule(actor(TECH_PRINCIPAL), "corr-sched", context);
        assertThat(schedule.items()).hasSize(1);
        assertThat(schedule.items().getFirst().appointmentId()).isEqualTo(APPOINTMENT_A);
        assertThat(schedule.items().getFirst().taskId()).isEqualTo(TASK_A);
        assertThat(schedule.items().getFirst().type()).isEqualTo("INSTALLATION");

        TechnicianPortalSyncSummary summary =
                portal.syncSummary(actor(TECH_PRINCIPAL), "corr-sync", context);
        assertThat(summary.pendingFeedItemCount()).isEqualTo(1);
        assertThat(summary.appointmentWindowCount()).isEqualTo(1);
        assertThat(summary.tombstoneCount()).isEqualTo(0);
    }

    @Test
    void crossNetworkAndNonTechnicianArePortalContextInvalid() {
        assertThatThrownBy(() -> portal.taskFeed(
                actor(TECH_PRINCIPAL), "corr-cross", "TECHNICIAN|NETWORK|" + NETWORK_B, null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));

        assertThatThrownBy(() -> portal.taskFeed(
                actor(NON_TECH_PRINCIPAL), "corr-non", "TECHNICIAN|NETWORK|" + NETWORK_A, null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));

        assertThatThrownBy(() -> portal.taskFeed(
                actor(TECH_PRINCIPAL), "corr-forged", "TECHNICIAN|NETWORK|" + UUID.randomUUID(), null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));

        assertThatThrownBy(() -> portal.taskFeed(
                actor(TECH_PRINCIPAL), "corr-net-ctx", "NETWORK|NETWORK|" + NETWORK_A, null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));
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
                ) VALUES (:id, :tenant, 'P-195', 'Partner 195', 'ACTIVE', 1, now(), now())
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

    private void seedHumanTask(UUID taskId, UUID workOrderId, UUID projectId) {
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
                .param("businessKey", "m195:" + taskId)
                .param("digest", "a".repeat(64))
                .param("now", java.sql.Timestamp.from(now))
                .param("projectId", projectId)
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

    private void seedActivePair(
            UUID networkId, UUID workOrderId, UUID taskId, String technicianAssigneeId,
            UUID techAssignmentId
    ) {
        Instant now = Instant.parse("2026-07-17T01:00:00Z");
        insertAssignment(UUID.randomUUID(), workOrderId, taskId, "NETWORK", networkId.toString(),
                UUID.randomUUID(), now);
        insertAssignment(techAssignmentId, workOrderId, taskId, "TECHNICIAN", technicianAssigneeId,
                UUID.randomUUID(), now);
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

    private void seedAppointment(UUID appointmentId, UUID taskId, UUID workOrderId, UUID projectId) {
        UUID revisionId = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-18T08:00:00Z");
        Instant end = Instant.parse("2026-07-18T10:00:00Z");
        Instant createdAt = Instant.parse("2026-07-17T01:30:00Z");
        // current_revision FK 为 DEFERRABLE：同事务内先插预约再插修订
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.sql("""
                    INSERT INTO apt_appointment (
                        appointment_id, tenant_id, project_id, work_order_id, task_id,
                        appointment_type, status, current_revision_id, current_revision_no,
                        assigned_network_id, technician_id, aggregate_version, created_by, created_at
                    ) VALUES (
                        :id, :tenant, :projectId, :workOrderId, :taskId,
                        'INSTALLATION', 'CONFIRMED', :revisionId, 1,
                        :network, :tech, 1, 'test', :createdAt
                    )
                    """)
                    .param("id", appointmentId)
                    .param("tenant", TENANT)
                    .param("projectId", projectId)
                    .param("workOrderId", workOrderId)
                    .param("taskId", taskId)
                    .param("revisionId", revisionId)
                    .param("network", NETWORK_A.toString())
                    .param("tech", TECH_PRINCIPAL.toString())
                    .param("createdAt", java.sql.Timestamp.from(createdAt))
                    .update();
            jdbc.sql("""
                    INSERT INTO apt_appointment_revision (
                        revision_id, tenant_id, appointment_id, revision_no, previous_revision_id,
                        window_start, window_end, timezone, estimated_duration_minutes,
                        address_ref, address_version,
                        confirmed_party_type, confirmed_party_ref, confirmation_channel, confirmed_at,
                        reason_code, note, revision_kind, created_by, created_at
                    ) VALUES (
                        :revisionId, :tenant, :appointmentId, 1, NULL,
                        :start, :end, 'Asia/Shanghai', 120,
                        'addr-ref', 'v1',
                        'CUSTOMER', 'customer-ref', 'PHONE', :confirmedAt,
                        NULL, 'note-should-not-leak', 'CONFIRM', 'test', :createdAt
                    )
                    """)
                    .param("revisionId", revisionId)
                    .param("tenant", TENANT)
                    .param("appointmentId", appointmentId)
                    .param("start", java.sql.Timestamp.from(start))
                    .param("end", java.sql.Timestamp.from(end))
                    .param("confirmedAt", java.sql.Timestamp.from(createdAt))
                    .param("createdAt", java.sql.Timestamp.from(createdAt))
                    .update();
        });
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
                .param("code", "m195-" + capability + "-" + UUID.randomUUID())
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
                "technician-portal", Set.of());
    }
}
