package com.serviceos.evidence.web;

import com.serviceos.evidence.api.BeginEvidenceUploadOnBehalfCommand;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.CorrectionResubmissionView;
import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.evidence.api.FinalizeEvidenceUploadCommand;
import com.serviceos.evidence.api.NetworkPortalEvidenceService;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Network Portal 资料代补 HTTP 适配器。
 * <p>
 * networkId 仅来自可信头 X-Network-Context；onBehalf 字段为命令级权威值。
 */
@RestController
@RequestMapping("/api/v1/network-portal")
final class NetworkPortalEvidenceController {
    private final NetworkPortalEvidenceService portalEvidence;
    private final CurrentPrincipalProvider principals;
    private final ObjectMapper objectMapper;

    NetworkPortalEvidenceController(
            NetworkPortalEvidenceService portalEvidence,
            CurrentPrincipalProvider principals,
            ObjectMapper objectMapper
    ) {
        this.portalEvidence = portalEvidence;
        this.principals = principals;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions")
    ResponseEntity<EvidenceUploadSessionResponse> beginUploadOnBehalf(
            @PathVariable UUID taskId,
            @PathVariable UUID slotId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody NetworkPortalBeginEvidenceUploadRequest request
    ) {
        EvidenceUploadSessionView session = portalEvidence.beginUploadOnBehalf(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                networkContext,
                taskId,
                slotId,
                new BeginEvidenceUploadOnBehalfCommand(
                        taskId, slotId, request.evidenceItemId(), request.originalFileName(),
                        request.declaredMimeType(), request.expectedSize(), request.expectedSha256(),
                        writeJson(request.captureMetadata()), request.onBehalfOf(),
                        request.onBehalfReason(), null));
        return ResponseEntity
                .created(URI.create("/api/v1/network-portal/tasks/" + taskId
                        + "/evidence-slots/" + slotId + "/upload-sessions/" + session.uploadSessionId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(sessionResponse(session));
    }

    @PostMapping("/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions/{uploadSessionId}:finalize")
    ResponseEntity<EvidenceItemResponse> finalizeUploadOnBehalf(
            @PathVariable UUID taskId,
            @PathVariable UUID slotId,
            @PathVariable UUID uploadSessionId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody FinalizeEvidenceUploadRequest request
    ) {
        EvidenceItemView item = portalEvidence.finalizeUploadOnBehalf(
                principals.current(),
                new CommandMetadata(correlationId, request.finalizeCommandId()),
                networkContext,
                taskId,
                slotId,
                uploadSessionId,
                new FinalizeEvidenceUploadCommand(
                        taskId, slotId, uploadSessionId,
                        request.actualSha256(), request.finalizeCommandId()));
        return ResponseEntity
                .created(URI.create("/api/v1/evidence-items/" + item.evidenceItemId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(itemResponse(item));
    }

    @PostMapping("/correction-cases/{correctionCaseId}/evidence-set-snapshots")
    ResponseEntity<EvidenceSetSnapshotResponse> createSnapshotOnBehalf(
            @PathVariable UUID correctionCaseId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreateEvidenceSetSnapshotOnBehalfRequest request
    ) {
        EvidenceSetSnapshotView snapshot = portalEvidence.createSnapshotOnBehalf(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                networkContext,
                correctionCaseId,
                request.memberRevisionIds());
        return ResponseEntity
                .created(URI.create("/api/v1/evidence-set-snapshots/" + snapshot.evidenceSetSnapshotId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(snapshotResponse(snapshot));
    }

    @PostMapping("/correction-cases/{correctionCaseId}:resubmit")
    CorrectionCaseResponse resubmit(
            @PathVariable UUID correctionCaseId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ResubmitCorrectionCaseRequest request
    ) {
        CorrectionCaseView view = portalEvidence.resubmit(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                networkContext,
                correctionCaseId,
                request.evidenceSetSnapshotId());
        return correctionResponse(view);
    }

    private EvidenceUploadSessionResponse sessionResponse(EvidenceUploadSessionView session) {
        return new EvidenceUploadSessionResponse(
                session.uploadSessionId(), session.fileId(), session.taskId(),
                session.evidenceSlotId(), session.evidenceItemId(), session.status(),
                session.uploadMethod(), session.uploadUrl(), session.requiredHeaders(),
                session.uploadAuthorizationExpiresAt(), session.sessionExpiresAt());
    }

    private EvidenceItemResponse itemResponse(EvidenceItemView item) {
        return new EvidenceItemResponse(
                item.evidenceItemId(), item.taskId(), item.projectId(), item.evidenceSlotId(),
                item.itemOrdinal(), item.status(), item.createdBy(), item.createdAt(),
                item.revisions().stream().map(this::revisionResponse).toList());
    }

    private EvidenceRevisionResponse revisionResponse(EvidenceRevisionView revision) {
        try {
            return new EvidenceRevisionResponse(
                    revision.evidenceRevisionId(), revision.evidenceItemId(),
                    revision.evidenceSlotId(), revision.taskId(), revision.projectId(),
                    revision.revisionNumber(), revision.fileObjectId(), revision.contentDigest(),
                    revision.mimeType(), revision.sizeBytes(),
                    objectMapper.readTree(revision.captureMetadataJson()), revision.status(),
                    revision.sourceUploadSessionId(), revision.createdBy(), revision.createdAt());
        } catch (JacksonException exception) {
            throw new IllegalStateException("EvidenceRevision captureMetadata is invalid", exception);
        }
    }

    private CorrectionCaseResponse correctionResponse(CorrectionCaseView view) {
        return new CorrectionCaseResponse(
                view.correctionCaseId(), view.projectId(), view.taskId(),
                view.sourceReviewCaseId(), view.sourceReviewDecisionId(),
                view.sourceEvidenceSetSnapshotId(), view.status(),
                view.latestResubmissionSnapshotId(),
                view.resubmissions().stream().map(this::resubmissionResponse).toList());
    }

    private EvidenceSetSnapshotResponse snapshotResponse(EvidenceSetSnapshotView snapshot) {
        try {
            return new EvidenceSetSnapshotResponse(
                    snapshot.evidenceSetSnapshotId(), snapshot.taskId(), snapshot.projectId(),
                    snapshot.resolutionId(), snapshot.purpose(), snapshot.memberCount(),
                    snapshot.contentDigest(),
                    objectMapper.readTree(snapshot.eligibilitySummaryJson()),
                    snapshot.createdBy(), snapshot.createdAt(),
                    snapshot.members().stream().map(this::snapshotMemberResponse).toList());
        } catch (JacksonException exception) {
            throw new IllegalStateException("EvidenceSetSnapshot eligibilitySummary is invalid", exception);
        }
    }

    private EvidenceSetSnapshotMemberResponse snapshotMemberResponse(
            com.serviceos.evidence.api.EvidenceSetSnapshotMemberView member
    ) {
        return new EvidenceSetSnapshotMemberResponse(
                member.memberId(), member.evidenceSlotId(), member.evidenceItemId(),
                member.evidenceRevisionId(), member.revisionNumber(), member.revisionStatus(),
                member.contentDigest(), member.validationDigest(), member.memberOrdinal());
    }

    private CorrectionResubmissionResponse resubmissionResponse(CorrectionResubmissionView round) {
        return new CorrectionResubmissionResponse(
                round.correctionResubmissionId(), round.correctionCaseId(),
                round.resubmissionOrdinal(), round.evidenceSetSnapshotId(),
                round.snapshotContentDigest(), round.submittedBy(), round.submittedAt());
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null ? objectMapper.createObjectNode() : node);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("captureMetadata cannot be serialized", exception);
        }
    }

    record NetworkPortalBeginEvidenceUploadRequest(
            UUID evidenceItemId,
            @NotBlank @Size(max = 255) String originalFileName,
            @NotBlank @Size(max = 255) String declaredMimeType,
            long expectedSize,
            @NotBlank String expectedSha256,
            @NotNull JsonNode captureMetadata,
            @NotBlank @Size(max = 128) String onBehalfOf,
            @NotBlank @Size(max = 500) String onBehalfReason
    ) {
    }

    record FinalizeEvidenceUploadRequest(
            @NotBlank String actualSha256,
            @NotBlank @Size(max = 160) String finalizeCommandId
    ) {
    }

    record ResubmitCorrectionCaseRequest(@NotNull UUID evidenceSetSnapshotId) {
    }

    record CreateEvidenceSetSnapshotOnBehalfRequest(@NotNull List<UUID> memberRevisionIds) {
    }

    record EvidenceSetSnapshotResponse(
            UUID evidenceSetSnapshotId,
            UUID taskId,
            UUID projectId,
            UUID resolutionId,
            String purpose,
            int memberCount,
            String contentDigest,
            JsonNode eligibilitySummary,
            String createdBy,
            Instant createdAt,
            List<EvidenceSetSnapshotMemberResponse> members
    ) {
    }

    record EvidenceSetSnapshotMemberResponse(
            UUID memberId,
            UUID evidenceSlotId,
            UUID evidenceItemId,
            UUID evidenceRevisionId,
            int revisionNumber,
            String revisionStatus,
            String contentDigest,
            String validationDigest,
            int memberOrdinal
    ) {
    }

    record EvidenceUploadSessionResponse(
            UUID uploadSessionId,
            UUID fileId,
            UUID taskId,
            UUID evidenceSlotId,
            UUID evidenceItemId,
            String status,
            String uploadMethod,
            String uploadUrl,
            Map<String, String> requiredHeaders,
            Instant uploadAuthorizationExpiresAt,
            Instant sessionExpiresAt
    ) {
    }

    record EvidenceItemResponse(
            UUID evidenceItemId,
            UUID taskId,
            UUID projectId,
            UUID evidenceSlotId,
            int itemOrdinal,
            String status,
            String createdBy,
            Instant createdAt,
            List<EvidenceRevisionResponse> revisions
    ) {
    }

    record EvidenceRevisionResponse(
            UUID evidenceRevisionId,
            UUID evidenceItemId,
            UUID evidenceSlotId,
            UUID taskId,
            UUID projectId,
            int revisionNumber,
            UUID fileObjectId,
            String contentDigest,
            String mimeType,
            long sizeBytes,
            JsonNode captureMetadata,
            String status,
            UUID sourceUploadSessionId,
            String createdBy,
            Instant createdAt
    ) {
    }

    record CorrectionCaseResponse(
            UUID correctionCaseId,
            UUID projectId,
            UUID taskId,
            UUID sourceReviewCaseId,
            UUID sourceReviewDecisionId,
            UUID sourceEvidenceSetSnapshotId,
            String status,
            UUID latestResubmissionSnapshotId,
            List<CorrectionResubmissionResponse> resubmissions
    ) {
    }

    record CorrectionResubmissionResponse(
            UUID correctionResubmissionId,
            UUID correctionCaseId,
            int resubmissionOrdinal,
            UUID evidenceSetSnapshotId,
            String snapshotContentDigest,
            String submittedBy,
            Instant submittedAt
    ) {
    }
}
