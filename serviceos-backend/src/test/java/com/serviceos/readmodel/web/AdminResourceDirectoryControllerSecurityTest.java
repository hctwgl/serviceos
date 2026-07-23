package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.AdminResourceDirectoryPage;
import com.serviceos.readmodel.api.AdminResourceDirectoryQueryService;
import com.serviceos.readmodel.api.AdminPartnerOrganizationDirectoryItem;
import com.serviceos.readmodel.api.AdminServiceNetworkDirectoryItem;
import com.serviceos.readmodel.api.AdminTechnicianDirectoryItem;
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

@WebMvcTest(AdminResourceDirectoryController.class)
@Import(SecurityConfiguration.class)
class AdminResourceDirectoryControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminResourceDirectoryQueryService queries;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    @DisplayName("资源目录必须认证且只返回页面展示数据")
    void requiresAuthenticationAndReturnsPageProjection() throws Exception {
        mvc.perform(get("/api/v1/admin/resource-directory"))
                .andExpect(status().isUnauthorized());

        Instant now = Instant.parse("2026-07-22T08:00:00Z");
        CurrentPrincipal principal = new CurrentPrincipal(
                "admin", "tenant", CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(queries.load(principal, "corr-resource-directory")).thenReturn(new AdminResourceDirectoryPage(
                List.of(new AdminPartnerOrganizationDirectoryItem(
                        UUID.randomUUID(), "PARTNER-JN", "济南恒通新能源服务有限公司", "ACTIVE")),
                List.of(new AdminServiceNetworkDirectoryItem(
                        UUID.randomUUID(), "NET-JN-LX", "济南历下服务中心",
                        "济南恒通新能源服务有限公司", "ACTIVE", List.of("370100"), 1, now)),
                List.of(new AdminTechnicianDirectoryItem(
                        UUID.randomUUID(), "李师傅", "ACTIVE", List.of("TECHNICIAN_IOS"),
                        List.of("济南历下服务中心"), List.of("HOME_CHARGING_INSTALLATION"), 0, now)),
                List.of("CREATE_PARTNER", "CREATE_NETWORK"),
                now));

        mvc.perform(get("/api/v1/admin/resource-directory")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-resource-directory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partners[0].partnerName").value("济南恒通新能源服务有限公司"))
                .andExpect(jsonPath("$.networks[0].networkName").value("济南历下服务中心"))
                .andExpect(jsonPath("$.technicians[0].displayName").value("李师傅"))
                .andExpect(jsonPath("$.technicians[0].principalId").doesNotExist())
                .andExpect(jsonPath("$.networks[0].partnerOrganizationId").doesNotExist());
    }
}
