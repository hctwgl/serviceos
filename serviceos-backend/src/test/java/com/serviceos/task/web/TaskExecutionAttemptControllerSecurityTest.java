package com.serviceos.task.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.task.api.TaskExecutionAttemptPage;
import com.serviceos.task.api.TaskExecutionAttemptQueryService;
import com.serviceos.task.api.TaskExecutionAttemptView;
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

@WebMvcTest(TaskExecutionAttemptController.class)
@Import(SecurityConfiguration.class)
class TaskExecutionAttemptControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaskExecutionAttemptQueryService attempts;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    void authenticationPaginationAndSafeResponseAreEnforced() throws Exception {
        UUID taskId = UUID.randomUUID();
        mvc.perform(get("/api/v1/tasks/{taskId}/execution-attempts", taskId))
                .andExpect(status().isUnauthorized());

        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "m72", Set.of());
        TaskExecutionAttemptView item = new TaskExecutionAttemptView(
                UUID.randomUUID(),
                2,
                "RETRYABLE_FAILURE",
                "REMOTE_TIMEOUT",
                null,
                Instant.parse("2026-07-16T00:03:00Z"),
                Instant.parse("2026-07-16T00:02:00Z"),
                Instant.parse("2026-07-16T00:02:10Z"));
        TaskExecutionAttemptPage page = new TaskExecutionAttemptPage(
                7, List.of(item), "next", Instant.parse("2026-07-16T00:04:00Z"));
        when(principals.current()).thenReturn(principal);
        when(attempts.list(principal, "corr-m72", taskId, "cursor", 20)).thenReturn(page);

        mvc.perform(get("/api/v1/tasks/{taskId}/execution-attempts", taskId)
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-m72")
                        .queryParam("cursor", "cursor")
                        .queryParam("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"7\""))
                .andExpect(header().string("X-Correlation-Id", "corr-m72"))
                .andExpect(jsonPath("$.resourceVersion").value(7))
                .andExpect(jsonPath("$.items[0].attemptNo").value(2))
                .andExpect(jsonPath("$.items[0].resultCode").value("RETRYABLE_FAILURE"))
                .andExpect(jsonPath("$.items[0].errorCode").value("REMOTE_TIMEOUT"))
                .andExpect(jsonPath("$.items[0].workerId").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload").doesNotExist())
                .andExpect(jsonPath("$.items[0].errorMessage").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").value("next"));
        verify(attempts).list(principal, "corr-m72", taskId, "cursor", 20);
    }
}
