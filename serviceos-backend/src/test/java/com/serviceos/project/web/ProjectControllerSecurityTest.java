package com.serviceos.project.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectView;
import com.serviceos.shared.CommandMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
@Import(SecurityConfiguration.class)
class ProjectControllerSecurityTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProjectCommandService commands;

    @MockitoBean
    CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedRequestIsRejectedByResourceServer() throws Exception {
        mvc.perform(post("/api/v1/projects")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void actorAndTenantComeFromCurrentPrincipalNotSpoofedHeaders() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "user-1", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("project.create"));
        ProjectView result = new ProjectView(
                UUID.fromString("19dac447-73fd-4f24-8178-a9eac8d9ed34"),
                "tenant-trusted", "BYD-2026", "client-byd", "比亚迪项目",
                LocalDate.of(2026, 1, 1), null, "DRAFT", 1,
                Instant.parse("2026-07-13T03:30:00Z"));
        when(principals.current()).thenReturn(principal);
        when(commands.create(eq(principal), any(), any())).thenReturn(result);

        mvc.perform(post("/api/v1/projects")
                        .with(jwt().jwt(token -> token.subject("user-1").claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "idem-2")
                        .header("X-Correlation-Id", "corr-2")
                        .header("X-Tenant-Id", "tenant-spoofed")
                        .header("X-Actor-Id", "actor-spoofed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "corr-2"))
                .andExpect(jsonPath("$.tenantId").value("tenant-trusted"));

        verify(commands).create(
                eq(principal),
                argThat((CommandMetadata metadata) ->
                        metadata.correlationId().equals("corr-2")
                                && metadata.idempotencyKey().equals("idem-2")),
                any());
    }

    private static String validRequest() {
        return """
                {
                  "code": "BYD-2026",
                  "clientId": "client-byd",
                  "name": "比亚迪项目",
                  "startsOn": "2026-01-01"
                }
                """;
    }
}
