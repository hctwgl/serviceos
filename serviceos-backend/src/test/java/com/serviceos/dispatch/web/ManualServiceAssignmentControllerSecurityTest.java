package com.serviceos.dispatch.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.dispatch.api.ManualServiceAssignmentService;
import com.serviceos.dispatch.api.NetworkPortalAcceptAssignmentReceipt;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M453：平台派网点 HTTP 只信任 JWT 主体，且不再暴露平台同时派师傅的旧入口。 */
@WebMvcTest(ManualServiceAssignmentController.class)
@Import(SecurityConfiguration.class)
class ManualServiceAssignmentControllerSecurityTest {
    private static final UUID TASK_ID = UUID.fromString("68888888-8888-4888-8888-888888888888");
    private static final UUID WORK_ORDER_ID = UUID.fromString("67777777-7777-4777-8777-777777777777");

    @Autowired MockMvc mvc;
    @MockitoBean ManualServiceAssignmentService manualAssignments;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedManualAssignNetworkIsRejected() throws Exception {
        mvc.perform(post("/api/v1/tasks/{taskId}/service-assignments:manual-assign-network", TASK_ID)
                        .header("Idempotency-Key", "manual-net-unauthenticated")
                        .contentType("application/json")
                        .content("""
                                {"networkAssigneeId":"network-1","businessType":"INSTALLATION"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyAdminDualAssignmentEndpointDoesNotExist() throws Exception {
        mvc.perform(post("/api/v1/tasks/{taskId}/service-assignments:manual-assign", TASK_ID)
                        .with(jwt().jwt(token -> token.subject("dispatch-admin")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "legacy-dual-assignment")
                        .contentType("application/json")
                        .content("""
                                {"networkAssigneeId":"network-1",
                                 "technicianAssigneeId":"tech-1",
                                 "businessType":"INSTALLATION"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void trustedPrincipalReachesManualAssignNetworkBoundary() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "dispatch-admin", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("dispatch.assignment.manage", "dispatch.capacity.configure"));
        NetworkPortalAcceptAssignmentReceipt receipt = new NetworkPortalAcceptAssignmentReceipt(
                TASK_ID, WORK_ORDER_ID,
                UUID.fromString("61111111-1111-4111-8111-111111111111"),
                "network-1", Instant.parse("2026-07-17T02:00:00Z"));
        when(principals.current()).thenReturn(principal);
        when(manualAssignments.manualAssignNetwork(
                eq(principal),
                org.mockito.ArgumentMatchers.any(),
                eq(TASK_ID),
                eq("network-1"),
                eq("INSTALLATION")))
                .thenReturn(receipt);

        mvc.perform(post("/api/v1/tasks/{taskId}/service-assignments:manual-assign-network", TASK_ID)
                        .with(jwt().jwt(token -> token.subject("dispatch-admin")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "manual-net-1")
                        .header("X-Correlation-Id", "corr-manual-net-1")
                        .contentType("application/json")
                        .content("""
                                {"networkAssigneeId":"network-1","businessType":"INSTALLATION"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "corr-manual-net-1"))
                .andExpect(jsonPath("$.networkAssigneeId").value("network-1"))
                .andExpect(jsonPath("$.technicianAssigneeId").doesNotExist());

        verify(manualAssignments).manualAssignNetwork(
                eq(principal),
                argThat((CommandMetadata metadata) -> metadata.idempotencyKey().equals("manual-net-1")),
                eq(TASK_ID),
                eq("network-1"),
                eq("INSTALLATION"));
    }
}
