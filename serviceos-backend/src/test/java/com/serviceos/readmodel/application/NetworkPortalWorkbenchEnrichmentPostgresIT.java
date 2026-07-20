package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.NetworkPortalQueryService;
import com.serviceos.readmodel.api.NetworkPortalWorkbenchView;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderWorkspaceSlaSummary;
import com.serviceos.shared.BusinessProblem;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M207 Network Portal 工作台能力门控计数增强：基座 + enrichment 省略/精确计数。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NetworkPortalWorkbenchEnrichmentPostgresIT {
    private static final String TENANT = "tenant-network-portal-m207";
    private static final UUID PRINCIPAL = UUID.fromString("019f84a0-1111-7f8c-9505-36fe5c0e8801");
    private static final UUID NETWORK_A = UUID.fromString("019f84a0-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID NETWORK_B = UUID.fromString("019f84a0-3333-7f8c-9505-36fe5c0e8804");
    private static final UUID PARTNER = UUID.fromString("019f84a0-4444-7f8c-9505-36fe5c0e8805");
    private static final UUID PROJECT = UUID.fromString("019f84a0-5555-7f8c-9505-36fe5c0e8806");
    private static final UUID TECH_PROFILE = UUID.fromString("019f84a0-6666-7f8c-9505-36fe5c0e8807");
    private static final UUID TECH_PRINCIPAL = UUID.fromString("019f84a0-7777-7f8c-9505-36fe5c0e8808");
    private static final UUID WO_ASSIGNED = UUID.fromString("019f84a0-8888-7f8c-9505-36fe5c0e8809");
    private static final UUID WO_UNASSIGNED = UUID.fromString("019f84a0-9999-7f8c-9505-36fe5c0e880a");
    private static final UUID TASK_ASSIGNED = UUID.fromString("019f84a0-aaaa-7f8c-9505-36fe5c0e880b");
    private static final UUID TASK_UNASSIGNED = UUID.fromString("019f84a0-bbbb-7f8c-9505-36fe5c0e880c");
    private static final UUID QUAL_PENDING = UUID.fromString("019f84a0-cccc-7f8c-9505-36fe5c0e880d");
    private static final UUID WO_FOREIGN = UUID.fromString("019f84a0-dddd-7f8c-9505-36fe5c0e880e");
    private static final UUID TASK_FOREIGN = UUID.fromString("019f84a0-eeee-7f8c-9505-36fe5c0e880f");
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
    @Autowired ConfigurationService configurations;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.sql("""
                TRUNCATE TABLE
                    evd_correction_resubmission, evd_correction_case, evd_correction_command_result,
                    evd_review_decision, evd_review_case, evd_review_command_result,
                    evd_evidence_set_member, evd_evidence_set_snapshot,
                    evd_evidence_validation, evd_evidence_command_result, evd_evidence_revision,
                    evd_evidence_item, evd_evidence_upload_session, evd_evidence_resolution_member,
                    evd_evidence_slot, evd_task_evidence_resolution,
                    ops_operational_exception,
                    dsp_assignment_command_result, dsp_capacity_command_result,
                    dsp_service_assignment_activation_saga, dsp_capacity_reservation,
                    dsp_service_assignment, dsp_capacity_counter,
                    sla_milestone, sla_clock_segment, sla_instance,
                    tsk_task,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version,
                    auth_delegation_capability, auth_delegation, auth_role_grant_event,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role,
                    net_technician_qualification, net_network_technician_membership,
                    net_technician_profile, net_network_membership, net_service_network,
                    net_partner_organization, net_directory_event, net_clearance_work_item,
                    idn_principal_lifecycle_event, idn_principal_persona, idn_identity_link,
                    idn_person_profile, idn_security_principal,
                    prj_project,
                    rel_idempotency_record, aud_audit_record CASCADE
                """).update();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("135");
        assertThat(flyway.info().applied()).hasSize(137);

        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at)
                VALUES (:projectId, :tenantId, 'EVD-M207', 'BYD', 'M207 工作台增强测试项目',
                    CURRENT_DATE - 1, 'ACTIVE', 1, now())
                """).param("projectId", PROJECT).param("tenantId", TENANT).update();

        seedPrincipal(PRINCIPAL, "Portal Member");
        seedPrincipal(TECH_PRINCIPAL, "Technician");
        seedPersona(PRINCIPAL, "NETWORK_MEMBER");
        seedPartnerAndNetworks();
        seedNetworkMembership(PRINCIPAL, NETWORK_A);
        seedTechnicianOn(NETWORK_A);
        // 基座：仅 networkTask.read
        seedGrant(PRINCIPAL, "networkTask.read", "NETWORK", NETWORK_A.toString());

        seedHumanTask(TASK_ASSIGNED, WO_ASSIGNED);
        seedHumanTask(TASK_UNASSIGNED, WO_UNASSIGNED);
        seedActiveNetworkAssignmentWithTech(NETWORK_A, WO_ASSIGNED, TASK_ASSIGNED, "tech-a");
        seedActiveNetworkAssignmentOnly(NETWORK_A, WO_UNASSIGNED, TASK_UNASSIGNED);

        ConfigurationBundleReference bundle = publishEvidenceBundle();
        seedOpenCorrection(TASK_ASSIGNED, "a", bundle);
        seedOpenException(TASK_ASSIGNED, WO_ASSIGNED, "P2", "a");
        seedPendingQualification(QUAL_PENDING, TECH_PROFILE);

        seedCapacity(NETWORK_A, 10, 2);
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void m207_01_baseOnlyReturnsUnassignedAndOmitsEnrichmentCounts() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalWorkbenchView wb = portal.workbench(actor(PRINCIPAL), "corr-base", context);

        assertThat(wb.networkId()).isEqualTo(NETWORK_A);
        assertThat(wb.activeWorkOrderCount()).isEqualTo(2);
        assertThat(wb.activeTaskCount()).isEqualTo(2);
        assertThat(wb.activeTechnicianCount()).isEqualTo(1);
        assertThat(wb.unassignedTechnicianTaskCount()).isEqualTo(1);
        assertThat(wb.openCorrectionCaseCount()).isNull();
        assertThat(wb.openOperationalExceptionCount()).isNull();
        assertThat(wb.pendingQualificationCount()).isNull();
        assertThat(wb.slaSummary()).isNull();
        assertThat(wb.capacity()).hasSize(1);
    }

    @Test
    void m207_02_evidenceReadAddsOpenCorrectionCaseCount() {
        seedGrant(PRINCIPAL, "evidence.read", "NETWORK", NETWORK_A.toString());
        bumpGrantGeneration();

        NetworkPortalWorkbenchView wb = portal.workbench(
                actor(PRINCIPAL), "corr-evd", "NETWORK|NETWORK|" + NETWORK_A);
        assertThat(wb.unassignedTechnicianTaskCount()).isEqualTo(1);
        assertThat(wb.openCorrectionCaseCount()).isEqualTo(1);
        assertThat(wb.openOperationalExceptionCount()).isNull();
        assertThat(wb.pendingQualificationCount()).isNull();
    }

    @Test
    void m207_03_exceptionReadAddsOpenOperationalExceptionCount() {
        seedGrant(PRINCIPAL, "operations.exception.read", "NETWORK", NETWORK_A.toString());
        bumpGrantGeneration();

        NetworkPortalWorkbenchView wb = portal.workbench(
                actor(PRINCIPAL), "corr-exc", "NETWORK|NETWORK|" + NETWORK_A);
        assertThat(wb.openOperationalExceptionCount()).isEqualTo(1);
        assertThat(wb.openCorrectionCaseCount()).isNull();
        assertThat(wb.pendingQualificationCount()).isNull();
    }

    @Test
    void m207_04_technicianReadAddsPendingQualificationCount() {
        seedGrant(PRINCIPAL, "technician.readOwnNetwork", "NETWORK", NETWORK_A.toString());
        bumpGrantGeneration();

        NetworkPortalWorkbenchView wb = portal.workbench(
                actor(PRINCIPAL), "corr-qual", "NETWORK|NETWORK|" + NETWORK_A);
        assertThat(wb.pendingQualificationCount()).isEqualTo(1);
        assertThat(wb.openCorrectionCaseCount()).isNull();
        assertThat(wb.openOperationalExceptionCount()).isNull();
    }

    @Test
    void m207_05_allCapabilitiesWithZeroCountsReturnZeroNotNull() {
        // 独立空网点：全能力但无 ACTIVE 任务/整改/异常/PENDING 资质（correction 表不可中途 DELETE）
        seedNetworkMembership(PRINCIPAL, NETWORK_B);
        seedGrant(PRINCIPAL, "networkTask.read", "NETWORK", NETWORK_B.toString());
        seedGrant(PRINCIPAL, "evidence.read", "NETWORK", NETWORK_B.toString());
        seedGrant(PRINCIPAL, "operations.exception.read", "NETWORK", NETWORK_B.toString());
        seedGrant(PRINCIPAL, "technician.readOwnNetwork", "NETWORK", NETWORK_B.toString());
        bumpGrantGeneration();

        NetworkPortalWorkbenchView wb = portal.workbench(
                actor(PRINCIPAL), "corr-zero", "NETWORK|NETWORK|" + NETWORK_B);
        assertThat(wb.activeWorkOrderCount()).isEqualTo(0);
        assertThat(wb.activeTaskCount()).isEqualTo(0);
        assertThat(wb.unassignedTechnicianTaskCount()).isEqualTo(0);
        assertThat(wb.openCorrectionCaseCount()).isEqualTo(0);
        assertThat(wb.openOperationalExceptionCount()).isEqualTo(0);
        assertThat(wb.pendingQualificationCount()).isEqualTo(0);
        assertThat(wb.slaSummary()).isNull();

        seedGrant(PRINCIPAL, "sla.read", "NETWORK", NETWORK_B.toString());
        bumpGrantGeneration();
        NetworkPortalWorkbenchView withSla = portal.workbench(
                actor(PRINCIPAL), "corr-zero-sla", "NETWORK|NETWORK|" + NETWORK_B);
        assertThat(withSla.slaSummary()).isEqualTo(new NetworkPortalWorkOrderWorkspaceSlaSummary(0, 0));
    }

    @Test
    void m224_slaSummaryIsCapabilityGatedAndNetworkScoped() {
        // 他网点 BREACHED 不得计入本网点工作台
        seedHumanTask(TASK_FOREIGN, WO_FOREIGN);
        seedActiveNetworkAssignmentWithTech(NETWORK_B, WO_FOREIGN, TASK_FOREIGN, "tech-b");
        UUID policyA = prepareSlaScopeForTask(TASK_ASSIGNED, "m224-a");
        alignTaskSlaScope(TASK_UNASSIGNED, TASK_ASSIGNED);
        UUID policyB = prepareSlaScopeForTask(TASK_FOREIGN, "m224-b");
        seedSlaInstance(TASK_ASSIGNED, policyA, "RUNNING");
        seedSlaInstance(TASK_UNASSIGNED, policyA, "BREACHED");
        seedSlaInstance(TASK_FOREIGN, policyB, "BREACHED");

        NetworkPortalWorkbenchView withoutCap = portal.workbench(
                actor(PRINCIPAL), "corr-sla-omit", "NETWORK|NETWORK|" + NETWORK_A);
        assertThat(withoutCap.slaSummary()).isNull();

        seedGrant(PRINCIPAL, "sla.read", "NETWORK", NETWORK_A.toString());
        bumpGrantGeneration();
        NetworkPortalWorkbenchView withCap = portal.workbench(
                actor(PRINCIPAL), "corr-sla", "NETWORK|NETWORK|" + NETWORK_A);
        assertThat(withCap.slaSummary()).isEqualTo(new NetworkPortalWorkOrderWorkspaceSlaSummary(2, 1));
    }

    @Test
    void m207_06_forgedContextIsPortalContextInvalid() {
        assertThatThrownBy(() -> portal.workbench(
                actor(PRINCIPAL), "corr-forged", "NETWORK|NETWORK|" + UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));
    }

    @Test
    void m207_missingNetworkTaskReadIsAccessDenied() {
        jdbc.sql("""
                UPDATE auth_role_grant SET grant_status='REVOKED', revoked_at=now(),
                       revoked_by='test', revoke_reason='m207',
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
        bumpGrantGeneration();

        assertThatThrownBy(() -> portal.workbench(
                actor(PRINCIPAL), "corr-cap", "NETWORK|NETWORK|" + NETWORK_A))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    private void bumpGrantGeneration() {
        jdbc.sql("""
                UPDATE auth_tenant_grant_generation
                   SET generation = generation + 1, updated_at = now()
                 WHERE tenant_id = :tenant
                """).param("tenant", TENANT).update();
    }

    private ConfigurationBundleReference publishEvidenceBundle() {
        String definition = """
                {"templateKey":"survey.site","version":"1.0.0","stage":"INSTALLATION",
                 "items":[{"evidenceKey":"site.photo","name":"现场照片","mediaType":"PHOTO","required":true,
                   "capture":{"minCount":1,"maxCount":2}}]}
                """;
        UUID assetId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, "survey.site", "1.0.0", "1.0.0",
                definition.trim(), Sha256.digest(definition.trim()))).versionId();
        return configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, PROJECT, "EVD-M207-BUNDLE", "1.0.0", "BYD", "HOME",
                        null, Instant.now().minusSeconds(60), null,
                        java.util.List.of(assetId)));
    }

    private UUID seedOpenCorrection(UUID taskId, String marker, ConfigurationBundleReference bundle) {
        UUID resolutionId = UUID.randomUUID();
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
                .param("id", resolutionId).param("tenant", TENANT).param("project", PROJECT)
                .param("task", taskId).param("bundle", bundle.bundleId())
                .param("digest", bundle.manifestDigest())
                .param("event", UUID.randomUUID()).param("eventDigest", digest).update();

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
                .param("id", snapshotId).param("tenant", TENANT).param("project", PROJECT)
                .param("task", taskId).param("resolution", resolutionId).param("digest", snapshotDigest)
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
                .param("id", reviewCaseId).param("tenant", TENANT).param("project", PROJECT)
                .param("task", taskId).param("snapshot", snapshotId)
                .param("digest", snapshotDigest).update();
        jdbc.sql("""
                INSERT INTO evd_review_decision (
                    review_decision_id, tenant_id, project_id, review_case_id,
                    decision_ordinal, decision, decision_source, reason_codes,
                    note, approval_ref, decided_by, decided_at
                ) VALUES (
                    :id, :tenant, :project, :review,
                    1, 'REJECTED', 'INTERNAL', '["IMAGE.BLUR"]'::jsonb,
                    'blurry', NULL, 'fixture', now())
                """)
                .param("id", reviewDecisionId).param("tenant", TENANT).param("project", PROJECT)
                .param("review", reviewCaseId).update();
        jdbc.sql("""
                INSERT INTO evd_correction_case (
                    correction_case_id, tenant_id, project_id, task_id,
                    source_review_case_id, source_review_decision_id,
                    source_evidence_set_snapshot_id, source_snapshot_content_digest,
                    reason_codes, status, created_by, created_at
                ) VALUES (
                    :id, :tenant, :project, :task,
                    :review, :decision, :snapshot, :digest,
                    '["IMAGE.BLUR"]'::jsonb, 'OPEN', 'fixture', now())
                """)
                .param("id", correctionCaseId).param("tenant", TENANT).param("project", PROJECT)
                .param("task", taskId).param("review", reviewCaseId).param("decision", reviewDecisionId)
                .param("snapshot", snapshotId).param("digest", snapshotDigest).update();
        return correctionCaseId;
    }

    private void seedOpenException(UUID taskId, UUID workOrderId, String severity, String marker) {
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
                .param("id", UUID.randomUUID()).param("tenant", TENANT).param("projectId", PROJECT)
                .param("sourceId", "m207-" + marker).param("attemptId", UUID.randomUUID())
                .param("severity", severity).param("workOrderId", workOrderId).param("taskId", taskId)
                .param("corr", "corr-m207-" + marker)
                .param("openedAt", Timestamp.from(openedAt))
                .update();
    }

    private void seedPendingQualification(UUID qualificationId, UUID profileId) {
        Instant submittedAt = Instant.parse("2026-07-17T03:00:00Z");
        jdbc.sql("""
                INSERT INTO net_technician_qualification (
                    qualification_id, tenant_id, technician_profile_id, qualification_code,
                    qualification_status, valid_from, valid_to, submitted_by, submitted_at,
                    decided_by, decided_at, decision_reason, aggregate_version
                ) VALUES (
                    :id, :tenant, :profile, 'ELEC-A', 'PENDING',
                    :validFrom, :validTo, 'submitter', :submittedAt,
                    NULL, NULL, NULL, 1
                )
                """)
                .param("id", qualificationId)
                .param("tenant", TENANT)
                .param("profile", profileId)
                .param("validFrom", Timestamp.from(submittedAt))
                .param("validTo", Timestamp.from(submittedAt.plusSeconds(86400L * 365)))
                .param("submittedAt", Timestamp.from(submittedAt))
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
                .param("id", principalId).param("tenant", TENANT)
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
                .param("id", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", principalId).param("type", type).update();
    }

    private void seedPartnerAndNetworks() {
        jdbc.sql("""
                INSERT INTO net_partner_organization (
                    partner_organization_id, tenant_id, partner_code, partner_name,
                    partner_status, aggregate_version, created_at, updated_at
                ) VALUES (:id, :tenant, 'P-207', 'Partner 207', 'ACTIVE', 1, now(), now())
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
                    .param("id", networkId).param("tenant", TENANT).param("partner", PARTNER)
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
                .param("id", UUID.randomUUID()).param("tenant", TENANT)
                .param("network", networkId).param("principal", principalId)
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
                .param("id", TECH_PROFILE).param("tenant", TENANT).param("principal", TECH_PRINCIPAL)
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
                .param("id", UUID.randomUUID()).param("tenant", TENANT)
                .param("network", networkId).param("profile", TECH_PROFILE)
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
                    500, 'READY', :now, 0, 3, 'm207-seed', 1, :now, :now, :projectId,
                    :workOrderId, :workflowInstanceId, :stageInstanceId, :workflowNodeInstanceId,
                    'INSTALL_NODE', :definitionId, :definitionDigest, :bundleId, :bundleDigest,
                    'INSTALL'
                )
                """)
                .param("taskId", taskId).param("tenantId", TENANT)
                .param("businessKey", "m207:" + taskId).param("digest", "a".repeat(64))
                .param("now", Timestamp.from(now))
                .param("projectId", PROJECT).param("workOrderId", workOrderId)
                .param("workflowInstanceId", UUID.randomUUID())
                .param("stageInstanceId", UUID.randomUUID())
                .param("workflowNodeInstanceId", UUID.randomUUID())
                .param("definitionId", UUID.randomUUID()).param("definitionDigest", "b".repeat(64))
                .param("bundleId", UUID.randomUUID()).param("bundleDigest", "c".repeat(64))
                .update();
    }

    private void seedActiveNetworkAssignmentWithTech(
            UUID networkId, UUID workOrderId, UUID taskId, String technicianId
    ) {
        Instant now = Instant.parse("2026-07-17T01:00:00Z");
        insertAssignment(UUID.randomUUID(), workOrderId, taskId, "NETWORK", networkId.toString(),
                UUID.randomUUID(), now);
        insertAssignment(UUID.randomUUID(), workOrderId, taskId, "TECHNICIAN", technicianId,
                UUID.randomUUID(), now);
    }

    private void seedActiveNetworkAssignmentOnly(UUID networkId, UUID workOrderId, UUID taskId) {
        Instant now = Instant.parse("2026-07-17T01:00:00Z");
        insertAssignment(UUID.randomUUID(), workOrderId, taskId, "NETWORK", networkId.toString(),
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
                .param("id", assignmentId).param("tenant", TENANT)
                .param("workOrderId", workOrderId).param("taskId", taskId)
                .param("level", level).param("assignee", assigneeId)
                .param("decision", "decision://" + assignmentId)
                .param("saga", sagaId).param("now", Timestamp.from(now))
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
                .param("id", UUID.randomUUID()).param("tenant", TENANT)
                .param("assignee", networkId.toString())
                .param("max", max).param("occupied", occupied)
                .update();
    }

    private UUID prepareSlaScopeForTask(UUID taskId, String projectCodeSuffix) {
        var task = jdbc.sql("""
                SELECT project_id, configuration_bundle_id, configuration_bundle_digest
                  FROM tsk_task WHERE task_id = :taskId
                """)
                .param("taskId", taskId)
                .query((rs, rowNum) -> new Object[] {
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("configuration_bundle_id", UUID.class),
                        rs.getString("configuration_bundle_digest")
                })
                .single();
        UUID projectId = (UUID) task[0];
        UUID bundleId = (UUID) task[1];
        String bundleDigest = (String) task[2];
        UUID policyVersionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
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
                .param("bundleCode", "M224-BUNDLE-" + projectCodeSuffix)
                .param("effectiveFrom", now)
                .param("manifestDigest", bundleDigest)
                .param("publishedAt", now)
                .update();
        jdbc.sql("""
                INSERT INTO cfg_configuration_bundle_item (
                    tenant_id, bundle_id, asset_type, asset_version_id, content_digest)
                VALUES (:tenantId, :bundleId, 'SLA', :versionId, :digest)
                ON CONFLICT DO NOTHING
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
                    1, 'corr-m224', :startedAt, :startedAt)
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
                .param("roleId", roleId).param("tenant", TENANT)
                .param("code", "m207-" + capability + "-" + UUID.randomUUID())
                .update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, :capability, now())
                """)
                .param("roleId", roleId).param("capability", capability)
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
