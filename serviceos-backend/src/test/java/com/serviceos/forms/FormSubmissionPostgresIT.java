package com.serviceos.forms;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.forms.api.FormSubmissionService;
import com.serviceos.forms.api.SubmitFormCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M34：真实 PostgreSQL 验证精确版本提交、不可变验证、幂等、授权和执行保护窗。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FormSubmissionPostgresIT {
    private static final String TENANT = "tenant-form-submit-it";
    private static final String TECHNICIAN = "technician-036";
    private static final UUID PROJECT = UUID.fromString("36000000-0000-4000-8000-000000000001");
    private static final UUID WORK_ORDER = UUID.fromString("36000000-0000-4000-8000-000000000002");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos").withUsername("serviceos_test").withPassword("serviceos_test");

    @org.springframework.test.context.DynamicPropertySource
    static void properties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired ConfigurationService configurations;
    @Autowired FormSubmissionService submissions;
    @Autowired JdbcClient jdbc;

    UUID formVersionId;
    ConfigurationBundleReference bundle;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE frm_form_command_result, frm_submission_validation, frm_form_submission,
                    tsk_task_execution_guard, tsk_task_assignment, tsk_task,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at)
                VALUES (:project, :tenant, 'FORM-SUBMIT-IT', 'BYD', '表单提交测试项目',
                    :startsOn, 'ACTIVE', 1, now())
                """).param("project", PROJECT).param("tenant", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1)).update();
        String definition = baseDefinition();
        formVersionId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.FORM, "survey.execution", "1.0.0", "1.0.0",
                definition, Sha256.digest(definition))).versionId();
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, PROJECT, "FORM-SUBMIT-BUNDLE", "1.0.0", "BYD", "SURVEY",
                null, Instant.now().minusSeconds(60), null, List.of(formVersionId)));
        grant(TECHNICIAN, "form.submit", "form.read");
    }

    @Test
    void submitsExactLockedVersionAtomicallyAndReplaysFrozenResult() {
        UUID taskId = runningTask(TECHNICIAN, "survey.execution", bundle, formVersionId);
        SubmitFormCommand command = new SubmitFormCommand(taskId, formVersionId,
                "{\"survey.conclusion\":\"PASS\",\"installation.count\":2,\"site.photo\":\"revision-1\"}", null);

        var first = submissions.submit(principal(), metadata("submit-valid"), command);
        var replay = submissions.submit(principal(), metadata("submit-valid"), command);

        assertThat(replay).isEqualTo(first);
        assertThat(first.validationStatus()).isEqualTo("VALIDATED");
        assertThat(first.submissionVersion()).isEqualTo(1);
        assertThat(submissions.get(principal(), "corr-read", first.submissionId())).isEqualTo(first);
        assertThat(jdbc.sql("SELECT count(*) FROM frm_form_submission").query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM frm_submission_validation").query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='form.submitted'")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT payload::text FROM rel_outbox_event WHERE event_type='form.submitted'")
                .query(String.class).single())
                .doesNotContain("survey.conclusion", "PASS", "installation.count", "site.photo");
        assertThat(jdbc.sql("SELECT count(*) FROM aud_audit_record WHERE capability_code='form.submit'")
                .query(Long.class).single()).isOne();
        assertThatThrownBy(() -> jdbc.sql("UPDATE frm_form_submission SET form_key='tampered'").update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> submissions.submit(principal(), metadata("submit-valid"),
                new SubmitFormCommand(taskId, formVersionId,
                        "{\"survey.conclusion\":\"CHANGED\"}", null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    void persistsInvalidSubmissionWithStableFieldErrorsAndNewVersion() {
        UUID taskId = runningTask(TECHNICIAN, "survey.execution", bundle, formVersionId);
        var invalid = submissions.submit(principal(), metadata("submit-invalid"),
                new SubmitFormCommand(taskId, formVersionId,
                        "{\"installation.count\":\"two\",\"site.photo\":\"https://public.example/file\",\"rogue\":true}", null));
        var valid = submissions.submit(principal(), metadata("submit-second"),
                new SubmitFormCommand(taskId, formVersionId,
                        "{\"survey.conclusion\":\"PASS\"}", null));

        assertThat(invalid.validationStatus()).isEqualTo("INVALID");
        assertThat(invalid.errors()).extracting(issue -> issue.code())
                .containsExactlyInAnyOrder("FIELD_REQUIRED", "FIELD_TYPE_INVALID",
                        "FIELD_TYPE_INVALID", "FIELD_UNKNOWN");
        assertThat(valid.submissionVersion()).isEqualTo(2);
        assertThat(valid.validationStatus()).isEqualTo("VALIDATED");
    }

    @Test
    void rejectsVersionDriftPrefillExpressionsOldOwnerAndActiveGuardWithoutPollution() {
        UUID taskId = runningTask(TECHNICIAN, "survey.execution", bundle, formVersionId);
        UUID wrongVersion = UUID.randomUUID();
        assertProblem(ProblemCode.FORM_VERSION_CONFLICT, () -> submissions.submit(
                principal(), metadata("wrong-version"),
                new SubmitFormCommand(taskId, wrongVersion, "{}", null)));
        assertProblem(ProblemCode.FORM_RUNTIME_UNSUPPORTED, () -> submissions.submit(
                principal(), metadata("prefill-not-approved"),
                new SubmitFormCommand(taskId, formVersionId, "{}", "prefill-v1")));

        jdbc.sql("UPDATE tsk_task_assignment SET principal_id='technician-new' WHERE task_id=:task")
                .param("task", taskId).update();
        assertProblem(ProblemCode.TECHNICIAN_ASSIGNMENT_CHANGED, () -> submissions.submit(
                principal(), metadata("old-owner"),
                new SubmitFormCommand(taskId, formVersionId, "{}", null)));
        jdbc.sql("UPDATE tsk_task_assignment SET principal_id=:actor WHERE task_id=:task")
                .param("actor", TECHNICIAN).param("task", taskId).update();
        jdbc.sql("""
                INSERT INTO tsk_task_execution_guard (
                    task_execution_guard_id, tenant_id, task_id, guard_type, guard_key,
                    reason_code, status, activated_task_version, activated_by, activated_at)
                VALUES (:id, :tenant, :task, 'REASSIGNMENT', 'guard-036',
                    'REASSIGNMENT_PENDING', 'ACTIVE', 3, 'fixture', now())
                """).param("id", UUID.randomUUID()).param("tenant", TENANT).param("task", taskId).update();
        assertProblem(ProblemCode.TASK_EXECUTION_GUARDED, () -> submissions.submit(
                principal(), metadata("guarded"),
                new SubmitFormCommand(taskId, formVersionId, "{}", null)));
        assertThat(jdbc.sql("SELECT count(*) FROM frm_form_submission").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM rel_idempotency_record WHERE operation_type='form.submit'")
                .query(Long.class).single()).isZero();
    }

    @Test
    void outboxFailureRollsBackSubmissionValidationAuditAndIdempotency() {
        UUID taskId = runningTask(TECHNICIAN, "survey.execution", bundle, formVersionId);
        jdbc.sql("""
                CREATE OR REPLACE FUNCTION frm_fail_form_outbox() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'form.submitted' THEN
                        RAISE EXCEPTION 'injected form.submitted outbox failure';
                    END IF;
                    RETURN NEW;
                END;
                $$;
                CREATE TRIGGER trg_frm_fail_form_outbox
                    BEFORE INSERT ON rel_outbox_event
                    FOR EACH ROW EXECUTE FUNCTION frm_fail_form_outbox();
                """).update();
        try {
            assertThatThrownBy(() -> submissions.submit(principal(), metadata("outbox-failure"),
                    new SubmitFormCommand(taskId, formVersionId,
                            "{\"survey.conclusion\":\"PASS\"}", null)))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("injected form.submitted outbox failure");
            assertThat(jdbc.sql("SELECT count(*) FROM frm_form_submission").query(Long.class).single()).isZero();
            assertThat(jdbc.sql("SELECT count(*) FROM frm_submission_validation").query(Long.class).single()).isZero();
            assertThat(jdbc.sql("SELECT count(*) FROM aud_audit_record").query(Long.class).single()).isZero();
            assertThat(jdbc.sql("SELECT count(*) FROM rel_idempotency_record WHERE operation_type='form.submit'")
                    .query(Long.class).single()).isZero();
        } finally {
            jdbc.sql("DROP TRIGGER IF EXISTS trg_frm_fail_form_outbox ON rel_outbox_event").update();
            jdbc.sql("DROP FUNCTION IF EXISTS frm_fail_form_outbox()").update();
        }
    }

    private UUID runningTask(String responsible, String formRef,
                             ConfigurationBundleReference frozenBundle, UUID definitionId) {
        UUID taskId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id, tenant_id, task_type, task_kind, business_key, payload_digest,
                    priority, status, next_run_at, attempt_count, max_attempts,
                    correlation_id, version, created_at, updated_at, claimed_by, claimed_at, started_at,
                    project_id, work_order_id, workflow_instance_id, stage_instance_id,
                    workflow_node_instance_id, workflow_node_id, workflow_definition_version_id,
                    workflow_definition_digest, form_ref, configuration_bundle_id,
                    configuration_bundle_digest)
                VALUES (:task, :tenant, 'SURVEY_TASK', 'HUMAN', :businessKey, :digest,
                    100, 'RUNNING', now(), 0, 1, 'corr-form-036', 3, now(), now(),
                    :responsible, now(), now(), :project, :workOrder, :workflow, :stage,
                    :nodeInstance, 'SURVEY_TASK', :definitionId, :digest, :formRef, :bundle,
                    :bundleDigest)
                """).param("task", taskId).param("tenant", TENANT).param("businessKey", taskId.toString())
                .param("digest", "d".repeat(64)).param("responsible", responsible)
                .param("project", PROJECT).param("workOrder", WORK_ORDER).param("workflow", UUID.randomUUID())
                .param("stage", UUID.randomUUID()).param("nodeInstance", UUID.randomUUID())
                .param("definitionId", definitionId).param("formRef", formRef)
                .param("bundle", frozenBundle.bundleId()).param("bundleDigest", frozenBundle.manifestDigest())
                .update();
        jdbc.sql("""
                INSERT INTO tsk_task_assignment (
                    task_assignment_id, tenant_id, task_id, assignment_kind, principal_type,
                    principal_id, status, source_type, source_id, effective_from, created_by, created_at)
                VALUES (:id, :tenant, :task, 'RESPONSIBLE', 'USER', :responsible, 'ACTIVE',
                    'MANUAL', 'M34-FIXTURE', now(), 'fixture', now())
                """).param("id", UUID.randomUUID()).param("tenant", TENANT).param("task", taskId)
                .param("responsible", responsible).update();
        return taskId;
    }

    private void grant(String principalId, String... capabilities) {
        UUID role = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:role, :tenant, 'form-executor-036', '表单执行人', 'ACTIVE', now())
                """).param("role", role).param("tenant", TENANT).update();
        for (String capability : capabilities) {
            jdbc.sql("INSERT INTO auth_role_capability (role_id, capability_code, granted_at) VALUES (:role,:cap,now())")
                    .param("role", role).param("cap", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (:grant, :tenant, :principal, :role, 'PROJECT', :project,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M34-FORM-SUBMIT', now())
                """).param("grant", UUID.randomUUID()).param("tenant", TENANT).param("principal", principalId)
                .param("role", role).param("project", PROJECT.toString()).update();
    }

    private CurrentPrincipal principal() {
        return new CurrentPrincipal(TECHNICIAN, TENANT, CurrentPrincipal.PrincipalType.USER,
                "form-submit-it", Set.of());
    }
    private static CommandMetadata metadata(String key) { return new CommandMetadata("corr-" + key, key); }
    private static void assertProblem(ProblemCode code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessProblem.class,
                problem -> assertThat(problem.code()).isEqualTo(code));
    }
    private static String baseDefinition() {
        return """
                {"formKey":"survey.execution","version":"1.0.0","stage":"SURVEY","sections":[
                  {"sectionKey":"site","title":"现场信息","fields":[
                    {"fieldKey":"survey.conclusion","label":"勘测结论","dataType":"STRING","binding":"task.input.survey.conclusion","required":true},
                    {"fieldKey":"installation.count","label":"数量","dataType":"INTEGER","binding":"task.input.installation.count"},
                    {"fieldKey":"site.photo","label":"现场照片","dataType":"FILE_REF","binding":"task.input.site.photo"}
                  ]}]}
                """.trim();
    }
}
