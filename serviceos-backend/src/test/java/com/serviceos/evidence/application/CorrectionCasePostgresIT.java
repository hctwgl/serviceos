package com.serviceos.evidence.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.evidence.api.BeginEvidenceUploadCommand;
import com.serviceos.evidence.api.CloseCorrectionCaseCommand;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.CreateEvidenceSetSnapshotCommand;
import com.serviceos.evidence.api.CreateReviewCaseCommand;
import com.serviceos.evidence.api.DecideReviewCaseCommand;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.FinalizeEvidenceUploadCommand;
import com.serviceos.evidence.api.ResubmitCorrectionCaseCommand;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.files.infrastructure.LocalObjectTransferService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.application.TaskExecutionWorker;
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
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CorrectionCasePostgresIT {
    private static final String TENANT = "tenant-correction-case-it";
    private static final String TECHNICIAN = "technician-evidence-045";
    private static final String REVIEWER = "reviewer-evidence-045";
    private static final Path STORAGE_ROOT = temporaryStorageRoot();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos")
            .withUsername("serviceos_test")
            .withPassword("serviceos_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("serviceos.files.local.root", STORAGE_ROOT::toString);
        registry.add("serviceos.files.local.signing-key",
                () -> "evidence-correction-it-signing-key-with-thirty-two-b");
        registry.add("serviceos.task.scheduling-enabled", () -> "false");
    }

    @Autowired ConfigurationService configurations;
    @Autowired EvidenceCommandService evidence;
    @Autowired EvidenceSetSnapshotService snapshots;
    @Autowired ReviewCaseService reviews;
    @Autowired CorrectionCaseService corrections;
    @Autowired LocalObjectTransferService transfers;
    @Autowired TaskExecutionWorker worker;
    @Autowired List<OutboxMessageHandler> handlers;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    UUID slotId;
    UUID taskId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("""
                TRUNCATE TABLE
                    evd_correction_resubmission, evd_correction_case, evd_correction_command_result,
                    evd_review_decision, evd_review_case, evd_review_command_result,
                    evd_evidence_set_member, evd_evidence_set_snapshot,
                    evd_evidence_validation, evd_evidence_command_result, evd_evidence_revision,
                    evd_evidence_item, evd_evidence_upload_session, evd_evidence_slot,
                    evd_task_evidence_resolution,
                    fil_download_authorization, fil_scan_result, fil_stored_file, fil_upload_session,
                    tsk_task_execution_guard, tsk_task_assignment, tsk_task,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_inbox_record, rel_idempotency_record,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        deleteRecursively(STORAGE_ROOT);
        Files.createDirectories(STORAGE_ROOT);
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at)
                VALUES (:projectId, :tenantId, 'EVD-COR-IT', 'BYD', '资料整改测试项目',
                    :startsOn, 'ACTIVE', 1, now())
                """).param("projectId", projectId).param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1)).update();
        grant(TECHNICIAN, "evidence.submit", "evidence.read", "file.upload", "file.download");
        grant(REVIEWER, "evidence.review", "evidence.read");
        seedResolvedSlot();
    }

    @Test
    void rejectOpensCorrectionThenResubmitAndClose() throws Exception {
        EvidenceSetSnapshotView first = createSnapshot("first");
        ReviewCaseView review = reviews.create(reviewer(), metadata("review-create"),
                new CreateReviewCaseCommand(first.evidenceSetSnapshotId(), null));
        ReviewCaseView rejected = reviews.decide(reviewer(), metadata("review-reject"),
                new DecideReviewCaseCommand(review.reviewCaseId(), "REJECTED",
                        List.of("IMAGE.BLUR"), "blurry"));
        assertThat(rejected.status()).isEqualTo("REJECTED");

        UUID correctionId = jdbc.sql("""
                SELECT correction_case_id FROM evd_correction_case
                 WHERE source_review_case_id = :review
                """).param("review", review.reviewCaseId()).query(UUID.class).single();
        CorrectionCaseView opened = corrections.get(reviewer(), "corr-get", correctionId);
        assertThat(opened.status()).isEqualTo("OPEN");
        assertThat(opened.reasonCodes()).containsExactly("IMAGE.BLUR");
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='evidence.correction-case-created'")
                .query(Long.class).single()).isOne();

        assertThatThrownBy(() -> corrections.close(reviewer(), metadata("close-too-early"),
                new CloseCorrectionCaseCommand(correctionId, "early")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.CORRECTION_CASE_STATE_CONFLICT));

        EvidenceSetSnapshotView second = createSnapshot("second");
        CorrectionCaseView resubmitted = corrections.resubmit(technician(), metadata("resubmit-1"),
                new ResubmitCorrectionCaseCommand(correctionId, second.evidenceSetSnapshotId()));
        CorrectionCaseView resubmitReplay = corrections.resubmit(technician(), metadata("resubmit-1"),
                new ResubmitCorrectionCaseCommand(correctionId, second.evidenceSetSnapshotId()));
        assertThat(resubmitReplay.correctionCaseId()).isEqualTo(correctionId);
        assertThat(resubmitted.status()).isEqualTo("RESUBMITTED");
        assertThat(resubmitted.resubmissions()).hasSize(1);
        assertThat(resubmitted.latestResubmissionSnapshotId()).isEqualTo(second.evidenceSetSnapshotId());
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='evidence.correction-resubmitted'")
                .query(Long.class).single()).isOne();

        CorrectionCaseView closed = corrections.close(reviewer(), metadata("close-1"),
                new CloseCorrectionCaseCommand(correctionId, "verified close"));
        CorrectionCaseView closeReplay = corrections.close(reviewer(), metadata("close-1"),
                new CloseCorrectionCaseCommand(correctionId, "verified close"));
        assertThat(closeReplay.status()).isEqualTo("CLOSED");
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedBy()).isEqualTo(REVIEWER);
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='evidence.correction-closed'")
                .query(Long.class).single()).isOne();

        assertThatThrownBy(() -> corrections.resubmit(technician(), metadata("resubmit-after-close"),
                new ResubmitCorrectionCaseCommand(correctionId, second.evidenceSetSnapshotId())))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.CORRECTION_CASE_STATE_CONFLICT));
    }

    @Test
    void approveDoesNotCreateCorrectionCase() throws Exception {
        EvidenceSetSnapshotView snapshot = createSnapshot("approve-only");
        ReviewCaseView review = reviews.create(reviewer(), metadata("approve-create"),
                new CreateReviewCaseCommand(snapshot.evidenceSetSnapshotId(), null));
        reviews.decide(reviewer(), metadata("approve-decide"),
                new DecideReviewCaseCommand(review.reviewCaseId(), "APPROVED", List.of(), "ok"));
        assertThat(jdbc.sql("SELECT count(*) FROM evd_correction_case").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='evidence.correction-case-created'")
                .query(Long.class).single()).isZero();
    }

    private EvidenceSetSnapshotView createSnapshot(String marker) throws Exception {
        UUID revisionId = uploadScanAndValidate(pngBytes(marker), "begin-" + marker, "cmd-" + marker);
        return snapshots.create(technician(), metadata("snap-" + marker),
                new CreateEvidenceSetSnapshotCommand(taskId, "TASK_SUBMISSION", List.of(revisionId)));
    }

    private UUID uploadScanAndValidate(byte[] content, String beginKey, String commandId)
            throws Exception {
        String checksum = sha256(content);
        var session = evidence.beginUpload(technician(), metadata(beginKey),
                new BeginEvidenceUploadCommand(
                        taskId, slotId, null, "site.png", "image/png",
                        content.length, checksum, captureJson()));
        transfers.upload(token(session.uploadUrl()), "image/png", content.length,
                new ByteArrayInputStream(content));
        var item = evidence.finalizeUpload(technician(), metadata("fin-" + beginKey),
                new FinalizeEvidenceUploadCommand(
                        taskId, slotId, session.uploadSessionId(), checksum, commandId));
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        handlers.stream().filter(handler -> handler.supports("file.scan-completed", 1))
                .forEach(handler -> handler.handle(latestScanCompletedEvent()));
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        return item.revisions().getFirst().evidenceRevisionId();
    }

    private void seedResolvedSlot() {
        String definition = """
                {"templateKey":"survey.site","version":"1.0.0","stage":"SURVEY",
                 "items":[{"evidenceKey":"site.photo","name":"现场照片","mediaType":"PHOTO","required":true,
                   "capture":{"minCount":1,"maxCount":2}}]}
                """;
        UUID assetId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, "survey.site", "1.0.0", "1.0.0",
                definition.trim(), Sha256.digest(definition.trim()))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "EVD-COR-BUNDLE", "1.0.0", "BYD", "HOME",
                        null, Instant.now().minusSeconds(60), null, List.of(assetId)));
        taskId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id, tenant_id, task_type, task_kind, business_key, payload_digest,
                    priority, status, next_run_at, attempt_count, max_attempts,
                    correlation_id, version, created_at, updated_at, claimed_by, claimed_at, started_at,
                    project_id, work_order_id, workflow_instance_id, stage_instance_id,
                    workflow_node_instance_id, workflow_node_id, workflow_definition_version_id,
                    workflow_definition_digest, configuration_bundle_id, configuration_bundle_digest,
                    stage_code)
                VALUES (:task, :tenant, 'SITE_SURVEY', 'HUMAN', :businessKey, :digest,
                    100, 'RUNNING', now(), 0, 1, 'corr-evd-045', 3, now(), now(),
                    :actor, now(), now(), :project, :workOrder, :workflow, :stage,
                    :nodeInstance, 'SITE_SURVEY', :definitionId, :digest, :bundle, :bundleDigest,
                    'SURVEY')
                """).param("task", taskId).param("tenant", TENANT).param("businessKey", taskId.toString())
                .param("digest", "d".repeat(64)).param("actor", TECHNICIAN)
                .param("project", projectId).param("workOrder", UUID.randomUUID())
                .param("workflow", UUID.randomUUID()).param("stage", UUID.randomUUID())
                .param("nodeInstance", UUID.randomUUID()).param("definitionId", assetId)
                .param("bundle", bundle.bundleId()).param("bundleDigest", bundle.manifestDigest())
                .update();
        jdbc.sql("""
                INSERT INTO tsk_task_assignment (
                    task_assignment_id, tenant_id, task_id, assignment_kind, principal_type,
                    principal_id, status, source_type, source_id, effective_from, created_by, created_at)
                VALUES (:id, :tenant, :task, 'RESPONSIBLE', 'USER', :actor, 'ACTIVE',
                    'MANUAL', 'M45-FIXTURE', now(), 'fixture', now())
                """).param("id", UUID.randomUUID()).param("tenant", TENANT).param("task", taskId)
                .param("actor", TECHNICIAN).update();

        UUID resolutionId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        String itemDefinition = """
                {"evidenceKey":"site.photo","name":"现场照片","mediaType":"PHOTO","required":true,
                 "capture":{"minCount":1,"maxCount":2}}
                """;
        jdbc.sql("""
                INSERT INTO evd_task_evidence_resolution (
                    resolution_id, tenant_id, project_id, task_id, configuration_bundle_id,
                    configuration_bundle_digest, stage_code, source_event_id, source_event_digest,
                    resolver_version, slot_count, resolved_at)
                VALUES (:id, :tenant, :project, :task, :bundle, :digest, 'SURVEY', :event,
                    :eventDigest, 'FIXED_EVIDENCE_V1', 1, now())
                """).param("id", resolutionId).param("tenant", TENANT).param("project", projectId)
                .param("task", taskId).param("bundle", bundle.bundleId())
                .param("digest", bundle.manifestDigest()).param("event", UUID.randomUUID())
                .param("eventDigest", "e".repeat(64)).update();
        jdbc.sql("""
                INSERT INTO evd_evidence_slot (
                    slot_id, tenant_id, project_id, task_id, resolution_id, template_version_id,
                    template_key, template_version, template_digest, requirement_code, occurrence_key,
                    requirement_name, media_type, required_flag, min_count, max_count,
                    condition_input_digest, resolution_explanation, requirement_definition,
                    requirement_digest, status_projection, resolved_at)
                VALUES (:slot, :tenant, :project, :task, :resolution, :template,
                    'survey.site', '1.0.0', :templateDigest, 'site.photo', 'default',
                    '现场照片', 'PHOTO', true, 1, 2, :conditionDigest,
                    CAST('{"kind":"FIXED"}' AS jsonb), CAST(:definition AS jsonb),
                    :reqDigest, 'MISSING', now())
                """).param("slot", slotId).param("tenant", TENANT).param("project", projectId)
                .param("task", taskId).param("resolution", resolutionId).param("template", assetId)
                .param("templateDigest", Sha256.digest(definition.trim()))
                .param("conditionDigest", "f".repeat(64))
                .param("definition", itemDefinition.trim())
                .param("reqDigest", Sha256.digest(itemDefinition.trim())).update();
    }

    private OutboxMessage latestScanCompletedEvent() {
        Map<String, Object> row = jdbc.sql("""
                SELECT outbox_id, event_id, module_name, event_type, schema_version,
                       aggregate_type, aggregate_id, aggregate_version, tenant_id,
                       correlation_id, causation_id, partition_key, payload::text AS payload,
                       payload_digest, occurred_at
                  FROM rel_outbox_event WHERE event_type='file.scan-completed'
                 ORDER BY occurred_at DESC LIMIT 1
                """).query().singleRow();
        return new OutboxMessage(
                uuid(row, "outbox_id"), uuid(row, "event_id"),
                text(row, "module_name"), text(row, "event_type"),
                number(row, "schema_version").intValue(),
                text(row, "aggregate_type"), text(row, "aggregate_id"),
                number(row, "aggregate_version").longValue(),
                text(row, "tenant_id"), text(row, "correlation_id"),
                text(row, "causation_id"), text(row, "partition_key"),
                text(row, "payload"), text(row, "payload_digest"),
                instant(row.get("occurred_at")), 1);
    }

    private void grant(String principalId, String... capabilities) {
        UUID role = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:role, :tenant, :code, '资料整改角色', 'ACTIVE', now())
                """).param("role", role).param("tenant", TENANT)
                .param("code", "correction-role-" + role).update();
        for (String capability : capabilities) {
            jdbc.sql("INSERT INTO auth_role_capability (role_id, capability_code, granted_at) VALUES (:role,:cap,now())")
                    .param("role", role).param("cap", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (:grant, :tenant, :principal, :role, 'PROJECT', :project,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M45-CORRECTION', now())
                """).param("grant", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", principalId).param("role", role)
                .param("project", projectId.toString()).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (:grant, :tenant, :principal, :role, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M45-CORRECTION', now())
                """).param("grant", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", principalId).param("role", role).update();
    }

    private CurrentPrincipal technician() {
        return new CurrentPrincipal(
                TECHNICIAN, TENANT, CurrentPrincipal.PrincipalType.USER, "mobile", Set.of());
    }

    private CurrentPrincipal reviewer() {
        return new CurrentPrincipal(
                REVIEWER, TENANT, CurrentPrincipal.PrincipalType.USER, "ops-web", Set.of());
    }

    private static CommandMetadata metadata(String suffix) {
        return new CommandMetadata("corr-" + suffix, "idem-" + suffix);
    }

    private static String captureJson() {
        return "{\"captureSource\":\"CAMERA\",\"capturedAt\":\"2026-07-14T08:00:00Z\",\"deviceId\":\"DEV-1\"}";
    }

    private static String token(String url) {
        String path = URI.create(url).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static byte[] pngBytes(String marker) {
        byte[] prefix = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] body = marker.getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[prefix.length + body.length + 16];
        System.arraycopy(prefix, 0, content, 0, prefix.length);
        System.arraycopy(body, 0, content, prefix.length, body.length);
        return content;
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private static Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instantValue) {
            return instantValue;
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalArgumentException("unsupported time type: " + value.getClass().getName());
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("serviceos-evidence-correction-it");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
