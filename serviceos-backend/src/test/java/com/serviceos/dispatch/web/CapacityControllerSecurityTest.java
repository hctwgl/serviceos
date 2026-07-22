package com.serviceos.dispatch.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.dispatch.api.CapacityAuthorityService;
import com.serviceos.dispatch.api.CapacityCounterReceipt;
import com.serviceos.dispatch.api.ResponsibilityLevel;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 产品场景容量只能通过正式派单用例配置，HTTP 边界只信任 JWT 派生主体。 */
@WebMvcTest(CapacityController.class)
@Import(SecurityConfiguration.class)
class CapacityControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean CapacityAuthorityService capacities;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedCapacityConfigurationIsRejected() throws Exception {
        mvc.perform(post("/api/v1/dispatch/capacities")
                        .header("Idempotency-Key", "idem-capacity")
                        .contentType("application/json")
                        .content(requestBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCapacityConfigurationReturnsVersionAndForwardsCommand() throws Exception {
        CurrentPrincipal actor = new CurrentPrincipal(
                "dispatch-admin", "tenant-a", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("dispatch.capacity.manage"));
        when(principals.current()).thenReturn(actor);
        when(capacities.configure(eq(actor), any(), any())).thenReturn(new CapacityCounterReceipt(
                UUID.fromString("45300000-0000-4000-8000-000000000080"),
                ResponsibilityLevel.NETWORK, "network-a", "HOME_CHARGING_SURVEY_INSTALL",
                30, 0, 1, Instant.parse("2026-07-22T08:00:00Z")));

        mvc.perform(post("/api/v1/dispatch/capacities")
                        .with(jwt().jwt(token -> token.subject("dispatch-admin").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-capacity")
                        .header("Idempotency-Key", "idem-capacity")
                        .contentType("application/json")
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(header().string("X-Correlation-Id", "corr-capacity"));

        verify(capacities).configure(eq(actor), any(), any());
    }

    private static String requestBody() {
        return """
                {"responsibilityLevel":"NETWORK","assigneeId":"network-a",
                 "businessType":"HOME_CHARGING_SURVEY_INSTALL","maxUnits":30,"expectedVersion":0}
                """;
    }
}
