package com.serviceos.identity.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.identity.api.SecurityPrincipalCommandService;
import com.serviceos.identity.api.SecurityPrincipalPage;
import com.serviceos.identity.api.SecurityPrincipalQueryService;
import com.serviceos.identity.api.SecurityPrincipalView;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M183：统一主体 HTTP 边界只信任 JWT 派生 CurrentPrincipal，并保持敏感身份接口分离。 */
@WebMvcTest(SecurityPrincipalController.class)
@Import(SecurityConfiguration.class)
class SecurityPrincipalControllerSecurityTest {
    private static final UUID PRINCIPAL_ID = UUID.fromString("019f7022-17ea-7f8c-9505-36fe5c0e8844");

    @Autowired MockMvc mvc;
    @MockitoBean SecurityPrincipalQueryService queries;
    @MockitoBean SecurityPrincipalCommandService commands;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedDirectoryRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/security-principals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ordinaryDirectoryResponseDoesNotExposeExternalIdentityFields() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T08:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(queries.list(eq(actor), eq("corr-directory"), eq("张"), eq("ACTIVE"), eq(null), eq(25)))
                .thenReturn(new SecurityPrincipalPage(List.of(new SecurityPrincipalView(
                        PRINCIPAL_ID, "USER", "ACTIVE", "张三", "EMP-001", 1, now, now)), null, now));

        mvc.perform(get("/api/v1/security-principals")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-directory")
                        .queryParam("query", "张").queryParam("status", "ACTIVE").queryParam("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(PRINCIPAL_ID.toString()))
                .andExpect(jsonPath("$.items[0].issuer").doesNotExist())
                .andExpect(jsonPath("$.items[0].subject").doesNotExist())
                .andExpect(jsonPath("$.asOf").value(now.toString()));
    }

    @Test
    void lifecycleCommandRequiresQuotedVersionAndForwardsIdempotencyMetadata() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T08:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(commands.disable(eq(actor), any(), eq(PRINCIPAL_ID), eq(3L), eq("离职停用")))
                .thenReturn(new SecurityPrincipalView(
                        PRINCIPAL_ID, "USER", "DISABLED", "张三", "EMP-001", 4, now, now));

        mvc.perform(post("/api/v1/security-principals/{principalId}:disable", PRINCIPAL_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-disable")
                        .header("Idempotency-Key", "idem-disable")
                        .header("If-Match", "\"3\"")
                        .contentType("application/json")
                        .content("{\"reason\":\"离职停用\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""))
                .andExpect(jsonPath("$.status").value("DISABLED"));
        verify(commands).disable(eq(actor), any(), eq(PRINCIPAL_ID), eq(3L), eq("离职停用"));

        mvc.perform(post("/api/v1/security-principals/{principalId}:disable", PRINCIPAL_ID)
                        .with(jwt()).header("Idempotency-Key", "idem-invalid")
                        .header("If-Match", "3").contentType("application/json")
                        .content("{\"reason\":\"离职停用\"}"))
                .andExpect(status().isBadRequest());
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal("identity-admin", "tenant-a", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of());
    }
}
