package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.AdminWorkbenchQueryService;
import com.serviceos.readmodel.api.AdminWorkbenchView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminWorkbenchController.class)
@Import(SecurityConfiguration.class)
class AdminWorkbenchControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminWorkbenchQueryService queries;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    void requiresAuthenticationAndReturnsOnlyPageProjection() throws Exception {
        mvc.perform(get("/api/v1/admin/workbench"))
                .andExpect(status().isUnauthorized());

        CurrentPrincipal principal = new CurrentPrincipal(
                "admin", "tenant", CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(queries.get(principal, "corr-admin-workbench")).thenReturn(
                new AdminWorkbenchView(23, 8, 5, 4, 3, 2, 1, 4,
                        Instant.parse("2026-07-22T08:00:00Z")));

        mvc.perform(get("/api/v1/admin/workbench")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-admin-workbench"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorityCount").value(23))
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.capabilities").doesNotExist());
    }
}
