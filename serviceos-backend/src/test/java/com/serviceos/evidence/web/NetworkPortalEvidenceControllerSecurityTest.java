package com.serviceos.evidence.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.evidence.api.NetworkPortalEvidenceService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M201：network-portal evidence on-behalf 未认证 401；缺能力 403；成功 201。 */
@WebMvcTest(NetworkPortalEvidenceController.class)
@Import(SecurityConfiguration.class)
class NetworkPortalEvidenceControllerSecurityTest {
    private static final UUID TASK_ID = UUID.fromString("019f83d1-9999-7f8c-9505-36fe5c0e880d");
    private static final UUID SLOT_ID = UUID.fromString("019f83d1-aaaa-7f8c-9505-36fe5c0e880e");
    private static final UUID NETWORK_ID = UUID.fromString("019f83d1-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID SESSION_ID = UUID.fromString("019f83d1-bbbb-7f8c-9505-36fe5c0e880f");
    private static final UUID FILE_ID = UUID.fromString("019f83d1-cccc-7f8c-9505-36fe5c0e8810");
    private static final UUID CORRECTION_ID = UUID.fromString("019f83d1-dddd-7f8c-9505-36fe5c0e8811");
    private static final UUID SNAPSHOT_ID = UUID.fromString("019f83d1-eeee-7f8c-9505-36fe5c0e8812");
    private static final String SHA = "a".repeat(64);

    @Autowired MockMvc mvc;
    @MockitoBean NetworkPortalEvidenceService portalEvidence;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(post("/api/v1/network-portal/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions",
                        TASK_ID, SLOT_ID)
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m201-1")
                        .contentType("application/json")
                        .content(beginBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCapabilityReturnsAccessDenied() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(portalEvidence.beginUploadOnBehalf(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(TASK_ID), eq(SLOT_ID), any()))
                .thenThrow(new BusinessProblem(ProblemCode.ACCESS_DENIED, "The action is not allowed"));

        mvc.perform(post("/api/v1/network-portal/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions",
                        TASK_ID, SLOT_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-deny")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m201-deny")
                        .contentType("application/json")
                        .content(beginBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void authenticatedBeginReturnsCreated() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(portalEvidence.beginUploadOnBehalf(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(TASK_ID), eq(SLOT_ID), any()))
                .thenReturn(new EvidenceUploadSessionView(
                        SESSION_ID, FILE_ID, TASK_ID, SLOT_ID, null, "CREATED", "PUT",
                        "https://example.local/upload", Map.of("Content-Type", "image/png"),
                        Instant.parse("2026-07-17T04:00:00Z"), Instant.parse("2026-07-17T05:00:00Z")));

        mvc.perform(post("/api/v1/network-portal/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions",
                        TASK_ID, SLOT_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-ok")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m201-ok")
                        .contentType("application/json")
                        .content(beginBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadSessionId").value(SESSION_ID.toString()));
    }

    @Test
    void authenticatedCreateSnapshotReturnsCreated() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(portalEvidence.createSnapshotOnBehalf(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(CORRECTION_ID), any()))
                .thenReturn(new EvidenceSetSnapshotView(
                        SNAPSHOT_ID, TASK_ID, UUID.randomUUID(), UUID.randomUUID(),
                        "TASK_SUBMISSION", 1, "d".repeat(64), "{}",
                        actor.principalId(), Instant.parse("2026-07-17T03:00:00Z"), List.of()));

        mvc.perform(post("/api/v1/network-portal/correction-cases/{correctionCaseId}/evidence-set-snapshots",
                        CORRECTION_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-snapshot")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m201-snapshot")
                        .contentType("application/json")
                        .content("{\"memberRevisionIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenceSetSnapshotId").value(SNAPSHOT_ID.toString()));
    }

    @Test
    void authenticatedResubmitReturnsOk() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(portalEvidence.resubmit(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(CORRECTION_ID), eq(SNAPSHOT_ID)))
                .thenReturn(new CorrectionCaseView(
                        CORRECTION_ID, UUID.randomUUID(), TASK_ID, UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), "d".repeat(64), List.of("IMAGE.BLUR"), null, "RESUBMITTED",
                        actor.principalId(), Instant.parse("2026-07-17T03:00:00Z"), SNAPSHOT_ID,
                        null, null, null, null, null, null, List.of()));

        mvc.perform(post("/api/v1/network-portal/correction-cases/{correctionCaseId}:resubmit",
                        CORRECTION_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-resubmit")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m201-resubmit")
                        .contentType("application/json")
                        .content("{\"evidenceSetSnapshotId\":\"" + SNAPSHOT_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESUBMITTED"));
    }

    private static String beginBody() {
        return """
                {
                  "originalFileName":"site.png",
                  "declaredMimeType":"image/png",
                  "expectedSize":128,
                  "expectedSha256":"%s",
                  "captureMetadata":{"captureSource":"CAMERA","capturedAt":"2026-07-17T02:00:00Z"},
                  "onBehalfOf":"tech-1",
                  "onBehalfReason":"整改代补"
                }
                """.formatted(SHA);
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(
                "019f83d1-1111-7f8c-9505-36fe5c0e8801",
                "tenant-a",
                CurrentPrincipal.PrincipalType.USER,
                "network-portal",
                Set.of());
    }
}
