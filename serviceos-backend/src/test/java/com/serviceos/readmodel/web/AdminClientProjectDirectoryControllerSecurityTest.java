package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.AdminClientProjectDirectoryQueryService;
import com.serviceos.readmodel.api.AdminClientProjectDirectoryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminClientProjectDirectoryController.class)
@Import(SecurityConfiguration.class)
class AdminClientProjectDirectoryControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminClientProjectDirectoryQueryService queries;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    @DisplayName("客户项目目录必须认证且不暴露租户和内部区域编码")
    void requiresAuthenticationAndReturnsProductProjection() throws Exception {
        mvc.perform(get("/api/v1/admin/client-project-directory"))
                .andExpect(status().isUnauthorized());

        Instant now = Instant.parse("2026-07-22T08:00:00Z");
        CurrentPrincipal principal = new CurrentPrincipal(
                "admin", "tenant", CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(queries.load(principal, "corr-client-project")).thenReturn(new AdminClientProjectDirectoryView(
                List.of(new AdminClientProjectDirectoryView.ClientItem(
                        "BYD", "比亚迪汽车", "ACTIVE", List.of(
                                new AdminClientProjectDirectoryView.BrandItem(
                                        "OCEAN", "海洋网", "ACTIVE", 10)), 1)),
                List.of(new AdminClientProjectDirectoryView.ProjectItem(
                        UUID.randomUUID(), "BYD-SD-HOME", "比亚迪山东家充服务项目", "BYD", "比亚迪汽车",
                        LocalDate.parse("2026-01-01"), null, List.of("济南市"), 1, "ACTIVE",
                        1, 0, "家充勘测安装标准流程", "3", now, "PUBLISHED", true, null)),
                List.of("CREATE_CLIENT", "CREATE_BRAND"), now));

        mvc.perform(get("/api/v1/admin/client-project-directory")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-client-project"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clients[0].clientName").value("比亚迪汽车"))
                .andExpect(jsonPath("$.clients[0].brands[0].brandName").value("海洋网"))
                .andExpect(jsonPath("$.allowedActions[0]").value("CREATE_CLIENT"))
                .andExpect(jsonPath("$.projects[0].regionNames[0]").value("济南市"))
                .andExpect(jsonPath("$.projects[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.projects[0].regionCodes").doesNotExist());
    }
}
