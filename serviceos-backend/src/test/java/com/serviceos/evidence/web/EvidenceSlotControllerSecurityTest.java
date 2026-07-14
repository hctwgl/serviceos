package com.serviceos.evidence.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.EvidenceSlotQueryService;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
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

@WebMvcTest(EvidenceSlotController.class)
@Import(SecurityConfiguration.class)
class EvidenceSlotControllerSecurityTest {
    private static final UUID TASK = UUID.fromString("37000000-0000-4000-8000-000000000037");

    @Autowired MockMvc mvc;
    @MockitoBean EvidenceSlotQueryService slots;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousEvidenceSlotReadIsRejected() throws Exception {
        mvc.perform(get("/api/v1/tasks/{taskId}/evidence-slots", TASK))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trustedPrincipalReceivesFrozenJsonConstraints() throws Exception {
        CurrentPrincipal principal = principal();
        when(principals.current()).thenReturn(principal);
        when(slots.listForTask(eq(principal), anyString(), eq(TASK))).thenReturn(List.of(slot()));

        mvc.perform(get("/api/v1/tasks/{taskId}/evidence-slots", TASK)
                        .with(jwt().jwt(token -> token.subject("evidence-reader")
                                .claim("tenant_id", "tenant-evidence")))
                        .header("X-Tenant-Id", "spoofed-tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requirementCode").value("site.photo"))
                .andExpect(jsonPath("$[0].resolutionExplanation.kind").value("FIXED"))
                .andExpect(jsonPath("$[0].requirement.capture.requireGps").value(true));

        verify(slots).listForTask(eq(principal), anyString(), eq(TASK));
    }

    @Test
    void unresolvedSlotsReturnStableConflictProblem() throws Exception {
        CurrentPrincipal principal = principal();
        when(principals.current()).thenReturn(principal);
        when(slots.listForTask(eq(principal), anyString(), eq(TASK))).thenThrow(
                new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                        "Task evidence slots have not completed reliable resolution"));

        mvc.perform(get("/api/v1/tasks/{taskId}/evidence-slots", TASK)
                        .with(jwt().jwt(token -> token.subject("evidence-reader")
                                .claim("tenant_id", "tenant-evidence"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TASK_STATE_CONFLICT"));
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(
                "evidence-reader", "tenant-evidence", CurrentPrincipal.PrincipalType.USER,
                "mobile", Set.of());
    }

    private static EvidenceSlotView slot() {
        return new EvidenceSlotView(
                UUID.randomUUID(), UUID.randomUUID(), TASK, UUID.randomUUID(), UUID.randomUUID(),
                "survey.site", "1.0.0", "a".repeat(64), "site.photo", "default",
                "现场照片", "PHOTO", true, 1, 3, "b".repeat(64),
                "{\"kind\":\"FIXED\",\"required\":true}",
                "{\"evidenceKey\":\"site.photo\",\"capture\":{\"requireGps\":true}}",
                "c".repeat(64), "MISSING", Instant.parse("2026-07-14T08:00:00Z"));
    }
}
