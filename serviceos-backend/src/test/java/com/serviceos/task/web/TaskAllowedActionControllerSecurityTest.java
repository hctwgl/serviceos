package com.serviceos.task.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.task.api.TaskAllowedAction;
import com.serviceos.task.api.TaskAllowedActionQueryService;
import com.serviceos.task.api.TaskAllowedActions;
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

@WebMvcTest(TaskAllowedActionController.class)
@Import(SecurityConfiguration.class)
class TaskAllowedActionControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaskAllowedActionQueryService actions;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    void authenticationTrustedPrincipalAndVersionProtectActionProjection() throws Exception {
        UUID taskId = UUID.randomUUID();
        mvc.perform(get("/api/v1/tasks/{taskId}/allowed-actions", taskId))
                .andExpect(status().isUnauthorized());

        CurrentPrincipal principal = new CurrentPrincipal(
                "worker", "tenant", CurrentPrincipal.PrincipalType.USER, "m71", Set.of());
        TaskAllowedActions result = new TaskAllowedActions(
                7,
                List.of(new TaskAllowedAction(
                        "task.complete",
                        "完成任务",
                        "#/components/schemas/CompleteHumanTaskRequest",
                        List.of("REQUIRE_RESULT"))),
                Instant.parse("2026-07-16T00:00:00Z"));
        when(principals.current()).thenReturn(principal);
        when(actions.get(principal, "corr-m71", taskId)).thenReturn(result);

        mvc.perform(get("/api/v1/tasks/{taskId}/allowed-actions", taskId)
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-m71"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"7\""))
                .andExpect(header().string("X-Correlation-Id", "corr-m71"))
                .andExpect(jsonPath("$.resourceVersion").value(7))
                .andExpect(jsonPath("$.actions[0].code").value("task.complete"))
                .andExpect(jsonPath("$.actions[0].inputSchemaRef")
                        .value("#/components/schemas/CompleteHumanTaskRequest"))
                .andExpect(jsonPath("$.actions[0].obligations[0]").value("REQUIRE_RESULT"));
        verify(actions).get(principal, "corr-m71", taskId);
    }
}
