package com.serviceos.task;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.evidence.api.BeginEvidenceUploadCommand;
import com.serviceos.evidence.api.CreateEvidenceSetSnapshotCommand;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.FinalizeEvidenceUploadCommand;
import com.serviceos.files.infrastructure.LocalObjectTransferService;
import com.serviceos.forms.api.FormSubmissionService;
import com.serviceos.forms.api.SubmitFormCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.InputVersionRef;
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

/** M43：真实 PostgreSQL 验证同一 HUMAN Task 的表单和资料快照双引用完成门禁。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DualInputTaskCompletionPostgresIT {
    private static final String TENANT = "tenant-dual-input-043";
    private static final String TECHNICIAN = "technician-dual-input-043";
    private static final Path STORAGE_ROOT = temporaryStorageRoot();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos").withUsername("serviceos_test").withPassword("serviceos_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("serviceos.files.local.root", STORAGE_ROOT::toString);
        registry.add("serviceos.files.local.signing-key",
                () -> "dual-input-it-signing-key-with-thirty-two-bytes");
        registry.add("serviceos.task.scheduling-enabled", () -> "false");
    }

    @Autowired ConfigurationService configurations;
    @Autowired FormSubmissionService submissions;
    @Autowired EvidenceCommandService evidence;
    @Autowired EvidenceSetSnapshotService snapshots;
    @Autowired HumanTaskCommandService humanTasks;
    @Autowired LocalObjectTransferService transfers;
    @Autowired TaskExecutionWorker worker;
    @Autowired List<OutboxMessageHandler> handlers;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    UUID taskId;
    UUID formVersionId;
    UUID slotId;
    ConfigurationBundleReference bundle;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("""
                TRUNCATE TABLE evd_evidence_set_member, evd_evidence_set_snapshot,
                    evd_evidence_validation, evd_evidence_command_result, evd_evidence_revision,
                    evd_evidence_item, evd_evidence_upload_session, evd_evidence_slot,
                    evd_task_evidence_resolution, frm_form_command_result, frm_submission_validation,
                    frm_form_submission, fil_download_authorization, fil_scan_result, fil_stored_file,
                    fil_upload_session, tsk_task_execution_guard, tsk_task_assignment, tsk_task,
                    cfg_configuration_bundle_item, cfg_configuration_bundle, cfg_configuration_asset_version,
                    prj_project, aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_inbox_record, rel_idempotency_record, auth_role_grant,
                    auth_role_capability, auth_role CASCADE
                """).update();
        deleteRecursively(STORAGE_ROOT);
        Files.createDirectories(STORAGE_ROOT);
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at)
                VALUES (:project, :tenant, 'DUAL-INPUT-IT', 'BYD', '双引用完成测试项目',
                    :startsOn, 'ACTIVE', 1, now())
                """).param("project", projectId).param("tenant", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1)).update();
        grant(TECHNICIAN, "task.complete", "evidence.submit", "evidence.read",
                "file.upload", "file.download", "form.submit", "form.read");
        seedTaskAndResolvedSlot();
    }

    @Test
    void completesDualInputTaskWithFormAndSnapshotRefs() throws Exception {
        var submission = submitValidatedForm();
        EvidenceSetSnapshotView snapshot = validatedSnapshot();
        String formRef = "form-submission://" + submission.submissionId();
        List<InputVersionRef> refs = List.of(
                new InputVersionRef(InputVersionRef.FORM_SUBMISSION, formRef, submission.contentDigest()),
                new InputVersionRef(InputVersionRef.EVIDENCE_SET_SNAPSHOT,
                        "evidence-set-snapshot://" + snapshot.evidenceSetSnapshotId(), snapshot.contentDigest()));

        var completed = humanTasks.complete(principal(), metadata("complete-dual"),
                new CompleteHumanTaskCommand(taskId, 3, formRef, submission.contentDigest(), refs));
        var replay = humanTasks.complete(principal(), metadata("complete-dual"),
                new CompleteHumanTaskCommand(taskId, 3, formRef, submission.contentDigest(), refs));

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(replay).isEqualTo(completed);
        assertThat(jdbc.sql("""
                SELECT result_ref || ':' || result_digest || ':' || input_version_refs::text
                  FROM tsk_task WHERE task_id=:task
                """).param("task", taskId).query(String.class).single())
                .contains(formRef, submission.contentDigest(), "FORM_SUBMISSION",
                        "EVIDENCE_SET_SNAPSHOT", snapshot.contentDigest());
        assertThat(count("rel_outbox_event WHERE event_type='task.completed'")).isOne();
    }

    @Test
    void rejectsDualInputMissingEvidenceRefWithoutPollution() {
        var submission = submitValidatedForm();
        String formRef = "form-submission://" + submission.submissionId();

        assertProblem(ProblemCode.TASK_INPUT_REFS_INVALID, () -> humanTasks.complete(
                principal(), metadata("missing-evidence"),
                new CompleteHumanTaskCommand(taskId, 3, formRef, submission.contentDigest(), List.of())));

        assertNoCompletionPollution();
    }

    @Test
    void rejectsWrongSnapshotDigest() throws Exception {
        var submission = submitValidatedForm();
        EvidenceSetSnapshotView snapshot = validatedSnapshot();
        String formRef = "form-submission://" + submission.submissionId();
        List<InputVersionRef> refs = List.of(
                new InputVersionRef(InputVersionRef.FORM_SUBMISSION, formRef, submission.contentDigest()),
                new InputVersionRef(InputVersionRef.EVIDENCE_SET_SNAPSHOT,
                        "evidence-set-snapshot://" + snapshot.evidenceSetSnapshotId(), "0".repeat(64)));

        assertThatThrownBy(() -> humanTasks.complete(principal(), metadata("wrong-snapshot-digest"),
                new CompleteHumanTaskCommand(taskId, 3, formRef, submission.contentDigest(), refs)))
                .isInstanceOfSatisfying(BusinessProblem.class, problem -> assertThat(problem.code())
                        .isIn(ProblemCode.EVIDENCE_SET_NOT_VALIDATED, ProblemCode.TASK_INPUT_REFS_INVALID));
        assertNoCompletionPollution();
    }

    private void assertNoCompletionPollution() {
        assertThat(count("tsk_task WHERE status='COMPLETED'")).isZero();
        assertThat(count("rel_outbox_event WHERE event_type='task.completed'")).isZero();
        assertThat(count("rel_idempotency_record WHERE operation_type='task.human.complete'")).isZero();
    }

    private com.serviceos.forms.api.FormSubmissionView submitValidatedForm() {
        var submission = submissions.submit(principal(), metadata("submit-form"),
                new SubmitFormCommand(taskId, formVersionId, "{\"survey.conclusion\":\"PASS\"}", null));
        assertThat(submission.validationStatus()).isEqualTo("VALIDATED");
        return submission;
    }

    private EvidenceSetSnapshotView validatedSnapshot() throws Exception {
        UUID revisionId = uploadScanAndValidate();
        return snapshots.create(principal(), metadata("create-snapshot"),
                new CreateEvidenceSetSnapshotCommand(taskId, "TASK_SUBMISSION", List.of(revisionId)));
    }

    private UUID uploadScanAndValidate() throws Exception {
        byte[] content = pngBytes("dual-input");
        String checksum = sha256(content);
        var session = evidence.beginUpload(principal(), metadata("begin-evidence"),
                new BeginEvidenceUploadCommand(taskId, slotId, null, "site.png", "image/png",
                        content.length, checksum, captureJson()));
        transfers.upload(token(session.uploadUrl()), "image/png", content.length,
                new ByteArrayInputStream(content));
        var item = evidence.finalizeUpload(principal(), metadata("finalize-evidence"),
                new FinalizeEvidenceUploadCommand(taskId, slotId, session.uploadSessionId(), checksum,
                        "dual-input-evidence-command"));
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        handlers.stream().filter(handler -> handler.supports("file.scan-completed", 1))
                .forEach(handler -> handler.handle(latestScanCompletedEvent()));
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        UUID revisionId = item.revisions().getFirst().evidenceRevisionId();
        assertThat(jdbc.sql("SELECT status FROM evd_evidence_revision WHERE evidence_revision_id=:id")
                .param("id", revisionId).query(String.class).single()).isEqualTo("VALIDATED");
        return revisionId;
    }

    private void seedTaskAndResolvedSlot() {
        String formDefinition = """
                {"formKey":"survey.form","version":"1.0.0","stage":"SURVEY","sections":[
                  {"sectionKey":"site","title":"现场信息","fields":[
                    {"fieldKey":"survey.conclusion","label":"勘测结论","dataType":"STRING",
                     "binding":"task.input.survey.conclusion","required":true}
                  ]}]}
                """.trim();
        String evidenceDefinition = """
                {"templateKey":"survey.site","version":"1.0.0","stage":"SURVEY","items":[
                  {"evidenceKey":"site.photo","name":"现场照片","mediaType":"PHOTO","required":true,
                   "capture":{"minCount":1,"maxCount":2}}]}
                """.trim();
        formVersionId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.FORM, "survey.form", "1.0.0", "1.0.0",
                formDefinition, Sha256.digest(formDefinition))).versionId();
        UUID evidenceVersionId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, "survey.site", "1.0.0", "1.0.0",
                evidenceDefinition, Sha256.digest(evidenceDefinition))).versionId();
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "DUAL-INPUT-BUNDLE", "1.0.0", "BYD", "SURVEY",
                null, Instant.now().minusSeconds(60), null, List.of(formVersionId, evidenceVersionId)));

        taskId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id, tenant_id, task_type, task_kind, business_key, payload_digest,
                    priority, status, next_run_at, attempt_count, max_attempts, correlation_id, version,
                    created_at, updated_at, claimed_by, claimed_at, started_at, project_id, work_order_id,
                    workflow_instance_id, stage_instance_id, workflow_node_instance_id, workflow_node_id,
                    workflow_definition_version_id, workflow_definition_digest, form_ref,
                    configuration_bundle_id, configuration_bundle_digest, stage_code)
                VALUES (:task, :tenant, 'SURVEY_TASK', 'HUMAN', :businessKey, :digest,
                    100, 'RUNNING', now(), 0, 1, 'corr-dual-043', 3, now(), now(), :actor, now(), now(),
                    :project, :workOrder, :workflow, :stage, :node, 'SURVEY_TASK', :formVersion, :digest,
                    'survey.form', :bundle, :bundleDigest, 'SURVEY')
                """).param("task", taskId).param("tenant", TENANT).param("businessKey", taskId.toString())
                .param("digest", "d".repeat(64)).param("actor", TECHNICIAN).param("project", projectId)
                .param("workOrder", UUID.randomUUID()).param("workflow", UUID.randomUUID())
                .param("stage", UUID.randomUUID()).param("node", UUID.randomUUID())
                .param("formVersion", formVersionId).param("bundle", bundle.bundleId())
                .param("bundleDigest", bundle.manifestDigest()).update();
        assignment("RESPONSIBLE", "M43-DUAL-RESPONSIBLE");
        assignment("CANDIDATE", "M43-DUAL-CANDIDATE");

        UUID resolutionId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        String itemDefinition = """
                {"evidenceKey":"site.photo","name":"现场照片","mediaType":"PHOTO","required":true,
                 "capture":{"minCount":1,"maxCount":2}}
                """.trim();
        jdbc.sql("""
                INSERT INTO evd_task_evidence_resolution (
                    resolution_id, tenant_id, project_id, task_id, configuration_bundle_id,
                    configuration_bundle_digest, stage_code, source_event_id, source_event_digest,
                    resolver_version, condition_input_digest, resolution_explanation,
                    slot_count, resolved_at)
                VALUES (:resolution, :tenant, :project, :task, :bundle, :bundleDigest, 'SURVEY',
                    :event, :eventDigest, 'FIXED_EVIDENCE_V1',
                    '44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a',
                    CAST('{"kind":"TEST_FIXED_CONTEXT","resolverVersion":"FIXED_EVIDENCE_V1"}' AS jsonb),
                    1, now())
                """).param("resolution", resolutionId).param("tenant", TENANT).param("project", projectId)
                .param("task", taskId).param("bundle", bundle.bundleId())
                .param("bundleDigest", bundle.manifestDigest()).param("event", UUID.randomUUID())
                .param("eventDigest", "e".repeat(64)).update();
        jdbc.sql("""
                INSERT INTO evd_evidence_slot (
                    slot_id, tenant_id, project_id, task_id, resolution_id, template_version_id,
                    template_key, template_version, template_digest, requirement_code, occurrence_key,
                    requirement_name, media_type, required_flag, min_count, max_count, condition_input_digest,
                    resolution_explanation, requirement_definition, requirement_digest, status_projection, resolved_at)
                VALUES (:slot, :tenant, :project, :task, :resolution, :template, 'survey.site', '1.0.0',
                    :templateDigest, 'site.photo', 'default', '现场照片', 'PHOTO', true, 1, 2, :conditionDigest,
                    CAST('{"kind":"FIXED"}' AS jsonb), CAST(:definition AS jsonb), :requirementDigest, 'MISSING', now())
                """).param("slot", slotId).param("tenant", TENANT).param("project", projectId)
                .param("task", taskId).param("resolution", resolutionId).param("template", evidenceVersionId)
                .param("templateDigest", Sha256.digest(evidenceDefinition)).param("conditionDigest", "f".repeat(64))
                .param("definition", itemDefinition).param("requirementDigest", Sha256.digest(itemDefinition)).update();
    }

    private void assignment(String kind, String sourceId) {
        jdbc.sql("""
                INSERT INTO tsk_task_assignment (
                    task_assignment_id, tenant_id, task_id, assignment_kind, principal_type, principal_id,
                    status, source_type, source_id, effective_from, created_by, created_at)
                VALUES (:id, :tenant, :task, :kind, 'USER', :actor, 'ACTIVE', 'MANUAL', :source, now(), 'fixture', now())
                """).param("id", UUID.randomUUID()).param("tenant", TENANT).param("task", taskId)
                .param("kind", kind).param("actor", TECHNICIAN).param("source", sourceId).update();
    }

    private OutboxMessage latestScanCompletedEvent() {
        Map<String, Object> row = jdbc.sql("""
                SELECT outbox_id, event_id, module_name, event_type, schema_version, aggregate_type,
                       aggregate_id, aggregate_version, tenant_id, correlation_id, causation_id, partition_key,
                       payload::text AS payload, payload_digest, occurred_at
                  FROM rel_outbox_event WHERE event_type='file.scan-completed'
                 ORDER BY occurred_at DESC LIMIT 1
                """).query().singleRow();
        return new OutboxMessage(uuid(row, "outbox_id"), uuid(row, "event_id"), text(row, "module_name"),
                text(row, "event_type"), number(row, "schema_version").intValue(), text(row, "aggregate_type"),
                text(row, "aggregate_id"), number(row, "aggregate_version").longValue(), text(row, "tenant_id"),
                text(row, "correlation_id"), text(row, "causation_id"), text(row, "partition_key"),
                text(row, "payload"), text(row, "payload_digest"), instant(row.get("occurred_at")), 1);
    }

    private void grant(String principalId, String... capabilities) {
        UUID role = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:role, :tenant, 'dual-input-executor-043', '双引用执行人', 'ACTIVE', now())
                """).param("role", role).param("tenant", TENANT).update();
        for (String capability : capabilities) {
            jdbc.sql("INSERT INTO auth_role_capability (role_id, capability_code, granted_at) VALUES (:role,:cap,now())")
                    .param("role", role).param("cap", capability).update();
        }
        for (String scope : List.of("PROJECT", "TENANT")) {
            jdbc.sql("""
                    INSERT INTO auth_role_grant (
                        grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                        valid_from, source_code, approval_ref, created_at)
                    VALUES (:grant, :tenant, :principal, :role, :scope, :scopeRef,
                        now() - interval '1 day', 'TEST_FIXTURE', 'M43-DUAL-INPUT', now())
                    """).param("grant", UUID.randomUUID()).param("tenant", TENANT).param("principal", principalId)
                    .param("role", role).param("scope", scope)
                    .param("scopeRef", "PROJECT".equals(scope) ? projectId.toString() : TENANT).update();
        }
    }

    private CurrentPrincipal principal() {
        return new CurrentPrincipal(TECHNICIAN, TENANT, CurrentPrincipal.PrincipalType.USER, "dual-input-it", Set.of());
    }
    private static CommandMetadata metadata(String key) { return new CommandMetadata("corr-" + key, "idem-" + key); }
    private long count(String tableAndPredicate) { return jdbc.sql("SELECT count(*) FROM " + tableAndPredicate).query(Long.class).single(); }
    private static void assertProblem(ProblemCode expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessProblem.class,
                problem -> assertThat(problem.code()).isEqualTo(expected));
    }
    private static String captureJson() {
        return "{\"captureSource\":\"CAMERA\",\"capturedAt\":\"2026-07-14T08:00:00Z\",\"deviceId\":\"DUAL-1\"}";
    }
    private static String token(String url) {
        String path = URI.create(url).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
    private static byte[] pngBytes(String marker) {
        byte[] prefix = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
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
    private static String text(Map<String, Object> row, String key) { return row.get(key).toString(); }
    private static Number number(Map<String, Object> row, String key) { return (Number) row.get(key); }
    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime offset) return offset.toInstant();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalArgumentException("Unsupported time type: " + value.getClass().getName());
    }
    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("serviceos-dual-input-it");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        }
    }
}
