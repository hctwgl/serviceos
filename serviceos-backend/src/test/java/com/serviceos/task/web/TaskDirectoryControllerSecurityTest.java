package com.serviceos.task.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.task.api.TaskDetail;
import com.serviceos.task.api.TaskDirectoryItem;
import com.serviceos.task.api.TaskDirectoryPage;
import com.serviceos.task.api.TaskDirectoryQueryService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskDirectoryController.class)
@Import(SecurityConfiguration.class)
class TaskDirectoryControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaskDirectoryQueryService queries;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    void authenticationAndTrustedPrincipalProtectQueueAndDetail() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        mvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isUnauthorized());

        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "admin", Set.of());
        TaskDirectoryItem item = new TaskDirectoryItem(
                taskId,
                projectId,
                UUID.randomUUID(),
                "SURVEY",
                "HUMAN",
                "SURVEY",
                500,
                "READY",
                Instant.parse("2026-07-16T01:00:00Z"),
                null,
                0,
                3,
                2,
                Instant.parse("2026-07-16T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z"));
        when(principals.current()).thenReturn(principal);
        when(queries.list(eq(principal), eq("corr-m70-list"), any()))
                .thenReturn(new TaskDirectoryPage(List.of(item), null, Instant.now()));
        TaskDetail detail = new TaskDetail(
                item,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                Instant.now());
        when(queries.get(principal, "corr-m70-get", taskId)).thenReturn(detail);

        mvc.perform(get("/api/v1/tasks")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-m70-list")
                        .queryParam("taskKind", "HUMAN")
                        .queryParam("status", "READY")
                        .queryParam("assignee", "me")
                        .queryParam("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(taskId.toString()))
                .andExpect(jsonPath("$.items[0].payloadRef").doesNotExist());
        mvc.perform(get("/api/v1/tasks/{id}", taskId)
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-m70-get"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.task.id").value(taskId.toString()));

        verify(queries).list(
                eq(principal),
                eq("corr-m70-list"),
                argThat(query -> "HUMAN".equals(query.taskKind())
                        && "READY".equals(query.status())
                        && "me".equals(query.assignee())
                        && query.limit() == 20));
    }
}
