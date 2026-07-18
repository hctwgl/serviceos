package com.serviceos.evidence.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceSetSnapshotMemberView;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TechnicianEvidenceController.class)
@Import(SecurityConfiguration.class)
class TechnicianEvidenceControllerSecurityTest {
    private static final UUID PRINCIPAL = UUID.fromString("10000000-0000-4000-8000-000000000264");
    private static final UUID NETWORK = UUID.fromString("20000000-0000-4000-8000-000000000264");
    private static final UUID TASK = UUID.fromString("30000000-0000-4000-8000-000000000264");
    private static final UUID SLOT = UUID.fromString("40000000-0000-4000-8000-000000000264");
    private static final UUID SESSION = UUID.fromString("50000000-0000-4000-8000-000000000264");
    private static final UUID ITEM = UUID.fromString("60000000-0000-4000-8000-000000000264");
    private static final UUID REVISION = UUID.fromString("70000000-0000-4000-8000-000000000264");
    private static final UUID SNAPSHOT = UUID.fromString("80000000-0000-4000-8000-000000000265");
    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");
    private static final String CONTEXT = "TECHNICIAN|NETWORK|" + NETWORK;

    @Autowired MockMvc mvc;
    @MockitoBean TechnicianEvidenceService evidence;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousIsRejectedAndSlotResponseOmitsFrozenRuleJson() throws Exception {
        mvc.perform(get("/api/v1/technician/me/tasks/{taskId}/evidence-slots", TASK))
                .andExpect(status().isUnauthorized());

        when(principals.current()).thenReturn(principal());
        when(evidence.listSlots(principal(), "corr-264", CONTEXT, TASK)).thenReturn(List.of(slot()));
        mvc.perform(get("/api/v1/technician/me/tasks/{taskId}/evidence-slots", TASK)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-264")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("X-Correlation-Id", "corr-264"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slotId").value(SLOT.toString()))
                .andExpect(jsonPath("$[0].requirementName").value("现场照片"))
                .andExpect(jsonPath("$[0].requirement").doesNotExist())
                .andExpect(jsonPath("$[0].resolutionExplanation").doesNotExist());
    }

    @Test
    void beginAndFinalizeExposeOnlyConstrainedUploadAndSafeRevisionMetadata() throws Exception {
        when(principals.current()).thenReturn(principal());
        when(evidence.beginUpload(eq(principal()), any(), eq(CONTEXT), any())).thenReturn(
                new EvidenceUploadSessionView(
                        SESSION, UUID.randomUUID(), TASK, SLOT, null, "CREATED", "PUT",
                        "https://upload.invalid/once", Map.of("Content-Type", "image/jpeg"),
                        NOW.plusSeconds(60), NOW.plusSeconds(600)));
        when(evidence.finalizeUpload(eq(principal()), any(), eq(CONTEXT), any())).thenReturn(item());

        String body = """
                {"originalFileName":"site.jpg","declaredMimeType":"image/jpeg","expectedSize":4,
                 "expectedSha256":"%s","captureSource":"CAMERA","capturedAt":"2026-07-18T12:00:00Z"}
                """.formatted("a".repeat(64));
        mvc.perform(post("/api/v1/technician/me/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions",
                        TASK, SLOT)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-264")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("Idempotency-Key", "begin-264")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl").value("https://upload.invalid/once"))
                .andExpect(jsonPath("$.fileId").doesNotExist());

        mvc.perform(post("/api/v1/technician/me/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions/{session}:finalize",
                        TASK, SLOT, SESSION)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-264")))
                        .header("X-Technician-Context", CONTEXT)
                        .contentType("application/json")
                        .content("{\"actualSha256\":\"" + "a".repeat(64)
                                + "\",\"finalizeCommandId\":\"finalize-264\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revisions[0].evidenceRevisionId").value(REVISION.toString()))
                .andExpect(jsonPath("$.revisions[0].fileObjectId").doesNotExist())
                .andExpect(jsonPath("$.revisions[0].captureMetadata").doesNotExist())
                .andExpect(jsonPath("$.createdBy").doesNotExist());

        verify(evidence).beginUpload(eq(principal()), any(), eq(CONTEXT), any());
        verify(evidence).finalizeUpload(eq(principal()), any(), eq(CONTEXT), any());
    }

    @Test
    void snapshotAndCompleteAcceptOnlyAuthorityObjectIdsAndQuotedVersion() throws Exception {
        when(principals.current()).thenReturn(principal());
        when(evidence.createTaskSubmissionSnapshot(eq(principal()), any(), eq(CONTEXT), eq(TASK), any()))
                .thenReturn(snapshot());
        when(evidence.completeTask(eq(principal()), any(), eq(CONTEXT), any())).thenReturn(
                new HumanTaskCommandReceipt(TASK, "COMPLETED", PRINCIPAL.toString(), 8, NOW));

        mvc.perform(post("/api/v1/technician/me/tasks/{taskId}/evidence-set-snapshots", TASK)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-264")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("Idempotency-Key", "snapshot-265")
                        .contentType("application/json")
                        .content("{\"memberRevisionIds\":[\"" + REVISION + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenceSetSnapshotId").value(SNAPSHOT.toString()))
                .andExpect(jsonPath("$.members[0].evidenceRevisionId").value(REVISION.toString()))
                .andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(jsonPath("$.eligibilitySummary").doesNotExist());

    }

    private static EvidenceSlotView slot() {
        return new EvidenceSlotView(
                SLOT, UUID.randomUUID(), TASK, UUID.randomUUID(), UUID.randomUUID(), "site-photo",
                "1.0.0", "template-digest", "SITE_PHOTO", "1", "现场照片", "IMAGE",
                true, 1, 3, "input-digest", "{}", "{}", "requirement-digest",
                "MISSING", NOW);
    }

    private static EvidenceItemView item() {
        return new EvidenceItemView(
                ITEM, TASK, UUID.randomUUID(), SLOT, 1, "ACTIVE", PRINCIPAL.toString(), NOW,
                List.of(new EvidenceRevisionView(
                        REVISION, ITEM, SLOT, TASK, UUID.randomUUID(), 1, UUID.randomUUID(),
                        "a".repeat(64), "image/jpeg", 4, "{\"uploadedBy\":\"secret\"}",
                        "STORED", SESSION, "finalize-264", PRINCIPAL.toString(), NOW,
                        List.of(), null, null, null, null)));
    }

    private static EvidenceSetSnapshotView snapshot() {
        return new EvidenceSetSnapshotView(
                SNAPSHOT, TASK, UUID.randomUUID(), UUID.randomUUID(), "TASK_SUBMISSION", 1,
                "b".repeat(64), "{\"eligible\":true}", PRINCIPAL.toString(), NOW,
                List.of(new EvidenceSetSnapshotMemberView(
                        UUID.randomUUID(), SLOT, ITEM, REVISION, 1, "VALIDATED",
                        "a".repeat(64), "c".repeat(64), 1)));
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(PRINCIPAL.toString(), "tenant-264",
                CurrentPrincipal.PrincipalType.USER, "technician-evidence-web", Set.of());
    }
}
