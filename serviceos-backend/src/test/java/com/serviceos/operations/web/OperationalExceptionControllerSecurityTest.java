package com.serviceos.operations.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.operations.api.OperationalExceptionAcknowledgement;
import com.serviceos.operations.api.OperationalExceptionWorkbenchService;
import com.serviceos.shared.CommandMetadata;
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

/** M29 HTTP 边界只接收受信主体、标准幂等键和带引号的聚合版本。 */
@WebMvcTest(OperationalExceptionController.class)
@Import(SecurityConfiguration.class)
class OperationalExceptionControllerSecurityTest {
    private static final UUID EXCEPTION_ID =
            UUID.fromString("92222222-2222-4222-8222-222222222222");

    @Autowired MockMvc mvc;
    @MockitoBean OperationalExceptionWorkbenchService workbench;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedAcknowledgeIsRejected() throws Exception {
        mvc.perform(post("/api/v1/operational-exceptions/{id}:acknowledge", EXCEPTION_ID)
                        .header("Idempotency-Key", "ack-1").header("If-Match", "\"1\"")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trustedPrincipalAndPreconditionsReachApplicationBoundary() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "ops-user", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(workbench.acknowledge(eq(principal),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new OperationalExceptionAcknowledgement(
                        EXCEPTION_ID, "ACKNOWLEDGED", 2,
                        Instant.parse("2026-07-14T10:00:00Z"), "ops-user"));

        mvc.perform(post("/api/v1/operational-exceptions/{id}:acknowledge", EXCEPTION_ID)
                        .with(jwt().jwt(token -> token.subject("ops-user")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "ack-1").header("If-Match", "\"1\"")
                        .header("X-Tenant-Id", "spoofed-tenant")
                        .contentType("application/json").content("{\"note\":\"已接管\"}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.acknowledgedBy").value("ops-user"));

        verify(workbench).acknowledge(eq(principal),
                argThat((CommandMetadata metadata) -> metadata.idempotencyKey().equals("ack-1")),
                argThat(command -> command.exceptionId().equals(EXCEPTION_ID)
                        && command.expectedVersion() == 1 && command.note().equals("已接管")));
    }

    @Test
    void unquotedIfMatchIsRejectedBeforeCommandExecution() throws Exception {
        when(principals.current()).thenReturn(new CurrentPrincipal(
                "ops-user", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of()));
        mvc.perform(post("/api/v1/operational-exceptions/{id}:acknowledge", EXCEPTION_ID)
                        .with(jwt().jwt(token -> token.subject("ops-user")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "ack-1").header("If-Match", "1")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
