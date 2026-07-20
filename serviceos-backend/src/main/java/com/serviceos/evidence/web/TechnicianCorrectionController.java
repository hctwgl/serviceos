package com.serviceos.evidence.web;

import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.evidence.api.TechnicianBeginCorrectionEvidenceUploadCommand;
import com.serviceos.evidence.api.TechnicianCorrectionService;
import com.serviceos.evidence.api.TechnicianCorrectionView;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.ClientMetadata;
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
import java.util.UUID;

/**
 * Technician Portal 整改 HTTP 边界；请求只接受资源 ID、乐观锁版本和最小上传声明。
 *
 * <p>M361：资料路径透传 {@code X-ServiceOS-Client-Kind}，由应用层权威复检能力。</p>
 * <p>M362：列表/claim/start 同样透传 clientKind，供源 Task 冻结 Bundle 能力预检注解。</p>
 */
@RestController
@RequestMapping("/api/v1/technician/me/corrections")
final class TechnicianCorrectionController {
    private final TechnicianCorrectionService corrections;
    private final CurrentPrincipalProvider principals;

    TechnicianCorrectionController(
            TechnicianCorrectionService corrections, CurrentPrincipalProvider principals
    ) {
        this.corrections = corrections;
        this.principals = principals;
    }

    @GetMapping
    List<TechnicianCorrectionView> list(
            @RequestHeader("X-Technician-Context") String context,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestAttribute(value = ClientMetadata.KIND_ATTRIBUTE, required = false) String clientKind
    ) {
        return corrections.list(principals.current(), correlationId, context, clientKind);
    }

