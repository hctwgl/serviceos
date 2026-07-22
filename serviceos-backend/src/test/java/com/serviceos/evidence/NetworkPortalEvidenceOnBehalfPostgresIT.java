package com.serviceos.evidence;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.evidence.api.BeginEvidenceUploadOnBehalfCommand;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.evidence.api.FinalizeEvidenceUploadCommand;
import com.serviceos.evidence.api.NetworkPortalEvidenceService;
import com.serviceos.files.infrastructure.LocalObjectTransferService;
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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M201/M368 Network Portal 资料代补：begin/finalize on-behalf、NETWORK_WEB 能力门禁、
 * 无整改/错误师傅/跨网点/伪造上下文失败关闭、resubmit。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NetworkPortalEvidenceOnBehalfPostgresIT {
    private static final String TENANT = "tenant-network-portal-m201";
    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private static final UUID PRINCIPAL = UUID.fromString("019f83d1-1111-7f8c-9505-36fe5c0e8801");
    private static final UUID NETWORK_A = UUID.fromString("019f83d1-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID NETWORK_B = UUID.fromString("019f83d1-3333-7f8c-9505-36fe5c0e8804");
    private static final UUID PARTNER = UUID.fromString("019f83d1-4444-7f8c-9505-36fe5c0e8805");
    private static final UUID TECH_A = UUID.fromString("019f83d1-5555-7f8c-9505-36fe5c0e8806");
    private static final UUID TECH_A_PRINCIPAL = UUID.fromString("019f83d1-6666-7f8c-9505-36fe5c0e8807");
    private static final UUID TECH_B = UUID.fromString("019f83d1-5556-7f8c-9505-36fe5c0e8808");
    private static final UUID TECH_B_PRINCIPAL = UUID.fromString("019f83d1-6667-7f8c-9505-36fe5c0e8809");
    private static final UUID WO = UUID.fromString("019f83d1-7777-7f8c-9505-36fe5c0e880c");
    private static final UUID TASK = UUID.fromString("019f83d1-9999-7f8c-9505-36fe5c0e880d");
    private static final UUID PROJECT = UUID.fromString("019f83d1-8888-7f8c-9505-36fe5c0e880b");

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
        registry.add("serviceos.files.local.root", STORAGE_ROOT::toString);
        registry.add("serviceos.files.local.signing-key",
                () -> "evidence-m201-it-signing-key-with-thirty-two-b");
        registry.add("serviceos.outbox.scheduling-enabled", () -> "false");
        registry.add("serviceos.task.scheduling-enabled", () -> "false");
    }

    @Autowired NetworkPortalEvidenceService portalEvidence;
    @Autowired ConfigurationService configurations;
    @Autowired LocalObjectTransferService transfers;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    private UUID slotId;
    private UUID resolutionId;
    private UUID sourceSnapshotId;
    private UUID correctionCaseId;

    @BeforeEach
    void cleanAndSeed() throws Exception {
        jdbc.sql("""
                TRUNCATE TABLE
                    evd_correction_resubmission, evd_correction_case, evd_correction_command_result,
                    evd_review_decision, evd_review_case, evd_review_command_result,
                    evd_evidence_set_member, evd_evidence_set_snapshot,
                    evd_evidence_validation, evd_evidence_command_result, evd_evidence_revision,
                    evd_evidence_item, evd_evidence_upload_session, evd_evidence_resolution_member,
                    evd_evidence_slot, evd_task_evidence_resolution,
                    fil_download_authorization, fil_scan_result, fil_stored_file, fil_upload_session,
                    dsp_assignment_command_result, dsp_capacity_command_result,
                    dsp_service_assignment_activation_saga, dsp_capacity_reservation,
                    dsp_service_assignment, dsp_capacity_counter,
                    tsk_task_execution_guard, tsk_task_assignment, tsk_task,
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
        deleteRecursively(STORAGE_ROOT);
        Files.createDirectories(STORAGE_ROOT);

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("146");
        assertThat(flyway.info().applied()).hasSize(148);
        assertThat(jdbc.sql("""
                        SELECT risk_level FROM auth_capability
                         WHERE capability_code='evidence.submitOnBehalf'
                        """).query(String.class).single()).isEqualTo("HIGH");

        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at)
                VALUES (:projectId, :tenantId, 'EVD-M201', 'BYD', 'M201 资料代补测试项目',
                    CURRENT_DATE - 1, 'ACTIVE', 1, now())
                """).param("projectId", PROJECT).param("tenantId", TENANT).update();

        seedPrincipal(PRINCIPAL, "Portal Member");
        seedPrincipal(TECH_A_PRINCIPAL, "Technician A");
        seedPrincipal(TECH_B_PRINCIPAL, "Technician B");
        seedPersona(PRINCIPAL, "NETWORK_MEMBER");
        seedPartnerAndNetworks();
        seedNetworkMembership(PRINCIPAL, NETWORK_A);
        seedTechnician(TECH_A, TECH_A_PRINCIPAL, NETWORK_A);
        seedTechnician(TECH_B, TECH_B_PRINCIPAL, NETWORK_A);
        seedGrant(PRINCIPAL, "evidence.submitOnBehalf", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL, "evidence.submit", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL, "evidence.read", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL, "file.upload", "TENANT", TENANT);
        seedRunningTask();
        seedActiveAssignment(NETWORK_A.toString(), "NETWORK", TASK, WO);
        seedActiveAssignment(TECH_A.toString(), "TECHNICIAN", TASK, WO);
        seedEvidenceSlotAndOpenCorrection();
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void m201_01_beginFinalizeWritesOnBehalfCaptureMetadata() throws Exception {
        byte[] content = pngBytes("m201-ok");
        String checksum = sha256(content);
        String context = "NETWORK|NETWORK|" + NETWORK_A;

        EvidenceUploadSessionView session = portalEvidence.beginUploadOnBehalf(
                actor(PRINCIPAL), metadata("m201-begin"), context,
                "NETWORK_WEB", TASK, slotId,
                beginCommand(checksum, content.length, TECH_A.toString(), "整改代补"));
        transfers.upload(token(session.uploadUrl()), "image/png", content.length,
                new ByteArrayInputStream(content));

        EvidenceItemView item = portalEvidence.finalizeUploadOnBehalf(
                actor(PRINCIPAL), metadata("m201-fin"), context, "NETWORK_WEB", TASK, slotId,
                session.uploadSessionId(),
                new FinalizeEvidenceUploadCommand(
                        TASK, slotId, session.uploadSessionId(), checksum, "m201-finalize-1"));

        assertThat(item.revisions()).hasSize(1);
        String capture = item.revisions().getFirst().captureMetadataJson();
        assertThat(capture).contains("\"uploadedBy\"");
        assertThat(capture).contains(PRINCIPAL.toString());
        assertThat(capture).contains("\"onBehalfOf\"");
        assertThat(capture).contains(TECH_A.toString());
        assertThat(capture).contains("\"onBehalfReason\"");
        assertThat(capture).contains("整改代补");
        assertThat(capture).contains("\"uploadedRole\"");
        assertThat(capture).contains("NETWORK_OPERATOR");
        assertThat(jdbc.sql("SELECT count(*) FROM evd_evidence_revision").query(Long.class).single()).isOne();
    }

    @Test
    void m201_02_withoutOpenCorrectionIsValidationFailed() {
        jdbc.sql("TRUNCATE TABLE evd_correction_resubmission, evd_correction_case CASCADE").update();
        assertThatThrownBy(() -> portalEvidence.beginUploadOnBehalf(
                actor(PRINCIPAL), metadata("m201-no-corr"), "NETWORK|NETWORK|" + NETWORK_A, "NETWORK_WEB", TASK, slotId,
                beginCommand("b".repeat(64), 10, TECH_A.toString(), "x")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void m201_03_onBehalfOfNotActiveTechnicianIsRejected() {
        assertThatThrownBy(() -> portalEvidence.beginUploadOnBehalf(
                actor(PRINCIPAL), metadata("m201-wrong-tech"), "NETWORK|NETWORK|" + NETWORK_A, "NETWORK_WEB", TASK, slotId,
                beginCommand("c".repeat(64), 10, TECH_B.toString(), "wrong")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void m201_04_crossNetworkActiveIsAccessDenied() {
        jdbc.sql("""
                UPDATE dsp_service_assignment
                   SET status='ENDED', effective_to=now(), ended_by='test', end_reason_code='MANUAL_REASSIGNMENT'
                 WHERE tenant_id=:tenant AND task_id=:task AND responsibility_level='NETWORK'
                   AND status='ACTIVE'
                """).param("tenant", TENANT).param("task", TASK).update();
        seedActiveAssignment(NETWORK_B.toString(), "NETWORK", TASK, WO);
        assertThatThrownBy(() -> portalEvidence.beginUploadOnBehalf(
                actor(PRINCIPAL), metadata("m201-cross"), "NETWORK|NETWORK|" + NETWORK_A, "NETWORK_WEB", TASK, slotId,
                beginCommand("d".repeat(64), 10, TECH_A.toString(), "cross")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    @Test
    void m201_05_forgedContextIsPortalContextInvalid() {
        assertThatThrownBy(() -> portalEvidence.beginUploadOnBehalf(
                actor(PRINCIPAL), metadata("m201-forged"), "NETWORK|NETWORK|" + UUID.randomUUID(), "NETWORK_WEB", TASK, slotId,
                beginCommand("e".repeat(64), 10, TECH_A.toString(), "forged")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));
    }

    @Test
    void m201_08_missingCapabilityIsAccessDenied() {
        jdbc.sql("""
                UPDATE auth_role_grant SET grant_status='REVOKED', revoked_at=now(),
                       revoked_by='test', revoke_reason='m201',
                       aggregate_version = aggregate_version + 1, updated_at=now()
                 WHERE tenant_id=:tenant AND principal_id=:principal
                   AND scope_type='NETWORK' AND scope_ref=:network
                   AND role_id IN (
                     SELECT role_id FROM auth_role_capability
                      WHERE capability_code='evidence.submitOnBehalf'
                   )
                """)
                .param("tenant", TENANT)
                .param("principal", PRINCIPAL.toString())
                .param("network", NETWORK_A.toString())
                .update();
        assertThatThrownBy(() -> portalEvidence.beginUploadOnBehalf(
                actor(PRINCIPAL), metadata("m201-cap"), "NETWORK|NETWORK|" + NETWORK_A, "NETWORK_WEB", TASK, slotId,
                beginCommand("f".repeat(64), 10, TECH_A.toString(), "cap")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    @Test
    void m201_09_portalCorrectionResubmit() {
        UUID secondSnapshot = seedExtraSnapshot("second");
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        CorrectionCaseView resubmitted = portalEvidence.resubmit(
                actor(PRINCIPAL), metadata("m201-resubmit"), context, "NETWORK_WEB", correctionCaseId, secondSnapshot);
        assertThat(resubmitted.status()).isEqualTo("RESUBMITTED");
        assertThat(resubmitted.latestResubmissionSnapshotId()).isEqualTo(secondSnapshot);

        CorrectionCaseView replay = portalEvidence.resubmit(
                actor(PRINCIPAL), metadata("m201-resubmit"), context, "NETWORK_WEB", correctionCaseId, secondSnapshot);
        assertThat(replay.correctionCaseId()).isEqualTo(correctionCaseId);
        assertThat(replay.resubmissions()).hasSize(1);
    }

    @Test
    void m368_01_unknownClientKindIsCapabilityUnsupported() {
        assertThatThrownBy(() -> portalEvidence.beginUploadOnBehalf(
                actor(PRINCIPAL), metadata("m368-unknown"), "NETWORK|NETWORK|" + NETWORK_A, "UNKNOWN", TASK, slotId,
                beginCommand("a".repeat(64), 10, TECH_A.toString(), "unknown-kind")))
                .isInstanceOfSatisfying(BusinessProblem.class, p -> {
                    assertThat(p.code()).isEqualTo(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED);
                    assertThat(p.getMessage()).contains("NETWORK_WEB");
                });
    }

    @Test
    void m368_02_signatureSlotRejectedForNetworkWeb() {
        // 槽位定义不可变；发布 SIGNATURE 资产并插入额外槽位以证明 NETWORK_WEB 目录拒单。
        String definition = """
                {"templateKey":"survey.sign","version":"1.0.0","stage":"INSTALLATION",
                 "items":[{"evidenceKey":"site.sign","name":"现场签名","mediaType":"SIGNATURE","required":true,
                   "capture":{"minCount":1,"maxCount":1}}]}
                """;
        UUID assetId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, "survey.sign", "1.0.0", "1.0.0",
                definition.trim(), Sha256.digest(definition.trim()))).versionId();
        UUID signatureSlot = UUID.randomUUID();
        String templateDigest = Sha256.digest(definition.trim());
        jdbc.sql("""
                INSERT INTO evd_evidence_slot (
                    slot_id, tenant_id, project_id, task_id, resolution_id, template_version_id,
                    template_key, template_version, template_digest, requirement_code, occurrence_key,
                    requirement_name, media_type, required_flag, min_count, max_count,
                    condition_input_digest, resolution_explanation, requirement_definition,
                    requirement_digest, status_projection, resolved_at, slot_generation)
                VALUES (
                    :slot, :tenant, :project, :task, :resolution, :template,
                    'survey.sign', '1.0.0', :templateDigest, 'site.sign', 'default',
                    '现场签名', 'SIGNATURE', true, 1, 1, :conditionDigest,
                    CAST('{"kind":"FIXED"}' AS jsonb), CAST(:definition AS jsonb),
                    :reqDigest, 'MISSING', now(), 1)
                """)
                .param("slot", signatureSlot).param("tenant", TENANT).param("project", PROJECT)
                .param("task", TASK).param("resolution", resolutionId)
                .param("template", assetId)
                .param("templateDigest", templateDigest)
                .param("conditionDigest", "e".repeat(64))
                .param("definition",
                        "{\"evidenceKey\":\"site.sign\",\"mediaType\":\"SIGNATURE\",\"required\":true}")
                .param("reqDigest", "f".repeat(64)).update();
        jdbc.sql("""
                INSERT INTO evd_evidence_resolution_member (
                    member_id, tenant_id, project_id, task_id, resolution_id, template_version_id,
                    requirement_code, occurrence_key, condition_result, active_slot_id,
                    previous_slot_id, transition, required_disposition, counting_item_count,
                    condition_input_digest, resolution_explanation, created_at)
                VALUES (
                    :slot, :tenant, :project, :task, :resolution, :template,
                    'site.sign', 'default', true, :slot, NULL, 'ACTIVATED', 'NONE', 0,
                    :conditionDigest, CAST('{"kind":"FIXED"}' AS jsonb), now())
                """)
                .param("slot", signatureSlot).param("tenant", TENANT).param("project", PROJECT)
                .param("task", TASK).param("resolution", resolutionId)
                .param("template", assetId)
                .param("conditionDigest", "e".repeat(64)).update();
        assertThatThrownBy(() -> portalEvidence.beginUploadOnBehalf(
                actor(PRINCIPAL), metadata("m368-sign"), "NETWORK|NETWORK|" + NETWORK_A, "NETWORK_WEB", TASK, slotId,
                beginCommand("b".repeat(64), 10, TECH_A.toString(), "signature")))
                .isInstanceOfSatisfying(BusinessProblem.class, p -> {
                    assertThat(p.code()).isEqualTo(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED);
                    assertThat(p.getMessage()).contains("SIGNATURE");
                });
    }

    @Test
    void m201_10_createSnapshotOnBehalfThenResubmit() throws Exception {
        byte[] content = pngBytes("m201-snapshot");
        String checksum = sha256(content);
        String context = "NETWORK|NETWORK|" + NETWORK_A;

        EvidenceUploadSessionView session = portalEvidence.beginUploadOnBehalf(
                actor(PRINCIPAL), metadata("m201-snap-begin"), context,
                "NETWORK_WEB", TASK, slotId,
                beginCommand(checksum, content.length, TECH_A.toString(), "整改代补"));
        transfers.upload(token(session.uploadUrl()), "image/png", content.length,
                new ByteArrayInputStream(content));
        EvidenceItemView item = portalEvidence.finalizeUploadOnBehalf(
                actor(PRINCIPAL), metadata("m201-snap-fin"), context, "NETWORK_WEB", TASK, slotId,
                session.uploadSessionId(),
                new FinalizeEvidenceUploadCommand(
                        TASK, slotId, session.uploadSessionId(), checksum, "m201-snap-finalize"));
        UUID revisionId = item.revisions().getFirst().evidenceRevisionId();
        markRevisionValidated(revisionId, item.evidenceItemId());

        jdbc.sql("""
                UPDATE tsk_task
                   SET status='COMPLETED', updated_at=now(), version=version+1,
                       completed_at=now(), result_ref='m201-snapshot',
                       result_digest=:digest
                 WHERE tenant_id=:tenant AND task_id=:task
                """)
                .param("tenant", TENANT).param("task", TASK)
                .param("digest", Sha256.digest("m201-snapshot-complete")).update();

        EvidenceSetSnapshotView snapshot = portalEvidence.createSnapshotOnBehalf(
                actor(PRINCIPAL), metadata("m201-snap-create"), context, "NETWORK_WEB", correctionCaseId,
                List.of(revisionId));
        assertThat(snapshot.evidenceSetSnapshotId()).isNotNull();
        assertThat(snapshot.memberCount()).isOne();

        CorrectionCaseView resubmitted = portalEvidence.resubmit(
                actor(PRINCIPAL), metadata("m201-snap-resubmit"), context, "NETWORK_WEB", correctionCaseId,
                snapshot.evidenceSetSnapshotId());
        assertThat(resubmitted.status()).isEqualTo("RESUBMITTED");
        assertThat(resubmitted.latestResubmissionSnapshotId()).isEqualTo(snapshot.evidenceSetSnapshotId());
    }

    private void markRevisionValidated(UUID revisionId, UUID evidenceItemId) {
        jdbc.sql("""
                UPDATE evd_evidence_revision
                   SET status='VALIDATED'
                 WHERE tenant_id=:tenant AND evidence_revision_id=:revision
                """).param("tenant", TENANT).param("revision", revisionId).update();
        jdbc.sql("""
                INSERT INTO evd_evidence_validation (
                    validation_id, tenant_id, project_id, task_id, slot_id, evidence_item_id,
                    evidence_revision_id, check_type, severity, result, reason_code, message,
                    details, validator_name, validator_version, created_at)
                VALUES (
                    :validationId, :tenant, :project, :task, :slot, :item, :revision,
                    'FORMAT', 'BLOCK', 'PASSED', NULL, NULL, '{}'::jsonb,
                    'M201-IT', '1', now())
                ON CONFLICT (tenant_id, evidence_revision_id, check_type) DO NOTHING
                """)
                .param("validationId", UUID.randomUUID()).param("tenant", TENANT)
                .param("project", PROJECT).param("task", TASK).param("slot", slotId)
                .param("item", evidenceItemId).param("revision", revisionId).update();
    }

    private BeginEvidenceUploadOnBehalfCommand beginCommand(
            String checksum, long size, String onBehalfOf, String reason
    ) {
        return new BeginEvidenceUploadOnBehalfCommand(
                TASK, slotId, null, "site.png", "image/png", size, checksum,
                "{\"captureSource\":\"CAMERA\",\"capturedAt\":\"2026-07-17T02:00:00Z\",\"deviceId\":\"DEV-M201\"}",
                onBehalfOf, reason, null);
    }

    private void seedRunningTask() {
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id, tenant_id, task_type, task_kind, business_key, payload_digest,
                    priority, status, next_run_at, attempt_count, max_attempts, correlation_id,
                    version, created_at, updated_at, claimed_by, claimed_at, started_at,
                    project_id, work_order_id, workflow_instance_id, stage_instance_id,
                    workflow_node_instance_id, workflow_node_id, workflow_definition_version_id,
                    workflow_definition_digest, configuration_bundle_id, configuration_bundle_digest,
                    stage_code
                ) VALUES (
                    :taskId, :tenantId, 'INSTALLATION', 'HUMAN', :businessKey, :digest,
                    500, 'RUNNING', :now, 0, 3, 'corr-m201', 1, :now, :now,
                    :actor, :now, :now, :projectId, :workOrderId, :workflowInstanceId, :stageInstanceId,
                    :workflowNodeInstanceId, 'INSTALL_NODE', :definitionId, :definitionDigest,
                    :bundleId, :bundleDigest, 'INSTALL'
                )
                """)
                .param("taskId", TASK).param("tenantId", TENANT)
                .param("businessKey", "m201:" + TASK).param("digest", "a".repeat(64))
                .param("now", java.sql.Timestamp.from(now))
                .param("actor", TECH_A_PRINCIPAL.toString())
                .param("projectId", PROJECT).param("workOrderId", WO)
                .param("workflowInstanceId", UUID.randomUUID()).param("stageInstanceId", UUID.randomUUID())
                .param("workflowNodeInstanceId", UUID.randomUUID())
                .param("definitionId", UUID.randomUUID()).param("definitionDigest", "b".repeat(64))
                .param("bundleId", UUID.randomUUID()).param("bundleDigest", "c".repeat(64))
                .update();
        jdbc.sql("""
                INSERT INTO tsk_task_assignment (
                    task_assignment_id, tenant_id, task_id, assignment_kind, principal_type,
                    principal_id, status, source_type, source_id, effective_from, created_by, created_at)
                VALUES (:id, :tenant, :task, 'RESPONSIBLE', 'USER', :actor, 'ACTIVE',
                    'MANUAL', 'M201-FIXTURE', now(), 'fixture', now())
                """).param("id", UUID.randomUUID()).param("tenant", TENANT).param("task", TASK)
                .param("actor", TECH_A_PRINCIPAL.toString()).update();
    }

    private void seedEvidenceSlotAndOpenCorrection() {
        String definition = """
                {"templateKey":"survey.site","version":"1.0.0","stage":"INSTALLATION",
                 "items":[{"evidenceKey":"site.photo","name":"现场照片","mediaType":"PHOTO","required":true,
                   "capture":{"minCount":1,"maxCount":2}}]}
                """;
        UUID assetId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, "survey.site", "1.0.0", "1.0.0",
                definition.trim(), Sha256.digest(definition.trim()))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, PROJECT, "EVD-M201-BUNDLE", "1.0.0", "BYD", "HOME",
                        null, Instant.now().minusSeconds(60), null, List.of(assetId)));

        resolutionId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        String digest = Sha256.digest(TASK.toString());
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
                .param("id", resolutionId).param("tenant", TENANT).param("project", PROJECT)
                .param("task", TASK).param("bundle", bundle.bundleId())
                .param("digest", bundle.manifestDigest())
                .param("event", UUID.randomUUID()).param("eventDigest", digest).update();
        jdbc.sql("""
                INSERT INTO evd_evidence_slot (
                    slot_id, tenant_id, project_id, task_id, resolution_id, template_version_id,
                    template_key, template_version, template_digest, requirement_code, occurrence_key,
                    requirement_name, media_type, required_flag, min_count, max_count,
                    condition_input_digest, resolution_explanation, requirement_definition,
                    requirement_digest, status_projection, resolved_at, slot_generation)
                VALUES (
                    :slot, :tenant, :project, :task, :resolution, :template,
                    'survey.site', '1.0.0', :templateDigest, 'site.photo', 'default',
                    '现场照片', 'PHOTO', true, 1, 2, :conditionDigest,
                    CAST('{"kind":"FIXED"}' AS jsonb), CAST(:definition AS jsonb),
                    :reqDigest, 'MISSING', now(), 1)
                """)
                .param("slot", slotId).param("tenant", TENANT).param("project", PROJECT)
                .param("task", TASK).param("resolution", resolutionId).param("template", assetId)
                .param("templateDigest", Sha256.digest(definition.trim()))
                .param("conditionDigest", "e".repeat(64))
                .param("definition", "{\"evidenceKey\":\"site.photo\",\"mediaType\":\"PHOTO\",\"required\":true,\"capture\":{\"minCount\":1,\"maxCount\":2}}")
                .param("reqDigest", "f".repeat(64)).update();
        jdbc.sql("""
                INSERT INTO evd_evidence_resolution_member (
                    member_id, tenant_id, project_id, task_id, resolution_id, template_version_id,
                    requirement_code, occurrence_key, condition_result, active_slot_id,
                    previous_slot_id, transition, required_disposition, counting_item_count,
                    condition_input_digest, resolution_explanation, created_at)
                VALUES (
                    :slot, :tenant, :project, :task, :resolution, :template,
                    'site.photo', 'default', true, :slot, NULL, 'ACTIVATED', 'NONE', 0,
                    :conditionDigest, CAST('{"kind":"FIXED"}' AS jsonb), now())
                """)
                .param("slot", slotId).param("tenant", TENANT).param("project", PROJECT)
                .param("task", TASK).param("resolution", resolutionId).param("template", assetId)
                .param("conditionDigest", "e".repeat(64)).update();

        sourceSnapshotId = seedExtraSnapshot("source");
        UUID reviewCaseId = UUID.randomUUID();
        UUID reviewDecisionId = UUID.randomUUID();
        correctionCaseId = UUID.randomUUID();
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
                .param("task", TASK).param("snapshot", sourceSnapshotId)
                .param("digest", Sha256.digest(sourceSnapshotId.toString())).update();
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
                .param("task", TASK).param("review", reviewCaseId).param("decision", reviewDecisionId)
                .param("snapshot", sourceSnapshotId)
                .param("digest", Sha256.digest(sourceSnapshotId.toString())).update();
    }

    private UUID seedExtraSnapshot(String marker) {
        UUID snapshotId = UUID.randomUUID();
        String digest = Sha256.digest(marker + ":" + snapshotId);
        jdbc.sql("""
                INSERT INTO evd_evidence_set_snapshot (
                    evidence_set_snapshot_id, tenant_id, project_id, task_id, resolution_id,
                    purpose, member_count, content_digest, eligibility_summary, created_by, created_at
                ) VALUES (
                    :id, :tenant, :project, :task, :resolution,
                    'TASK_SUBMISSION', 0, :digest, '{}'::jsonb, 'fixture', now())
                """)
                .param("id", snapshotId).param("tenant", TENANT).param("project", PROJECT)
                .param("task", TASK).param("resolution", resolutionId).param("digest", digest)
                .update();
        return snapshotId;
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
                ) VALUES (:id, :tenant, 'P-201', 'Partner 201', 'ACTIVE', 1, now(), now())
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
                    .param("name", "Network " + networkId).update();
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
                .param("network", networkId).param("principal", principalId).update();
    }

    private void seedTechnician(UUID profileId, UUID principalId, UUID networkId) {
        jdbc.sql("""
                INSERT INTO net_technician_profile (
                    technician_profile_id, tenant_id, principal_id, display_name, profile_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (:id, :tenant, :principal, :name, 'ACTIVE', 1, now(), now())
                """)
                .param("id", profileId).param("tenant", TENANT).param("principal", principalId)
                .param("name", "师傅 " + profileId.toString().substring(24)).update();
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
                .param("network", networkId).param("profile", profileId).update();
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
                .param("id", counterId).param("tenant", TENANT).param("level", level)
                .param("assignee", assigneeId).param("now", java.sql.Timestamp.from(now)).update();
        UUID resolvedCounterId = jdbc.sql("""
                        SELECT capacity_counter_id FROM dsp_capacity_counter
                         WHERE tenant_id = :tenant AND responsibility_level = :level
                           AND assignee_id = :assignee AND business_type = 'INSTALLATION'
                        """)
                .param("tenant", TENANT).param("level", level).param("assignee", assigneeId)
                .query(UUID.class).single();
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
                    :authorityId, 1, :fenceDecision, :fencePolicy
                )
                """)
                .param("id", assignmentId).param("tenant", TENANT).param("workOrderId", workOrderId)
                .param("taskId", taskId).param("level", level).param("assignee", assigneeId)
                .param("decision", "decision://" + assignmentId).param("saga", sagaId)
                .param("now", java.sql.Timestamp.from(now))
                .param("authorityId", "authority://" + assignmentId)
                .param("fenceDecision", "fence://" + assignmentId)
                .param("fencePolicy", "fence-policy-v1").update();
        jdbc.sql("""
                INSERT INTO dsp_capacity_reservation (
                    capacity_reservation_id, tenant_id, service_assignment_id,
                    capacity_counter_id, units, status, held_at, confirmed_at
                ) VALUES (
                    :id, :tenant, :assignmentId, :counterId, 1, 'CONFIRMED', :now, :now
                )
                """)
                .param("id", UUID.randomUUID()).param("tenant", TENANT)
                .param("assignmentId", assignmentId).param("counterId", resolvedCounterId)
                .param("now", java.sql.Timestamp.from(now)).update();
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
                .param("code", "m201-" + capability + "-" + UUID.randomUUID()).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, :capability, now())
                """).param("roleId", roleId).param("capability", capability).update();
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
                .param("grantId", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", principalId.toString()).param("roleId", roleId)
                .param("scopeType", scopeType).param("scopeRef", scopeRef).update();
    }

    private static CurrentPrincipal actor(UUID principalId) {
        return new CurrentPrincipal(principalId.toString(), TENANT, CurrentPrincipal.PrincipalType.USER,
                "network-portal", Set.of());
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }

    private static String token(String url) {
        String path = URI.create(url).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static byte[] pngBytes(String marker) {
        byte[] prefix = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] body = marker.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, out, 0, prefix.length);
        System.arraycopy(body, 0, out, prefix.length, body.length);
        return out;
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("serviceos-m201-evidence-");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // best-effort cleanup between tests
                }
            });
        }
    }
}
