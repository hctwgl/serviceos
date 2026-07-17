package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.UiPreferenceCommandService;
import com.serviceos.readmodel.api.UiPreferenceEntry;
import com.serviceos.readmodel.api.UiPreferenceQueryService;
import com.serviceos.readmodel.api.UiPreferencesDocument;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.node.StringNode;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M190：UI Preference HTTP 只信任 JWT 派生主体；未认证 401；写路径走本人服务。 */
@WebMvcTest(UiPreferenceController.class)
@Import(SecurityConfiguration.class)
class UiPreferenceControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean UiPreferenceQueryService queries;
    @MockitoBean UiPreferenceCommandService commands;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedGetIsRejected() throws Exception {
        mvc.perform(get("/api/v1/me/ui-preferences").param("portal", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedGetReturnsOwnPreferences() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(queries.get(eq(actor), eq("corr-get"), eq("ADMIN")))
                .thenReturn(new UiPreferencesDocument(
                        "ADMIN",
                        Map.of("theme", new UiPreferenceEntry(
                                "theme", StringNode.valueOf("DARK"), 1, 1L, now)),
                        now));

        mvc.perform(get("/api/v1/me/ui-preferences")
                        .param("portal", "ADMIN")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portal").value("ADMIN"))
                .andExpect(jsonPath("$.preferences.theme.value").value("DARK"));
    }

    @Test
    void putUsesAuthenticatedPrincipalOnly() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(commands.put(eq(actor), eq("corr-put"), eq("ADMIN"), anyMap()))
                .thenReturn(new UiPreferencesDocument(
                        "ADMIN",
                        Map.of("theme", new UiPreferenceEntry(
                                "theme", StringNode.valueOf("LIGHT"), 1, 1L, now)),
                        now));

        mvc.perform(put("/api/v1/me/ui-preferences")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-put")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "portal": "ADMIN",
                                  "preferences": {
                                    "theme": { "value": "LIGHT", "schemaVersion": 1 }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.theme.value").value("LIGHT"));
    }

    @Test
    void serviceRejectsCrossPrincipalSemanticsAsNotFoundStyle() throws Exception {
        // Controller 从不接受目标 principal；跨主体只能通过伪造服务结果验证隔离语义。
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(commands.put(eq(actor), eq("corr-x"), eq("ADMIN"), any()))
                .thenThrow(new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "偏好不存在"));

        mvc.perform(put("/api/v1/me/ui-preferences")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "portal": "ADMIN",
                                  "preferences": {
                                    "theme": { "value": "DARK", "schemaVersion": 1 }
                                  }
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unauthenticatedDeleteIsRejected() throws Exception {
        mvc.perform(delete("/api/v1/me/ui-preferences/theme").param("portal", "ADMIN"))
                .andExpect(status().isUnauthorized());
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
