package com.serviceos.dispatch.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.dispatch.api.NetworkAssignmentCandidateQuery;
import com.serviceos.dispatch.api.NetworkAssignmentCandidateView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M453：候选查询 HTTP 只信任 JWT 主体，项目范围由应用服务通过 Task 事实解析。 */
@WebMvcTest(NetworkAssignmentCandidateController.class)
@Import(SecurityConfiguration.class)
class NetworkAssignmentCandidateControllerSecurityTest {
    private static final UUID TASK_ID = UUID.fromString("45300000-0000-4000-8000-000000000001");
    private static final UUID WORK_ORDER_ID = UUID.fromString("45300000-0000-4000-8000-000000000002");
    private static final UUID NETWORK_ID = UUID.fromString("45300000-0000-4000-8000-000000000003");

    @Autowired MockMvc mvc;
    @MockitoBean NetworkAssignmentCandidateQuery candidates;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/tasks/{taskId}/network-assignment-candidates", TASK_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestReturnsProductCandidateWithoutTechnicalFallback() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "admin-reader",
                "tenant-m453",
                CurrentPrincipal.PrincipalType.USER,
                "admin-web",
                Set.of("dispatch.read"));
        when(principals.current()).thenReturn(principal);
        when(candidates.findCandidates(eq(principal), eq("corr-m453"), eq(TASK_ID)))
                .thenReturn(new NetworkAssignmentCandidateView(
                        TASK_ID,
                        WORK_ORDER_ID,
                        "HOME_CHARGING_SURVEY_INSTALL",
                        Instant.parse("2026-07-22T03:00:00Z"),
                        "候选已通过硬规则校验，并按项目当前派单策略排序",
                        null,
                        List.of(new NetworkAssignmentCandidateView.Candidate(
                                NETWORK_ID,
                                "浦东服务中心",
                                1,
                                "覆盖工单所在区县",
                                8,
                                "符合项目、服务区域、业务类型和容量要求，当前推荐顺序第 1 位"))));

        mvc.perform(get("/api/v1/tasks/{taskId}/network-assignment-candidates", TASK_ID)
                        .with(jwt().jwt(token -> token.subject("admin-reader")
                                .claim("tenant_id", "tenant-m453")))
                        .header("X-Correlation-Id", "corr-m453"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "corr-m453"))
                .andExpect(jsonPath("$.businessType").value("HOME_CHARGING_SURVEY_INSTALL"))
                .andExpect(jsonPath("$.candidates[0].networkName").value("浦东服务中心"))
                .andExpect(jsonPath("$.candidates[0].remainingCapacity").value(8));
    }
}
