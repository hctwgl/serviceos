package com.serviceos.task.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.task.api.AssignTaskCandidatesCommand;
import com.serviceos.task.api.AssignmentSourceType;
import com.serviceos.task.api.TaskAssignmentBatchReceipt;
import com.serviceos.task.api.TaskAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M21 候选快照 HTTP 只信任 JWT 映射主体和服务端 Task 版本。 */
@WebMvcTest(TaskAssignmentController.class)
@Import(SecurityConfiguration.class)
class TaskAssignmentControllerSecurityTest {
    private static final UUID TASK_ID = UUID.fromString("68888888-8888-4888-8888-888888888888");

    @Autowired MockMvc mvc;
    @MockitoBean TaskAssignmentService assignments;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedAssignmentIsRejected() throws Exception {
        mvc.perform(post("/api/v1/tasks/{taskId}:assign-candidates", TASK_ID)
                        .header("Idempotency-Key", "assign-1")
                        .header("If-Match", "\"1\"")
                        .contentType("application/json")
                        .content("""
                                {"candidatePrincipalIds":["actor-a"],
                                 "sourceType":"MANUAL","sourceId":"manual://1"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trustedPrincipalAndCandidateSnapshotReachAssignmentBoundary() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "assignment-admin", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("task.assign"));
        TaskAssignmentBatchReceipt receipt = new TaskAssignmentBatchReceipt(
                UUID.fromString("69999999-9999-4999-8999-999999999999"), TASK_ID, 2, 2,
                Instant.parse("2026-07-14T04:00:00Z"));
        when(principals.current()).thenReturn(principal);
        when(assignments.assignCandidates(eq(principal),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(receipt);

        mvc.perform(post("/api/v1/tasks/{taskId}:assign-candidates", TASK_ID)
                        .with(jwt().jwt(token -> token.subject("assignment-admin")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "assign-1")
                        .header("If-Match", "\"1\"")
                        .header("X-Actor-Id", "spoofed")
                        .contentType("application/json")
                        .content("""
                                {"candidatePrincipalIds":["actor-b","actor-a"],
                                 "sourceType":"MANUAL","sourceId":"manual://1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.candidateCount").value(2));

        verify(assignments).assignCandidates(
                eq(principal),
                argThat((CommandMetadata metadata) -> metadata.idempotencyKey().equals("assign-1")),
                argThat((AssignTaskCandidatesCommand command) ->
                        command.expectedVersion() == 1
                                && command.sourceType() == AssignmentSourceType.MANUAL
                                && command.candidatePrincipalIds().equals(java.util.List.of("actor-a", "actor-b"))));
    }
}
