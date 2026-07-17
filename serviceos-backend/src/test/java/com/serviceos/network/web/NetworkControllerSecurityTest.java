package com.serviceos.network.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.network.api.NetworkCommandService;
import com.serviceos.network.api.NetworkQueryService;
import com.serviceos.network.api.PartnerOrganizationPage;
import com.serviceos.network.api.PartnerOrganizationView;
import com.serviceos.network.api.ServiceNetworkView;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M185：网点目录 HTTP 边界只信任 JWT 派生 CurrentPrincipal，并校验 If-Match。 */
@WebMvcTest(NetworkController.class)
@Import(SecurityConfiguration.class)
class NetworkControllerSecurityTest {
    private static final UUID PARTNER_ID = UUID.fromString("019f7022-17ea-7f8c-9505-36fe5c0e8844");
    private static final UUID NETWORK_ID = UUID.fromString("019f7022-17ea-7f8c-9505-36fe5c0e8845");

    @Autowired MockMvc mvc;
    @MockitoBean NetworkQueryService queries;
    @MockitoBean NetworkCommandService commands;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedNetworkRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/partner-organizations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedListPartnerOrganizationsIsAllowed() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(queries.listPartnerOrganizations(eq(actor), eq("corr-list")))
                .thenReturn(new PartnerOrganizationPage(List.of(), Instant.parse("2026-07-17T08:00:00Z")));

        mvc.perform(get("/api/v1/partner-organizations")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-list"))
                .andExpect(status().isOk());
    }

    @Test
    void deactivateNetworkRequiresQuotedVersionAndForwardsIdempotencyMetadata() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T08:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(commands.deactivateServiceNetwork(eq(actor), any(), eq(NETWORK_ID), eq(2L), eq("合同到期")))
                .thenReturn(new ServiceNetworkView(NETWORK_ID, PARTNER_ID, "NET-A", "Network A", "DEACTIVATED",
                        3, now, now, now, "network-admin", "合同到期"));

        mvc.perform(post("/api/v1/service-networks/{networkId}:deactivate", NETWORK_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-deactivate")
                        .header("Idempotency-Key", "idem-deactivate")
                        .header("If-Match", "\"2\"")
                        .contentType("application/json")
                        .content("{\"reason\":\"合同到期\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""));
        verify(commands).deactivateServiceNetwork(eq(actor), any(), eq(NETWORK_ID), eq(2L), eq("合同到期"));

        mvc.perform(post("/api/v1/service-networks/{networkId}:deactivate", NETWORK_ID)
                        .with(jwt()).header("Idempotency-Key", "idem-invalid")
                        .header("If-Match", "2").contentType("application/json")
                        .content("{\"reason\":\"合同到期\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPartnerOrganizationReturnsVersionEtag() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T08:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(commands.createPartnerOrganization(eq(actor), any(), eq("ACME"), eq("Acme Partner")))
                .thenReturn(new PartnerOrganizationView(PARTNER_ID, "ACME", "Acme Partner", "ACTIVE",
                        1, now, now));

        mvc.perform(post("/api/v1/partner-organizations")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-partner")
                        .header("Idempotency-Key", "idem-partner")
                        .contentType("application/json")
                        .content("{\"code\":\"ACME\",\"name\":\"Acme Partner\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal("network-admin", "tenant-a", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of());
    }
}
