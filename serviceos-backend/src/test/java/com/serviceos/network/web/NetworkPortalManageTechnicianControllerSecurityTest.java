package com.serviceos.network.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.network.api.NetworkPortalManageTechnicianService;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M204：network-portal technician-memberships 未认证 401；缺能力 403；成功 200。 */
@WebMvcTest(NetworkPortalManageTechnicianController.class)
@Import(SecurityConfiguration.class)
class NetworkPortalManageTechnicianControllerSecurityTest {
    private static final UUID NETWORK_ID = UUID.fromString("019f84c0-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID PROFILE_ID = UUID.fromString("019f84c0-5555-7f8c-9505-36fe5c0e8806");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("019f84c0-7777-7f8c-9505-36fe5c0e8801");
    private static final Instant VALID_FROM = Instant.parse("2026-07-17T00:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean NetworkPortalManageTechnicianService manageTechnicians;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(post("/api/v1/network-portal/technician-memberships")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m204-1")
                        .contentType("application/json")
                        .content("""
                                {"technicianProfileId":"%s","validFrom":"2026-07-17T00:00:00Z"}
                                """.formatted(PROFILE_ID)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCapabilityReturnsAccessDenied() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(manageTechnicians.createMembership(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(PROFILE_ID), eq(VALID_FROM)))
                .thenThrow(new BusinessProblem(ProblemCode.ACCESS_DENIED, "The action is not allowed"));

        mvc.perform(post("/api/v1/network-portal/technician-memberships")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-deny")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m204-deny")
                        .contentType("application/json")
                        .content("""
                                {"technicianProfileId":"%s","validFrom":"2026-07-17T00:00:00Z"}
                                """.formatted(PROFILE_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void authenticatedCreateReturnsMembership() throws Exception {
        CurrentPrincipal actor = actor();
        NetworkTechnicianMembershipView view = new NetworkTechnicianMembershipView(
                MEMBERSHIP_ID, NETWORK_ID, PROFILE_ID, "ACTIVE",
                VALID_FROM, null, actor.principalId(), VALID_FROM, null, null, null, 1L);
        when(principals.current()).thenReturn(actor);
        when(manageTechnicians.createMembership(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(PROFILE_ID), eq(VALID_FROM)))
                .thenReturn(view);

        mvc.perform(post("/api/v1/network-portal/technician-memberships")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-ok")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m204-ok")
                        .contentType("application/json")
                        .content("""
                                {"technicianProfileId":"%s","validFrom":"2026-07-17T00:00:00Z"}
                                """.formatted(PROFILE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceNetworkId").value(NETWORK_ID.toString()))
                .andExpect(jsonPath("$.technicianProfileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void authenticatedTerminateReturnsMembership() throws Exception {
        CurrentPrincipal actor = actor();
        NetworkTechnicianMembershipView view = new NetworkTechnicianMembershipView(
                MEMBERSHIP_ID, NETWORK_ID, PROFILE_ID, "TERMINATED",
                VALID_FROM, Instant.parse("2026-07-17T01:00:00Z"), actor.principalId(), VALID_FROM,
                actor.principalId(), Instant.parse("2026-07-17T01:00:00Z"), "调整", 2L);
        when(principals.current()).thenReturn(actor);
        when(manageTechnicians.terminateMembership(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID),
                eq(MEMBERSHIP_ID), eq(1L), eq("调整")))
                .thenReturn(view);

        mvc.perform(post("/api/v1/network-portal/technician-memberships/{id}:terminate", MEMBERSHIP_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-term")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m204-term")
                        .header("If-Match", "\"1\"")
                        .contentType("application/json")
                        .content("""
                                {"reason":"调整"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"));
    }

    @Test
    void authenticatedSubmitQualificationSucceeds() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(manageTechnicians.submitQualification(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID),
                eq(PROFILE_ID), eq("EV-INSTALL"), eq(VALID_FROM), isNull()))
                .thenReturn(new com.serviceos.network.api.TechnicianQualificationView(
                        UUID.fromString("019f84c0-8888-7f8c-9505-36fe5c0e8801"),
                        PROFILE_ID, "EV-INSTALL", "PENDING", VALID_FROM, null,
                        actor.principalId(), VALID_FROM, null, null, null, 1L));

        mvc.perform(post("/api/v1/network-portal/technician-qualifications")
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-qual")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m204-qual")
                        .contentType("application/json")
                        .content("""
                                {"technicianProfileId":"%s","qualificationCode":"EV-INSTALL","validFrom":"2026-07-17T00:00:00Z"}
                                """.formatted(PROFILE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.qualificationCode").value("EV-INSTALL"));
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(
                "019f84c0-1111-7f8c-9505-36fe5c0e8801",
                "tenant-a",
                CurrentPrincipal.PrincipalType.USER,
                "network-portal",
                Set.of());
    }
}
