package com.serviceos.dispatch.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.dispatch.api.ManualServiceAssignmentReceipt;
import com.serviceos.dispatch.api.NetworkPortalAssignTechnicianService;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M196：network-portal assign-technician 未认证 401；缺能力 403。 */
@WebMvcTest(NetworkPortalAssignTechnicianController.class)
@Import(SecurityConfiguration.class)
class NetworkPortalAssignTechnicianControllerSecurityTest {
    private static final UUID TASK_ID = UUID.fromString("019f83b0-9999-7f8c-9505-36fe5c0e880b");
    private static final UUID NETWORK_ID = UUID.fromString("019f83b0-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID WORK_ORDER_ID = UUID.fromString("019f83b0-7777-7f8c-9505-36fe5c0e880a");

    @Autowired MockMvc mvc;
    @MockitoBean NetworkPortalAssignTechnicianService assignTechnicians;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(post("/api/v1/network-portal/tasks/{taskId}:assign-technician", TASK_ID)
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m196-1")
                        .contentType("application/json")
                        .content("""
                                {"technicianAssigneeId":"tech-1","businessType":"INSTALLATION"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCapabilityReturnsAccessDenied() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(assignTechnicians.assignTechnician(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(TASK_ID),
                eq("tech-1"), eq("INSTALLATION")))
                .thenThrow(new BusinessProblem(ProblemCode.ACCESS_DENIED, "The action is not allowed"));

        mvc.perform(post("/api/v1/network-portal/tasks/{taskId}:assign-technician", TASK_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-deny")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m196-deny")
                        .contentType("application/json")
                        .content("""
                                {"technicianAssigneeId":"tech-1","businessType":"INSTALLATION"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void authenticatedAssignReturnsReceipt() throws Exception {
        CurrentPrincipal actor = actor();
        ManualServiceAssignmentReceipt receipt = new ManualServiceAssignmentReceipt(
                TASK_ID, WORK_ORDER_ID,
                UUID.fromString("61111111-1111-4111-8111-111111111111"),
                UUID.fromString("62222222-2222-4222-8222-222222222222"),
                NETWORK_ID.toString(), "tech-1", Instant.parse("2026-07-17T02:00:00Z"));
        when(principals.current()).thenReturn(actor);
        when(assignTechnicians.assignTechnician(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(TASK_ID),
                eq("tech-1"), eq("INSTALLATION")))
                .thenReturn(receipt);

        mvc.perform(post("/api/v1/network-portal/tasks/{taskId}:assign-technician", TASK_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-ok")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m196-ok")
                        .contentType("application/json")
                        .content("""
                                {"technicianAssigneeId":"tech-1","businessType":"INSTALLATION"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networkAssigneeId").value(NETWORK_ID.toString()))
                .andExpect(jsonPath("$.technicianAssigneeId").value("tech-1"));
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(
                "019f83b0-1111-7f8c-9505-36fe5c0e8801",
                "tenant-a",
                CurrentPrincipal.PrincipalType.USER,
                "network-portal",
                Set.of());
    }
}
