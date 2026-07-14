package com.serviceos.evidence.web;

import com.serviceos.evidence.api.BeginEvidenceUploadCommand;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.evidence.api.FinalizeEvidenceUploadCommand;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

/** Evidence 上传与资料查询 HTTP 边界；租户与主体只从受信 JWT 获取。 */
@RestController
@RequestMapping("/api/v1")
final class EvidenceItemController {
    private final EvidenceCommandService evidence;
    private final CurrentPrincipalProvider principals;
    private final ObjectMapper objectMapper;

    EvidenceItemController(
            EvidenceCommandService evidence,
            CurrentPrincipalProvider principals,
            ObjectMapper objectMapper
    ) {
        this.evidence = evidence;
        this.principals = principals;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions")
    ResponseEntity<EvidenceUploadSessionResponse> beginUpload(
            @PathVariable UUID taskId,
            @PathVariable UUID slotId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody BeginEvidenceUploadRequest request
    ) {
        EvidenceUploadSessionView session = evidence.beginUpload(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new BeginEvidenceUploadCommand(
                        taskId, slotId, request.evidenceItemId(), request.originalFileName(),
                        request.declaredMimeType(), request.expectedSize(), request.expectedSha256(),
                        writeJson(request.captureMetadata())));
        return ResponseEntity
                .created(URI.create("/api/v1/tasks/" + taskId + "/evidence-slots/" + slotId
                        + "/upload-sessions/" + session.uploadSessionId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(sessionResponse(session));
    }

    @PostMapping("/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions/{uploadSessionId}:finalize")
    ResponseEntity<EvidenceItemResponse> finalizeUpload(
            @PathVariable UUID taskId,
            @PathVariable UUID slotId,
            @PathVariable UUID uploadSessionId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody FinalizeEvidenceUploadRequest request
    ) {
        EvidenceItemView item = evidence.finalizeUpload(
                principals.current(),
                new CommandMetadata(correlationId, request.finalizeCommandId()),
                new FinalizeEvidenceUploadCommand(
                        taskId, slotId, uploadSessionId,
                        request.actualSha256(), request.finalizeCommandId()));
        return ResponseEntity
                .created(URI.create("/api/v1/evidence-items/" + item.evidenceItemId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(itemResponse(item));
    }

    @GetMapping("/tasks/{taskId}/evidence-items")
    List<EvidenceItemResponse> list(
            @PathVariable UUID taskId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return evidence.listForTask(principals.current(), correlationId, taskId).stream()
                .map(this::itemResponse).toList();
    }

    @GetMapping("/evidence-items/{itemId}")
    EvidenceItemResponse get(
            @PathVariable UUID itemId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return itemResponse(evidence.get(principals.current(), correlationId, itemId));
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
                    revision.sourceUploadSessionId(), revision.createdBy(), revision.createdAt(),
                    revision.validations().stream().map(this::validationResponse).toList());
        } catch (JacksonException exception) {
            throw new IllegalStateException("EvidenceRevision captureMetadata is invalid", exception);
        }
    }

    private EvidenceValidationResponse validationResponse(
            com.serviceos.evidence.api.EvidenceValidationView validation
    ) {
        try {
            return new EvidenceValidationResponse(
                    validation.validationId(), validation.evidenceRevisionId(),
                    validation.checkType(), validation.severity(), validation.result(),
                    validation.reasonCode(), validation.message(),
                    objectMapper.readTree(validation.detailsJson() == null ? "{}" : validation.detailsJson()),
                    validation.validatorName(), validation.validatorVersion(), validation.createdAt());
        } catch (JacksonException exception) {
            throw new IllegalStateException("EvidenceValidation details are invalid", exception);
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null ? objectMapper.createObjectNode() : node);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("captureMetadata cannot be serialized", exception);
        }
    }

    record BeginEvidenceUploadRequest(
            UUID evidenceItemId,
            String originalFileName,
            String declaredMimeType,
            long expectedSize,
            String expectedSha256,
            JsonNode captureMetadata
    ) {
    }

    record FinalizeEvidenceUploadRequest(String actualSha256, String finalizeCommandId) {
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
            Instant createdAt,
            List<EvidenceValidationResponse> validations
    ) {
    }

    record EvidenceValidationResponse(
            UUID validationId,
            UUID evidenceRevisionId,
            String checkType,
            String severity,
            String result,
            String reasonCode,
            String message,
            JsonNode details,
            String validatorName,
            String validatorVersion,
            Instant createdAt
    ) {
    }
}
