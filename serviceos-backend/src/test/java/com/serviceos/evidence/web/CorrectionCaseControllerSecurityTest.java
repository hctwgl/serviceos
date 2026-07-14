package com.serviceos.evidence.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CorrectionCaseView;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CorrectionCaseController.class)
@Import(SecurityConfiguration.class)
class CorrectionCaseControllerSecurityTest {
    private static final UUID CASE_ID = UUID.fromString("45000000-0000-4000-8000-000000000045");

    @Autowired MockMvc mvc;
    @MockitoBean CorrectionCaseService corrections;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousGetIsRejected() throws Exception {
        mvc.perform(get("/api/v1/correction-cases/{id}", CASE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trustedPrincipalCanReadWithoutClientTenantAuthority() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "reviewer-1", "tenant-correction", CurrentPrincipal.PrincipalType.USER,
                "ops-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(corrections.get(eq(principal), anyString(), eq(CASE_ID))).thenReturn(new CorrectionCaseView(
                CASE_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "a".repeat(64), List.of("IMAGE.BLUR"), UUID.randomUUID(), "OPEN",
                "reviewer-1", Instant.parse("2026-07-14T15:00:00Z"), null, null, null, List.of()));

        mvc.perform(get("/api/v1/correction-cases/{id}", CASE_ID)
                        .with(jwt().jwt(token -> token.subject("reviewer-1")
                                .claim("tenant_id", "tenant-correction")))
                        .header("X-Tenant-Id", "spoofed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctionCaseId").value(CASE_ID.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(corrections).get(eq(principal), anyString(), eq(CASE_ID));
    }
}
