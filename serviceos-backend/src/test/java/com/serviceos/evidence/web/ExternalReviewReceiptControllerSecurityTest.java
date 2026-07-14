package com.serviceos.evidence.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.ExternalReviewReceiptService;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExternalReviewReceiptController.class)
@Import(SecurityConfiguration.class)
class ExternalReviewReceiptControllerSecurityTest {
    private static final UUID CASE_ID = UUID.fromString("49000000-0000-4000-8000-000000000049");

    @Autowired MockMvc mvc;
    @MockitoBean ExternalReviewReceiptService receipts;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousRecordIsRejected() throws Exception {
        mvc.perform(post("/api/v1/internal/external-review-receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-ext")
                        .content("""
                                {"reviewCaseId":"%s","inboundEnvelopeId":"ENV-1","canonicalMessageId":"CAN-1",
                                 "externalKey":"EXT-1","callbackBatchRef":"BATCH-1","mappingVersionId":"MAP-1",
                                 "result":"APPROVED"}
                                """.formatted(CASE_ID)))
                .andExpect(status().isUnauthorized());
    }
}
