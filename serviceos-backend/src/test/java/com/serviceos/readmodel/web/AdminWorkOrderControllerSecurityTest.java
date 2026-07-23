package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.AdminWorkOrderDirectoryView;
import com.serviceos.readmodel.api.AdminWorkOrderQueryService;
import com.serviceos.readmodel.api.AdminWorkbenchView;
import com.serviceos.workorder.api.WorkOrderQuery;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminWorkOrderController.class)
@Import(SecurityConfiguration.class)
class AdminWorkOrderControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminWorkOrderQueryService queries;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    void directoryRequiresAuthenticationAndDoesNotExposeTechnicalFallbacks() throws Exception {
        mvc.perform(get("/api/v1/admin/work-orders"))
                .andExpect(status().isUnauthorized());

        CurrentPrincipal principal = new CurrentPrincipal(
                "admin", "tenant", CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-22T08:00:00Z");
        AdminWorkOrderDirectoryView view = new AdminWorkOrderDirectoryView(
                List.of(new AdminWorkOrderDirectoryView.Item(
                        id, "BYD202607220001", "王*", "*******5678", UUID.randomUUID(),
                        "比亚迪山东家充项目", "比亚迪", "充电桩安装服务", "上门安装",
                        "济南智联服务中心", "李师傅", "RISK", "存在风险", "履约中",
                        now, true, null)),
                List.of(), new AdminWorkbenchView(1, 0, 0, 1, 0, 0, 0, 1, now), null, 1, now);
        when(principals.current()).thenReturn(principal);
        when(queries.list(eq(principal), eq("corr-admin-orders"), any(WorkOrderQuery.class)))
                .thenReturn(view);

        mvc.perform(get("/api/v1/admin/work-orders")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-admin-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].projectName").value("比亚迪山东家充项目"))
                .andExpect(jsonPath("$.items[0].stageName").value("上门安装"))
                .andExpect(jsonPath("$.items[0].currentStageCode").doesNotExist())
                .andExpect(jsonPath("$.items[0].projectId").isNotEmpty());
    }
}
