package com.serviceos.evidence.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.EvidenceConditionDispositionService;
import com.serviceos.evidence.api.EvidenceConditionDispositionView;
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

@WebMvcTest(EvidenceConditionDispositionController.class)
@Import(SecurityConfiguration.class)
class EvidenceConditionDispositionControllerSecurityTest {
    private static final UUID TASK = UUID.fromString("53000000-0000-4000-8000-000000000001");
    private static final UUID SLOT = UUID.fromString("53000000-0000-4000-8000-000000000002");
    private static final UUID RESOLUTION = UUID.fromString("53000000-0000-4000-8000-000000000003");
    private static final UUID DISPOSITION = UUID.fromString("53000000-0000-4000-8000-000000000004");

    @Autowired MockMvc mvc;
    @MockitoBean EvidenceConditionDispositionService dispositions;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousDispositionIsRejected() throws Exception {
        mvc.perform(post("/api/v1/tasks/{taskId}/evidence-slots/{slotId}:resolve-condition-change", TASK, SLOT)
                        .header("Idempotency-Key", "m53-anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestUsesTrustedPrincipalAndMapsExactResolution() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "evidence-reviewer", "tenant-evidence", CurrentPrincipal.PrincipalType.USER,
                "web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(dispositions.resolve(eq(principal), any(), any())).thenReturn(
                new EvidenceConditionDispositionView(
                        DISPOSITION, TASK, SLOT, RESOLUTION, "KEEP", "CONDITION_CHANGED",
                        "review-53", "evidence-reviewer", Instant.parse("2026-07-15T08:00:00Z")));

        mvc.perform(post("/api/v1/tasks/{taskId}/evidence-slots/{slotId}:resolve-condition-change", TASK, SLOT)
                        .with(jwt().jwt(token -> token.subject("evidence-reviewer")
                                .claim("tenant_id", "tenant-evidence")))
                        .header("Idempotency-Key", "m53-keep")
                        .header("X-Tenant-Id", "spoofed-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispositionId").value(DISPOSITION.toString()))
                .andExpect(jsonPath("$.resolutionId").value(RESOLUTION.toString()))
                .andExpect(jsonPath("$.decision").value("KEEP"));

        verify(dispositions).resolve(eq(principal), any(), any());
    }

    private static String request() {
        return """
                {
                  "expectedResolutionId":"%s",
                  "decision":"KEEP",
                  "reasonCode":"CONDITION_CHANGED",
                  "reviewRef":"review-53"
                }
                """.formatted(RESOLUTION);
    }
}