    @PostMapping("/{correctionCaseId}:claim")
    TechnicianCorrectionView claim(
            @PathVariable UUID correctionCaseId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") @Pattern(regexp = "^\"[1-9][0-9]*\"$") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestAttribute(value = ClientMetadata.KIND_ATTRIBUTE, required = false) String clientKind
    ) {
        return corrections.claim(principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                context, clientKind, correctionCaseId, version(ifMatch));
    }

    @PostMapping("/{correctionCaseId}:start")
    TechnicianCorrectionView start(
            @PathVariable UUID correctionCaseId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") @Pattern(regexp = "^\"[1-9][0-9]*\"$") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestAttribute(value = ClientMetadata.KIND_ATTRIBUTE, required = false) String clientKind
    ) {
        return corrections.start(principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                context, clientKind, correctionCaseId, version(ifMatch));
    }

    @GetMapping("/{correctionCaseId}/evidence-slots")
    List<TechnicianEvidenceController.TechnicianEvidenceSlotResponse> listSlots(
            @PathVariable UUID correctionCaseId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestAttribute(value = ClientMetadata.KIND_ATTRIBUTE, required = false) String clientKind
    ) {
        return corrections.listSlots(
                        principals.current(), correlationId, context, clientKind, correctionCaseId)
                .stream().map(TechnicianEvidenceController::slotResponse).toList();
    }

    @GetMapping("/{correctionCaseId}/evidence-items")
    List<TechnicianEvidenceController.TechnicianEvidenceItemResponse> listItems(
            @PathVariable UUID correctionCaseId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestAttribute(value = ClientMetadata.KIND_ATTRIBUTE, required = false) String clientKind
    ) {
        return corrections.listItems(
                        principals.current(), correlationId, context, clientKind, correctionCaseId)
                .stream().map(TechnicianEvidenceController::itemResponse).toList();
    }

    @PostMapping("/{correctionCaseId}/evidence-slots/{slotId}/upload-sessions")
    ResponseEntity<TechnicianEvidenceController.TechnicianEvidenceUploadSessionResponse> beginUpload(
            @PathVariable UUID correctionCaseId,
            @PathVariable UUID slotId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestAttribute(value = ClientMetadata.KIND_ATTRIBUTE, required = false) String clientKind,
            @Valid @RequestBody BeginUploadRequest request
    ) {
        EvidenceUploadSessionView session = corrections.beginUpload(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey), context,
                clientKind, correctionCaseId, slotId, new TechnicianBeginCorrectionEvidenceUploadCommand(
                        request.evidenceItemId(), request.originalFileName(),
                        request.declaredMimeType(), request.expectedSize(), request.expectedSha256(),
                        request.captureSource(), request.capturedAt()));
        return ResponseEntity.created(URI.create("/api/v1/technician/me/corrections/"
                        + correctionCaseId + "/evidence-slots/" + slotId + "/upload-sessions/"
                        + session.uploadSessionId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(TechnicianEvidenceController.sessionResponse(session));
    }

    @PostMapping("/{correctionCaseId}/evidence-slots/{slotId}/upload-sessions/{uploadSessionId}:finalize")
    ResponseEntity<TechnicianEvidenceController.TechnicianEvidenceItemResponse> finalizeUpload(
            @PathVariable UUID correctionCaseId,
            @PathVariable UUID slotId,
            @PathVariable UUID uploadSessionId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestAttribute(value = ClientMetadata.KIND_ATTRIBUTE, required = false) String clientKind,
            @Valid @RequestBody FinalizeUploadRequest request
    ) {
        EvidenceItemView item = corrections.finalizeUpload(
                principals.current(), new CommandMetadata(correlationId, request.finalizeCommandId()),
                context, clientKind, correctionCaseId, slotId, uploadSessionId,
                request.actualSha256(), request.finalizeCommandId());
        return ResponseEntity.created(URI.create("/api/v1/technician/me/corrections/"
                        + correctionCaseId + "/evidence-items/" + item.evidenceItemId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(TechnicianEvidenceController.itemResponse(item));
    }

    @PostMapping("/{correctionCaseId}/evidence-set-snapshots")
    ResponseEntity<TechnicianEvidenceController.TechnicianEvidenceSetSnapshotResponse> createSnapshot(
            @PathVariable UUID correctionCaseId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestAttribute(value = ClientMetadata.KIND_ATTRIBUTE, required = false) String clientKind,
            @Valid @RequestBody SnapshotRequest request
    ) {
        EvidenceSetSnapshotView snapshot = corrections.createSnapshot(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey), context,
                clientKind, correctionCaseId, request.memberRevisionIds());
        return ResponseEntity.created(URI.create("/api/v1/evidence-set-snapshots/"
                        + snapshot.evidenceSetSnapshotId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(TechnicianEvidenceController.snapshotResponse(snapshot));
    }

    @PostMapping("/{correctionCaseId}:resubmit")
    TechnicianCorrectionView resubmit(
            @PathVariable UUID correctionCaseId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestAttribute(value = ClientMetadata.KIND_ATTRIBUTE, required = false) String clientKind,
            @Valid @RequestBody ResubmitRequest request
    ) {
        return corrections.resubmit(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                context, clientKind, correctionCaseId, request.evidenceSetSnapshotId());
    }

    private static long version(String ifMatch) {
        return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
    }

    record BeginUploadRequest(
            UUID evidenceItemId,
            @NotBlank @Size(max = 255) String originalFileName,
            @NotBlank @Size(max = 128) String declaredMimeType,
            @Positive long expectedSize,
            @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{64}$") String expectedSha256,
            @NotBlank @Pattern(regexp = "^(CAMERA|GALLERY|FILE)$") String captureSource,
            @NotNull Instant capturedAt
    ) {
    }

    record FinalizeUploadRequest(
            @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{64}$") String actualSha256,
            @NotBlank @Size(max = 160) String finalizeCommandId
    ) {
    }

    record SnapshotRequest(@NotNull @Size(min = 1, max = 100) List<@NotNull UUID> memberRevisionIds) {
    }

    record ResubmitRequest(@NotNull UUID evidenceSetSnapshotId) {
    }
}
