package com.serviceos.evidence.web;

import com.serviceos.evidence.api.CreateEvidenceSetSnapshotCommand;
import com.serviceos.evidence.api.EvidenceSetSnapshotMemberView;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
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
import java.util.UUID;

/** EvidenceSetSnapshot HTTP 边界。 */
@RestController
@RequestMapping("/api/v1")
final class EvidenceSetSnapshotController {
    private final EvidenceSetSnapshotService snapshots;
    private final CurrentPrincipalProvider principals;
    private final ObjectMapper objectMapper;

    EvidenceSetSnapshotController(
            EvidenceSetSnapshotService snapshots,
            CurrentPrincipalProvider principals,
            ObjectMapper objectMapper
    ) {
        this.snapshots = snapshots;
        this.principals = principals;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/tasks/{taskId}/evidence-set-snapshots")
    ResponseEntity<EvidenceSetSnapshotResponse> create(
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody CreateEvidenceSetSnapshotRequest request
    ) {
        EvidenceSetSnapshotView snapshot = snapshots.create(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new CreateEvidenceSetSnapshotCommand(
                        taskId, request.purpose(), request.memberRevisionIds()));
        return ResponseEntity
                .created(URI.create("/api/v1/evidence-set-snapshots/" + snapshot.evidenceSetSnapshotId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(response(snapshot));
    }

    @GetMapping("/evidence-set-snapshots/{snapshotId}")
    EvidenceSetSnapshotResponse get(
            @PathVariable UUID snapshotId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(snapshots.get(principals.current(), correlationId, snapshotId));
    }

    private EvidenceSetSnapshotResponse response(EvidenceSetSnapshotView snapshot) {
        try {
            return new EvidenceSetSnapshotResponse(
                    snapshot.evidenceSetSnapshotId(), snapshot.taskId(), snapshot.projectId(),
                    snapshot.resolutionId(), snapshot.purpose(), snapshot.memberCount(),
                    snapshot.contentDigest(),
                    objectMapper.readTree(snapshot.eligibilitySummaryJson()),
                    snapshot.createdBy(), snapshot.createdAt(),
                    snapshot.members().stream().map(this::member).toList());
        } catch (JacksonException exception) {
            throw new IllegalStateException("EvidenceSetSnapshot eligibilitySummary is invalid", exception);
        }
    }

    private EvidenceSetSnapshotMemberResponse member(EvidenceSetSnapshotMemberView member) {
        return new EvidenceSetSnapshotMemberResponse(
                member.memberId(), member.evidenceSlotId(), member.evidenceItemId(),
                member.evidenceRevisionId(), member.revisionNumber(), member.revisionStatus(),
                member.contentDigest(), member.validationDigest(), member.memberOrdinal());
    }

    record CreateEvidenceSetSnapshotRequest(String purpose, List<UUID> memberRevisionIds) {
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
}
