package com.serviceos.evidence.web;

import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceSetSnapshotMemberView;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.evidence.api.FinalizeEvidenceUploadCommand;
import com.serviceos.evidence.api.TechnicianBeginEvidenceUploadCommand;
import com.serviceos.evidence.api.TechnicianEvidenceService;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Technician 在线资料 HTTP 边界；不暴露 uploader、文件对象 ID、原始 CaptureMetadata 或永久 URL。 */
@RestController
@RequestMapping("/api/v1/technician/me/tasks/{taskId}")
final class TechnicianEvidenceController {
    private final TechnicianEvidenceService evidence;
    private final CurrentPrincipalProvider principals;

    TechnicianEvidenceController(TechnicianEvidenceService evidence, CurrentPrincipalProvider principals) {
        this.evidence = evidence;
        this.principals = principals;
    }

    @GetMapping("/evidence-slots")
    List<TechnicianEvidenceSlotResponse> listSlots(
            @PathVariable UUID taskId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return evidence.listSlots(principals.current(), correlationId, context, taskId).stream()
                .map(TechnicianEvidenceController::slotResponse)
                .toList();
    }

    @GetMapping("/evidence-items")
    List<TechnicianEvidenceItemResponse> listItems(
            @PathVariable UUID taskId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return evidence.listItems(principals.current(), correlationId, context, taskId).stream()
                .map(TechnicianEvidenceController::itemResponse)
                .toList();
    }

