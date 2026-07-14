package com.serviceos.evidence.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewCaseController.class)
@Import(SecurityConfiguration.class)
class ReviewCaseControllerSecurityTest {
    private static final UUID SNAPSHOT = UUID.fromString("44000000-0000-4000-8000-000000000044");
    private static final UUID CASE_ID = UUID.fromString("44000000-0000-4000-8000-000000000045");
    private static final UUID PROJECT = UUID.fromString("44000000-0000-4000-8000-000000000046");
    private static final UUID TASK = UUID.fromString("44000000-0000-4000-8000-000000000047");

    @Autowired MockMvc mvc;
    @MockitoBean ReviewCaseService reviews;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousCreateIsRejected() throws Exception {
        mvc.perform(post("/api/v1/review-cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-1")
                        .content("{\"evidenceSetSnapshotId\":\"%s\"}".formatted(SNAPSHOT)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trustedPrincipalCanCreateWithoutClientTenantAuthority() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "reviewer-1", "tenant-review", CurrentPrincipal.PrincipalType.USER,
                "ops-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(reviews.create(eq(principal), any(), any())).thenReturn(new ReviewCaseView(
                CASE_ID, PROJECT, TASK, SNAPSHOT, "a".repeat(64),
                "EVIDENCE_SET_SNAPSHOT", "REVIEW_POLICY_V1", "OPEN", "reviewer-1",
                Instant.parse("2026-07-14T14:00:00Z"), null, List.of()));

        mvc.perform(post("/api/v1/review-cases")
                        .with(jwt().jwt(token -> token.subject("reviewer-1")
                                .claim("tenant_id", "tenant-review")))
                        .header("Idempotency-Key", "idem-create")
                        .header("X-Tenant-Id", "spoofed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evidenceSetSnapshotId\":\"%s\"}".formatted(SNAPSHOT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewCaseId").value(CASE_ID.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(reviews).create(eq(principal), any(), any());
    }
}
