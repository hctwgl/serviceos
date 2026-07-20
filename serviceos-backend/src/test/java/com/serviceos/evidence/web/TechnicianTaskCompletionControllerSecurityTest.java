package com.serviceos.evidence.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.TechnicianEvidenceService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.task.api.HumanTaskCommandReceipt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TechnicianTaskCompletionController.class)
@Import(SecurityConfiguration.class)
class TechnicianTaskCompletionControllerSecurityTest {
    private static final UUID TASK = UUID.randomUUID();
    private static final UUID SNAPSHOT = UUID.randomUUID();
    private static final UUID PRINCIPAL = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @MockitoBean TechnicianEvidenceService evidence;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void requiresAuthenticationAndReturnsOnlySafeReceipt() throws Exception {
        mvc.perform(post("/api/v1/technician/me/tasks/{taskId}:complete", TASK)).andExpect(status().isUnauthorized());
        CurrentPrincipal principal = new CurrentPrincipal(PRINCIPAL.toString(), "tenant-265",
                CurrentPrincipal.PrincipalType.USER, "test", Set.of());
        when(principals.current()).thenReturn(principal);
        when(evidence.completeTask(eq(principal), any(), anyString(), any(), any())).thenReturn(
                new HumanTaskCommandReceipt(TASK, "COMPLETED", PRINCIPAL.toString(), 8, Instant.EPOCH));
        mvc.perform(post("/api/v1/technician/me/tasks/{taskId}:complete", TASK)
                        .with(jwt().jwt(j -> j.subject(PRINCIPAL.toString()).claim("tenant_id", "tenant-265")))
                        .header("X-Technician-Context", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "complete-265").header("If-Match", "\"7\"")
                        .contentType("application/json")
                        .content("{\"evidenceSetSnapshotId\":\"" + SNAPSHOT + "\"}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"8\""))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.resultRef").doesNotExist());
    }
}
