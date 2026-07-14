package com.serviceos.forms.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.forms.api.FormSubmissionService;
import com.serviceos.forms.api.FormSubmissionView;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FormSubmissionController.class)
@Import(SecurityConfiguration.class)
class FormSubmissionControllerSecurityTest {
    private static final UUID TASK = UUID.fromString("36000000-0000-4000-8000-000000000010");
    private static final UUID FORM = UUID.fromString("36000000-0000-4000-8000-000000000011");
    private static final UUID SUBMISSION = UUID.fromString("36000000-0000-4000-8000-000000000012");
    private static final UUID PROJECT = UUID.fromString("36000000-0000-4000-8000-000000000013");

    @Autowired MockMvc mvc;
    @MockitoBean FormSubmissionService submissions;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousSubmissionIsRejected() throws Exception {
        mvc.perform(post("/api/v1/tasks/{taskId}/form-submissions", TASK)
                        .header("Idempotency-Key", "form-submit-http")
                        .contentType("application/json")
                        .content("{\"formVersionId\":\"" + FORM + "\",\"values\":{}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trustedPrincipalSubmitsJsonObjectAndReceivesCreated() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "technician-036", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "mobile-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(submissions.submit(eq(principal), any(), any())).thenReturn(new FormSubmissionView(
                SUBMISSION, TASK, PROJECT, FORM, "survey.execution", 1,
                "{\"survey.conclusion\":\"PASS\"}", "a".repeat(64), "VALIDATED",
                List.of(), List.of(), null, "technician-036", Instant.parse("2026-07-14T07:00:00Z")));

        mvc.perform(post("/api/v1/tasks/{taskId}/form-submissions", TASK)
                        .with(jwt().jwt(token -> token.subject("technician-036")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "form-submit-http")
                        .header("X-Tenant-Id", "spoofed-tenant")
                        .contentType("application/json")
                        .content("{\"formVersionId\":\"" + FORM
                                + "\",\"values\":{\"survey.conclusion\":\"PASS\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.submissionId").value(SUBMISSION.toString()))
                .andExpect(jsonPath("$.values['survey.conclusion']").value("PASS"))
                .andExpect(jsonPath("$.validationStatus").value("VALIDATED"));

        verify(submissions).submit(eq(principal), any(), any());
    }
}
