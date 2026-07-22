package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.AdminProjectCreationOptionsQueryService;
import com.serviceos.readmodel.api.AdminProjectCreationOptionsView;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProjectCreationOptionsController.class)
@Import(SecurityConfiguration.class)
class AdminProjectCreationOptionsControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminProjectCreationOptionsQueryService queries;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    @DisplayName("新建项目选项必须认证并只返回业务名称与稳定选项")
    void requiresAuthenticationAndReturnsProductOptions() throws Exception {
        mvc.perform(get("/api/v1/admin/projects/creation-options"))
                .andExpect(status().isUnauthorized());

        CurrentPrincipal principal = new CurrentPrincipal(
                "admin", "tenant", CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(queries.load(principal, "corr-create-project", "历城")).thenReturn(
                new AdminProjectCreationOptionsView(
                        List.of(new AdminProjectCreationOptionsView.ClientOption("BYD", "比亚迪汽车")),
                        List.of(new AdminProjectCreationOptionsView.RegionOption(
                                "370112", "历城区", "DISTRICT", "370100")),
                        List.of(new AdminProjectCreationOptionsView.NetworkOption(
                                UUID.randomUUID(), "JN-ZL", "济南智联服务中心", "ACTIVE")),
                        List.of("CREATE_PROJECT"), Instant.parse("2026-07-22T08:00:00Z")));

        mvc.perform(get("/api/v1/admin/projects/creation-options")
                        .queryParam("regionQuery", "历城")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-create-project"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clients[0].name").value("比亚迪汽车"))
                .andExpect(jsonPath("$.regions[0].name").value("历城区"))
                .andExpect(jsonPath("$.networks[0].name").value("济南智联服务中心"))
                .andExpect(jsonPath("$.allowedActions[0]").value("CREATE_PROJECT"))
                .andExpect(jsonPath("$.tenantId").doesNotExist());
    }
}
