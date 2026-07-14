package com.serviceos.evidence.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.EvidenceSetSnapshotMemberView;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvidenceSetSnapshotController.class)
@Import(SecurityConfiguration.class)
class EvidenceSetSnapshotControllerSecurityTest {
    private static final UUID TASK = UUID.fromString("41000000-0000-4000-8000-000000000041");
    private static final UUID SNAPSHOT = UUID.fromString("41000000-0000-4000-8000-000000000040");
    private static final UUID REVISION = UUID.fromString("41000000-0000-4000-8000-000000000039");

    @Autowired MockMvc mvc;
    @MockitoBean EvidenceSetSnapshotService snapshots;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousCreateIsRejected() throws Exception {
        mvc.perform(post("/api/v1/tasks/" + TASK + "/evidence-set-snapshots")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purpose":"TASK_SUBMISSION","memberRevisionIds":["%s"]}
                                """.formatted(REVISION)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCreateAndGetSucceed() throws Exception {
        when(principals.current()).thenReturn(new CurrentPrincipal(
                "tech", "tenant-a", CurrentPrincipal.PrincipalType.USER, "mobile", Set.of()));
        EvidenceSetSnapshotView view = new EvidenceSetSnapshotView(
                SNAPSHOT, TASK, UUID.randomUUID(), UUID.randomUUID(), "TASK_SUBMISSION", 1,
                "a".repeat(64), "{\"status\":\"ELIGIBLE\"}", "tech",
                Instant.parse("2026-07-14T11:00:00Z"),
                List.of(new EvidenceSetSnapshotMemberView(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), REVISION,
                        1, "VALIDATED", "b".repeat(64), "c".repeat(64), 1)));
        when(snapshots.create(any(), any(), any())).thenReturn(view);
        when(snapshots.get(any(), anyString(), eq(SNAPSHOT))).thenReturn(view);

        mvc.perform(post("/api/v1/tasks/" + TASK + "/evidence-set-snapshots")
                        .with(jwt().jwt(jwt -> jwt.subject("tech").claim("tenant_id", "tenant-a")))
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Correlation-Id", "corr-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purpose":"TASK_SUBMISSION","memberRevisionIds":["%s"]}
                                """.formatted(REVISION)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenceSetSnapshotId").value(SNAPSHOT.toString()))
                .andExpect(jsonPath("$.purpose").value("TASK_SUBMISSION"));

        mvc.perform(get("/api/v1/evidence-set-snapshots/" + SNAPSHOT)
                        .with(jwt().jwt(jwt -> jwt.subject("tech").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].evidenceRevisionId").value(REVISION.toString()));
    }
}
