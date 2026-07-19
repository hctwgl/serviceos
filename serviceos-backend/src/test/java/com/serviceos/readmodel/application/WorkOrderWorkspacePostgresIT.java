package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.FinalReviewWorkspaceQueryService;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse;
import com.serviceos.readmodel.api.WorkOrderWorkspace;
import com.serviceos.readmodel.api.WorkOrderWorkspaceQueryService;
import com.serviceos.workorder.api.WorkOrderMaskedContactView;
import com.serviceos.workorder.api.WorkOrderQueryService;
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

/** M85～M96：工作区组合查询的授权、无 PII、按需区块与缺权降级证据。 */
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
    private FinalReviewWorkspaceQueryService finalReviews;

    @Autowired
    private WorkOrderQueryService workOrderQueries;

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
                    int_delivery_replay_request, int_external_acknowledgement, int_delivery_attempt,
                    int_outbound_delivery, int_inbound_item_result, int_external_review_route,
                    int_canonical_message, int_inbound_envelope,
                    evd_correction_resubmission, evd_correction_case,
                    evd_review_decision, evd_review_case, evd_evidence_set_member,
                    evd_evidence_set_snapshot,
                    evd_evidence_condition_disposition, evd_evidence_resolution_member,
                    evd_evidence_revision, evd_evidence_item, evd_evidence_slot,
                    evd_task_evidence_resolution,
                    frm_form_command_result, frm_submission_validation, frm_form_submission,
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
        assertThat(workspace.sectionAvailability().get("REVIEWS_CORRECTIONS")).isEqualTo("UNAVAILABLE");
        assertThat(workspace.sectionAvailability().get("FINAL_REVIEW")).isIn("EMPTY", "UNAVAILABLE");
        assertThat(workspace.sectionAvailability().get("INTEGRATION")).isEqualTo("UNAVAILABLE");
        assertThat(workspace.sectionAvailability().get("SERVICE_ASSIGNMENT")).isEqualTo("UNAVAILABLE");
        assertThat(workspace.sectionAvailability().get("SLA")).isEqualTo("UNAVAILABLE");
        assertThat(workspace.sectionAvailability().get("EXCEPTIONS")).isEqualTo("UNAVAILABLE");
        assertThat(workspace.slaSummary()).isNull();
        assertThat(workspace.exceptionSummary()).isNull();
        assertThat(workspace.serviceAssignmentSummary()).isNull();
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
    void finalReviewSectionReturnsMaskedContactAndOmitsRawPii() {
        WorkOrderMaskedContactView contact = workOrderQueries.getMaskedContact(
                principal("reader"), "corr-mask", workOrderId);
        assertThat(contact.maskedCustomerName()).isNotBlank();
        assertThat(contact.maskedCustomerPhone()).doesNotContain("138");
        assertThat(contact.maskedServiceAddress()).endsWith("***");

        FinalReviewWorkspaceSectionResponse section = finalReviews.get(
                principal("reader"), "corr-final-review", workOrderId);
        assertThat(section.data().workOrder().workOrderId()).isEqualTo(workOrderId);
        assertThat(section.data().workOrder().maskedCustomerPhone())
                .isEqualTo(contact.maskedCustomerPhone());
        assertThat(section.data().gateChecks()).extracting(gate -> gate.code())
                .contains(
                        "REVIEW_CASE_OPEN", "TASK_ACTIONABLE", "SNAPSHOT_COMPLETE",
                        "REQUIRED_EVIDENCE_READY", "NO_QUARANTINED_EVIDENCE",
                        "ALL_TARGETS_DECIDED", "REJECTED_TARGET_COMPLETE",
                        "NO_OPEN_CORRECTION", "AUTHORIZATION_VALID");
        assertThat(section.data().rejectionReasons()).isNotEmpty();
        assertThat(section.meta().freshnessStatus()).isEqualTo("FRESH");
        String json = section.toString();
        assertThat(json).doesNotContain("objectKey", "customerMobile", "vehicleVin");
        assertThat(json).doesNotContain("13800000000");
        assertThat(json).doesNotContain("山东省济南市历下区测试路1号");
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
        assertThat(tasks.reviewsCorrections()).isNull();
        assertThat(tasks.integration()).isNull();
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
        assertThat(timeline.reviewsCorrections()).isNull();
        assertThat(timeline.integration()).isNull();
        assertThat(timeline.timeline().freshnessStatus()).isEqualTo("UNKNOWN");

        assertThatThrownBy(() -> workspaces.getSection(
                principal("reader"), "corr-bad", workOrderId, "FACTS_CALCULATIONS", null, 20))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void appointmentsVisitsSectionLoadsVisitWithoutSensitiveFields() {
        seedReader("field-reader", projectId, "workOrder.read", "visit.read", "appointment.read");
        UUID appointmentId = seedAppointment(taskId);
        UUID visitId = seedVisit(appointmentId, taskId);
        UUID contactAttemptId = seedContactAttempt(taskId);

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
        assertThat(section.appointmentsVisits().contactAttempts())
                .extracting(item -> item.contactAttemptId())
                .containsExactly(contactAttemptId);
        assertThat(section.toString()).doesNotContain(
                "checkInLocation", "customerName", "address-ref",
                "note-should-not-leak", "device-should-not-leak",
                "contacted-party-should-not-leak", "recording-should-not-leak",
                "contact-actor-should-not-leak");

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
        UUID submissionId = seedFormSubmission(taskId);
        UUID evidenceItemId = seedEvidenceItem(taskId, slotId);

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
        assertThat(section.formsEvidence().formSubmissions())
                .extracting(item -> item.submissionId())
                .containsExactly(submissionId);
        assertThat(section.formsEvidence().formSubmissions().getFirst().errorCount()).isOne();
        assertThat(section.formsEvidence().evidenceItems())
                .extracting(item -> item.evidenceItemId())
                .containsExactly(evidenceItemId);
        assertThat(section.formsEvidence().evidenceItems().getFirst().revisionCount()).isZero();
        assertThat(section.toString()).doesNotContain(
                "definitionJson", "requirementDefinition", "resolutionExplanation",
                "survey.conclusion", "requireGps", "valuesJson", "submittedBy",
                "validation-message-should-not-leak", "revisions", "fileObjectId",
                "captureMetadata", "createdBy");

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

    @Test
    void reviewsCorrectionsSectionLoadsWithoutFreeText() {
        seedReader("review-reader", projectId, "workOrder.read", "evidence.read");
        ReviewCorrectionFixture fixture = seedReviewCorrection(taskId);

        var section = workspaces.getSection(
                principal("review-reader"), "corr-rc", workOrderId,
                "REVIEWS_CORRECTIONS", null, 50);

        assertThat(section.section()).isEqualTo("REVIEWS_CORRECTIONS");
        assertThat(section.reviewsCorrections()).isNotNull();
        assertThat(section.tasks()).isNull();
        assertThat(section.timeline()).isNull();
        assertThat(section.appointmentsVisits()).isNull();
        assertThat(section.formsEvidence()).isNull();
        assertThat(section.reviewsCorrections().reviews())
                .extracting(item -> item.reviewCaseId())
                .containsExactly(fixture.reviewCaseId());
        assertThat(section.reviewsCorrections().reviews().getFirst().decisions())
                .extracting(item -> item.reviewDecisionId())
                .containsExactly(fixture.reviewDecisionId());
        assertThat(section.reviewsCorrections().reviews().getFirst().sourceReviewCaseId()).isNull();
        assertThat(section.reviewsCorrections().reviews().getFirst().externalSubmissionRef()).isNull();
        assertThat(section.reviewsCorrections().reviews().getFirst().reopenedFromReviewCaseId()).isNull();
        assertThat(section.reviewsCorrections().corrections())
                .extracting(item -> item.correctionCaseId())
                .containsExactly(fixture.correctionCaseId());
        assertThat(section.toString()).doesNotContain(
                "review-note-should-not-leak", "approval-ref-should-not-leak",
                "waiveNote", "decidedBy");

        WorkOrderWorkspace workspace = workspaces.get(
                principal("review-reader"), "corr-rc-top", workOrderId);
        assertThat(workspace.sectionAvailability().get("REVIEWS_CORRECTIONS"))
                .isEqualTo("AVAILABLE");
    }

    @Test
    void reviewsCorrectionsRejectsCursorPaging() {
        assertThatThrownBy(() -> workspaces.getSection(
                principal("reader"), "corr-rc-cursor", workOrderId,
                "REVIEWS_CORRECTIONS", "cursor-1", 20))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void integrationSectionLoadsInboundAndOutboundWithoutSensitiveFields() {
        seedReader("integration-reader", projectId, "workOrder.read",
                "integration.readInbound", "integration.readOutbound");
        IntegrationFixture fixture = seedIntegration(workOrderId);

        var section = workspaces.getSection(
                principal("integration-reader"), "corr-int", workOrderId,
                "INTEGRATION", null, 50);

        assertThat(section.section()).isEqualTo("INTEGRATION");
        assertThat(section.integration()).isNotNull();
        assertThat(section.tasks()).isNull();
        assertThat(section.timeline()).isNull();
        assertThat(section.appointmentsVisits()).isNull();
        assertThat(section.formsEvidence()).isNull();
        assertThat(section.reviewsCorrections()).isNull();
        assertThat(section.integration().inboundEnvelopes())
                .extracting(item -> item.inboundEnvelopeId())
                .containsExactly(fixture.envelopeId());
        assertThat(section.integration().outboundDeliveries())
                .extracting(item -> item.deliveryId())
                .containsExactly(fixture.deliveryId());
        assertThat(section.toString()).doesNotContain(
                "private/raw/m91.json", "private/outbound/m91.json",
                "operator-m91-sensitive", "external-key-m91",
                "rawPayloadDigest", "payloadDigest", "operatorPrincipalId");

        WorkOrderWorkspace workspace = workspaces.get(
                principal("integration-reader"), "corr-int-top", workOrderId);
        assertThat(workspace.sectionAvailability().get("INTEGRATION")).isEqualTo("AVAILABLE");
    }

    @Test
    void integrationRejectsCursorPaging() {
        assertThatThrownBy(() -> workspaces.getSection(
                principal("reader"), "corr-int-cursor", workOrderId,
                "INTEGRATION", "cursor-1", 20))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void serviceAssignmentSummaryLoadsActiveResponsibilitiesWithoutInternalIds() {
        seedReader("dispatch-reader", projectId, "workOrder.read", "dispatch.read");
        seedActiveServiceResponsibilities(taskId);

        WorkOrderWorkspace workspace = workspaces.get(
                principal("dispatch-reader"), "corr-assignment", workOrderId);

        assertThat(workspace.sectionAvailability().get("SERVICE_ASSIGNMENT")).isEqualTo("AVAILABLE");
        assertThat(workspace.serviceAssignmentSummary()).isNotNull();
        assertThat(workspace.serviceAssignmentSummary().taskId()).isEqualTo(taskId);
        assertThat(workspace.serviceAssignmentSummary().networkId()).isEqualTo("network-m92");
        assertThat(workspace.serviceAssignmentSummary().technicianId()).isEqualTo("technician-m92");
        assertThat(workspace.serviceAssignmentSummary().networkReassignmentReasonCode())
                .isEqualTo("INITIAL_ASSIGNMENT");
        assertThat(workspace.serviceAssignmentSummary().technicianReassignmentReasonCode())
                .isEqualTo("TECHNICIAN_SELECTED");
        assertThat(jdbc.sql("""
                SELECT risk_level FROM auth_capability WHERE capability_code='dispatch.read'
                """).query(String.class).single()).isEqualTo("NORMAL");
        assertThat(workspace.toString()).doesNotContain(
                "activationSagaId", "sourceDecisionId", "authorityAssignmentId",
                "taskExecutionGuardId", "createdBy");
    }

    @Test
    void serviceAssignmentSummaryIsEmptyWhenAuthorizedButNoActiveResponsibility() {
        seedReader("dispatch-empty-reader", projectId, "workOrder.read", "dispatch.read");

        WorkOrderWorkspace workspace = workspaces.get(
                principal("dispatch-empty-reader"), "corr-assignment-empty", workOrderId);

        assertThat(workspace.sectionAvailability().get("SERVICE_ASSIGNMENT")).isEqualTo("EMPTY");
        assertThat(workspace.serviceAssignmentSummary()).isNull();
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

    private UUID seedFormSubmission(UUID taskId) {
        UUID submissionId = UUID.randomUUID();
        Instant submittedAt = Instant.parse("2026-07-16T04:30:00Z");
        jdbc.sql("""
                INSERT INTO frm_form_submission (
                    form_submission_id, tenant_id, task_id, project_id, form_version_id,
                    form_key, submission_version, values_document, content_digest,
                    validation_status, prefill_version, submitted_by, submitted_at
                ) VALUES (
                    :submissionId, :tenantId, :taskId, :projectId, :formVersionId,
                    :formKey, 1, '{"survey.conclusion":"should-not-leak"}'::jsonb, :digest,
                    'INVALID', 'prefill-should-not-leak', 'submitter-should-not-leak', :submittedAt
                )
                """)
                .param("submissionId", submissionId)
                .param("tenantId", TENANT)
                .param("taskId", taskId)
                .param("projectId", projectId)
                .param("formVersionId", formVersionId)
                .param("formKey", FORM_KEY)
                .param("digest", Sha256.digest("m95-form-values"))
                .param("submittedAt", java.sql.Timestamp.from(submittedAt))
                .update();
        jdbc.sql("""
                INSERT INTO frm_submission_validation (
                    submission_validation_id, tenant_id, form_submission_id,
                    validator_version, input_digest, validation_status,
                    errors_document, warnings_document, executed_at
                ) VALUES (
                    :validationId, :tenantId, :submissionId,
                    'M95_VALIDATOR', :digest, 'INVALID',
                    '[{"message":"validation-message-should-not-leak"}]'::jsonb,
                    '[]'::jsonb, :submittedAt
                )
                """)
                .param("validationId", UUID.randomUUID())
                .param("tenantId", TENANT)
                .param("submissionId", submissionId)
                .param("digest", Sha256.digest("m95-validation"))
                .param("submittedAt", java.sql.Timestamp.from(submittedAt))
                .update();
        return submissionId;
    }

    private UUID seedEvidenceItem(UUID taskId, UUID slotId) {
        UUID itemId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO evd_evidence_item (
                    evidence_item_id, tenant_id, project_id, task_id, slot_id,
                    item_ordinal, status, created_by, created_at
                ) VALUES (
                    :itemId, :tenantId, :projectId, :taskId, :slotId,
                    1, 'OPEN', 'evidence-creator-should-not-leak', :createdAt
                )
                """)
                .param("itemId", itemId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .param("slotId", slotId)
                .param("createdAt", java.sql.Timestamp.from(
                        Instant.parse("2026-07-16T04:31:00Z")))
                .update();
        return itemId;
    }

    private UUID seedContactAttempt(UUID taskId) {
        UUID id = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-07-16T02:30:00Z");
        jdbc.sql("""
                INSERT INTO apt_contact_attempt (
                    contact_attempt_id, tenant_id, project_id, work_order_id, task_id,
                    channel, contacted_party_ref, started_at, ended_at, result_code,
                    note, next_contact_at, recording_ref, actor_id, created_at
                ) VALUES (
                    :id, :tenantId, :projectId, :workOrderId, :taskId,
                    'PHONE', 'contacted-party-should-not-leak', :startedAt, :endedAt, 'CONNECTED',
                    'note-should-not-leak', NULL, 'recording-should-not-leak',
                    'contact-actor-should-not-leak', :createdAt
                )
                """)
                .param("id", id)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("workOrderId", workOrderId)
                .param("taskId", taskId)
                .param("startedAt", java.sql.Timestamp.from(startedAt))
                .param("endedAt", java.sql.Timestamp.from(startedAt.plusSeconds(30)))
                .param("createdAt", java.sql.Timestamp.from(startedAt.plusSeconds(31)))
                .update();
        return id;
    }

    private ReviewCorrectionFixture seedReviewCorrection(UUID taskId) {
        UUID resolutionId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID reviewCaseId = UUID.randomUUID();
        UUID reviewDecisionId = UUID.randomUUID();
        UUID correctionCaseId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-16T05:00:00Z");
        Instant decidedAt = createdAt.plusSeconds(60);
        String digest = Sha256.digest(snapshotId.toString());
        jdbc.sql("""
                INSERT INTO evd_task_evidence_resolution (
                    resolution_id, tenant_id, project_id, task_id,
                    configuration_bundle_id, configuration_bundle_digest, stage_code,
                    source_event_id, source_event_digest, resolver_version,
                    condition_input_digest, resolution_explanation,
                    generation_no, condition_fact_type, condition_fact_ref, condition_fact_revision,
                    slot_count, resolved_at
                ) VALUES (
                    :resolutionId, :tenantId, :projectId, :taskId,
                    :bundleId, :bundleDigest, 'SURVEY',
                    :eventId, :eventDigest, 'M90_FIXTURE',
                    :conditionDigest, '{"fixture":true}'::jsonb,
                    1, 'TASK_CREATED', :factRef, 0, 0, :createdAt
                )
                """)
                .param("resolutionId", resolutionId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .param("bundleId", bundle.bundleId())
                .param("bundleDigest", bundle.manifestDigest())
                .param("eventId", UUID.randomUUID())
                .param("eventDigest", "f".repeat(64))
                .param("conditionDigest", "d".repeat(64))
                .param("factRef", UUID.randomUUID().toString())
                .param("createdAt", java.sql.Timestamp.from(createdAt))
                .update();
        jdbc.sql("""
                INSERT INTO evd_evidence_set_snapshot (
                    evidence_set_snapshot_id, tenant_id, project_id, task_id, resolution_id,
                    purpose, member_count, content_digest, eligibility_summary, created_by, created_at
                ) VALUES (
                    :snapshotId, :tenantId, :projectId, :taskId, :resolutionId,
                    'TASK_SUBMISSION', 0, :digest, '{}'::jsonb, 'fixture', :createdAt
                )
                """)
                .param("snapshotId", snapshotId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .param("resolutionId", resolutionId)
                .param("digest", digest)
                .param("createdAt", java.sql.Timestamp.from(createdAt))
                .update();
        jdbc.sql("""
                INSERT INTO evd_review_case (
                    review_case_id, tenant_id, project_id, task_id, evidence_set_snapshot_id,
                    snapshot_content_digest, scope_type, origin, policy_version, status,
                    created_by, created_at, decided_at
                ) VALUES (
                    :reviewCaseId, :tenantId, :projectId, :taskId, :snapshotId,
                    :digest, 'EVIDENCE_SET_SNAPSHOT', 'INTERNAL', 'POLICY_V1', 'REJECTED',
                    'reviewer-should-not-leak', :createdAt, :decidedAt
                )
                """)
                .param("reviewCaseId", reviewCaseId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .param("snapshotId", snapshotId)
                .param("digest", digest)
                .param("createdAt", java.sql.Timestamp.from(createdAt))
                .param("decidedAt", java.sql.Timestamp.from(decidedAt))
                .update();
        jdbc.sql("""
                INSERT INTO evd_review_decision (
                    review_decision_id, tenant_id, project_id, review_case_id,
                    decision_ordinal, decision, decision_source, reason_codes,
                    note, approval_ref, decided_by, decided_at
                ) VALUES (
                    :reviewDecisionId, :tenantId, :projectId, :reviewCaseId,
                    1, 'REJECTED', 'INTERNAL', '["PHOTO_BLUR"]'::jsonb,
                    'review-note-should-not-leak', NULL, 'reviewer-should-not-leak', :decidedAt
                )
                """)
                .param("reviewDecisionId", reviewDecisionId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("reviewCaseId", reviewCaseId)
                .param("decidedAt", java.sql.Timestamp.from(decidedAt))
                .update();
        jdbc.sql("""
                INSERT INTO evd_correction_case (
                    correction_case_id, tenant_id, project_id, task_id,
                    source_review_case_id, source_review_decision_id,
                    source_evidence_set_snapshot_id, source_snapshot_content_digest,
                    reason_codes, status, created_by, created_at
                ) VALUES (
                    :correctionCaseId, :tenantId, :projectId, :taskId,
                    :reviewCaseId, :reviewDecisionId, :snapshotId, :digest,
                    '["PHOTO_BLUR"]'::jsonb, 'OPEN', 'fixture', :decidedAt
                )
                """)
                .param("correctionCaseId", correctionCaseId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .param("reviewCaseId", reviewCaseId)
                .param("reviewDecisionId", reviewDecisionId)
                .param("snapshotId", snapshotId)
                .param("digest", digest)
                .param("decidedAt", java.sql.Timestamp.from(decidedAt))
                .update();
        return new ReviewCorrectionFixture(reviewCaseId, reviewDecisionId, correctionCaseId);
    }

    private IntegrationFixture seedIntegration(UUID workOrderId) {
        UUID envelopeId = UUID.randomUUID();
        UUID canonicalMessageId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        Instant receivedAt = Instant.parse("2026-07-16T06:00:00Z");
        Instant completedAt = receivedAt.plusSeconds(1);
        jdbc.sql("""
                INSERT INTO int_inbound_envelope (
                    inbound_envelope_id, tenant_id, project_id, connector_version_id,
                    message_type, transport_dedup_key, external_message_id, received_at,
                    raw_payload_object_ref, raw_payload_digest, signature_status,
                    processing_status, correlation_id
                ) VALUES (
                    :envelopeId, :tenantId, :projectId, 'byd-cpim-v7.3.1',
                    'CREATE_WORK_ORDER', :dedupKey, 'M91-EXTERNAL-MESSAGE', :receivedAt,
                    'private/raw/m91.json', :rawDigest, 'VALID', 'RECEIVED', 'corr-m91-inbound'
                )
                """)
                .param("envelopeId", envelopeId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("dedupKey", Sha256.digest("m91-inbound-dedup"))
                .param("receivedAt", java.sql.Timestamp.from(receivedAt))
                .param("rawDigest", Sha256.digest("m91-raw"))
                .update();
        jdbc.sql("""
                INSERT INTO int_canonical_message (
                    canonical_message_id, tenant_id, project_id, connector_version_id,
                    message_type, business_key, payload_object_ref, payload_digest,
                    mapping_version_id, processing_status, result_code, result_type, result_id,
                    source_envelope_id, created_at, processed_at
                ) VALUES (
                    :canonicalId, :tenantId, :projectId, 'byd-cpim-v7.3.1',
                    'CREATE_WORK_ORDER', 'M91-BUSINESS-KEY', 'private/canonical/m91.json',
                    :payloadDigest, 'm91-mapping-v1', 'COMPLETED', 'ACCEPTED', 'WORK_ORDER',
                    :workOrderId, :envelopeId, :receivedAt, :completedAt
                )
                """)
                .param("canonicalId", canonicalMessageId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("payloadDigest", Sha256.digest("m91-canonical"))
                .param("workOrderId", workOrderId.toString())
                .param("envelopeId", envelopeId)
                .param("receivedAt", java.sql.Timestamp.from(receivedAt))
                .param("completedAt", java.sql.Timestamp.from(completedAt))
                .update();
        jdbc.sql("""
                UPDATE int_inbound_envelope
                   SET canonical_payload_digest=:canonicalDigest,
                       mapping_version_id='m91-mapping-v1',
                       canonical_message_id=:canonicalId,
                       processing_status='COMPLETED',
                       result_code='ACCEPTED',
                       result_type='WORK_ORDER',
                       result_id=:workOrderId,
                       completed_at=:completedAt
                 WHERE inbound_envelope_id=:envelopeId
                """)
                .param("canonicalDigest", Sha256.digest("m91-canonical"))
                .param("canonicalId", canonicalMessageId)
                .param("workOrderId", workOrderId.toString())
                .param("completedAt", java.sql.Timestamp.from(completedAt))
                .param("envelopeId", envelopeId)
                .update();
        jdbc.sql("""
                INSERT INTO int_outbound_delivery (
                    delivery_id, tenant_id, project_id, connector_version_id, mapping_version_id,
                    business_message_type, business_key, source_review_case_id, source_task_id,
                    source_work_order_id, source_snapshot_id, source_snapshot_digest,
                    external_order_code, operator_principal_id, operator_display_value,
                    payload_object_ref, payload_digest, external_idempotency_key,
                    failure_policy_version_id, status, created_by, created_at
                ) VALUES (
                    :deliveryId, :tenantId, :projectId, 'byd-cpim-v7.3.1', 'm91-outbound-v1',
                    'SUBMIT_CLIENT_REVIEW', 'M91-DELIVERY', :reviewCaseId, :taskId,
                    :workOrderId, :snapshotId, :snapshotDigest,
                    'M91-ORDER', 'operator-m91-sensitive', '敏感操作者',
                    'private/outbound/m91.json', :payloadDigest, :externalKey,
                    'm91-fail-closed-v1', 'PENDING', 'fixture', :receivedAt
                )
                """)
                .param("deliveryId", deliveryId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("reviewCaseId", UUID.randomUUID())
                .param("taskId", taskId)
                .param("workOrderId", workOrderId)
                .param("snapshotId", UUID.randomUUID())
                .param("snapshotDigest", Sha256.digest("m91-snapshot"))
                .param("payloadDigest", Sha256.digest("m91-outbound-payload"))
                .param("externalKey", Sha256.digest("external-key-m91"))
                .param("receivedAt", java.sql.Timestamp.from(receivedAt))
                .update();
        return new IntegrationFixture(envelopeId, deliveryId);
    }

    private void seedActiveServiceResponsibilities(UUID taskId) {
        Instant networkEffectiveFrom = Instant.parse("2026-07-16T06:30:00Z");
        Instant technicianEffectiveFrom = networkEffectiveFrom.plusSeconds(300);
        insertActiveServiceResponsibility(
                taskId, "NETWORK", "network-m92", "INITIAL_ASSIGNMENT", networkEffectiveFrom);
        insertActiveServiceResponsibility(
                taskId, "TECHNICIAN", "technician-m92", "TECHNICIAN_SELECTED",
                technicianEffectiveFrom);
    }

    private void insertActiveServiceResponsibility(
            UUID taskId,
            String level,
            String assigneeId,
            String reasonCode,
            Instant effectiveFrom
    ) {
        jdbc.sql("""
                INSERT INTO dsp_service_assignment (
                    service_assignment_id, tenant_id, work_order_id, task_id,
                    responsibility_level, assignee_id, business_type, source_decision_id,
                    status, activation_saga_id, reassignment_reason_code,
                    effective_from, authority_assignment_id, authority_version,
                    fence_decision_id, fence_policy_version, created_by, created_at
                ) VALUES (
                    :id, :tenantId, :workOrderId, :taskId,
                    :level, :assigneeId, 'SITE_SURVEY', :sourceDecisionId,
                    'ACTIVE', :activationSagaId, :reasonCode,
                    :effectiveFrom, :authorityAssignmentId, 1,
                    :fenceDecisionId, 'M92_FENCE_V1', 'fixture', :effectiveFrom
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenantId", TENANT)
                .param("workOrderId", workOrderId)
                .param("taskId", taskId)
                .param("level", level)
                .param("assigneeId", assigneeId)
                .param("sourceDecisionId", "decision-" + level.toLowerCase())
                .param("activationSagaId", UUID.randomUUID())
                .param("reasonCode", reasonCode)
                .param("effectiveFrom", java.sql.Timestamp.from(effectiveFrom))
                .param("authorityAssignmentId", "authority-" + level.toLowerCase())
                .param("fenceDecisionId", "fence-" + level.toLowerCase())
                .update();
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

    private record ReviewCorrectionFixture(
            UUID reviewCaseId,
            UUID reviewDecisionId,
            UUID correctionCaseId
    ) {
    }

    private record IntegrationFixture(UUID envelopeId, UUID deliveryId) {
    }
}
