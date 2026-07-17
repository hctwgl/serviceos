package com.serviceos.authorization.web;

import com.serviceos.authorization.api.MeContextView;
import com.serviceos.authorization.api.MeContextsView;
import com.serviceos.authorization.api.MeNavigationItemView;
import com.serviceos.authorization.api.MeNavigationView;
import com.serviceos.authorization.api.MeProfileView;
import com.serviceos.authorization.api.PortalContextQueryService;
import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.identity.api.PrincipalPersonaView;
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

/** M188：/me* 只信任 JWT 派生 CurrentPrincipal；伪造上下文与旧版本失败关闭。 */
@WebMvcTest(PortalContextController.class)
@Import(SecurityConfiguration.class)
class PortalContextControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean PortalContextQueryService queries;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedMeIsRejected() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedMeReturnsProfile() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T10:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(queries.me(eq(actor), eq("corr-me"))).thenReturn(new MeProfileView(
                actor.principalId(), actor.tenantId(), "Portal User",
                List.of(new PrincipalPersonaView(UUID.randomUUID(), "INTERNAL_EMPLOYEE", "ACTIVE",
                        now.minusSeconds(60), null, 1)),
                "role-grant-v3:g1", now));

        mvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Portal User"))
                .andExpect(jsonPath("$.contextVersion").value("role-grant-v3:g1"));
    }

    @Test
    void forgedContextReturnsForbidden() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(queries.navigation(eq(actor), eq("corr-deny"), eq("NETWORK|NETWORK|forged"), isNull()))
                .thenThrow(new BusinessProblem(ProblemCode.ACCESS_DENIED, "请求的 Portal 上下文无效或未授权"));

        mvc.perform(get("/api/v1/me/navigation")
                        .param("contextId", "NETWORK|NETWORK|forged")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-deny"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void staleContextVersionReturnsConflict() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(queries.capabilities(eq(actor), eq("corr-stale"), eq("ADMIN|TENANT|tenant-a"), eq("role-grant-v3:g1")))
                .thenThrow(new BusinessProblem(ProblemCode.VERSION_CONFLICT, "上下文版本已失效，请刷新后重试"));

        mvc.perform(get("/api/v1/me/capabilities")
                        .param("contextId", "ADMIN|TENANT|tenant-a")
                        .param("expectedContextVersion", "role-grant-v3:g1")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("VERSION_CONFLICT"));
    }

    @Test
    void navigationReturnsStablePageIds() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T10:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(queries.contexts(eq(actor), eq("corr-ctx"))).thenReturn(new MeContextsView(
                List.of(new MeContextView(
                        "ADMIN|TENANT|tenant-a", "ADMIN", "INTERNAL_EMPLOYEE", "TENANT", "tenant-a",
                        new MeContextView.MeContextScopeSummary(List.of(), List.of(), List.of()),
                        "role-grant-v3:g2")),
                "role-grant-v3:g2", now));
        when(queries.navigation(eq(actor), eq("corr-nav"), eq("ADMIN|TENANT|tenant-a"), isNull()))
                .thenReturn(new MeNavigationView(
                        "ADMIN|TENANT|tenant-a", "ADMIN", "role-grant-v3:g2", "page-registry-v1",
                        List.of(new MeNavigationItemView(
                                "ADMIN.USER.DIRECTORY", "users", "用户目录", 200, "平台治理",
                                List.of("identity.read"))),
                        now));

        mvc.perform(get("/api/v1/me/contexts")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-ctx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contexts[0].portal").value("ADMIN"));

        mvc.perform(get("/api/v1/me/navigation")
                        .param("contextId", "ADMIN|TENANT|tenant-a")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-nav"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].pageId").value("ADMIN.USER.DIRECTORY"));
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal("06b612f3-a901-4b0e-bd90-86b4259cc087", "tenant-a",
                CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
    }
}
