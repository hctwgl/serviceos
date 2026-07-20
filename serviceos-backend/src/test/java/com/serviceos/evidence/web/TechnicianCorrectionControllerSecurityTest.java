package com.serviceos.evidence.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSetSnapshotMemberView;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.evidence.api.TechnicianCorrectionService;
import com.serviceos.evidence.api.TechnicianCorrectionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
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

@WebMvcTest(TechnicianCorrectionController.class)
@Import(SecurityConfiguration.class)
class TechnicianCorrectionControllerSecurityTest {
    private static final UUID PRINCIPAL = UUID.fromString("10000000-0000-4000-8000-000000000266");
    private static final UUID NETWORK = UUID.fromString("20000000-0000-4000-8000-000000000266");
    private static final UUID CASE = UUID.fromString("30000000-0000-4000-8000-000000000266");
    private static final UUID SOURCE_TASK = UUID.fromString("40000000-0000-4000-8000-000000000266");
    private static final UUID CORRECTION_TASK = UUID.fromString("50000000-0000-4000-8000-000000000266");
    private static final UUID SLOT = UUID.fromString("60000000-0000-4000-8000-000000000266");
    private static final UUID SESSION = UUID.fromString("70000000-0000-4000-8000-000000000266");
    private static final UUID ITEM = UUID.fromString("80000000-0000-4000-8000-000000000266");
    private static final UUID REVISION = UUID.fromString("90000000-0000-4000-8000-000000000266");
    private static final UUID SNAPSHOT = UUID.fromString("a0000000-0000-4000-8000-000000000266");
    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");
    private static final String CONTEXT = "TECHNICIAN|NETWORK|" + NETWORK;

