package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.NetworkPortalCapacityItem;
import com.serviceos.readmodel.api.NetworkPortalExceptionItem;
import com.serviceos.readmodel.api.NetworkPortalPage;
import com.serviceos.readmodel.api.NetworkPortalQueryService;
import com.serviceos.readmodel.api.NetworkPortalTaskItem;
import com.serviceos.readmodel.api.NetworkPortalTechnicianItem;
import com.serviceos.readmodel.api.NetworkPortalWorkbenchView;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderItem;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderWorkspace;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderWorkspaceSlaSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceCorrectionCaseSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceEvidenceItemSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceEvidenceSlotSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceFormSubmissionSummary;
import com.serviceos.shared.Sha256;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private static final UUID TASK_A2 = UUID.fromString("019f83a0-999a-7f8c-9505-36fe5c0e880c");
    private static final UUID TASK_B = UUID.fromString("019f83a0-aaaa-7f8c-9505-36fe5c0e880b");
    private static final String SLA_REF = "install.sla";
    private static final String SLA_POLICY_DIGEST = "d".repeat(64);

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
                    sla_milestone, sla_clock_segment, sla_instance,
                    frm_form_command_result, frm_submission_validation, frm_form_submission,
                    fld_visit_command_result, fld_visit_fact, fld_visit,
                    evd_correction_resubmission, evd_correction_case, evd_correction_command_result,
                    evd_review_decision, evd_review_case, evd_review_command_result,
                    evd_evidence_set_member, evd_evidence_set_snapshot,
                    evd_evidence_revision, evd_evidence_item, evd_evidence_upload_session,
                    evd_evidence_resolution_member, evd_evidence_slot, evd_task_evidence_resolution,
                    ops_exception_ack_result, ops_operational_exception,
                    tsk_task,
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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("100");
        assertThat(flyway.info().applied()).hasSize(102);

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
        seedHumanTask(TASK_A2, WO_A);
        seedHumanTask(TASK_B, WO_B);
        seedActiveNetworkAssignment(NETWORK_A, WO_A, TASK_A, "tech-a");
        seedActiveNetworkAssignment(NETWORK_A, WO_A, TASK_A2, "tech-a");
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
        assertThat(tasks.items()).extracting(NetworkPortalTaskItem::taskId)
                .containsExactlyInAnyOrder(TASK_A, TASK_A2);
        assertThat(tasks.items()).allMatch(item -> "READY".equals(item.status()));

        NetworkPortalWorkbenchView workbench = portal.workbench(actor(PRINCIPAL), "corr-wb", context);
        assertThat(workbench.activeWorkOrderCount()).isEqualTo(1);
        assertThat(workbench.activeTaskCount()).isEqualTo(2);
        assertThat(workbench.activeTechnicianCount()).isEqualTo(1);
        assertThat(workbench.unassignedTechnicianTaskCount()).isEqualTo(0);
        assertThat(workbench.openCorrectionCaseCount()).isNull();
        assertThat(workbench.openOperationalExceptionCount()).isNull();
        // PRINCIPAL 另有 technician.readOwnNetwork，故 pendingQualificationCount 存在（本夹具无 PENDING）
        assertThat(workbench.pendingQualificationCount()).isEqualTo(0);
        assertThat(workbench.capacity()).extracting(NetworkPortalCapacityItem::occupiedUnits)
                .containsExactly(3);

        // 纯 UUID 形态在 ACTIVE membership 下也可接受
        NetworkPortalPage<NetworkPortalWorkOrderItem> byUuid =
                portal.listWorkOrders(actor(PRINCIPAL), "corr-uuid", NETWORK_A.toString());
        assertThat(byUuid.items()).hasSize(1);
    }

    @Test
    void workOrderWorkspaceReturnsActiveTasksAndDeniesForeignWorkOrders() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalWorkOrderWorkspace workspace = portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws", context, WO_A);
        assertThat(workspace.networkId()).isEqualTo(NETWORK_A);
        assertThat(workspace.workOrderId()).isEqualTo(WO_A);
        assertThat(workspace.taskIds()).containsExactlyInAnyOrder(TASK_A, TASK_A2);
        assertThat(workspace.tasks()).extracting(NetworkPortalTaskItem::taskId)
                .containsExactlyInAnyOrder(TASK_A, TASK_A2);
        assertThat(workspace.tasks().getFirst().status()).isEqualTo("READY");
        assertThat(workspace.technicianId()).isEqualTo("tech-a");

        assertThatThrownBy(() -> portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws-foreign", context, WO_B))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));

        assertThatThrownBy(() -> portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws-unknown", context, UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        // M221/M222/M223/M225/M226：无 soft-gate 能力时省略 enrichment 字段
        assertThat(workspace.slaSummary()).isNull();
        assertThat(workspace.visits()).isNull();
        assertThat(workspace.formSubmissions()).isNull();
        assertThat(workspace.evidenceSlots()).isNull();
        assertThat(workspace.evidenceItems()).isNull();
        assertThat(workspace.corrections()).isNull();
        assertThat(workspace.exceptions()).isNull();
    }

    @Test
    void workOrderWorkspaceExceptionSummariesAreCapabilityGatedAndTaskScoped() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalWorkOrderWorkspace withoutCap = portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws-exc-omit", context, WO_A);
        assertThat(withoutCap.exceptions()).isNull();

        seedGrant(PRINCIPAL, "operations.exception.read", "NETWORK", NETWORK_A.toString());
        NetworkPortalWorkOrderWorkspace empty = portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws-exc-empty", context, WO_A);
        assertThat(empty.exceptions()).isEmpty();

        UUID exceptionA = seedOpenException(TASK_A, WO_A, "P1", "m226-a");
        seedOpenException(TASK_B, WO_B, "P2", "m226-b");

        NetworkPortalWorkOrderWorkspace withData = portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws-exc", context, WO_A);
        assertThat(withData.exceptions())
                .extracting(NetworkPortalExceptionItem::exceptionId)
                .containsExactly(exceptionA);
        assertThat(withData.exceptions().getFirst().status()).isEqualTo("OPEN");
        assertThat(withData.exceptions().getFirst().severity()).isEqualTo("P1");
        assertThat(withData.exceptions().getFirst().allowedActions()).isEmpty();
    }

    @Test
    void workOrderWorkspaceVisitAndFormSummariesAreCapabilityGatedAndTaskScoped() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalWorkOrderWorkspace withoutCap = portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws-vf-omit", context, WO_A);
        assertThat(withoutCap.visits()).isNull();
        assertThat(withoutCap.formSubmissions()).isNull();

        seedGrant(PRINCIPAL, "visit.read", "NETWORK", NETWORK_A.toString());
        NetworkPortalWorkOrderWorkspace withVisitOnly = portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws-visit-empty", context, WO_A);
        assertThat(withVisitOnly.visits()).isEmpty();
        assertThat(withVisitOnly.formSubmissions()).isNull();

        seedGrant(PRINCIPAL, "form.read", "NETWORK", NETWORK_A.toString());
        seedFormSubmission(TASK_A, "install.form");
        seedFormSubmission(TASK_B, "foreign.form");
        NetworkPortalWorkOrderWorkspace withBoth = portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws-form", context, WO_A);
        assertThat(withBoth.visits()).isEmpty();
        assertThat(withBoth.formSubmissions())
                .extracting(NetworkPortalWorkspaceFormSubmissionSummary::taskId)
                .containsExactly(TASK_A);
        assertThat(withBoth.formSubmissions().getFirst().formKey()).isEqualTo("install.form");
        assertThat(withBoth.formSubmissions().getFirst().validationStatus()).isEqualTo("VALIDATED");
    }

    @Test
    void workOrderWorkspaceEvidenceSummariesAreCapabilityGatedAndTaskScoped() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalWorkOrderWorkspace withoutCap = portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws-evd-omit", context, WO_A);
        assertThat(withoutCap.evidenceSlots()).isNull();
        assertThat(withoutCap.evidenceItems()).isNull();
        assertThat(withoutCap.corrections()).isNull();

        seedGrant(PRINCIPAL, "evidence.read", "NETWORK", NETWORK_A.toString());
        NetworkPortalWorkOrderWorkspace empty = portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws-evd-empty", context, WO_A);
        assertThat(empty.evidenceSlots()).isEmpty();
        assertThat(empty.evidenceItems()).isEmpty();
        assertThat(empty.corrections()).isEmpty();

        UUID slotA = seedEvidenceSlot(TASK_A, "site.photo", "现场照片");
        UUID itemA = seedEvidenceItem(TASK_A, slotA);
        seedEvidenceSlot(TASK_B, "foreign.photo", "他网点照片");
        UUID correctionA = seedOpenCorrection(TASK_A, "m225-a");
        seedOpenCorrection(TASK_B, "m225-b");

        NetworkPortalWorkOrderWorkspace withData = portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws-evd", context, WO_A);
        assertThat(withData.evidenceSlots())
                .extracting(NetworkPortalWorkspaceEvidenceSlotSummary::taskId)
                .containsExactly(TASK_A);
        assertThat(withData.evidenceSlots().getFirst().requirementCode()).isEqualTo("site.photo");
        assertThat(withData.evidenceSlots().getFirst().mediaType()).isEqualTo("PHOTO");
        assertThat(withData.evidenceItems())
                .extracting(NetworkPortalWorkspaceEvidenceItemSummary::evidenceItemId)
                .containsExactly(itemA);
        assertThat(withData.evidenceItems().getFirst().status()).isEqualTo("OPEN");
        assertThat(withData.evidenceItems().getFirst().revisionCount()).isZero();
        assertThat(withData.corrections())
                .extracting(NetworkPortalWorkspaceCorrectionCaseSummary::correctionCaseId)
                .containsExactly(correctionA);
        assertThat(withData.corrections().getFirst().status()).isEqualTo("OPEN");
        assertThat(withData.corrections().getFirst().reasonCodes()).containsExactly("MISSING_PHOTO");
        assertThat(withData.corrections().getFirst().resubmissions()).isEmpty();
    }

    @Test
    void workOrderWorkspaceSlaSummaryIsCapabilityGatedAndTaskScoped() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        // 每任务至多一条 sla_instance；用 TASK_A=RUNNING、TASK_A2=BREACHED 得到 open=2/breached=1
        UUID policyA = prepareSlaScopeForTask(TASK_A, "m221-a");
        alignTaskSlaScope(TASK_A2, TASK_A);
        UUID policyB = prepareSlaScopeForTask(TASK_B, "m221-b");
        seedSlaInstance(TASK_A, policyA, "RUNNING");
        seedSlaInstance(TASK_A2, policyA, "BREACHED");
        // 他网点任务实例不得计入本网点工作区
        seedSlaInstance(TASK_B, policyB, "BREACHED");

        NetworkPortalWorkOrderWorkspace withoutCap = portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws-sla-omit", context, WO_A);
        assertThat(withoutCap.slaSummary()).isNull();

        seedGrant(PRINCIPAL, "sla.read", "NETWORK", NETWORK_A.toString());
        NetworkPortalWorkOrderWorkspace withCap = portal.getWorkOrderWorkspace(
                actor(PRINCIPAL), "corr-ws-sla", context, WO_A);
        assertThat(withCap.slaSummary()).isEqualTo(new NetworkPortalWorkOrderWorkspaceSlaSummary(2, 1));
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

    private void seedFormSubmission(UUID taskId, String formKey) {
        UUID projectId = jdbc.sql("SELECT project_id FROM tsk_task WHERE task_id=:id")
                .param("id", taskId).query(UUID.class).single();
        UUID formVersionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.parse("2026-07-17T04:00:00Z"), ZoneOffset.UTC);
        String digest = "e".repeat(64);
        jdbc.sql("""
                INSERT INTO cfg_configuration_asset_version (
                    version_id, tenant_id, asset_type, asset_key, semantic_version, schema_version,
                    definition, content_digest, status, published_at)
                VALUES (
                    :versionId, :tenantId, 'FORM', :assetKey, '1.0.0', '1.0.0',
                    CAST(:definition AS jsonb), :digest, 'PUBLISHED', :publishedAt)
                """)
                .param("versionId", formVersionId)
                .param("tenantId", TENANT)
                .param("assetKey", formKey)
                .param("definition", "{\"formKey\":\"" + formKey + "\",\"fields\":[]}")
                .param("digest", digest)
                .param("publishedAt", now)
                .update();
        jdbc.sql("""
                INSERT INTO frm_form_submission (
                    form_submission_id, tenant_id, task_id, project_id, form_version_id, form_key,
                    submission_version, values_document, content_digest, validation_status,
                    submitted_by, submitted_at)
                VALUES (
                    :submissionId, :tenantId, :taskId, :projectId, :formVersionId, :formKey,
                    1, '{}'::jsonb, :digest, 'VALIDATED', 'tester', :submittedAt)
                """)
                .param("submissionId", submissionId)
                .param("tenantId", TENANT)
                .param("taskId", taskId)
                .param("projectId", projectId)
                .param("formVersionId", formVersionId)
                .param("formKey", formKey)
                .param("digest", digest)
                .param("submittedAt", now)
                .update();
        jdbc.sql("""
                INSERT INTO frm_submission_validation (
                    submission_validation_id, tenant_id, form_submission_id, validator_version,
                    input_digest, validation_status, errors_document, warnings_document, executed_at)
                VALUES (
                    :validationId, :tenantId, :submissionId, 'v1',
                    :digest, 'VALIDATED', '[]'::jsonb, '[]'::jsonb, :executedAt)
                """)
                .param("validationId", UUID.randomUUID())
                .param("tenantId", TENANT)
                .param("submissionId", submissionId)
                .param("digest", digest)
                .param("executedAt", now)
                .update();
    }

    /** M223：最小已解析 EvidenceSlot 夹具（含 resolution member）。 */
    private UUID seedEvidenceSlot(UUID taskId, String requirementCode, String requirementName) {
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
        // resolution FK → cfg_configuration_bundle；任务夹具可能只有孤儿 UUID，按需补齐
        OffsetDateTime scopeNow = OffsetDateTime.ofInstant(
                Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at)
                VALUES (
                    :projectId, :tenantId, :code, 'BYD', 'M223 Evidence fixture',
                    DATE '2026-07-01', NULL, 'ACTIVE', 1, :createdAt)
                ON CONFLICT (project_id) DO NOTHING
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("code", "M223-" + taskId.toString().substring(24))
                .param("createdAt", scopeNow)
                .update();
        jdbc.sql("""
                INSERT INTO cfg_configuration_bundle (
                    bundle_id, tenant_id, project_id, bundle_code, bundle_version,
                    brand_code, service_product_code, province_code, effective_from, effective_until,
                    manifest_digest, status, published_at)
                VALUES (
                    :bundleId, :tenantId, :projectId, :bundleCode, '1.0.0',
                    'BYD_OCEAN', 'HOME_CHARGING', NULL, :effectiveFrom, NULL,
                    :manifestDigest, 'PUBLISHED', :publishedAt)
                ON CONFLICT (bundle_id) DO NOTHING
                """)
                .param("bundleId", bundleId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("bundleCode", "M223-BUNDLE-" + taskId.toString().substring(24))
                .param("effectiveFrom", scopeNow)
                .param("manifestDigest", bundleDigest)
                .param("publishedAt", scopeNow)
                .update();
        UUID templateId = UUID.randomUUID();
        UUID resolutionId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.parse("2026-07-17T04:00:00Z"), ZoneOffset.UTC);
        String definition = "{\"evidenceKey\":\"" + requirementCode
                + "\",\"mediaType\":\"PHOTO\",\"required\":true,\"capture\":{\"minCount\":1,\"maxCount\":2}}";
        String digest = "a".repeat(64);
        jdbc.sql("""
                INSERT INTO cfg_configuration_asset_version (
                    version_id, tenant_id, asset_type, asset_key, semantic_version, schema_version,
                    definition, content_digest, status, published_at)
                VALUES (
                    :versionId, :tenantId, 'EVIDENCE', :assetKey, '1.0.0', '1.0.0',
                    CAST(:definition AS jsonb), :digest, 'PUBLISHED', :publishedAt)
                """)
                .param("versionId", templateId)
                .param("tenantId", TENANT)
                .param("assetKey", "tpl." + requirementCode + "." + taskId.toString().substring(24))
                .param("definition", definition)
                .param("digest", digest)
                .param("publishedAt", now)
                .update();
        jdbc.sql("""
                INSERT INTO evd_task_evidence_resolution (
                    resolution_id, tenant_id, project_id, task_id, configuration_bundle_id,
                    configuration_bundle_digest, stage_code, source_event_id, source_event_digest,
                    resolver_version, condition_input_digest, resolution_explanation,
                    generation_no, condition_fact_type, condition_fact_ref, condition_fact_revision,
                    slot_count, resolved_at)
                VALUES (
                    :id, :tenant, :project, :task, :bundle, :digest, 'INSTALLATION', :event,
                    :eventDigest, 'FIXED_EVIDENCE_V1',
                    '44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a',
                    CAST('{"kind":"TEST_FIXED_CONTEXT"}' AS jsonb),
                    1, 'TASK_CREATED', CAST(:event AS varchar), 0, 1, now())
                """)
                .param("id", resolutionId)
                .param("tenant", TENANT)
                .param("project", projectId)
                .param("task", taskId)
                .param("bundle", bundleId)
                .param("digest", bundleDigest)
                .param("event", UUID.randomUUID())
                .param("eventDigest", digest)
                .update();
        jdbc.sql("""
                INSERT INTO evd_evidence_slot (
                    slot_id, tenant_id, project_id, task_id, resolution_id, template_version_id,
                    template_key, template_version, template_digest, requirement_code, occurrence_key,
                    requirement_name, media_type, required_flag, min_count, max_count,
                    condition_input_digest, resolution_explanation, requirement_definition,
                    requirement_digest, status_projection, resolved_at, slot_generation)
                VALUES (
                    :slot, :tenant, :project, :task, :resolution, :template,
                    :templateKey, '1.0.0', :templateDigest, :reqCode, 'default',
                    :reqName, 'PHOTO', true, 1, 2, :conditionDigest,
                    CAST('{"kind":"FIXED"}' AS jsonb), CAST(:definition AS jsonb),
                    :reqDigest, 'MISSING', now(), 1)
                """)
                .param("slot", slotId)
                .param("tenant", TENANT)
                .param("project", projectId)
                .param("task", taskId)
                .param("resolution", resolutionId)
                .param("template", templateId)
                .param("templateKey", "survey.site")
                .param("templateDigest", digest)
                .param("reqCode", requirementCode)
                .param("reqName", requirementName)
                .param("conditionDigest", "e".repeat(64))
                .param("definition", definition)
                .param("reqDigest", "f".repeat(64))
                .update();
        jdbc.sql("""
                INSERT INTO evd_evidence_resolution_member (
                    member_id, tenant_id, project_id, task_id, resolution_id, template_version_id,
                    requirement_code, occurrence_key, condition_result, active_slot_id,
                    previous_slot_id, transition, required_disposition, counting_item_count,
                    condition_input_digest, resolution_explanation, created_at)
                VALUES (
                    :slot, :tenant, :project, :task, :resolution, :template,
                    :reqCode, 'default', true, :slot, NULL, 'ACTIVATED', 'NONE', 0,
                    :conditionDigest, CAST('{"kind":"FIXED"}' AS jsonb), now())
                """)
                .param("slot", slotId)
                .param("tenant", TENANT)
                .param("project", projectId)
                .param("task", taskId)
                .param("resolution", resolutionId)
                .param("template", templateId)
                .param("reqCode", requirementCode)
                .param("conditionDigest", "e".repeat(64))
                .update();
        return slotId;
    }

    /** M223：无 Revision 的 OPEN EvidenceItem（revisionCount=0）。 */
    private UUID seedEvidenceItem(UUID taskId, UUID slotId) {
        UUID projectId = jdbc.sql("SELECT project_id FROM tsk_task WHERE task_id=:id")
                .param("id", taskId).query(UUID.class).single();
        UUID itemId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO evd_evidence_item (
                    evidence_item_id, tenant_id, project_id, task_id, slot_id,
                    item_ordinal, status, created_by, created_at)
                VALUES (
                    :itemId, :tenantId, :projectId, :taskId, :slotId,
                    1, 'OPEN', 'tester', now())
                """)
                .param("itemId", itemId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .param("slotId", slotId)
                .update();
        return itemId;
    }

    /** M225：最小 OPEN CorrectionCase（复用既有 resolution，避免 generation 冲突）。 */
    private UUID seedOpenCorrection(UUID taskId, String marker) {
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
        UUID resolutionId = jdbc.sql("""
                SELECT resolution_id FROM evd_task_evidence_resolution
                 WHERE tenant_id=:tenant AND task_id=:task
                 ORDER BY generation_no DESC LIMIT 1
                """)
                .param("tenant", TENANT)
                .param("task", taskId)
                .query(UUID.class)
                .optional()
                .orElse(null);
        if (resolutionId == null) {
            resolutionId = UUID.randomUUID();
            String digest = Sha256.digest(taskId.toString() + marker);
            jdbc.sql("""
                    INSERT INTO evd_task_evidence_resolution (
                        resolution_id, tenant_id, project_id, task_id, configuration_bundle_id,
                        configuration_bundle_digest, stage_code, source_event_id, source_event_digest,
                        resolver_version, condition_input_digest, resolution_explanation,
                        generation_no, condition_fact_type, condition_fact_ref, condition_fact_revision,
                        slot_count, resolved_at)
                    VALUES (
                        :id, :tenant, :project, :task, :bundle, :digest, 'INSTALLATION', :event,
                        :eventDigest, 'FIXED_EVIDENCE_V1',
                        '44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a',
                        CAST('{"kind":"TEST_FIXED_CONTEXT"}' AS jsonb),
                        1, 'TASK_CREATED', CAST(:event AS varchar), 0, 0, now())
                    """)
                    .param("id", resolutionId)
                    .param("tenant", TENANT)
                    .param("project", projectId)
                    .param("task", taskId)
                    .param("bundle", bundleId)
                    .param("digest", bundleDigest)
                    .param("event", UUID.randomUUID())
                    .param("eventDigest", digest)
                    .update();
        }
        UUID snapshotId = UUID.randomUUID();
        String snapshotDigest = Sha256.digest(marker + ":" + snapshotId);
        jdbc.sql("""
                INSERT INTO evd_evidence_set_snapshot (
                    evidence_set_snapshot_id, tenant_id, project_id, task_id, resolution_id,
                    purpose, member_count, content_digest, eligibility_summary, created_by, created_at
                ) VALUES (
                    :id, :tenant, :project, :task, :resolution,
                    'TASK_SUBMISSION', 0, :digest, '{}'::jsonb, 'fixture', now())
                """)
                .param("id", snapshotId)
                .param("tenant", TENANT)
                .param("project", projectId)
                .param("task", taskId)
                .param("resolution", resolutionId)
                .param("digest", snapshotDigest)
                .update();
        UUID reviewCaseId = UUID.randomUUID();
        UUID reviewDecisionId = UUID.randomUUID();
        UUID correctionCaseId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO evd_review_case (
                    review_case_id, tenant_id, project_id, task_id, evidence_set_snapshot_id,
                    snapshot_content_digest, scope_type, origin, policy_version, status,
                    created_by, created_at, decided_at
                ) VALUES (
                    :id, :tenant, :project, :task, :snapshot, :digest,
                    'EVIDENCE_SET_SNAPSHOT', 'INTERNAL', 'POLICY_V1', 'REJECTED',
                    'fixture', now(), now())
                """)
                .param("id", reviewCaseId)
                .param("tenant", TENANT)
                .param("project", projectId)
                .param("task", taskId)
                .param("snapshot", snapshotId)
                .param("digest", snapshotDigest)
                .update();
        jdbc.sql("""
                INSERT INTO evd_review_decision (
                    review_decision_id, tenant_id, project_id, review_case_id,
                    decision_ordinal, decision, decision_source, reason_codes,
                    note, approval_ref, decided_by, decided_at
                ) VALUES (
                    :id, :tenant, :project, :review,
                    1, 'REJECTED', 'INTERNAL', '["MISSING_PHOTO"]'::jsonb,
                    NULL, NULL, 'fixture', now())
                """)
                .param("id", reviewDecisionId)
                .param("tenant", TENANT)
                .param("project", projectId)
                .param("review", reviewCaseId)
                .update();
        jdbc.sql("""
                INSERT INTO evd_correction_case (
                    correction_case_id, tenant_id, project_id, task_id,
                    source_review_case_id, source_review_decision_id,
                    source_evidence_set_snapshot_id, source_snapshot_content_digest,
                    reason_codes, status, created_by, created_at
                ) VALUES (
                    :id, :tenant, :project, :task,
                    :review, :decision, :snapshot, :digest,
                    '["MISSING_PHOTO"]'::jsonb, 'OPEN', 'fixture', now())
                """)
                .param("id", correctionCaseId)
                .param("tenant", TENANT)
                .param("project", projectId)
                .param("task", taskId)
                .param("review", reviewCaseId)
                .param("decision", reviewDecisionId)
                .param("snapshot", snapshotId)
                .param("digest", snapshotDigest)
                .update();
        return correctionCaseId;
    }

    /** M226：最小 OPEN OperationalException（按 task 关联；他网点任务用于 scope 负例）。 */
    private UUID seedOpenException(UUID taskId, UUID workOrderId, String severity, String marker) {
        UUID exceptionId = UUID.randomUUID();
        UUID projectId = jdbc.sql("SELECT project_id FROM tsk_task WHERE task_id=:id")
                .param("id", taskId)
                .query(UUID.class)
                .single();
        // ops_operational_exception.project_id → prj_project；任务夹具可能只有孤儿 UUID
        OffsetDateTime scopeNow = OffsetDateTime.ofInstant(
                Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at)
                VALUES (
                    :projectId, :tenantId, :code, 'BYD', 'M226 Exception fixture',
                    DATE '2026-07-01', NULL, 'ACTIVE', 1, :createdAt)
                ON CONFLICT (project_id) DO NOTHING
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("code", "M226-" + taskId.toString().substring(24))
                .param("createdAt", scopeNow)
                .update();
        Instant openedAt = Instant.parse("2026-07-17T02:00:00Z");
        jdbc.sql("""
                INSERT INTO ops_operational_exception (
                    exception_id, tenant_id, project_id, source_type, source_id, source_attempt_id,
                    source_task_type, category_code, severity_code, error_code, status,
                    work_order_id, task_id, occurrence_count, correlation_id,
                    opened_at, last_detected_at, aggregate_version
                ) VALUES (
                    :id, :tenant, :projectId, 'TEST', :sourceId, :attemptId,
                    'operations.test', 'AUTOMATION_FINAL_FAILURE', :severity, 'TEST_FAILURE', 'OPEN',
                    :workOrderId, :taskId, 1, :corr,
                    :openedAt, :openedAt, 1
                )
                """)
                .param("id", exceptionId)
                .param("tenant", TENANT)
                .param("projectId", projectId)
                .param("sourceId", "m226-" + marker)
                .param("attemptId", UUID.randomUUID())
                .param("severity", severity)
                .param("workOrderId", workOrderId)
                .param("taskId", taskId)
                .param("corr", "corr-m226-" + marker)
                .param("openedAt", java.sql.Timestamp.from(openedAt))
                .update();
        return exceptionId;
    }

    /**
     * 为既有任务补齐 SLA 触发器所需的 project/bundle/policy，并写入 {@code sla_ref}。
     *
     * @return 发布的 SLA policy versionId
     */
    private UUID prepareSlaScopeForTask(UUID taskId, String projectCodeSuffix) {
        var task = jdbc.sql("""
                SELECT project_id, work_order_id, configuration_bundle_id, configuration_bundle_digest
                  FROM tsk_task WHERE task_id = :taskId
                """)
                .param("taskId", taskId)
                .query((rs, rowNum) -> new Object[] {
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("work_order_id", UUID.class),
                        rs.getObject("configuration_bundle_id", UUID.class),
                        rs.getString("configuration_bundle_digest")
                })
                .single();
        UUID projectId = (UUID) task[0];
        UUID bundleId = (UUID) task[2];
        String bundleDigest = (String) task[3];
        UUID policyVersionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at)
                VALUES (
                    :projectId, :tenantId, :code, 'BYD', 'M221 SLA fixture',
                    DATE '2026-07-01', NULL, 'ACTIVE', 1, :createdAt)
                ON CONFLICT (project_id) DO NOTHING
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("code", "M221-" + projectCodeSuffix)
                .param("createdAt", now)
                .update();
        jdbc.sql("""
                INSERT INTO cfg_configuration_asset_version (
                    version_id, tenant_id, asset_type, asset_key, semantic_version, schema_version,
                    definition, content_digest, status, published_at)
                VALUES (
                    :versionId, :tenantId, 'SLA', :assetKey, '1.0.0', '1.0.0',
                    CAST(:definition AS jsonb), :digest, 'PUBLISHED', :publishedAt)
                """)
                .param("versionId", policyVersionId)
                .param("tenantId", TENANT)
                .param("assetKey", SLA_REF + "." + projectCodeSuffix)
                .param("definition", """
                        {"policyKey":"%s","version":"1.0.0","subjectType":"TASK",
                         "taskTypes":["INSTALLATION"],"startEvent":"TASK_CREATED",
                         "stopEvent":"TASK_COMPLETED","clockMode":"ELAPSED","targetDurationSeconds":3600}
                        """.formatted(SLA_REF).trim())
                .param("digest", SLA_POLICY_DIGEST)
                .param("publishedAt", now)
                .update();
        jdbc.sql("""
                INSERT INTO cfg_configuration_bundle (
                    bundle_id, tenant_id, project_id, bundle_code, bundle_version,
                    brand_code, service_product_code, province_code, effective_from, effective_until,
                    manifest_digest, status, published_at)
                VALUES (
                    :bundleId, :tenantId, :projectId, :bundleCode, '1.0.0',
                    'BYD_OCEAN', 'HOME_CHARGING', NULL, :effectiveFrom, NULL,
                    :manifestDigest, 'PUBLISHED', :publishedAt)
                ON CONFLICT (bundle_id) DO NOTHING
                """)
                .param("bundleId", bundleId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("bundleCode", "M221-BUNDLE-" + projectCodeSuffix)
                .param("effectiveFrom", now)
                .param("manifestDigest", bundleDigest)
                .param("publishedAt", now)
                .update();
        jdbc.sql("""
                INSERT INTO cfg_configuration_bundle_item (
                    tenant_id, bundle_id, asset_type, asset_version_id, content_digest)
                VALUES (:tenantId, :bundleId, 'SLA', :versionId, :digest)
                """)
                .param("tenantId", TENANT)
                .param("bundleId", bundleId)
                .param("versionId", policyVersionId)
                .param("digest", SLA_POLICY_DIGEST)
                .update();
        jdbc.sql("UPDATE tsk_task SET sla_ref = :slaRef WHERE task_id = :taskId")
                .param("slaRef", SLA_REF)
                .param("taskId", taskId)
                .update();
        return policyVersionId;
    }

    /** 将 sibling 任务对齐到 source 任务的 project/bundle/sla_ref，供同工单多实例计数。 */
    private void alignTaskSlaScope(UUID siblingTaskId, UUID sourceTaskId) {
        jdbc.sql("""
                UPDATE tsk_task AS sibling
                   SET project_id = source.project_id,
                       configuration_bundle_id = source.configuration_bundle_id,
                       configuration_bundle_digest = source.configuration_bundle_digest,
                       sla_ref = source.sla_ref
                  FROM tsk_task AS source
                 WHERE sibling.task_id = :sibling
                   AND source.task_id = :source
                """)
                .param("sibling", siblingTaskId)
                .param("source", sourceTaskId)
                .update();
    }

    private void seedSlaInstance(UUID taskId, UUID policyVersionId, String status) {
        var scope = jdbc.sql("""
                SELECT project_id, work_order_id FROM tsk_task WHERE task_id = :taskId
                """)
                .param("taskId", taskId)
                .query((rs, rowNum) -> new Object[] {
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("work_order_id", UUID.class)
                })
                .single();
        Instant started = Instant.parse("2026-07-17T02:00:00Z");
        Instant deadline = Instant.parse("2026-07-17T03:00:00Z");
        OffsetDateTime startedAt = OffsetDateTime.ofInstant(started, ZoneOffset.UTC);
        OffsetDateTime deadlineAt = OffsetDateTime.ofInstant(deadline, ZoneOffset.UTC);
        OffsetDateTime breachedAt = "BREACHED".equals(status) ? deadlineAt : null;
        jdbc.sql("""
                INSERT INTO sla_instance (
                    sla_instance_id, tenant_id, project_id, work_order_id, task_id, sla_ref,
                    policy_version_id, policy_semantic_version, policy_content_digest,
                    clock_mode, target_duration_seconds, start_event_id, started_at, deadline_at,
                    status, breached_at, breach_detected_at, aggregate_version, correlation_id,
                    created_at, updated_at)
                VALUES (
                    :instanceId, :tenantId, :projectId, :workOrderId, :taskId, :slaRef,
                    :policyVersionId, '1.0.0', :policyDigest, 'ELAPSED', 3600, :eventId,
                    :startedAt, :deadlineAt, :status, :breachedAt, :breachDetectedAt,
                    1, 'corr-m221', :startedAt, :startedAt)
                """)
                .param("instanceId", UUID.randomUUID())
                .param("tenantId", TENANT)
                .param("projectId", (UUID) scope[0])
                .param("workOrderId", (UUID) scope[1])
                .param("taskId", taskId)
                .param("slaRef", SLA_REF)
                .param("policyVersionId", policyVersionId)
                .param("policyDigest", SLA_POLICY_DIGEST)
                .param("eventId", UUID.randomUUID())
                .param("startedAt", startedAt)
                .param("deadlineAt", deadlineAt)
                .param("status", status)
                .param("breachedAt", breachedAt)
                .param("breachDetectedAt", breachedAt)
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
