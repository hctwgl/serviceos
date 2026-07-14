package com.serviceos.evidence.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({EvidenceItemController.class, EvidenceSlotController.class})
@Import(SecurityConfiguration.class)
class EvidenceItemControllerSecurityTest {
    private static final UUID TASK = UUID.fromString("39000000-0000-4000-8000-000000000039");
    private static final UUID SLOT = UUID.fromString("39000000-0000-4000-8000-000000000038");
    private static final UUID ITEM = UUID.fromString("39000000-0000-4000-8000-000000000037");

    @Autowired MockMvc mvc;
    @MockitoBean EvidenceCommandService evidence;
    @MockitoBean com.serviceos.evidence.api.EvidenceSlotQueryService slots;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousEvidenceUploadIsRejected() throws Exception {
        mvc.perform(post("/api/v1/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions", TASK, SLOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-1")
                        .content("""
                                {"originalFileName":"a.png","declaredMimeType":"image/png",
                                 "expectedSize":10,"expectedSha256":"%s",
                                 "captureMetadata":{"captureSource":"CAMERA","capturedAt":"2026-07-14T08:00:00Z"}}
                                """.formatted("a".repeat(64))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousInvalidateIsRejected() throws Exception {
        mvc.perform(post("/api/v1/evidence-revisions/{revisionId}:invalidate", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-invalidate")
                        .content("""
                                {"reasonCode":"DUPLICATE_EVIDENCE"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trustedPrincipalCanBeginUploadWithoutClientTenantAuthority() throws Exception {
        CurrentPrincipal principal = principal();
        when(principals.current()).thenReturn(principal);
        when(evidence.beginUpload(eq(principal), any(), any())).thenReturn(session());

        mvc.perform(post("/api/v1/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions", TASK, SLOT)
                        .with(jwt().jwt(token -> token.subject("technician-evidence")
                                .claim("tenant_id", "tenant-evidence")))
                        .header("Idempotency-Key", "idem-begin")
                        .header("X-Tenant-Id", "spoofed-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalFileName":"a.png","declaredMimeType":"image/png",
                                 "expectedSize":10,"expectedSha256":"%s",
                                 "captureMetadata":{"captureSource":"CAMERA","capturedAt":"2026-07-14T08:00:00Z"}}
                                """.formatted("a".repeat(64))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenceSlotId").value(SLOT.toString()));

        verify(evidence).beginUpload(eq(principal), any(), any());
    }

    @Test
    void projectScopeDenialReturnsStableProblem() throws Exception {
        CurrentPrincipal principal = principal();
        when(principals.current()).thenReturn(principal);
        when(evidence.get(eq(principal), anyString(), eq(ITEM))).thenThrow(
                new BusinessProblem(ProblemCode.ACCESS_DENIED, "Project scope denied"));

        mvc.perform(get("/api/v1/evidence-items/{itemId}", ITEM)
                        .with(jwt().jwt(token -> token.subject("technician-evidence")
                                .claim("tenant_id", "tenant-evidence"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void itemReadReturnsRevisionProjection() throws Exception {
        CurrentPrincipal principal = principal();
        when(principals.current()).thenReturn(principal);
        when(evidence.get(eq(principal), anyString(), eq(ITEM))).thenReturn(item());

        mvc.perform(get("/api/v1/evidence-items/{itemId}", ITEM)
                        .with(jwt().jwt(token -> token.subject("technician-evidence")
                                .claim("tenant_id", "tenant-evidence"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceItemId").value(ITEM.toString()))
                .andExpect(jsonPath("$.revisions[0].status").value("STORED"))
                .andExpect(jsonPath("$.revisions[0].captureMetadata.captureSource").value("CAMERA"));
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(
                "technician-evidence", "tenant-evidence", CurrentPrincipal.PrincipalType.USER,
                "mobile", Set.of());
    }

    private static EvidenceUploadSessionView session() {
        return new EvidenceUploadSessionView(
                UUID.randomUUID(), UUID.randomUUID(), TASK, SLOT, null, "CREATED", "PUT",
                "http://localhost/upload", Map.of("Content-Type", "image/png"),
                Instant.parse("2026-07-14T09:00:00Z"), Instant.parse("2026-07-14T10:00:00Z"));
    }

    private static EvidenceItemView item() {
        EvidenceRevisionView revision = new EvidenceRevisionView(
                UUID.randomUUID(), ITEM, SLOT, TASK, UUID.randomUUID(), 1, UUID.randomUUID(),
                "b".repeat(64), "image/png", 12,
                "{\"captureSource\":\"CAMERA\",\"capturedAt\":\"2026-07-14T08:00:00Z\"}",
                "STORED", UUID.randomUUID(), "cmd-1", "technician-evidence",
                Instant.parse("2026-07-14T08:01:00Z"), List.of(), null, null, null, null);
        return new EvidenceItemView(
                ITEM, TASK, UUID.randomUUID(), SLOT, 1, "OPEN", "technician-evidence",
                Instant.parse("2026-07-14T08:01:00Z"), List.of(revision));
    }
}
