package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.RecentResourceCommandService;
import com.serviceos.readmodel.api.RecentResourceItem;
import com.serviceos.readmodel.api.RecentResourcePage;
import com.serviceos.readmodel.api.RecentResourceQueryService;
import com.serviceos.readmodel.api.RecentResourceType;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M193：最近访问 HTTP 只信任 JWT 派生主体；未认证 401；无跨主体路径。 */
@WebMvcTest(RecentResourceController.class)
@Import(SecurityConfiguration.class)
class RecentResourceControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean RecentResourceQueryService queries;
    @MockitoBean RecentResourceCommandService commands;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedGetIsRejected() throws Exception {
        mvc.perform(get("/api/v1/me/recent-resources"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedPutIsRejected() throws Exception {
        mvc.perform(put("/api/v1/me/recent-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceType": "WORK_ORDER",
                                  "resourceId": "019f81a0-aaaa-7f8c-9505-36fe5c0e8801"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedGetReturnsOwnList() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        UUID woId = UUID.fromString("019f81a0-aaaa-7f8c-9505-36fe5c0e8801");
        when(principals.current()).thenReturn(actor);
        when(queries.list(eq(actor), eq("corr-get"), eq("ADMIN"), isNull()))
                .thenReturn(new RecentResourcePage(
                        List.of(new RecentResourceItem(
                                RecentResourceType.WORK_ORDER,
                                woId.toString(),
                                "ADMIN.WORKORDER.WORKSPACE",
                                "WO-1",
                                now,
                                "/work-orders/" + woId)),
                        now));

        mvc.perform(get("/api/v1/me/recent-resources")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].resourceType").value("WORK_ORDER"))
                .andExpect(jsonPath("$.items[0].deepLink").value("/work-orders/" + woId));
    }

    @Test
    void putUsesAuthenticatedPrincipalOnly() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        UUID woId = UUID.fromString("019f81a0-aaaa-7f8c-9505-36fe5c0e8801");
        when(principals.current()).thenReturn(actor);
        when(commands.touch(eq(actor), eq("corr-put"), eq("ADMIN"), any()))
                .thenReturn(new RecentResourceItem(
                        RecentResourceType.WORK_ORDER,
                        woId.toString(),
                        "ADMIN.WORKORDER.WORKSPACE",
                        "WO-1",
                        now,
                        "/work-orders/" + woId));

        mvc.perform(put("/api/v1/me/recent-resources")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-put")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "portal": "ADMIN",
                                  "resourceType": "WORK_ORDER",
                                  "resourceId": "019f81a0-aaaa-7f8c-9505-36fe5c0e8801",
                                  "pageId": "ADMIN.WORKORDER.WORKSPACE",
                                  "displayRef": "WO-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("WORK_ORDER"))
                .andExpect(jsonPath("$.displayRef").value("WO-1"));
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(
                "019f81a0-1111-7f8c-9505-36fe5c0e8801",
                "tenant-a",
                CurrentPrincipal.PrincipalType.USER,
                "admin-web",
                Set.of());
    }
}
