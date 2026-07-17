package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.NetworkPortalCorrectionItem;
import com.serviceos.readmodel.api.NetworkPortalPage;
import com.serviceos.readmodel.api.NetworkPortalQueryService;
import com.serviceos.readmodel.api.NetworkPortalWorkbenchView;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderItem;
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

/** M194/M202：network-portal 未认证 401；伪造上下文 403 PORTAL_CONTEXT_INVALID。 */
@WebMvcTest(NetworkPortalController.class)
@Import(SecurityConfiguration.class)
class NetworkPortalControllerSecurityTest {
    private static final UUID NETWORK_ID = UUID.fromString("019f83a0-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID CORRECTION_ID = UUID.fromString("019f83e0-bbbb-7f8c-9505-36fe5c0e8811");

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
        UUID workOrderId = UUID.fromString("019f83a0-7777-7f8c-9505-36fe5c0e8808");
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
    void workbenchUsesHeaderContext() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(queries.workbench(eq(actor), eq("corr-wb"), eq("NETWORK|NETWORK|" + NETWORK_ID)))
                .thenReturn(new NetworkPortalWorkbenchView(NETWORK_ID, 1, 2, 3, List.of(), now));

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

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(
                "019f83a0-1111-7f8c-9505-36fe5c0e8801",
                "tenant-a",
                CurrentPrincipal.PrincipalType.USER,
                "network-portal",
                Set.of());
    }
}
