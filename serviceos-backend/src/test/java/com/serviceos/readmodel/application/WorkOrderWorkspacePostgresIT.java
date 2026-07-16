package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.WorkOrderWorkspace;
import com.serviceos.readmodel.api.WorkOrderWorkspaceQueryService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M85～M89：工作区组合查询的授权、无 PII、按需区块与缺权降级证据。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkOrderWorkspacePostgresIT {
    private static final String TENANT = "tenant-workspace-it";
    private static final String FORM_KEY = "survey.execution";
    private static final String EVIDENCE_KEY = "survey.site";

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
    }

    @Autowired
    private WorkOrderWorkspaceQueryService workspaces;

    @Autowired
    private WorkOrderCommandService workOrders;

    @Autowired
    private ConfigurationService configurations;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID projectId;
    private ConfigurationBundleReference bundle;
    private UUID formVersionId;
    private UUID evidenceVersionId;
    private String evidenceDigest;
    private UUID workOrderId;
    private UUID taskId;

    @BeforeEach
    void seed() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_projection_dead_letter, rdm_projection_checkpoint,
                    rdm_work_order_timeline_entry, rel_inbox_record,
                    aud_audit_record, tsk_task_execution_attempt, tsk_task,
                    ops_task_failure_recovery, ops_exception_ack_result, ops_operational_exception,
                    fld_visit_command_result, fld_visit_fact, fld_visit, fld_geofence_policy,
                    apt_contact_attempt_command_result, apt_contact_attempt,
                    apt_appointment_command_result, apt_appointment_status_history,
                    apt_appointment_revision, apt_appointment, dsp_service_assignment,
                    evd_evidence_condition_disposition, evd_evidence_resolution_member,
                    evd_evidence_slot, evd_task_evidence_resolution,
                    rel_outbox_publish_attempt, rel_outbox_event,
                    wo_work_order, cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    auth_role_field_policy, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        jdbc.sql("""
                UPDATE rdm_projection_state
                   SET active_generation = 1, status = 'RUNNING',
                       last_rebuild_started_at = NULL, last_rebuild_completed_at = NULL,
                       updated_at = now()
                 WHERE projection_code = 'work-order-core-timeline.v1'
                """).update();
        projectId = project();
        bundle = bundle();
        workOrderId = receive("M85-ORDER-1");
        taskId = task(projectId, workOrderId, "SITE_SURVEY");
        seedReader("reader", projectId, "workOrder.read");
    }

    @Test
    void composesWorkspaceWithoutPiiAndDegradesMissingSecondaryCapabilities() {
        WorkOrderWorkspace workspace = workspaces.get(
                principal("reader"), "corr-workspace", workOrderId);

        assertThat(workspace.header().id()).isEqualTo(workOrderId);
        assertThat(workspace.header().externalOrderCode()).isEqualTo("M85-ORDER-1");
        assertThat(workspace.currentTaskSummary()).isNotNull();
        assertThat(workspace.currentTaskSummary().taskId()).isEqualTo(taskId);
        assertThat(workspace.currentTaskSummary().status()).isEqualTo("READY");
        assertThat(workspace.allowedActionLink())
                .isEqualTo("/api/v1/tasks/" + taskId + "/allowed-actions");
        assertThat(workspace.sectionAvailability().get("TASKS")).isEqualTo("AVAILABLE");
        assertThat(workspace.sectionAvailability().get("APPOINTMENTS_VISITS")).isEqualTo("UNAVAILABLE");
        assertThat(workspace.sectionAvailability().get("FORMS_EVIDENCE")).isEqualTo("UNAVAILABLE");
        assertThat(workspace.sectionAvailability().get("SLA")).isEqualTo("UNAVAILABLE");
        assertThat(workspace.sectionAvailability().get("EXCEPTIONS")).isEqualTo("UNAVAILABLE");
        assertThat(workspace.slaSummary()).isNull();
        assertThat(workspace.exceptionSummary()).isNull();
        assertThat(workspace.timelineFreshnessStatus()).isEqualTo("UNKNOWN");
        assertThat(workspace.meta().freshnessStatus()).isEqualTo("UNKNOWN");
        assertThat(workspace.meta().projectionCheckpoint()).startsWith("work-order-core-timeline.v1:gen:");
        assertThat(workspace.sourceVersions().workOrderVersion()).isEqualTo(1);

        String json = workspace.toString();
        assertThat(json).doesNotContain("customerName", "customerMobile", "serviceAddress", "vehicleVin");
    }

    @Test
    void deniesCrossTenantAndMissingGrant() {
        assertThatThrownBy(() -> workspaces.get(
                principal("missing"), "corr-deny", workOrderId))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThatThrownBy(() -> workspaces.get(
                new CurrentPrincipal(
                        "reader", "other-tenant", CurrentPrincipal.PrincipalType.USER, "m85", Set.of()),
                "corr-cross", workOrderId))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void loadsAcceptedSectionsAndRejectsUnacceptedSection() {
        var tasks = workspaces.getSection(
                principal("reader"), "corr-tasks", workOrderId, "TASKS", null, 50);
        assertThat(tasks.section()).isEqualTo("TASKS");
        assertThat(tasks.tasks()).isNotNull();
        assertThat(tasks.timeline()).isNull();
        assertThat(tasks.appointmentsVisits()).isNull();
        assertThat(tasks.formsEvidence()).isNull();
        assertThat(tasks.tasks().items()).extracting(item -> item.taskId()).containsExactly(taskId);
        assertThat(tasks.sourceVersions().workOrderVersion()).isEqualTo(1);
        assertThat(tasks.toString()).doesNotContain("customerName", "customerMobile");

        var timeline = workspaces.getSection(
                principal("reader"), "corr-timeline", workOrderId, "TIMELINE_AUDIT", null, 20);
        assertThat(timeline.section()).isEqualTo("TIMELINE_AUDIT");
        assertThat(timeline.timeline()).isNotNull();
        assertThat(timeline.tasks()).isNull();
        assertThat(timeline.appointmentsVisits()).isNull();
        assertThat(timeline.formsEvidence()).isNull();
        assertThat(timeline.timeline().freshnessStatus()).isEqualTo("UNKNOWN");

        assertThatThrownBy(() -> workspaces.getSection(
                principal("reader"), "corr-bad", workOrderId, "REVIEWS_CORRECTIONS", null, 20))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void appointmentsVisitsSectionLoadsVisitWithoutSensitiveFields() {
        seedReader("field-reader", projectId, "workOrder.read", "visit.read", "appointment.read");
        UUID appointmentId = seedAppointment(taskId);
        UUID visitId = seedVisit(appointmentId, taskId);

        var section = workspaces.getSection(
                principal("field-reader"), "corr-av", workOrderId, "APPOINTMENTS_VISITS", null, 50);
        assertThat(section.section()).isEqualTo("APPOINTMENTS_VISITS");
        assertThat(section.appointmentsVisits()).isNotNull();
        assertThat(section.tasks()).isNull();
        assertThat(section.timeline()).isNull();
        assertThat(section.formsEvidence()).isNull();
        assertThat(section.appointmentsVisits().visits()).extracting(item -> item.visitId())
                .containsExactly(visitId);
        assertThat(section.appointmentsVisits().appointments()).extracting(item -> item.appointmentId())
                .containsExactly(appointmentId);
        assertThat(section.toString()).doesNotContain(
                "checkInLocation", "customerName", "address-ref",
                "note-should-not-leak", "device-should-not-leak");

        WorkOrderWorkspace workspace = workspaces.get(principal("field-reader"), "corr-av-top", workOrderId);
        assertThat(workspace.sectionAvailability().get("APPOINTMENTS_VISITS")).isEqualTo("AVAILABLE");
    }

    @Test
    void appointmentsVisitsRejectsCursorPaging() {
        assertThatThrownBy(() -> workspaces.getSection(
                principal("reader"), "corr-av-cursor", workOrderId,
                "APPOINTMENTS_VISITS", "cursor-1", 20))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void formsEvidenceSectionLoadsWithoutDefinitionJson() {
        seedReader("forms-reader", projectId, "workOrder.read", "form.read", "evidence.read");
        UUID slotId = seedEvidenceSlot(taskId);

        var section = workspaces.getSection(
                principal("forms-reader"), "corr-fe", workOrderId, "FORMS_EVIDENCE", null, 50);
        assertThat(section.section()).isEqualTo("FORMS_EVIDENCE");
        assertThat(section.formsEvidence()).isNotNull();
        assertThat(section.tasks()).isNull();
        assertThat(section.appointmentsVisits()).isNull();
        assertThat(section.formsEvidence().forms()).extracting(item -> item.formKey())
                .containsExactly(FORM_KEY);
        assertThat(section.formsEvidence().forms()).extracting(item -> item.formVersionId())
                .containsExactly(formVersionId);
        assertThat(section.formsEvidence().evidenceSlots()).extracting(item -> item.slotId())
                .containsExactly(slotId);
        assertThat(section.toString()).doesNotContain(
                "definitionJson", "requirementDefinition", "resolutionExplanation",
                "survey.conclusion", "requireGps");

        WorkOrderWorkspace workspace = workspaces.get(principal("forms-reader"), "corr-fe-top", workOrderId);
        assertThat(workspace.sectionAvailability().get("FORMS_EVIDENCE")).isEqualTo("AVAILABLE");
    }

    @Test
    void formsEvidenceRejectsCursorPaging() {
        assertThatThrownBy(() -> workspaces.getSection(
                principal("reader"), "corr-fe-cursor", workOrderId,
                "FORMS_EVIDENCE", "cursor-1", 20))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    private UUID project() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id,tenant_id,project_code,client_id,project_name,starts_on,
                    project_status,aggregate_version,created_at
                ) VALUES (
                    :id,:tenantId,'M85','BYD','M85 项目',current_date,'ACTIVE',1,now()
                )
                """).param("id", id).param("tenantId", TENANT).update();
        return id;
    }

    private ConfigurationBundleReference bundle() {
        String workflowDefinition = "{\"workflowCode\":\"M85\"}";
        UUID workflowId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "M85-WF", "1.0.0", "1.0.0",
                workflowDefinition, Sha256.digest(workflowDefinition))).versionId();
        String formDefinition = """
                {"formKey":"survey.execution","version":"1.0.0","stage":"SURVEY","sections":[
                  {"sectionKey":"site","title":"现场信息","fields":[
                    {"fieldKey":"survey.conclusion","label":"勘测结论","dataType":"STRING",
                     "binding":"task.input.survey.conclusion","required":true}]}]}
                """.trim();
        formVersionId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.FORM, FORM_KEY, "1.0.0", "1.0.0",
                formDefinition, Sha256.digest(formDefinition))).versionId();
        String evidenceDefinition = """
                {"templateKey":"survey.site","version":"1.0.0","stage":"SURVEY",
                 "items":[{"evidenceKey":"site.photo","name":"现场照片","mediaType":"PHOTO",
                   "required":true,"capture":{"minCount":1,"maxCount":3,"requireGps":true}}]}
                """.trim();
        evidenceDigest = Sha256.digest(evidenceDefinition);
        evidenceVersionId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, EVIDENCE_KEY, "1.0.0", "1.0.0",
                evidenceDefinition, evidenceDigest)).versionId();
        return configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "M85-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowId, formVersionId, evidenceVersionId)));
    }

    private UUID receive(String externalOrderCode) {
        return workOrders.receive(new ReceiveExternalWorkOrderCommand(
                TENANT, projectId, "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                externalOrderCode, "e".repeat(64), bundle.bundleId(), bundle.bundleCode(),
                bundle.bundleVersion(), bundle.manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000",
                "山东省济南市历下区测试路1号", "LGXCE6CD0RA123456",
                LocalDateTime.of(2026, 7, 16, 8, 0), "corr-receive", "cause-receive"
        )).workOrderId();
    }

    private UUID task(UUID project, UUID workOrder, String taskType) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id,tenant_id,task_type,task_kind,business_key,payload_digest,
                    priority,status,next_run_at,attempt_count,max_attempts,correlation_id,
                    version,created_at,updated_at,project_id,work_order_id,
                    workflow_instance_id,stage_instance_id,workflow_node_instance_id,
                    workflow_node_id,workflow_definition_version_id,workflow_definition_digest,
                    form_ref,configuration_bundle_id,configuration_bundle_digest,stage_code
                ) VALUES (
                    :id,:tenantId,:taskType,'HUMAN',:businessKey,:digest,
                    500,'READY',:now,0,3,'corr-task',1,:now,:now,:projectId,:workOrderId,
                    :workflowId,:stageId,:nodeId,'SURVEY_NODE',:definitionId,:definitionDigest,
                    :formRef,:bundleId,:bundleDigest,'SURVEY'
                )
                """)
                .param("id", id)
                .param("tenantId", TENANT)
                .param("taskType", taskType)
                .param("businessKey", "m85:" + id)
                .param("digest", "a".repeat(64))
                .param("now", java.sql.Timestamp.from(now))
                .param("projectId", project)
                .param("workOrderId", workOrder)
                .param("workflowId", UUID.randomUUID())
                .param("stageId", UUID.randomUUID())
                .param("nodeId", UUID.randomUUID())
                .param("definitionId", UUID.randomUUID())
                .param("definitionDigest", "b".repeat(64))
                .param("formRef", FORM_KEY)
                .param("bundleId", bundle.bundleId())
                .param("bundleDigest", bundle.manifestDigest())
                .update();
        return id;
    }

    private void seedReader(String principalId, UUID project, String... capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (
                    role_id,tenant_id,role_code,role_name,role_status,created_at
                ) VALUES (:id,:tenantId,:roleCode,'Workspace Reader','ACTIVE',now())
                """)
                .param("id", roleId)
                .param("tenantId", TENANT)
                .param("roleCode", "WS_READER_" + principalId)
                .update();
        for (String capability : capabilities) {
            jdbc.sql("""
                    INSERT INTO auth_role_capability (role_id,capability_code,granted_at)
                    VALUES (:id,:capability,now())
                    """).param("id", roleId).param("capability", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id,tenant_id,principal_id,role_id,scope_type,scope_ref,
                    valid_from,source_code,approval_ref,created_at
                ) VALUES (
                    :grantId,:tenantId,:principalId,:roleId,'PROJECT',:projectId,
                    now()-interval '1 day','TEST','m88',now()
                )
                """)
                .param("grantId", UUID.randomUUID())
                .param("tenantId", TENANT)
                .param("principalId", principalId)
                .param("roleId", roleId)
                .param("projectId", project.toString())
                .update();
    }

    /**
     * 在同一事务内写入预约与修订：current_revision 外键为 DEFERRABLE，
     * 需先插预约再插修订，提交时再校验。
     */
    private UUID seedAppointment(UUID taskId) {
        UUID appointmentId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-16T01:00:00Z");
        Instant start = Instant.parse("2026-07-16T02:00:00Z");
        Instant end = Instant.parse("2026-07-16T05:00:00Z");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.sql("""
                    INSERT INTO apt_appointment (
                        appointment_id, tenant_id, project_id, work_order_id, task_id,
                        appointment_type, status, current_revision_id, current_revision_no,
                        assigned_network_id, technician_id, aggregate_version, created_by, created_at
                    ) VALUES (
                        :id, :tenantId, :projectId, :workOrderId, :taskId,
                        'SURVEY', 'CONFIRMED', :revisionId, 1,
                        'network-m88', 'tech-m88', 1, 'fixture', :createdAt
                    )
                    """)
                    .param("id", appointmentId)
                    .param("tenantId", TENANT)
                    .param("projectId", projectId)
                    .param("workOrderId", workOrderId)
                    .param("taskId", taskId)
                    .param("revisionId", revisionId)
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
                        :revisionId, :tenantId, :id, 1, NULL,
                        :start, :end, 'Asia/Shanghai', 120,
                        'address-ref', 'address-v1',
                        'CUSTOMER', 'customer-ref', 'PHONE', :confirmedAt,
                        NULL, 'note-should-not-leak', 'CONFIRM', 'fixture', :createdAt
                    )
                    """)
                    .param("revisionId", revisionId)
                    .param("tenantId", TENANT)
                    .param("id", appointmentId)
                    .param("start", java.sql.Timestamp.from(start))
                    .param("end", java.sql.Timestamp.from(end))
                    .param("confirmedAt", java.sql.Timestamp.from(createdAt))
                    .param("createdAt", java.sql.Timestamp.from(createdAt))
                    .update();
        });
        return appointmentId;
    }

    private UUID seedEvidenceSlot(UUID taskId) {
        UUID resolutionId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T04:00:00Z");
        String definitionJson = "{\"evidenceKey\":\"site.photo\",\"name\":\"现场照片\"}";
        String explanationJson = "{\"fixture\":true}";
        String conditionDigest = "d".repeat(64);
        String requirementDigest = Sha256.digest(definitionJson);
        jdbc.sql("""
                INSERT INTO evd_task_evidence_resolution (
                    resolution_id, tenant_id, project_id, task_id,
                    configuration_bundle_id, configuration_bundle_digest, stage_code,
                    source_event_id, source_event_digest, resolver_version, slot_count, resolved_at,
                    condition_input_digest, resolution_explanation,
                    generation_no, condition_fact_type, condition_fact_ref, condition_fact_revision
                ) VALUES (
                    :resolutionId, :tenantId, :projectId, :taskId,
                    :bundleId, :bundleDigest, 'SURVEY',
                    :eventId, :eventDigest, 'M89_FIXTURE', 1, :now,
                    :conditionDigest, CAST(:explanation AS jsonb),
                    1, 'TASK_CREATED', :factRef, 0
                )
                """)
                .param("resolutionId", resolutionId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .param("bundleId", bundle.bundleId())
                .param("bundleDigest", bundle.manifestDigest())
                .param("eventId", UUID.randomUUID())
                .param("eventDigest", "e".repeat(64))
                .param("now", java.sql.Timestamp.from(now))
                .param("conditionDigest", conditionDigest)
                .param("explanation", explanationJson)
                .param("factRef", UUID.randomUUID().toString())
                .update();
        jdbc.sql("""
                INSERT INTO evd_evidence_slot (
                    slot_id, tenant_id, project_id, task_id, resolution_id,
                    template_version_id, template_asset_type, template_key, template_version,
                    template_digest, requirement_code, occurrence_key, requirement_name,
                    media_type, required_flag, min_count, max_count, condition_input_digest,
                    resolution_explanation, requirement_definition, requirement_digest,
                    status_projection, resolved_at, slot_generation
                ) VALUES (
                    :slotId, :tenantId, :projectId, :taskId, :resolutionId,
                    :templateVersionId, 'EVIDENCE', :templateKey, '1.0.0',
                    :templateDigest, 'site.photo', '1', '现场照片',
                    'PHOTO', true, 1, 3, :conditionDigest,
                    CAST(:explanation AS jsonb), CAST(:definition AS jsonb), :requirementDigest,
                    'MISSING', :now, 1
                )
                """)
                .param("slotId", slotId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .param("resolutionId", resolutionId)
                .param("templateVersionId", evidenceVersionId)
                .param("templateKey", EVIDENCE_KEY)
                .param("templateDigest", evidenceDigest)
                .param("conditionDigest", conditionDigest)
                .param("explanation", explanationJson)
                .param("definition", definitionJson)
                .param("requirementDigest", requirementDigest)
                .param("now", java.sql.Timestamp.from(now))
                .update();
        jdbc.sql("""
                INSERT INTO evd_evidence_resolution_member (
                    member_id, tenant_id, project_id, task_id, resolution_id,
                    template_version_id, requirement_code, occurrence_key, condition_result,
                    active_slot_id, previous_slot_id, transition, required_disposition,
                    counting_item_count, condition_input_digest, resolution_explanation, created_at
                ) VALUES (
                    :memberId, :tenantId, :projectId, :taskId, :resolutionId,
                    :templateVersionId, 'site.photo', '1', true,
                    :slotId, NULL, 'ACTIVATED', 'NONE',
                    0, :conditionDigest, CAST(:explanation AS jsonb), :now
                )
                """)
                .param("memberId", memberId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .param("resolutionId", resolutionId)
                .param("templateVersionId", evidenceVersionId)
                .param("slotId", slotId)
                .param("conditionDigest", conditionDigest)
                .param("explanation", explanationJson)
                .param("now", java.sql.Timestamp.from(now))
                .update();
        return slotId;
    }

    private UUID seedVisit(UUID appointmentId, UUID taskId) {
        UUID visitId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T03:00:00Z");
        jdbc.sql("""
                INSERT INTO fld_visit (
                    visit_id, tenant_id, project_id, work_order_id, task_id, appointment_id,
                    visit_sequence, technician_id, network_id, status,
                    check_in_captured_at, check_in_received_at,
                    check_in_latitude, check_in_longitude, check_in_accuracy_meters,
                    geofence_result, geofence_distance_meters, geofence_policy_version,
                    policy_decision, device_id, device_command_id, offline_flag,
                    check_out_captured_at, check_out_received_at, result_code, exception_code, note,
                    operation_refs, evidence_refs, aggregate_version, created_by, created_at, updated_at
                ) VALUES (
                    :id, :tenantId, :projectId, :workOrderId, :taskId, :appointmentId,
                    1, 'tech-m88', 'network-m88', 'IN_PROGRESS',
                    :now, :now,
                    31.230400, 121.473700, 8.0,
                    'WITHIN_GEOFENCE', 5.0, 'geo-v1',
                    'ACCEPTED', 'device-should-not-leak', 'cmd-m88', false,
                    NULL, NULL, NULL, NULL, 'note-should-not-leak',
                    '[]'::jsonb, '[]'::jsonb, 1, 'fixture', :now, :now
                )
                """)
                .param("id", visitId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("workOrderId", workOrderId)
                .param("taskId", taskId)
                .param("appointmentId", appointmentId)
                .param("now", java.sql.Timestamp.from(now))
                .update();
        return visitId;
    }

    private static CurrentPrincipal principal(String principalId) {
        return new CurrentPrincipal(
                principalId, TENANT, CurrentPrincipal.PrincipalType.USER, "m88", Set.of());
    }
}
