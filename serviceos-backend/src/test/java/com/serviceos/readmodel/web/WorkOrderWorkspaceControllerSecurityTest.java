package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.FinalReviewWorkspaceQueryService;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewAllowedAction;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewGateCheck;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewWorkOrderSummary;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewWorkspaceData;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewWorkspaceMeta;
import com.serviceos.readmodel.api.WorkOrderWorkspace;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceMeta;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceServiceAssignmentSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceSourceVersions;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceTaskSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceQueryService;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceAppointmentsVisitsSectionData;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceCorrectionCaseSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceContactAttemptSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceFormSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceFormsEvidenceSectionData;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceInboundEnvelopeSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceIntegrationSectionData;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceReviewCaseSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceReviewsCorrectionsSectionData;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceTasksSectionData;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceVisitSummary;
import com.serviceos.workorder.api.WorkOrderView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkOrderWorkspaceController.class)
@Import(SecurityConfiguration.class)
class WorkOrderWorkspaceControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorkOrderWorkspaceQueryService workspaces;

    @MockitoBean
    private FinalReviewWorkspaceQueryService finalReviews;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    void authenticationAndSafeWorkspaceContractAreEnforced() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        mvc.perform(get("/api/v1/work-orders/{id}/workspace", workOrderId))
                .andExpect(status().isUnauthorized());

        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "m85", Set.of());
        Instant now = Instant.parse("2026-07-16T10:00:00Z");
        UUID taskId = UUID.randomUUID();
        WorkOrderView header = new WorkOrderView(
                workOrderId, "tenant", UUID.randomUUID(), "BYD", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "EXT-1", "ACTIVE",
                UUID.randomUUID(), "BUNDLE", "1.0.0", "a".repeat(64),
                "370000", "370100", "370102", now.minusSeconds(100), now.minusSeconds(90),
                now.minusSeconds(80), null, 3);
        WorkOrderWorkspace workspace = new WorkOrderWorkspace(
                header,
                new WorkOrderWorkspaceTaskSummary(
                        taskId, "SITE_SURVEY", "HUMAN", "READY", "SURVEY", null, 1),
                Map.of("TASKS", "AVAILABLE", "SLA", "UNAVAILABLE", "EXCEPTIONS", "UNAVAILABLE",
                        "SERVICE_ASSIGNMENT", "AVAILABLE"),
                "/api/v1/tasks/" + taskId + "/allowed-actions",
                new WorkOrderWorkspaceServiceAssignmentSummary(
                        taskId, "network-m92", now.minusSeconds(70), "INITIAL_ASSIGNMENT",
                        "technician-m92", now.minusSeconds(60), "TECHNICIAN_SELECTED"),
                null,
                null,
                "UNKNOWN",
                new WorkOrderWorkspaceSourceVersions(3),
                new WorkOrderWorkspaceMeta(now, "work-order-core-timeline.v1:gen:1", "UNKNOWN", "q-1"));
        when(principals.current()).thenReturn(principal);
        when(workspaces.get(principal, "corr-m85", workOrderId)).thenReturn(workspace);

        mvc.perform(get("/api/v1/work-orders/{id}/workspace", workOrderId)
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-m85"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(header().string("X-Correlation-Id", "corr-m85"))
                .andExpect(jsonPath("$.header.externalOrderCode").value("EXT-1"))
                .andExpect(jsonPath("$.currentTaskSummary.taskType").value("SITE_SURVEY"))
                .andExpect(jsonPath("$.allowedActionLink").value("/api/v1/tasks/" + taskId + "/allowed-actions"))
                .andExpect(jsonPath("$.serviceAssignmentSummary.networkId").value("network-m92"))
                .andExpect(jsonPath("$.serviceAssignmentSummary.technicianId").value("technician-m92"))
                .andExpect(jsonPath("$.serviceAssignmentSummary.activationSagaId").doesNotExist())
                .andExpect(jsonPath("$.serviceAssignmentSummary.sourceDecisionId").doesNotExist())
                .andExpect(jsonPath("$.header.customerName").doesNotExist())
                .andExpect(jsonPath("$.header.customerMobile").doesNotExist())
                .andExpect(jsonPath("$.header.serviceAddress").doesNotExist())
                .andExpect(jsonPath("$.header.vehicleVin").doesNotExist());
        verify(workspaces).get(principal, "corr-m85", workOrderId);
    }

    @Test
    void workspaceSectionContractIsEnforced() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "m87", Set.of());
        Instant now = Instant.parse("2026-07-16T11:00:00Z");
        UUID taskId = UUID.randomUUID();
        WorkOrderWorkspaceSection section = new WorkOrderWorkspaceSection(
                "TASKS",
                new WorkOrderWorkspaceSourceVersions(4),
                new WorkOrderWorkspaceMeta(now, "work-order-core-timeline.v1:gen:1", "FRESH", "q-2"),
                new WorkOrderWorkspaceTasksSectionData(
                        List.of(new WorkOrderWorkspaceTaskSummary(
                                taskId, "SITE_SURVEY", "HUMAN", "READY", "SURVEY", null, 1)),
                        null),
                null,
                null,
                null,
                null,
                null);
        when(principals.current()).thenReturn(principal);
        when(workspaces.getSection(eq(principal), eq("corr-sec"), eq(workOrderId),
                eq("TASKS"), isNull(), eq(50))).thenReturn(section);

        mvc.perform(get("/api/v1/work-orders/{id}/workspace/sections/{section}", workOrderId, "TASKS")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-sec"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""))
                .andExpect(jsonPath("$.section").value("TASKS"))
                .andExpect(jsonPath("$.tasks.items[0].taskType").value("SITE_SURVEY"))
                .andExpect(jsonPath("$.timeline").value(nullValue()))
                .andExpect(jsonPath("$.appointmentsVisits").value(nullValue()))
                .andExpect(jsonPath("$.formsEvidence").value(nullValue()));
        verify(workspaces).getSection(principal, "corr-sec", workOrderId, "TASKS", null, 50);
    }

    @Test
    void appointmentsVisitsSectionContractIsEnforced() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID contactAttemptId = UUID.randomUUID();
        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "m88", Set.of());
        Instant now = Instant.parse("2026-07-16T12:00:00Z");
        WorkOrderWorkspaceSection section = new WorkOrderWorkspaceSection(
                "APPOINTMENTS_VISITS",
                new WorkOrderWorkspaceSourceVersions(5),
                new WorkOrderWorkspaceMeta(now, "work-order-core-timeline.v1:gen:1", "FRESH", "q-3"),
                null,
                null,
                new WorkOrderWorkspaceAppointmentsVisitsSectionData(
                        List.of(new WorkOrderWorkspaceVisitSummary(
                                visitId, taskId, appointmentId, 1, "tech-m88", "network-m88",
                                "IN_PROGRESS", now, now, "WITHIN_GEOFENCE", "ACCEPTED",
                                null, null, null, null, 1)),
                        List.of(),
                        List.of(new WorkOrderWorkspaceContactAttemptSummary(
                                contactAttemptId, taskId, UUID.randomUUID(), workOrderId,
                                "PHONE", now, now.plusSeconds(30), "CONNECTED", null, now)),
                        null),
                null,
                null,
                null);
        when(principals.current()).thenReturn(principal);
        when(workspaces.getSection(eq(principal), eq("corr-av"), eq(workOrderId),
                eq("APPOINTMENTS_VISITS"), isNull(), eq(50))).thenReturn(section);

        mvc.perform(get("/api/v1/work-orders/{id}/workspace/sections/{section}",
                        workOrderId, "APPOINTMENTS_VISITS")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-av"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(jsonPath("$.section").value("APPOINTMENTS_VISITS"))
                .andExpect(jsonPath("$.appointmentsVisits.visits[0].visitId").value(visitId.toString()))
                .andExpect(jsonPath("$.appointmentsVisits.visits[0].checkInLatitude").doesNotExist())
                .andExpect(jsonPath("$.appointmentsVisits.visits[0].deviceId").doesNotExist())
                .andExpect(jsonPath("$.appointmentsVisits.contactAttempts[0].contactAttemptId")
                        .value(contactAttemptId.toString()))
                .andExpect(jsonPath("$.appointmentsVisits.contactAttempts[0].contactedPartyRef").doesNotExist())
                .andExpect(jsonPath("$.appointmentsVisits.contactAttempts[0].note").doesNotExist())
                .andExpect(jsonPath("$.appointmentsVisits.contactAttempts[0].recordingRef").doesNotExist())
                .andExpect(jsonPath("$.appointmentsVisits.contactAttempts[0].actorId").doesNotExist())
                .andExpect(jsonPath("$.tasks").value(nullValue()))
                .andExpect(jsonPath("$.timeline").value(nullValue()))
                .andExpect(jsonPath("$.formsEvidence").value(nullValue()));
        verify(workspaces).getSection(
                principal, "corr-av", workOrderId, "APPOINTMENTS_VISITS", null, 50);
    }

    @Test
    void formsEvidenceSectionContractIsEnforced() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID formVersionId = UUID.randomUUID();
        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "m89", Set.of());
        Instant now = Instant.parse("2026-07-16T13:00:00Z");
        WorkOrderWorkspaceSection section = new WorkOrderWorkspaceSection(
                "FORMS_EVIDENCE",
                new WorkOrderWorkspaceSourceVersions(6),
                new WorkOrderWorkspaceMeta(now, "work-order-core-timeline.v1:gen:1", "FRESH", "q-4"),
                null,
                null,
                null,
                new WorkOrderWorkspaceFormsEvidenceSectionData(
                        List.of(new WorkOrderWorkspaceFormSummary(
                                taskId, formVersionId, "survey.execution",
                                "1.0.0", "1.0.0", "c".repeat(64))),
                        List.of(),
                        List.of(),
                        List.of(),
                        null),
                null,
                null);
        when(principals.current()).thenReturn(principal);
        when(workspaces.getSection(eq(principal), eq("corr-fe"), eq(workOrderId),
                eq("FORMS_EVIDENCE"), isNull(), eq(50))).thenReturn(section);

        mvc.perform(get("/api/v1/work-orders/{id}/workspace/sections/{section}",
                        workOrderId, "FORMS_EVIDENCE")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-fe"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"6\""))
                .andExpect(jsonPath("$.section").value("FORMS_EVIDENCE"))
                .andExpect(jsonPath("$.formsEvidence.forms[0].formKey").value("survey.execution"))
                .andExpect(jsonPath("$.formsEvidence.forms[0].definitionJson").doesNotExist())
                .andExpect(jsonPath("$.formsEvidence.forms[0].definition").doesNotExist())
                .andExpect(jsonPath("$.formsEvidence.formSubmissions").isArray())
                .andExpect(jsonPath("$.formsEvidence.evidenceItems").isArray())
                .andExpect(jsonPath("$.tasks").value(nullValue()))
                .andExpect(jsonPath("$.appointmentsVisits").value(nullValue()));
        verify(workspaces).getSection(principal, "corr-fe", workOrderId, "FORMS_EVIDENCE", null, 50);
    }

    @Test
    void reviewsCorrectionsSectionContractIsEnforced() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID reviewCaseId = UUID.randomUUID();
        UUID sourceReviewCaseId = UUID.randomUUID();
        UUID reopenedReviewCaseId = UUID.randomUUID();
        UUID correctionCaseId = UUID.randomUUID();
        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "m90", Set.of());
        Instant now = Instant.parse("2026-07-16T14:00:00Z");
        WorkOrderWorkspaceSection section = new WorkOrderWorkspaceSection(
                "REVIEWS_CORRECTIONS",
                new WorkOrderWorkspaceSourceVersions(7),
                new WorkOrderWorkspaceMeta(now, "work-order-core-timeline.v1:gen:1", "FRESH", "q-5"),
                null,
                null,
                null,
                null,
                new WorkOrderWorkspaceReviewsCorrectionsSectionData(
                        List.of(new WorkOrderWorkspaceReviewCaseSummary(
                                reviewCaseId, taskId, projectId, UUID.randomUUID(),
                                "EVIDENCE_SET_SNAPSHOT", "CLIENT", "POLICY_V1",
                                "OPEN", now, null, sourceReviewCaseId,
                                "submission-m96", "batch-m96", "mapping-m96",
                                null, null, List.of()),
                                new WorkOrderWorkspaceReviewCaseSummary(
                                        reopenedReviewCaseId, taskId, projectId, UUID.randomUUID(),
                                        "EVIDENCE_SET_SNAPSHOT", "INTERNAL", "POLICY_V1",
                                        "OPEN", now.plusSeconds(1), null,
                                        null, null, null, null,
                                        reviewCaseId, "OEM_REJECTION:batch-m96", List.of())),
                        List.of(new WorkOrderWorkspaceCorrectionCaseSummary(
                                correctionCaseId, taskId, projectId, reviewCaseId, UUID.randomUUID(),
                                List.of("PHOTO_BLUR"), null, "OPEN", now, null,
                                null, null, List.of())),
                        null),
                null);
        when(principals.current()).thenReturn(principal);
        when(workspaces.getSection(eq(principal), eq("corr-rc"), eq(workOrderId),
                eq("REVIEWS_CORRECTIONS"), isNull(), eq(50))).thenReturn(section);

        mvc.perform(get("/api/v1/work-orders/{id}/workspace/sections/{section}",
                        workOrderId, "REVIEWS_CORRECTIONS")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-rc"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"7\""))
                .andExpect(jsonPath("$.section").value("REVIEWS_CORRECTIONS"))
                .andExpect(jsonPath("$.reviewsCorrections.reviews[0].reviewCaseId")
                        .value(reviewCaseId.toString()))
                .andExpect(jsonPath("$.reviewsCorrections.reviews[0].sourceReviewCaseId")
                        .value(sourceReviewCaseId.toString()))
                .andExpect(jsonPath("$.reviewsCorrections.reviews[0].externalSubmissionRef")
                        .value("submission-m96"))
                .andExpect(jsonPath("$.reviewsCorrections.reviews[0].callbackBatchRef")
                        .value("batch-m96"))
                .andExpect(jsonPath("$.reviewsCorrections.reviews[0].mappingVersionId")
                        .value("mapping-m96"))
                .andExpect(jsonPath("$.reviewsCorrections.reviews[1].reopenedFromReviewCaseId")
                        .value(reviewCaseId.toString()))
                .andExpect(jsonPath("$.reviewsCorrections.reviews[1].reopenTriggerRef")
                        .value("OEM_REJECTION:batch-m96"))
                .andExpect(jsonPath("$.reviewsCorrections.corrections[0].correctionCaseId")
                        .value(correctionCaseId.toString()))
                .andExpect(jsonPath("$.reviewsCorrections.reviews[0].note").doesNotExist())
                .andExpect(jsonPath("$.reviewsCorrections.corrections[0].waiveNote").doesNotExist())
                .andExpect(jsonPath("$.tasks").value(nullValue()))
                .andExpect(jsonPath("$.formsEvidence").value(nullValue()));
        verify(workspaces).getSection(
                principal, "corr-rc", workOrderId, "REVIEWS_CORRECTIONS", null, 50);
    }

    @Test
    void integrationSectionContractIsEnforced() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID envelopeId = UUID.randomUUID();
        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "m91", Set.of());
        Instant now = Instant.parse("2026-07-16T15:00:00Z");
        WorkOrderWorkspaceSection section = new WorkOrderWorkspaceSection(
                "INTEGRATION",
                new WorkOrderWorkspaceSourceVersions(8),
                new WorkOrderWorkspaceMeta(now, "work-order-core-timeline.v1:gen:1", "FRESH", "q-6"),
                null,
                null,
                null,
                null,
                null,
                new WorkOrderWorkspaceIntegrationSectionData(
                        List.of(new WorkOrderWorkspaceInboundEnvelopeSummary(
                                envelopeId, projectId, "byd-cpim-v7.3.1", "CREATE_WORK_ORDER",
                                "EXT-M91", "VALID", "COMPLETED", "mapping-v1", UUID.randomUUID(),
                                "ACCEPTED", "WORK_ORDER", workOrderId.toString(), now, now, "corr-in")),
                        List.of(),
                        null));
        when(principals.current()).thenReturn(principal);
        when(workspaces.getSection(eq(principal), eq("corr-int"), eq(workOrderId),
                eq("INTEGRATION"), isNull(), eq(50))).thenReturn(section);

        mvc.perform(get("/api/v1/work-orders/{id}/workspace/sections/{section}",
                        workOrderId, "INTEGRATION")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-int"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"8\""))
                .andExpect(jsonPath("$.section").value("INTEGRATION"))
                .andExpect(jsonPath("$.integration.inboundEnvelopes[0].inboundEnvelopeId")
                        .value(envelopeId.toString()))
                .andExpect(jsonPath("$.integration.inboundEnvelopes[0].rawPayloadDigest").doesNotExist())
                .andExpect(jsonPath("$.integration.outboundDeliveries").isArray())
                .andExpect(jsonPath("$.tasks").value(nullValue()))
                .andExpect(jsonPath("$.reviewsCorrections").value(nullValue()));
        verify(workspaces).getSection(
                principal, "corr-int", workOrderId, "INTEGRATION", null, 50);
    }

    @Test
    void finalReviewWorkspaceSectionContractIsEnforced() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        CurrentPrincipal principal = new CurrentPrincipal(
                "reviewer", "tenant", CurrentPrincipal.PrincipalType.USER, "m351", Set.of());
        Instant now = Instant.parse("2026-07-19T10:00:00Z");
        FinalReviewWorkspaceSectionResponse response = new FinalReviewWorkspaceSectionResponse(
                new FinalReviewWorkspaceData(
                        new FinalReviewWorkOrderSummary(
                                workOrderId, "EXT-351", projectId, "试点项目", "ACTIVE", "履约中",
                                "HOME_CHARGING_SURVEY_INSTALL", "HOME_CHARGING_SURVEY_INSTALL",
                                "张**", "*******5678", "山东省济南市***",
                                "济南网点", "张师傅", null, "提交平台终审"),
                        null,
                        null,
                        null,
                        List.of(new FinalReviewGateCheck(
                                "REVIEW_CASE_OPEN", "审核案例待审", "FAIL", true, "当前无 OPEN 的平台审核案例")),
                        List.of(),
                        List.of(),
                        List.of(new FinalReviewAllowedAction("DECIDE", false, "当前状态不允许提交终审")),
                        null,
                        null),
                new FinalReviewWorkspaceMeta(now, "final-review.v1:live", "FRESH", 3L, "frq-1"));
        when(principals.current()).thenReturn(principal);
        when(finalReviews.get(principal, "corr-fr", workOrderId)).thenReturn(response);

        mvc.perform(get("/api/v1/work-orders/{id}/workspace/sections/FINAL_REVIEW", workOrderId)
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-fr"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.data.workOrder.displayNo").value("EXT-351"))
                .andExpect(jsonPath("$.data.workOrder.maskedCustomerPhone").value("*******5678"))
                .andExpect(jsonPath("$.data.workOrder.maskedCustomerPhone").value(org.hamcrest.Matchers.not("13812345678")))
                .andExpect(jsonPath("$.meta.freshnessStatus").value("FRESH"))
                .andExpect(jsonPath("$.data.gateChecks[0].code").value("REVIEW_CASE_OPEN"))
                .andExpect(jsonPath("$.data.objectKey").doesNotExist())
                .andExpect(jsonPath("$.data.workOrder.customerMobile").doesNotExist());
        verify(finalReviews).get(principal, "corr-fr", workOrderId);
    }
}