    @PostMapping("/evidence-slots/{slotId}/upload-sessions")
    ResponseEntity<TechnicianEvidenceUploadSessionResponse> beginUpload(
            @PathVariable UUID taskId,
            @PathVariable UUID slotId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody TechnicianBeginEvidenceUploadRequest request
    ) {
        EvidenceUploadSessionView session = evidence.beginUpload(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey), context,
                new TechnicianBeginEvidenceUploadCommand(
                        taskId, slotId, request.evidenceItemId(), request.originalFileName(),
                        request.declaredMimeType(), request.expectedSize(), request.expectedSha256(),
                        request.captureSource(), request.capturedAt()));
        return ResponseEntity.created(URI.create("/api/v1/technician/me/tasks/" + taskId
                        + "/evidence-slots/" + slotId + "/upload-sessions/" + session.uploadSessionId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(sessionResponse(session));
    }

    @PostMapping("/evidence-slots/{slotId}/upload-sessions/{uploadSessionId}:finalize")
    ResponseEntity<TechnicianEvidenceItemResponse> finalizeUpload(
            @PathVariable UUID taskId,
            @PathVariable UUID slotId,
            @PathVariable UUID uploadSessionId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody TechnicianFinalizeEvidenceUploadRequest request
    ) {
        EvidenceItemView item = evidence.finalizeUpload(
                principals.current(), new CommandMetadata(correlationId, request.finalizeCommandId()), context,
                new FinalizeEvidenceUploadCommand(taskId, slotId, uploadSessionId,
                        request.actualSha256(), request.finalizeCommandId()));
        return ResponseEntity.created(URI.create("/api/v1/technician/me/tasks/" + taskId
                        + "/evidence-items/" + item.evidenceItemId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(itemResponse(item));
    }

    @PostMapping("/evidence-set-snapshots")
    ResponseEntity<TechnicianEvidenceSetSnapshotResponse> createSnapshot(
            @PathVariable UUID taskId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody TechnicianCreateEvidenceSetSnapshotRequest request
    ) {
        EvidenceSetSnapshotView snapshot = evidence.createTaskSubmissionSnapshot(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey), context,
                taskId, request.memberRevisionIds());
        return ResponseEntity.created(URI.create("/api/v1/evidence-set-snapshots/"
                        + snapshot.evidenceSetSnapshotId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(snapshotResponse(snapshot));
    }


    static TechnicianEvidenceSlotResponse slotResponse(EvidenceSlotView slot) {
        return new TechnicianEvidenceSlotResponse(
                slot.slotId(), slot.requirementCode(), slot.occurrenceKey(), slot.requirementName(),
                slot.mediaType(), slot.required(), slot.minCount(), slot.maxCount(), slot.status(),
                slot.active(), slot.transition(), slot.requiredDisposition());
    }

    static TechnicianEvidenceUploadSessionResponse sessionResponse(EvidenceUploadSessionView session) {
        return new TechnicianEvidenceUploadSessionResponse(
                session.uploadSessionId(), session.evidenceSlotId(), session.evidenceItemId(),
                session.status(), session.uploadMethod(), session.uploadUrl(), session.requiredHeaders(),
                session.uploadAuthorizationExpiresAt(), session.sessionExpiresAt());
    }

    static TechnicianEvidenceItemResponse itemResponse(EvidenceItemView item) {
        return new TechnicianEvidenceItemResponse(
                item.evidenceItemId(), item.taskId(), item.evidenceSlotId(), item.itemOrdinal(),
                item.status(), item.createdAt(), item.revisions().stream()
                        .map(TechnicianEvidenceController::revisionResponse).toList());
    }

    static TechnicianEvidenceSetSnapshotResponse snapshotResponse(EvidenceSetSnapshotView snapshot) {
        return new TechnicianEvidenceSetSnapshotResponse(
                snapshot.evidenceSetSnapshotId(), snapshot.taskId(), snapshot.purpose(),
                snapshot.memberCount(), snapshot.contentDigest(), snapshot.createdAt(),
                snapshot.members().stream().map(TechnicianEvidenceController::snapshotMemberResponse).toList());
    }

    private static TechnicianEvidenceSetMemberResponse snapshotMemberResponse(EvidenceSetSnapshotMemberView member) {
        return new TechnicianEvidenceSetMemberResponse(
                member.evidenceSlotId(), member.evidenceItemId(), member.evidenceRevisionId(),
                member.revisionNumber(), member.revisionStatus(), member.contentDigest(), member.memberOrdinal());
    }


    private static TechnicianEvidenceRevisionResponse revisionResponse(EvidenceRevisionView revision) {
        return new TechnicianEvidenceRevisionResponse(
                revision.evidenceRevisionId(), revision.revisionNumber(), revision.contentDigest(),
                revision.mimeType(), revision.sizeBytes(), revision.status(), revision.createdAt());
    }

    record TechnicianBeginEvidenceUploadRequest(
            UUID evidenceItemId,
            @NotBlank @Size(max = 255) String originalFileName,
            @NotBlank @Size(max = 128) String declaredMimeType,
            @Positive long expectedSize,
            @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{64}$") String expectedSha256,
            @NotBlank @Pattern(regexp = "^(CAMERA|GALLERY|FILE)$") String captureSource,
            @NotNull Instant capturedAt
    ) {
    }

    record TechnicianFinalizeEvidenceUploadRequest(
            @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{64}$") String actualSha256,
            @NotBlank @Size(max = 160) String finalizeCommandId
    ) {
    }

    record TechnicianCreateEvidenceSetSnapshotRequest(
            @NotNull @Size(min = 1, max = 100) List<@NotNull UUID> memberRevisionIds
    ) {
    }


    record TechnicianEvidenceSlotResponse(
            UUID slotId,
            String requirementCode,
            String occurrenceKey,
            String requirementName,
            String mediaType,
            boolean required,
            int minCount,
            Integer maxCount,
            String status,
            boolean active,
            String transition,
            String requiredDisposition
    ) {
    }

    record TechnicianEvidenceUploadSessionResponse(
            UUID uploadSessionId,
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

    record TechnicianEvidenceItemResponse(
            UUID evidenceItemId,
            UUID taskId,
            UUID evidenceSlotId,
            int itemOrdinal,
            String status,
            Instant createdAt,
            List<TechnicianEvidenceRevisionResponse> revisions
    ) {
    }

    record TechnicianEvidenceRevisionResponse(
            UUID evidenceRevisionId,
            int revisionNumber,
            String contentDigest,
            String mimeType,
            long sizeBytes,
            String status,
            Instant createdAt
    ) {
    }

    record TechnicianEvidenceSetSnapshotResponse(
            UUID evidenceSetSnapshotId,
            UUID taskId,
            String purpose,
            int memberCount,
            String contentDigest,
            Instant createdAt,
            List<TechnicianEvidenceSetMemberResponse> members
    ) {
    }

    record TechnicianEvidenceSetMemberResponse(
            UUID evidenceSlotId,
            UUID evidenceItemId,
            UUID evidenceRevisionId,
            int revisionNumber,
            String revisionStatus,
            String contentDigest,
            int memberOrdinal
    ) {
    }

}
