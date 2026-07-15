package com.serviceos.integration.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.integration.api.OutboundDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OutboundDeliveryController.class)
@Import(SecurityConfiguration.class)
class OutboundDeliveryControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean OutboundDeliveryService deliveries;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousCreateAndQueryAreRejected() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/internal/integration/byd/review-submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-submit")
                        .content("{\"sourceReviewCaseId\":\"" + id + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/outbound-deliveries/{id}", id))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/outbound-deliveries/{id}:retry", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-retry")
                        .content("""
                                {"expectedAggregateVersion":2,"reason":"人工重发",
                                 "approvalRef":"approval://ops/1"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedInvalidCreatePayloadIsRejectedBeforeServiceInvocation() throws Exception {
        mvc.perform(post("/api/v1/internal/integration/byd/review-submissions")
                        .with(jwt().jwt(token -> token.subject("service-adapter")
                                .claim("tenant_id", "tenant-1")
                                .claim("principal_type", "service")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-submit")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticatedRetryRequiresReasonApprovalAndPositiveVersion() throws Exception {
        mvc.perform(post("/api/v1/outbound-deliveries/{id}:retry", UUID.randomUUID())
                        .with(jwt().jwt(token -> token.subject("ops-user")
                                .claim("tenant_id", "tenant-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-retry-invalid")
                        .content("""
                                {"expectedAggregateVersion":0,"reason":" ","approvalRef":" "}
                                """))
                .andExpect(status().isBadRequest());
    }
}