    @Autowired MockMvc mvc;
    @MockitoBean TechnicianCorrectionService corrections;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousIsRejectedAndListOmitsInternalReviewActorsAndDigests() throws Exception {
        mvc.perform(get("/api/v1/technician/me/corrections"))
                .andExpect(status().isUnauthorized());

        when(principals.current()).thenReturn(principal());
        when(corrections.list(eq(principal()), eq("corr-266"), eq(CONTEXT), any()))
                .thenReturn(List.of(view("READY", 1)));
        mvc.perform(get("/api/v1/technician/me/corrections")
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-266")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("X-Correlation-Id", "corr-266")
                        .header("X-ServiceOS-Client-Kind", "TECHNICIAN_WEB")
                        .header("X-ServiceOS-Client-Version", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].correctionCaseId").value(CASE.toString()))
                .andExpect(jsonPath("$[0].reasonCodes[0]").value("IMAGE.BLUR"))
                .andExpect(jsonPath("$[0].sourceSnapshotContentDigest").doesNotExist())
                .andExpect(jsonPath("$[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$[0].closedBy").doesNotExist());
        verify(corrections).list(eq(principal()), eq("corr-266"), eq(CONTEXT), eq("TECHNICIAN_WEB"));
    }

    @Test
    void listSurfacesClientCapabilityUnsupportedDetail() throws Exception {
        when(principals.current()).thenReturn(principal());
        when(corrections.list(eq(principal()), eq("corr-preflight"), eq(CONTEXT), eq("TECHNICIAN_IOS")))
                .thenReturn(List.of(new TechnicianCorrectionView(
                        CASE, SOURCE_TASK, CORRECTION_TASK, "IN_PROGRESS", List.of("IMAGE.BLUR"),
                        "READY", 1, null, 0,
                        "当前客户端（师傅 iOS）不支持本任务资料所需能力：evidence.media.signature")));

        mvc.perform(get("/api/v1/technician/me/corrections")
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-266")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("X-Correlation-Id", "corr-preflight")
                        .header("X-ServiceOS-Client-Kind", "TECHNICIAN_IOS")
                        .header("X-ServiceOS-Client-Version", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientCapabilityUnsupportedDetail")
                        .value("当前客户端（师傅 iOS）不支持本任务资料所需能力：evidence.media.signature"));
    }

    @Test
    void lifecycleAndResubmitAcceptOnlyVersionAndAuthorityObjectIds() throws Exception {
        when(principals.current()).thenReturn(principal());
        when(corrections.claim(eq(principal()), any(), eq(CONTEXT), any(), eq(CASE), eq(1L)))
                .thenReturn(view("CLAIMED", 2));
        when(corrections.start(eq(principal()), any(), eq(CONTEXT), any(), eq(CASE), eq(2L)))
                .thenReturn(view("RUNNING", 3));
        when(corrections.resubmit(eq(principal()), any(), eq(CONTEXT), any(), eq(CASE), eq(SNAPSHOT)))
                .thenReturn(new TechnicianCorrectionView(
                        CASE, SOURCE_TASK, CORRECTION_TASK, "RESUBMITTED", List.of("IMAGE.BLUR"),
                        "RUNNING", 3, SNAPSHOT, 1, null));

        mvc.perform(post("/api/v1/technician/me/corrections/{id}:claim", CASE)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-266")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("Idempotency-Key", "claim-266").header("If-Match", "\"1\"")
                        .header("X-ServiceOS-Client-Kind", "TECHNICIAN_WEB")
                        .header("X-ServiceOS-Client-Version", "1.0.0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskStatus").value("CLAIMED"));
        mvc.perform(post("/api/v1/technician/me/corrections/{id}:start", CASE)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-266")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("Idempotency-Key", "start-266").header("If-Match", "\"2\"")
                        .header("X-ServiceOS-Client-Kind", "TECHNICIAN_WEB")
                        .header("X-ServiceOS-Client-Version", "1.0.0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskStatus").value("RUNNING"));
        mvc.perform(post("/api/v1/technician/me/corrections/{id}:resubmit", CASE)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-266")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("Idempotency-Key", "resubmit-266")
                        .contentType("application/json")
                        .content("{\"evidenceSetSnapshotId\":\"" + SNAPSHOT + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseStatus").value("RESUBMITTED"))
                .andExpect(jsonPath("$.taskStatus").value("RUNNING"));

        verify(corrections).claim(eq(principal()), any(), eq(CONTEXT), eq("TECHNICIAN_WEB"), eq(CASE), eq(1L));
        verify(corrections).start(eq(principal()), any(), eq(CONTEXT), eq("TECHNICIAN_WEB"), eq(CASE), eq(2L));
        verify(corrections).resubmit(eq(principal()), any(), eq(CONTEXT), any(), eq(CASE), eq(SNAPSHOT));
    }

    @Test
    void uploadAndSnapshotResponsesStayOnSafeTechnicianShape() throws Exception {
        when(principals.current()).thenReturn(principal());
        when(corrections.beginUpload(eq(principal()), any(), eq(CONTEXT), any(), eq(CASE), eq(SLOT), any()))
                .thenReturn(new EvidenceUploadSessionView(
                        SESSION, UUID.randomUUID(), SOURCE_TASK, SLOT, null, "CREATED", "PUT",
                        "https://upload.invalid/once", Map.of(), NOW.plusSeconds(60), NOW.plusSeconds(600)));
        when(corrections.finalizeUpload(eq(principal()), any(), eq(CONTEXT), any(), eq(CASE), eq(SLOT),
                eq(SESSION), any(), any())).thenReturn(item());
        when(corrections.createSnapshot(eq(principal()), any(), eq(CONTEXT), any(), eq(CASE), any()))
                .thenReturn(snapshot());

        String begin = """
                {"originalFileName":"fix.jpg","declaredMimeType":"image/jpeg","expectedSize":4,
                 "expectedSha256":"%s","captureSource":"CAMERA","capturedAt":"2026-07-18T12:00:00Z"}
                """.formatted("a".repeat(64));
        mvc.perform(post("/api/v1/technician/me/corrections/{id}/evidence-slots/{slot}/upload-sessions",
                        CASE, SLOT).with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-266")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("Idempotency-Key", "begin-266")
                        .contentType("application/json").content(begin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl").value("https://upload.invalid/once"))
                .andExpect(jsonPath("$.fileId").doesNotExist());

        mvc.perform(post("/api/v1/technician/me/corrections/{id}/evidence-slots/{slot}/upload-sessions/{session}:finalize",
                        CASE, SLOT, SESSION).with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-266")))
                        .header("X-Technician-Context", CONTEXT)
                        .contentType("application/json")
                        .content("{\"actualSha256\":\"" + "a".repeat(64)
                                + "\",\"finalizeCommandId\":\"finalize-266\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revisions[0].fileObjectId").doesNotExist())
                .andExpect(jsonPath("$.createdBy").doesNotExist());

        mvc.perform(post("/api/v1/technician/me/corrections/{id}/evidence-set-snapshots", CASE)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-266")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("Idempotency-Key", "snapshot-266")
                        .contentType("application/json")
                        .content("{\"memberRevisionIds\":[\"" + REVISION + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenceSetSnapshotId").value(SNAPSHOT.toString()))
                .andExpect(jsonPath("$.createdBy").doesNotExist());
    }

    @Test
    void correctionEvidenceSlotsCapabilityUnsupportedReturns422() throws Exception {
        when(principals.current()).thenReturn(principal());
        when(corrections.listSlots(eq(principal()), eq("corr-cap"), eq(CONTEXT), any(), eq(CASE)))
                .thenThrow(new BusinessProblem(
                        ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED,
                        "当前客户端（师傅 iOS）不支持本任务资料所需能力：evidence.media.signature"));

        mvc.perform(get("/api/v1/technician/me/corrections/{id}/evidence-slots", CASE)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-266")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("X-Correlation-Id", "corr-cap")
                        .header("X-ServiceOS-Client-Kind", "TECHNICIAN_IOS"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CLIENT_CAPABILITY_UNSUPPORTED"));
    }

    private static TechnicianCorrectionView view(String status, long version) {
        return new TechnicianCorrectionView(
                CASE, SOURCE_TASK, CORRECTION_TASK, "IN_PROGRESS", List.of("IMAGE.BLUR"),
                status, version, null, 0, null);
    }

    private static EvidenceItemView item() {
        return new EvidenceItemView(
                ITEM, SOURCE_TASK, UUID.randomUUID(), SLOT, 1, "ACTIVE", PRINCIPAL.toString(), NOW,
                List.of(new EvidenceRevisionView(
                        REVISION, ITEM, SLOT, SOURCE_TASK, UUID.randomUUID(), 1, UUID.randomUUID(),
                        "a".repeat(64), "image/jpeg", 4, "{\"uploadedBy\":\"secret\"}",
                        "VALIDATED", SESSION, "finalize-266", PRINCIPAL.toString(), NOW,
                        List.of(), null, null, null, null)));
    }

    private static EvidenceSetSnapshotView snapshot() {
        return new EvidenceSetSnapshotView(
                SNAPSHOT, SOURCE_TASK, UUID.randomUUID(), UUID.randomUUID(), "TASK_SUBMISSION", 1,
                "b".repeat(64), "{\"eligible\":true}", PRINCIPAL.toString(), NOW,
                List.of(new EvidenceSetSnapshotMemberView(
                        UUID.randomUUID(), SLOT, ITEM, REVISION, 1, "VALIDATED",
                        "a".repeat(64), "c".repeat(64), 1)));
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(PRINCIPAL.toString(), "tenant-266",
                CurrentPrincipal.PrincipalType.USER, "technician-correction-web", Set.of());
    }
}
