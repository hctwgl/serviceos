package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.AdminProjectWorkspaceQueryService;
import com.serviceos.readmodel.api.AdminProjectWorkspaceView;
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

@WebMvcTest(AdminProjectWorkspaceController.class)
@Import(SecurityConfiguration.class)
class AdminProjectWorkspaceControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminProjectWorkspaceQueryService queries;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    @DisplayName("项目工作区必须认证且不返回技术配置正文")
    void requiresAuthenticationAndDoesNotExposeConfigurationJson() throws Exception {
        UUID projectId = UUID.randomUUID();
        mvc.perform(get("/api/v1/admin/projects/{projectId}/workspace", projectId))
                .andExpect(status().isUnauthorized());

        Instant now = Instant.parse("2026-07-22T08:00:00Z");
        CurrentPrincipal actor = new CurrentPrincipal(
                "admin", "tenant", CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
        when(principals.current()).thenReturn(actor);
        when(queries.get(actor, "corr-project", projectId)).thenReturn(new AdminProjectWorkspaceView(
                projectId, "BYD-SD-HOME", "比亚迪山东家充项目", "比亚迪", "ACTIVE",
                LocalDate.parse("2026-01-01"), null, List.of("济南市"), List.of("济南历下服务中心"),
                true, List.of(new AdminProjectWorkspaceView.FulfillmentProfile(
                        UUID.randomUUID(), "家充勘测安装", "充电桩安装服务", "ACTIVE", 7, 3, 8,
                        "V1", now, "7 个阶段", "安装 48 小时", now, true, null)),
                8, false, true, null, now));

        mvc.perform(get("/api/v1/admin/projects/{projectId}/workspace", projectId)
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-project"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value("比亚迪山东家充项目"))
                .andExpect(jsonPath("$.fulfillmentProfiles[0].serviceProductName").value("充电桩安装服务"))
                .andExpect(jsonPath("$.fulfillmentProfiles[0].documentJson").doesNotExist())
                .andExpect(jsonPath("$.fulfillmentProfiles[0].manifestJson").doesNotExist());
    }
}
