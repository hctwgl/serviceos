package com.serviceos.integration.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.integration.api.ExternalReviewRouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExternalReviewRouteController.class)
@Import(SecurityConfiguration.class)
class ExternalReviewRouteControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ExternalReviewRouteService routes;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousRouteRegistrationIsRejected() throws Exception {
        mvc.perform(post("/api/v1/internal/integration/byd/review-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-route")
                        .content("""
                                {"externalOrderCode":"ORDER-1",
                                 "reviewCaseId":"57000000-0000-4000-8000-000000000001",
                                 "externalSubmissionRef":"SUB-1","callbackBatchRef":"BATCH-1",
                                 "mappingVersionId":"MAP-1"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
