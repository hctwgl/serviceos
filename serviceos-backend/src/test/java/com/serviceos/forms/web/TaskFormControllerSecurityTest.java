package com.serviceos.forms.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.forms.api.TaskFormDefinition;
import com.serviceos.forms.api.TaskFormQueryService;
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

@WebMvcTest(TaskFormController.class)
@Import(SecurityConfiguration.class)
class TaskFormControllerSecurityTest {
    private static final UUID TASK = UUID.fromString("33000000-0000-4000-8000-000000000035");
    private static final UUID FORM_VERSION = UUID.fromString("35000000-0000-4000-8000-000000000035");

    @Autowired MockMvc mvc;
    @MockitoBean TaskFormQueryService forms;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousTaskFormReadIsRejected() throws Exception {
        mvc.perform(get("/api/v1/tasks/{taskId}/forms", TASK))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trustedPrincipalReachesAuthorizedQueryAndDefinitionRemainsJson() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "technician-035", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "mobile-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(forms.listForTask(eq(principal), anyString(), eq(TASK))).thenReturn(List.of(
                new TaskFormDefinition(
                        TASK, FORM_VERSION, "intake.form", "1.0.0", "1.0.0",
                        "{\"sections\":[]}", "a".repeat(64))));

        mvc.perform(get("/api/v1/tasks/{taskId}/forms", TASK)
                        .with(jwt().jwt(token -> token.subject("technician-035")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("X-Tenant-Id", "spoofed-tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].formVersionId").value(FORM_VERSION.toString()))
                .andExpect(jsonPath("$[0].definition.sections").isArray());

        verify(forms).listForTask(eq(principal), anyString(), eq(TASK));
    }

    @Test
    void unresolvedFrozenFormReturnsStableConflictProblem() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "technician-035", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "mobile-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(forms.listForTask(eq(principal), anyString(), eq(TASK))).thenThrow(
                new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                        "Task formRef must resolve to exactly one FormVersion in the frozen bundle"));

        mvc.perform(get("/api/v1/tasks/{taskId}/forms", TASK)
                        .with(jwt().jwt(token -> token.subject("technician-035")
                                .claim("tenant_id", "tenant-trusted"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TASK_STATE_CONFLICT"));
    }
}
