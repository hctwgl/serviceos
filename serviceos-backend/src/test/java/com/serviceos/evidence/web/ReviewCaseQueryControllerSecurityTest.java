package com.serviceos.evidence.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.ReviewCaseQueryService;
import com.serviceos.evidence.api.ReviewCaseQueueItem;
import com.serviceos.evidence.api.ReviewCaseQueuePage;
import com.serviceos.evidence.api.ReviewCaseQueueQuery;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewCaseQueryController.class)
@Import(SecurityConfiguration.class)
class ReviewCaseQueryControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReviewCaseQueryService reviews;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    void authenticationAndSafeQueueContractAreEnforced() throws Exception {
        mvc.perform(get("/api/v1/review-cases"))
                .andExpect(status().isUnauthorized());

        UUID projectId = UUID.randomUUID();
        UUID reviewCaseId = UUID.randomUUID();
        CurrentPrincipal principal = new CurrentPrincipal(
                "reviewer", "tenant", CurrentPrincipal.PrincipalType.USER, "m97", Set.of());
        Instant now = Instant.parse("2026-07-16T17:00:00Z");
        ReviewCaseQueuePage page = new ReviewCaseQueuePage(
                List.of(new ReviewCaseQueueItem(
                        reviewCaseId, projectId, UUID.randomUUID(), UUID.randomUUID(),
                        "EVIDENCE_SET_SNAPSHOT", "INTERNAL", "POLICY_V1", "OPEN",
                        now, null, null, null, null, null, null, null,
                        null, null, null, List.of(), null)),
                null,
                now.plusSeconds(1));
        ReviewCaseQueueQuery query =
                new ReviewCaseQueueQuery(projectId, null, "INTERNAL", null, null, 50);
        when(principals.current()).thenReturn(principal);
        when(reviews.list(principal, "corr-m97", query)).thenReturn(page);

        mvc.perform(get("/api/v1/review-cases")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-m97")
                        .queryParam("projectId", projectId.toString())
                        .queryParam("origin", "INTERNAL"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "corr-m97"))
                .andExpect(jsonPath("$.items[0].reviewCaseId").value(reviewCaseId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].snapshotContentDigest").doesNotExist())
                .andExpect(jsonPath("$.items[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$.items[0].note").doesNotExist())
                .andExpect(jsonPath("$.items[0].approvalRef").doesNotExist())
                .andExpect(jsonPath("$.items[0].decidedBy").doesNotExist());
        verify(reviews).list(principal, "corr-m97", query);
    }
}
