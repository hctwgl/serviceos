package com.serviceos.integration.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.integration.api.InboundEnvelopeQueueItem;
import com.serviceos.integration.api.InboundEnvelopeQueuePage;
import com.serviceos.integration.api.InboundEnvelopeQueueQuery;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.api.InboundMessageQueryService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InboundMessageController.class)
@Import(SecurityConfiguration.class)
class InboundMessageControllerSecurityTest {
    private static final UUID ENVELOPE = UUID.fromString("56000000-0000-4000-8000-000000000001");
    private static final UUID PROJECT = UUID.fromString("56000000-0000-4000-8000-000000000002");

    @Autowired MockMvc mvc;
    @MockitoBean InboundMessageQueryService messages;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousInboundSummaryIsRejected() throws Exception {
        mvc.perform(get("/api/v1/inbound-envelopes/{id}", ENVELOPE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousInboundQueueIsRejected() throws Exception {
        mvc.perform(get("/api/v1/inbound-envelopes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedQueueUsesTrustedPrincipalAndOmitsDigests() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "integration-operator", "tenant-int", CurrentPrincipal.PrincipalType.USER,
                "ops-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(messages.list(eq(principal), anyString(), any(InboundEnvelopeQueueQuery.class)))
                .thenReturn(new InboundEnvelopeQueuePage(
                        List.of(new InboundEnvelopeQueueItem(
                                ENVELOPE, PROJECT, "byd-cpim-v7.3.1", "CREATE_WORK_ORDER",
                                "nonce-1", "VALID", "RECEIVED", null, null, null, null, null,
                                Instant.parse("2026-07-15T07:00:00Z"), null, "corr-queue")),
                        null,
                        Instant.parse("2026-07-15T07:00:02Z")));

        mvc.perform(get("/api/v1/inbound-envelopes")
                        .param("projectId", PROJECT.toString())
                        .with(jwt().jwt(token -> token.subject("integration-operator")
                                .claim("tenant_id", "tenant-int"))))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.items[0].inboundEnvelopeId").value(ENVELOPE.toString()))
                .andExpect(jsonPath("$.items[0].rawPayloadDigest").doesNotExist())
                .andExpect(jsonPath("$.items[0].rawPayloadObjectRef").doesNotExist());

        verify(messages).list(eq(principal), anyString(), any(InboundEnvelopeQueueQuery.class));
    }

    @Test
    void authenticatedRequestUsesTrustedPrincipalAndReturnsNoRawObjectReference() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "integration-operator", "tenant-int", CurrentPrincipal.PrincipalType.USER,
                "ops-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(messages.getEnvelope(eq(principal), anyString(), eq(ENVELOPE)))
                .thenReturn(new InboundEnvelopeView(
                        ENVELOPE, PROJECT, "byd-cpim-v7.3.1", "CREATE_WORK_ORDER", "nonce-1",
                        "a".repeat(64), "b".repeat(64), "VALID", "COMPLETED",
                        "map-v1", UUID.randomUUID(), "ACCEPTED", "WORK_ORDER",
                        UUID.randomUUID().toString(), Instant.parse("2026-07-15T07:00:00Z"),
                        Instant.parse("2026-07-15T07:00:01Z"), "corr-1"));

        mvc.perform(get("/api/v1/inbound-envelopes/{id}", ENVELOPE)
                        .with(jwt().jwt(token -> token.subject("integration-operator")
                                .claim("tenant_id", "tenant-int"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inboundEnvelopeId").value(ENVELOPE.toString()))
                .andExpect(jsonPath("$.rawPayloadDigest").value("a".repeat(64)))
                .andExpect(jsonPath("$.rawPayloadObjectRef").doesNotExist());

        verify(messages).getEnvelope(eq(principal), anyString(), eq(ENVELOPE));
    }
}
