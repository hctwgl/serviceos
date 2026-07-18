package com.serviceos.forms.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.forms.api.FormSubmissionView;
import com.serviceos.forms.api.TaskFormDefinition;
import com.serviceos.forms.api.TechnicianFormService;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TechnicianFormController.class)
@Import(SecurityConfiguration.class)
class TechnicianFormControllerSecurityTest {
    private static final UUID PRINCIPAL = UUID.fromString("10000000-0000-4000-8000-000000000263");
    private static final UUID NETWORK = UUID.fromString("20000000-0000-4000-8000-000000000263");
    private static final UUID TASK = UUID.fromString("30000000-0000-4000-8000-000000000263");
    private static final UUID FORM = UUID.fromString("40000000-0000-4000-8000-000000000263");
    private static final String CONTEXT = "TECHNICIAN|NETWORK|" + NETWORK;

    @Autowired MockMvc mvc;
    @MockitoBean TechnicianFormService forms;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousIsRejectedAndFrozenDefinitionIsReturned() throws Exception {
        mvc.perform(get("/api/v1/technician/me/tasks/{taskId}/forms", TASK))
                .andExpect(status().isUnauthorized());

        when(principals.current()).thenReturn(principal());
        when(forms.listForTask(principal(), "corr-263", CONTEXT, TASK)).thenReturn(List.of(
                new TaskFormDefinition(TASK, FORM, "survey", "1.0.0", "FORM_V1",
                        "{\"formKey\":\"survey\",\"version\":\"1.0.0\",\"sections\":[]}", "digest")));
        mvc.perform(get("/api/v1/technician/me/tasks/{taskId}/forms", TASK)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-263")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("X-Correlation-Id", "corr-263"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].formVersionId").value(FORM.toString()))
                .andExpect(jsonPath("$[0].definition.formKey").value("survey"));
    }

    @Test
    void onlineSubmitDoesNotAcceptPrefillOrTrustedIdentityFields() throws Exception {
        when(principals.current()).thenReturn(principal());
        when(forms.submit(eq(principal()), any(), eq(CONTEXT), any())).thenReturn(
                new FormSubmissionView(
                        UUID.randomUUID(), TASK, UUID.randomUUID(), FORM, "survey", 1,
                        "{\"result\":\"PASS\"}", "digest", "VALIDATED",
                        List.of(), List.of(), null, PRINCIPAL.toString(), Instant.parse("2026-07-18T12:00:00Z")));

        mvc.perform(post("/api/v1/technician/me/tasks/{taskId}/form-submissions", TASK)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-263")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("Idempotency-Key", "form-command-263")
                        .contentType("application/json")
                        .content("{\"formVersionId\":\"" + FORM + "\",\"values\":{\"result\":\"PASS\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validationStatus").value("VALIDATED"))
                .andExpect(jsonPath("$.submittedBy").doesNotExist())
                .andExpect(jsonPath("$.prefillVersion").doesNotExist());

        verify(forms).submit(eq(principal()),
                argThat(metadata -> metadata.idempotencyKey().equals("form-command-263")),
                eq(CONTEXT), argThat(command -> command.taskId().equals(TASK)
                        && command.prefillVersion() == null));
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(PRINCIPAL.toString(), "tenant-263",
                CurrentPrincipal.PrincipalType.USER, "technician-form-web", Set.of());
    }
}
