package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.TechnicianPortalFeedPage;
import com.serviceos.readmodel.api.TechnicianPortalQueryService;
import com.serviceos.readmodel.api.TechnicianPortalSyncSummary;
import com.serviceos.readmodel.api.TechnicianPortalTaskDetail;
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

/** M195：technician feed 未认证 401；伪造上下文 403 PORTAL_CONTEXT_INVALID。 */
@WebMvcTest(TechnicianPortalController.class)
@Import(SecurityConfiguration.class)
class TechnicianPortalControllerSecurityTest {
    private static final UUID NETWORK_ID = UUID.fromString("019f83b0-2222-7f8c-9505-36fe5c0e8803");

    @Autowired MockMvc mvc;
    @MockitoBean TechnicianPortalQueryService queries;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/v1/technician/me/task-feed")
                        .header("X-Technician-Context", "TECHNICIAN|NETWORK|" + NETWORK_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgedContextReturnsPortalContextInvalid() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(queries.taskFeed(eq(actor), eq("corr-deny"), eq("TECHNICIAN|NETWORK|forged"), isNull()))
                .thenThrow(new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                        "当前主体不能使用请求的 Technician Portal 上下文"));

        mvc.perform(get("/api/v1/technician/me/task-feed")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-deny")
                        .header("X-Technician-Context", "TECHNICIAN|NETWORK|forged"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PORTAL_CONTEXT_INVALID"));
    }

    @Test
    void authenticatedTechnicianGetsFeed() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(queries.taskFeed(eq(actor), eq("corr-ok"), eq("TECHNICIAN|NETWORK|" + NETWORK_ID), isNull()))
                .thenReturn(new TechnicianPortalFeedPage(NETWORK_ID, List.of(), null, now));

        mvc.perform(get("/api/v1/technician/me/task-feed")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-ok")
                        .header("X-Technician-Context", "TECHNICIAN|NETWORK|" + NETWORK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networkId").value(NETWORK_ID.toString()));
    }

    @Test
    void syncSummaryUsesHeaderContext() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(queries.syncSummary(eq(actor), eq("corr-sync"), eq("TECHNICIAN|NETWORK|" + NETWORK_ID)))
                .thenReturn(new TechnicianPortalSyncSummary(NETWORK_ID, 1, 2, 0, now));

        mvc.perform(get("/api/v1/technician/me/sync-summary")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-sync")
                        .header("X-Technician-Context", "TECHNICIAN|NETWORK|" + NETWORK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingFeedItemCount").value(1))
                .andExpect(jsonPath("$.appointmentWindowCount").value(2));
    }

    @Test
    void authenticatedTechnicianGetsTaskDetailThroughHeaderContext() throws Exception {
        CurrentPrincipal actor = actor();
        UUID taskId = UUID.fromString("019f83b0-9999-7f8c-9505-36fe5c0e8809");
        UUID workOrderId = UUID.fromString("019f83b0-7777-7f8c-9505-36fe5c0e8807");
        UUID projectId = UUID.fromString("019f83b0-6666-7f8c-9505-36fe5c0e8806");
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(queries.taskDetail(
                eq(actor), eq("corr-detail"), eq("TECHNICIAN|NETWORK|" + NETWORK_ID), eq(taskId)))
                .thenReturn(new TechnicianPortalTaskDetail(
                        NETWORK_ID, taskId, workOrderId, projectId,
                        null, null, "INSTALLATION", "HUMAN", "INSTALL", "READY",
                        "INSTALLATION", now, false, 1, List.of(), List.of(), now));

        mvc.perform(get("/api/v1/technician/me/tasks/{taskId}", taskId)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-detail")
                        .header("X-Technician-Context", "TECHNICIAN|NETWORK|" + NETWORK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networkId").value(NETWORK_ID.toString()))
                .andExpect(jsonPath("$.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.taskStatus").value("READY"))
                .andExpect(jsonPath("$.appointments").isArray())
                .andExpect(jsonPath("$.contactAttempts").isArray());
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(
                "019f83b0-1111-7f8c-9505-36fe5c0e8801",
                "tenant-a",
                CurrentPrincipal.PrincipalType.USER,
                "technician-portal",
                Set.of());
    }
}
