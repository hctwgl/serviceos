package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.SavedView;
import com.serviceos.readmodel.api.SavedViewCommandService;
import com.serviceos.readmodel.api.SavedViewFilterAst;
import com.serviceos.readmodel.api.SavedViewFilterClause;
import com.serviceos.readmodel.api.SavedViewPage;
import com.serviceos.readmodel.api.SavedViewQueryService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M189：SavedView HTTP 只信任 JWT 派生主体；未认证 401；跨主体 404。 */
@WebMvcTest(SavedViewController.class)
@Import(SecurityConfiguration.class)
class SavedViewControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean SavedViewQueryService queries;
    @MockitoBean SavedViewCommandService commands;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedListIsRejected() throws Exception {
        mvc.perform(get("/api/v1/me/saved-views").param("pageId", "ADMIN.TASK.QUEUE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedListReturnsOwnViews() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(queries.list(eq(actor), eq("corr-list"), eq("ADMIN.TASK.QUEUE")))
                .thenReturn(new SavedViewPage(List.of(sampleView(now)), now));

        mvc.perform(get("/api/v1/me/saved-views")
                        .param("pageId", "ADMIN.TASK.QUEUE")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].pageId").value("ADMIN.TASK.QUEUE"))
                .andExpect(jsonPath("$.items[0].name").value("READY 视图"));
    }

    @Test
    void createPersistsForAuthenticatedPrincipal() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(commands.create(
                eq(actor), eq("corr-create"), eq("ADMIN.TASK.QUEUE"), eq("READY 视图"),
                eq(1), any(), isNull(), isNull(), eq(false)))
                .thenReturn(sampleView(now));

        mvc.perform(post("/api/v1/me/saved-views")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageId": "ADMIN.TASK.QUEUE",
                                  "name": "READY 视图",
                                  "schemaVersion": 1,
                                  "filter": { "clauses": [{ "field": "status", "operator": "EQ", "value": "READY" }] },
                                  "isDefault": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void crossPrincipalUpdateReturnsNotFound() throws Exception {
        CurrentPrincipal actor = actor();
        UUID foreignId = UUID.fromString("019f81a0-9999-7f8c-9505-36fe5c0e8809");
        when(principals.current()).thenReturn(actor);
        when(commands.update(
                eq(actor), eq("corr-x"), eq(foreignId), anyLong(), anyString(), anyInt(),
                any(), any(), any(), anyBoolean()))
                .thenThrow(new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "保存视图不存在"));

        mvc.perform(put("/api/v1/me/saved-views/" + foreignId)
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-x")
                        .header("If-Match", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "偷改",
                                  "schemaVersion": 1,
                                  "filter": { "clauses": [{ "field": "status", "operator": "EQ", "value": "READY" }] }
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void crossPrincipalDeleteReturnsNotFound() throws Exception {
        CurrentPrincipal actor = actor();
        UUID foreignId = UUID.fromString("019f81a0-9999-7f8c-9505-36fe5c0e8809");
        when(principals.current()).thenReturn(actor);
        doThrow(new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "保存视图不存在"))
                .when(commands).delete(eq(actor), eq("corr-del"), eq(foreignId));

        mvc.perform(delete("/api/v1/me/saved-views/" + foreignId)
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-del"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(
                "019f81a0-1111-7f8c-9505-36fe5c0e8801",
                "tenant-a",
                CurrentPrincipal.PrincipalType.USER,
                "admin-web",
                Set.of());
    }

    private static SavedView sampleView(Instant now) {
        return new SavedView(
                UUID.fromString("019f81a0-4444-7f8c-9505-36fe5c0e8804"),
                "ADMIN",
                "ADMIN.TASK.QUEUE",
                "READY 视图",
                1,
                new SavedViewFilterAst(List.of(new SavedViewFilterClause("status", "EQ", "READY"))),
                null,
                null,
                false,
                1L,
                now,
                now);
    }
}
