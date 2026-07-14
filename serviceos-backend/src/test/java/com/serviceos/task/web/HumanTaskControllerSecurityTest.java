package com.serviceos.task.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCommandReceipt;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.ReleaseHumanTaskCommand;
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

@WebMvcTest(HumanTaskController.class)
@Import(SecurityConfiguration.class)
class HumanTaskControllerSecurityTest {
    private static final UUID TASK_ID = UUID.fromString("18888888-8888-4888-8888-888888888888");

    @Autowired MockMvc mvc;
    @MockitoBean HumanTaskCommandService commands;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedTaskCommandIsRejected() throws Exception {
        mvc.perform(post("/api/v1/tasks/{taskId}:claim", TASK_ID)
                        .header("Idempotency-Key", "idem-claim")
                        .header("If-Match", "\"1\""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void trustedPrincipalAndQuotedVersionReachCommandBoundary() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "actor-trusted", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("task.claim"));
        HumanTaskCommandReceipt receipt = new HumanTaskCommandReceipt(
                TASK_ID, "CLAIMED", "actor-trusted", 2,
                Instant.parse("2026-07-14T03:00:00Z"));
        when(principals.current()).thenReturn(principal);
        when(commands.claim(eq(principal), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(receipt);

        mvc.perform(post("/api/v1/tasks/{taskId}:claim", TASK_ID)
                        .with(jwt().jwt(token -> token.subject("actor-trusted")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "idem-claim")
                        .header("If-Match", "\"1\"")
                        .header("X-Actor-Id", "actor-spoofed")
                        .header("X-Tenant-Id", "tenant-spoofed"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.actorId").value("actor-trusted"));

        verify(commands).claim(
                eq(principal),
                argThat((CommandMetadata metadata) ->
                        metadata.idempotencyKey().equals("idem-claim")),
                argThat((ClaimHumanTaskCommand command) ->
                        command.taskId().equals(TASK_ID) && command.expectedVersion() == 1));
    }

    @Test
    void malformedIfMatchFailsBeforeCommandExecution() throws Exception {
        mvc.perform(post("/api/v1/tasks/{taskId}:claim", TASK_ID)
                        .with(jwt().jwt(token -> token.subject("actor-trusted")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "idem-claim")
                        .header("If-Match", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void releaseUsesTrustedPrincipalVersionAndStableReasonCode() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "actor-trusted", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("task.release"));
        HumanTaskCommandReceipt receipt = new HumanTaskCommandReceipt(
                TASK_ID, "READY", "actor-trusted", 4,
                Instant.parse("2026-07-14T04:30:00Z"));
        when(principals.current()).thenReturn(principal);
        when(commands.release(eq(principal), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(receipt);

        mvc.perform(post("/api/v1/tasks/{taskId}:release", TASK_ID)
                        .with(jwt().jwt(token -> token.subject("actor-trusted")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "release-1")
                        .header("If-Match", "\"3\"")
                        .contentType("application/json")
                        .content("{\"reasonCode\":\"CUSTOMER_RESCHEDULE\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""));

        verify(commands).release(
                eq(principal), org.mockito.ArgumentMatchers.any(),
                argThat((ReleaseHumanTaskCommand command) ->
                        command.expectedVersion() == 3
                                && command.reasonCode().equals("CUSTOMER_RESCHEDULE")));
    }
}
