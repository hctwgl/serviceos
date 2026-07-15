package com.serviceos.sla.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.sla.api.SlaInstancePage;
import com.serviceos.sla.api.SlaQueryService;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M63：SLA HTTP 边界只接受 JWT 主体；project 为空时由应用层实时解析授权集合。 */
@WebMvcTest(SlaQueryController.class)
@Import(SecurityConfiguration.class)
class SlaQueryControllerSecurityTest {
    private static final UUID PROJECT_ID = UUID.fromString("62111111-1111-4111-8111-111111111111");

    @Autowired MockMvc mvc;
    @MockitoBean SlaQueryService queries;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedSlaQueryIsRejected() throws Exception {
        mvc.perform(get("/api/v1/sla-instances").queryParam("projectId", PROJECT_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trustedPrincipalAndExplicitProjectReachApplicationBoundary() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "sla-reader", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of());
        Instant asOf = Instant.parse("2026-07-15T12:00:00Z");
        when(principals.current()).thenReturn(principal);
        when(queries.list(eq(principal), eq("corr-sla-list"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SlaInstancePage(List.of(), null, asOf));

        mvc.perform(get("/api/v1/sla-instances")
                        .with(jwt().jwt(token -> token.subject("sla-reader")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("X-Correlation-Id", "corr-sla-list")
                        .header("X-Tenant-Id", "spoofed-tenant")
                        .queryParam("projectId", PROJECT_ID.toString())
                        .queryParam("status", "BREACHED")
                        .queryParam("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.asOf").value(asOf.toString()));

        verify(queries).list(eq(principal), eq("corr-sla-list"), argThat(query ->
                query.projectId().equals(PROJECT_ID)
                        && query.status().equals("BREACHED") && query.limit() == 25));
    }

    @Test
    void omittedProjectReachesAuthorizedCollectionAndInvalidLimitFailsEarly() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "sla-reader", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(queries.list(eq(principal), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(
                new SlaInstancePage(List.of(), null, Instant.parse("2026-07-15T12:00:00Z")));
        var authenticated = jwt().jwt(token -> token.subject("sla-reader")
                .claim("tenant_id", "tenant-trusted"));

        mvc.perform(get("/api/v1/sla-instances").with(authenticated))
                .andExpect(status().isOk());
        verify(queries).list(eq(principal), org.mockito.ArgumentMatchers.anyString(),
                argThat(query -> query.projectId() == null));
        mvc.perform(get("/api/v1/sla-instances").with(authenticated)
                        .queryParam("projectId", PROJECT_ID.toString()).queryParam("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
