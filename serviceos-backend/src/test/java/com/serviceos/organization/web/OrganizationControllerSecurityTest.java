package com.serviceos.organization.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.organization.api.OrgUnitView;
import com.serviceos.organization.api.OrganizationCommandService;
import com.serviceos.organization.api.OrganizationPage;
import com.serviceos.organization.api.OrganizationQueryService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M184：组织目录 HTTP 边界只信任 JWT 派生 CurrentPrincipal，并校验 If-Match。 */
@WebMvcTest(OrganizationController.class)
@Import(SecurityConfiguration.class)
class OrganizationControllerSecurityTest {
    private static final UUID ORG_ID = UUID.fromString("019f7022-17ea-7f8c-9505-36fe5c0e8844");
    private static final UUID UNIT_ID = UUID.fromString("019f7022-17ea-7f8c-9505-36fe5c0e8845");

    @Autowired MockMvc mvc;
    @MockitoBean OrganizationQueryService queries;
    @MockitoBean OrganizationCommandService commands;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedOrganizationRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedListOrganizationsIsAllowed() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(queries.listOrganizations(eq(actor), eq("corr-list")))
                .thenReturn(new OrganizationPage(List.of(), Instant.parse("2026-07-17T08:00:00Z")));

        mvc.perform(get("/api/v1/organizations")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-list"))
                .andExpect(status().isOk());
    }

    @Test
    void createUnitRequiresQuotedVersionAndForwardsIdempotencyMetadata() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T08:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(commands.createUnit(eq(actor), any(), eq(ORG_ID), eq(2L), eq(null), eq("ENG"), eq("Engineering")))
                .thenReturn(new OrgUnitView(UNIT_ID, ORG_ID, null, "ENG", "Engineering", "ACTIVE",
                        null, null, null, 1, now, now));

        mvc.perform(post("/api/v1/organizations/{organizationId}/units", ORG_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-unit")
                        .header("Idempotency-Key", "idem-unit")
                        .header("If-Match", "\"2\"")
                        .contentType("application/json")
                        .content("{\"unitCode\":\"ENG\",\"unitName\":\"Engineering\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));
        verify(commands).createUnit(eq(actor), any(), eq(ORG_ID), eq(2L), eq(null), eq("ENG"), eq("Engineering"));

        mvc.perform(post("/api/v1/organizations/{organizationId}/units", ORG_ID)
                        .with(jwt()).header("Idempotency-Key", "idem-invalid")
                        .header("If-Match", "2").contentType("application/json")
                        .content("{\"unitCode\":\"ENG\",\"unitName\":\"Engineering\"}"))
                .andExpect(status().isBadRequest());
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal("org-admin", "tenant-a", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of());
    }
}
