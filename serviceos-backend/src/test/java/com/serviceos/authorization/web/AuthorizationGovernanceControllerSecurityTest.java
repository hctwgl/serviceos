package com.serviceos.authorization.web;

import com.serviceos.authorization.api.AuthorizationGovernanceCommandService;
import com.serviceos.authorization.api.AuthorizationGovernanceQueryService;
import com.serviceos.authorization.api.CapabilityView;
import com.serviceos.authorization.api.RoleView;
import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
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

/** M186：授权治理 HTTP 边界只信任 JWT 派生 CurrentPrincipal，缺权返回 403。 */
@WebMvcTest(AuthorizationGovernanceController.class)
@Import(SecurityConfiguration.class)
class AuthorizationGovernanceControllerSecurityTest {
    private static final UUID ROLE_ID = UUID.fromString("019f7022-17ea-7f8c-9505-36fe5c0e8861");

    @Autowired MockMvc mvc;
    @MockitoBean AuthorizationGovernanceQueryService queries;
    @MockitoBean AuthorizationGovernanceCommandService commands;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedGovernanceRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/capabilities"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCapabilityReturnsForbiddenProblem() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(queries.listCapabilities(eq(actor), eq("corr-deny")))
                .thenThrow(new BusinessProblem(ProblemCode.ACCESS_DENIED, "The action is not allowed"));

        mvc.perform(get("/api/v1/capabilities")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-deny"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void createRoleReturnsVersionEtag() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T08:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(commands.createRole(eq(actor), any(), eq("OPS-READER"), eq("Ops Reader"), eq(null),
                eq(List.of("authorization.read"))))
                .thenReturn(new RoleView(ROLE_ID, "OPS-READER", "Ops Reader", "TENANT", "ACTIVE", null,
                        List.of("authorization.read"), 1, now, now));

        mvc.perform(post("/api/v1/roles")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-role")
                        .header("Idempotency-Key", "idem-role")
                        .contentType("application/json")
                        .content("{\"roleCode\":\"OPS-READER\",\"roleName\":\"Ops Reader\","
                                + "\"capabilityCodes\":[\"authorization.read\"]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));
        verify(commands).createRole(eq(actor), any(), eq("OPS-READER"), eq("Ops Reader"), eq(null),
                eq(List.of("authorization.read")));
    }

    @Test
    void authenticatedListCapabilitiesIsAllowed() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(queries.listCapabilities(eq(actor), eq("corr-list")))
                .thenReturn(List.of(new CapabilityView("authorization.read", "读取角色与授权治理目录", "HIGH")));

        mvc.perform(get("/api/v1/capabilities")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].capabilityCode").value("authorization.read"));
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal("auth-admin", "tenant-a", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of());
    }
}
