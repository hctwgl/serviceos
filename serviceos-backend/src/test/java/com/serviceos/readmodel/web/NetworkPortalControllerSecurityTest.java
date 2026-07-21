package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.NetworkPortalCorrectionItem;
import com.serviceos.readmodel.api.NetworkPortalExceptionItem;
import com.serviceos.readmodel.api.NetworkPortalMembershipItem;
import com.serviceos.readmodel.api.NetworkPortalPage;
import com.serviceos.readmodel.api.NetworkPortalQualificationItem;
import com.serviceos.readmodel.api.NetworkPortalQueryService;
import com.serviceos.readmodel.api.NetworkPortalWorkbenchView;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderItem;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderWorkspace;
import com.serviceos.readmodel.api.NetworkPortalTaskItem;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M194/M202/M203/M205/M206：network-portal 未认证 401；伪造上下文 403 PORTAL_CONTEXT_INVALID。 */
@WebMvcTest(NetworkPortalController.class)
@Import(SecurityConfiguration.class)
class NetworkPortalControllerSecurityTest {
    private static final UUID NETWORK_ID = UUID.fromString("019f83a0-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID CORRECTION_ID = UUID.fromString("019f83e0-bbbb-7f8c-9505-36fe5c0e8811");
    private static final UUID EXCEPTION_ID = UUID.fromString("019f83f0-bbbb-7f8c-9505-36fe5c0e8812");
    private static final UUID QUALIFICATION_ID = UUID.fromString("019f85d0-bbbb-7f8c-9505-36fe5c0e8813");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("019f85d0-cccc-7f8c-9505-36fe5c0e8814");
    private static final UUID WORK_ORDER_ID = UUID.fromString("019f83a0-7777-7f8c-9505-36fe5c0e8808");

    @Autowired MockMvc mvc;
    @MockitoBean NetworkPortalQueryService queries;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/v1/network-portal/work-orders")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/network-portal/correction-cases")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/network-portal/correction-cases/" + CORRECTION_ID)
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/network-portal/operational-exceptions")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/network-portal/operational-exceptions/" + EXCEPTION_ID)
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/network-portal/technician-qualifications")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/network-portal/technician-qualifications/" + QUALIFICATION_ID)
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/network-portal/technician-memberships")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/network-portal/technician-memberships/" + MEMBERSHIP_ID)
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/network-portal/work-orders/" + WORK_ORDER_ID + "/workspace")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgedContextReturnsPortalContextInvalid() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(queries.listWorkOrders(eq(actor), eq("corr-deny"), eq("NETWORK|NETWORK|forged")))
                .thenThrow(new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                        "当前主体不能使用请求的 Network Portal 上下文"));

        mvc.perform(get("/api/v1/network-portal/work-orders")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-deny")
                        .header("X-Network-Context", "NETWORK|NETWORK|forged"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PORTAL_CONTEXT_INVALID"));
    }

    @Test
    void authenticatedMemberGetsWorkOrders() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        UUID workOrderId = WORK_ORDER_ID;
        when(principals.current()).thenReturn(actor);
        when(queries.listWorkOrders(eq(actor), eq("corr-ok"), eq("NETWORK|NETWORK|" + NETWORK_ID)))
                .thenReturn(new NetworkPortalPage<>(
                        NETWORK_ID,
                        List.of(new NetworkPortalWorkOrderItem(
                                workOrderId, null, List.of(UUID.randomUUID()),
                                "INSTALLATION", "tech-1", now)),
                        now));

        mvc.perform(get("/api/v1/network-portal/work-orders")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-ok")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networkId").value(NETWORK_ID.toString()))
                .andExpect(jsonPath("$.items[0].workOrderId").value(workOrderId.toString()));
    }

    @Test
    void authenticatedMemberGetsWorkOrderWorkspace() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        UUID taskId = UUID.fromString("019f83a0-9999-7f8c-9505-36fe5c0e880a");
        when(principals.current()).thenReturn(actor);
        when(queries.getWorkOrderWorkspace(
                eq(actor), eq("corr-ws"), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(WORK_ORDER_ID)))
                .thenReturn(new NetworkPortalWorkOrderWorkspace(
                        NETWORK_ID,
                        WORK_ORDER_ID,
                        null,
                        List.of(taskId),
                        "INSTALLATION",
                        "tech-1",
                        now,
                        List.of(new NetworkPortalTaskItem(
                                taskId, WORK_ORDER_ID, null, "INSTALL", "HUMAN", "S1",
                                "READY", "INSTALLATION", "tech-1", now)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        now));

        mvc.perform(get("/api/v1/network-portal/work-orders/" + WORK_ORDER_ID + "/workspace")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-ws")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workOrderId").value(WORK_ORDER_ID.toString()))
                .andExpect(jsonPath("$.tasks[0].taskId").value(taskId.toString()));
    }

    @Test
    void workbenchUsesHeaderContext() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(queries.workbench(eq(actor), eq("corr-wb"), eq("NETWORK|NETWORK|" + NETWORK_ID)))
                .thenReturn(new NetworkPortalWorkbenchView(
                        NETWORK_ID, 1, 2, 3, List.of(), now, 0, null, null, null, null,
                        null, null, List.of()));

        mvc.perform(get("/api/v1/network-portal/workbench")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-wb")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeWorkOrderCount").value(1))
                .andExpect(jsonPath("$.activeTechnicianCount").value(3));
    }

    @Test
    void authenticatedMemberGetsCorrectionQueue() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        UUID taskId = UUID.fromString("019f83e0-9999-7f8c-9505-36fe5c0e880a");
        when(principals.current()).thenReturn(actor);
        when(queries.listCorrections(
                eq(actor), eq("corr-list"), eq("NETWORK|NETWORK|" + NETWORK_ID),
                isNull(), isNull(), isNull()))
                .thenReturn(new NetworkPortalPage<>(
                        NETWORK_ID,
                        List.of(new NetworkPortalCorrectionItem(
                                CORRECTION_ID, UUID.randomUUID(), taskId,
                                UUID.randomUUID(), UUID.randomUUID(), List.of("IMAGE.BLUR"),
                                null, "OPEN", now, null, null, null, 0)),
                        now));

        mvc.perform(get("/api/v1/network-portal/correction-cases")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-list")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networkId").value(NETWORK_ID.toString()))
                .andExpect(jsonPath("$.items[0].correctionCaseId").value(CORRECTION_ID.toString()))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"));
    }

    @Test
    void authenticatedMemberGetsCorrectionDetail() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        UUID projectId = UUID.fromString("019f83e0-8888-7f8c-9505-36fe5c0e880b");
        UUID taskId = UUID.fromString("019f83e0-9999-7f8c-9505-36fe5c0e880a");
        when(principals.current()).thenReturn(actor);
        when(queries.getCorrection(
                eq(actor), eq("corr-get"), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(CORRECTION_ID)))
                .thenReturn(new CorrectionCaseView(
                        CORRECTION_ID, projectId, taskId,
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "a".repeat(64), List.of("IMAGE.BLUR"), null, "OPEN",
                        "fixture", now, null, null, null, null, null, null, null, List.of()));

        mvc.perform(get("/api/v1/network-portal/correction-cases/" + CORRECTION_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-get")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctionCaseId").value(CORRECTION_ID.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void authenticatedMemberGetsExceptionQueue() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        UUID taskId = UUID.fromString("019f83f0-9999-7f8c-9505-36fe5c0e880a");
        when(principals.current()).thenReturn(actor);
        when(queries.listExceptions(
                eq(actor), eq("exc-list"), eq("NETWORK|NETWORK|" + NETWORK_ID),
                isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new NetworkPortalPage<>(
                        NETWORK_ID,
                        List.of(new NetworkPortalExceptionItem(
                                EXCEPTION_ID, UUID.randomUUID(), "TEST", "AUTOMATION_FINAL_FAILURE",
                                "P1", "TEST_FAILURE", "OPEN", UUID.randomUUID(), taskId, null,
                                1L, now, now, null, null, List.of())),
                        now));

        mvc.perform(get("/api/v1/network-portal/operational-exceptions")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "exc-list")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networkId").value(NETWORK_ID.toString()))
                .andExpect(jsonPath("$.items[0].exceptionId").value(EXCEPTION_ID.toString()))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].allowedActions").isEmpty());
    }

    @Test
    void authenticatedMemberGetsExceptionDetail() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        UUID taskId = UUID.fromString("019f83f0-9999-7f8c-9505-36fe5c0e880a");
        when(principals.current()).thenReturn(actor);
        when(queries.getException(
                eq(actor), eq("exc-get"), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(EXCEPTION_ID)))
                .thenReturn(new NetworkPortalExceptionItem(
                        EXCEPTION_ID, UUID.randomUUID(), "TEST", "AUTOMATION_FINAL_FAILURE",
                        "P1", "TEST_FAILURE", "OPEN", UUID.randomUUID(), taskId, null,
                        1L, now, now, null, null, List.of()));

        mvc.perform(get("/api/v1/network-portal/operational-exceptions/" + EXCEPTION_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "exc-get")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exceptionId").value(EXCEPTION_ID.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.allowedActions").isEmpty());
    }

    @Test
    void authenticatedMemberGetsQualificationList() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        UUID profileId = UUID.fromString("019f85d0-5555-7f8c-9505-36fe5c0e8806");
        when(principals.current()).thenReturn(actor);
        when(queries.listQualifications(
                eq(actor), eq("qual-list"), eq("NETWORK|NETWORK|" + NETWORK_ID),
                isNull(), isNull(), isNull()))
                .thenReturn(new NetworkPortalPage<>(
                        NETWORK_ID,
                        List.of(new NetworkPortalQualificationItem(
                                QUALIFICATION_ID, profileId, "EV-INSTALL", "PENDING",
                                now, now.plusSeconds(86400), "submitter", now,
                                null, null, null, 1L)),
                        now));

        mvc.perform(get("/api/v1/network-portal/technician-qualifications")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "qual-list")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networkId").value(NETWORK_ID.toString()))
                .andExpect(jsonPath("$.items[0].id").value(QUALIFICATION_ID.toString()))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }

    @Test
    void authenticatedMemberGetsMembershipList() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        UUID profileId = UUID.fromString("019f85d0-5555-7f8c-9505-36fe5c0e8806");
        when(principals.current()).thenReturn(actor);
        when(queries.listMemberships(
                eq(actor), eq("mem-list"), eq("NETWORK|NETWORK|" + NETWORK_ID),
                isNull(), isNull(), isNull()))
                .thenReturn(new NetworkPortalPage<>(
                        NETWORK_ID,
                        List.of(new NetworkPortalMembershipItem(
                                MEMBERSHIP_ID, NETWORK_ID, profileId, "ACTIVE",
                                now, null, 3L, now, null, null)),
                        now));

        mvc.perform(get("/api/v1/network-portal/technician-memberships")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "mem-list")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networkId").value(NETWORK_ID.toString()))
                .andExpect(jsonPath("$.items[0].id").value(MEMBERSHIP_ID.toString()))
                .andExpect(jsonPath("$.items[0].version").value(3))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(
                "019f83a0-1111-7f8c-9505-36fe5c0e8801",
                "tenant-a",
                CurrentPrincipal.PrincipalType.USER,
                "network-portal",
                Set.of());
    }
}
