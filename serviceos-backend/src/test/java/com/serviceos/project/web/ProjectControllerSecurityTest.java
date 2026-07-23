package com.serviceos.project.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectDetail;
import com.serviceos.project.api.ProjectClientOption;
import com.serviceos.project.api.ProjectPage;
import com.serviceos.project.api.ProjectQuery;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.project.api.ProjectReferenceOptions;
import com.serviceos.project.api.ProjectRegionOption;
import com.serviceos.project.api.ProjectScopeRelationRevisionView;
import com.serviceos.project.api.ReviseProjectScopeRelationsCommand;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    ProjectQueryService queries;

    @MockitoBean
    CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedRequestIsRejectedByResourceServer() throws Exception {
        var result = mvc.perform(post("/api/v1/projects")
                        .header("Idempotency-Key", "idem-1")
                        // 空格和凭据形态不允许成为 correlationId，更不能被原样写回日志或响应。
                        .header("X-Correlation-Id", "Bearer top-secret-token-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(correlationId).matches("[0-9a-f-]{36}");
        assertThat(result.getResponse().getContentAsString())
                .contains(correlationId)
                .doesNotContain("top-secret-token-value");
    }

    @Test
    void actorAndTenantComeFromCurrentPrincipalNotSpoofedHeaders() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "user-1", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("project.create"));
        ProjectView result = new ProjectView(
                UUID.fromString("19dac447-73fd-4f24-8178-a9eac8d9ed34"),
                "tenant-trusted", "BYD-2026", "client-byd", "比亚迪项目",
                LocalDate.of(2026, 1, 1), null, java.util.List.of("CN-3702"),
                java.util.List.of("network-qingdao-a"), "DRAFT", 1,
                Instant.parse("2026-07-13T03:30:00Z"), null, null);
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

    @Test
    void projectQueriesUseTrustedPrincipalAndReturnStableEtag() throws Exception {
        UUID projectId = UUID.fromString("19dac447-73fd-4f24-8178-a9eac8d9ed34");
        CurrentPrincipal principal = new CurrentPrincipal(
                "reader-1", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("project.read"));
        ProjectView project = new ProjectView(
                projectId, "tenant-trusted", "BYD-2026", "client-byd", "比亚迪项目",
                LocalDate.of(2026, 1, 1), null, java.util.List.of("CN-3702"),
                java.util.List.of("network-qingdao-a"), "DRAFT", 3,
                Instant.parse("2026-07-13T03:30:00Z"), 1, 0);
        when(principals.current()).thenReturn(principal);
        when(queries.list(eq(principal), eq("corr-m67-list"), any()))
                .thenReturn(new ProjectPage(java.util.List.of(project), null,
                        Instant.parse("2026-07-15T10:00:00Z")));
        when(queries.get(principal, "corr-m67-get", projectId))
                .thenReturn(new ProjectDetail(project, Instant.parse("2026-07-15T10:00:00Z")));

        mvc.perform(get("/api/v1/projects")
                        .with(jwt().jwt(token -> token.subject("reader-1").claim("tenant_id", "tenant-trusted")))
                        .header("X-Correlation-Id", "corr-m67-list")
                        .queryParam("clientId", "client-byd")
                        .queryParam("status", "DRAFT")
                        .queryParam("activeOn", "2026-07-15")
                        .queryParam("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "corr-m67-list"))
                .andExpect(jsonPath("$.items[0].id").value(projectId.toString()));

        mvc.perform(get("/api/v1/projects/{projectId}", projectId)
                        .with(jwt().jwt(token -> token.subject("reader-1").claim("tenant_id", "tenant-trusted")))
                        .header("X-Correlation-Id", "corr-m67-get"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.project.id").value(projectId.toString()));

        verify(queries).list(eq(principal), eq("corr-m67-list"), argThat((ProjectQuery query) ->
                "client-byd".equals(query.clientId()) && "DRAFT".equals(query.status())
                        && LocalDate.of(2026, 7, 15).equals(query.activeOn()) && query.limit() == 20));
        verify(queries).get(principal, "corr-m67-get", projectId);
    }

    @Test
    void reviseScopeRelationsUsesTrustedPrincipalIfMatchAndExplicitSets() throws Exception {
        UUID projectId = UUID.fromString("19dac447-73fd-4f24-8178-a9eac8d9ed34");
        CurrentPrincipal principal = new CurrentPrincipal(
                "user-1", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("project.reviseScopeRelations"));
        ProjectScopeRelationRevisionView result = new ProjectScopeRelationRevisionView(
                UUID.fromString("0999d4b6-070a-4a54-870d-43a252302b61"), projectId,
                java.util.List.of("CN-4403"), java.util.List.of("network-shenzhen-a"),
                java.util.List.of("CN-4403"), java.util.List.of("CN-3702"),
                java.util.List.of("network-shenzhen-a"), java.util.List.of("network-qingdao-a"),
                "项目服务范围调整", 2, Instant.parse("2026-07-15T06:30:00Z"));
        when(principals.current()).thenReturn(principal);
        when(commands.reviseScopeRelations(eq(principal), any(), any())).thenReturn(result);

        mvc.perform(post("/api/v1/projects/{projectId}:revise-scope-relations", projectId)
                        .with(jwt().jwt(token -> token.subject("user-1").claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "idem-m66-http-001")
                        .header("If-Match", "\"1\"")
                        .header("X-Correlation-Id", "corr-m66-http-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "regionCodes": ["CN-4403"],
                                  "networkIds": ["network-shenzhen-a"],
                                  "reason": "项目服务范围调整"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(header().string("X-Correlation-Id", "corr-m66-http-001"))
                .andExpect(jsonPath("$.aggregateVersion").value(2));

        verify(commands).reviseScopeRelations(
                eq(principal),
                argThat((CommandMetadata metadata) ->
                        metadata.correlationId().equals("corr-m66-http-001")
                                && metadata.idempotencyKey().equals("idem-m66-http-001")),
                argThat((ReviseProjectScopeRelationsCommand command) ->
                        command.projectId().equals(projectId)
                                && command.expectedVersion() == 1
                                && command.regionCodes().equals(java.util.List.of("CN-4403"))
                                && command.networkIds().equals(java.util.List.of("network-shenzhen-a"))));
    }

    @Test
    void referenceOptionsUsesTrustedPrincipalAndDoesNotTreatPathAsProjectId() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "user-1", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("project.read"));
        when(principals.current()).thenReturn(principal);
        when(queries.referenceOptions(eq(principal), any())).thenReturn(new ProjectReferenceOptions(
                java.util.List.of(new ProjectClientOption("client-byd", "比亚迪", 1)),
                java.util.List.of(new ProjectRegionOption("CN-3702", "青岛市", 1)),
                Instant.parse("2026-07-20T00:00:00Z")));

        mvc.perform(get("/api/v1/projects/reference-options")
                        .with(jwt().jwt(token -> token.subject("user-1").claim("tenant_id", "tenant-trusted")))
                        .header("X-Correlation-Id", "corr-m400-options"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "corr-m400-options"))
                .andExpect(jsonPath("$.clients[0].clientId").value("client-byd"))
                .andExpect(jsonPath("$.regions[0].regionCode").value("CN-3702"));

        verify(queries).referenceOptions(eq(principal), eq("corr-m400-options"));
    }

    @Test
    void activateProjectUsesTrustedPrincipalAndQuotedVersion() throws Exception {
        UUID projectId = UUID.fromString("19dac447-73fd-4f24-8178-a9eac8d9ed34");
        CurrentPrincipal principal = new CurrentPrincipal(
                "project-admin", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("project.create"));
        ProjectView active = new ProjectView(
                projectId, "tenant-trusted", "BYD-2026", "client-byd", "比亚迪项目",
                LocalDate.of(2026, 1, 1), null, java.util.List.of("CN-3702"),
                java.util.List.of("network-qingdao-a"), "ACTIVE", 2,
                Instant.parse("2026-07-22T08:00:00Z"), 1, 0);
        when(principals.current()).thenReturn(principal);
        when(commands.activate(eq(principal), any(), eq(projectId), eq(1L))).thenReturn(active);

        mvc.perform(post("/api/v1/projects/{projectId}:activate", projectId)
                        .with(jwt().jwt(token -> token.subject("project-admin")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "idem-project-activate")
                        .header("If-Match", "\"1\"")
                        .header("X-Correlation-Id", "corr-project-activate"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(commands).activate(eq(principal), any(), eq(projectId), eq(1L));
    }

    @Test
    void reviseScopeRelationsRejectsMissingExplicitSetAndUnquotedIfMatch() throws Exception {
        UUID projectId = UUID.fromString("19dac447-73fd-4f24-8178-a9eac8d9ed34");

        mvc.perform(post("/api/v1/projects/{projectId}:revise-scope-relations", projectId)
                        .with(jwt())
                        .header("Idempotency-Key", "idem-m66-http-002")
                        .header("If-Match", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionCodes": [], "reason": "缺少 networkIds"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mvc.perform(post("/api/v1/projects/{projectId}:revise-scope-relations", projectId)
                        .with(jwt())
                        .header("Idempotency-Key", "idem-m66-http-003")
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionCodes": [], "reason": "缺少 networkIds"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private static String validRequest() {
        return """
                {
                  "code": "BYD-2026",
                  "clientId": "client-byd",
                  "name": "比亚迪项目",
                  "startsOn": "2026-01-01",
                  "regionCodes": ["CN-3702"],
                  "networkIds": ["network-qingdao-a"]
                }
                """;
    }
}
