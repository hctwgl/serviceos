package com.serviceos.evidence.web;

import com.serviceos.evidence.api.CloseCorrectionCaseCommand;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.CorrectionResubmissionView;
import com.serviceos.evidence.api.ResubmitCorrectionCaseCommand;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** CorrectionCase HTTP 边界；租户与主体只从受信 JWT 获取。 */
@RestController
@RequestMapping("/api/v1")
final class CorrectionCaseController {
    private final CorrectionCaseService corrections;
    private final CurrentPrincipalProvider principals;

    CorrectionCaseController(CorrectionCaseService corrections, CurrentPrincipalProvider principals) {
        this.corrections = corrections;
        this.principals = principals;
    }

    @GetMapping("/correction-cases/{correctionCaseId}")
    CorrectionCaseResponse get(
            @PathVariable UUID correctionCaseId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(corrections.get(principals.current(), correlationId, correctionCaseId));
    }

    @PostMapping("/correction-cases/{correctionCaseId}:resubmit")
    CorrectionCaseResponse resubmit(
            @PathVariable UUID correctionCaseId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody ResubmitCorrectionCaseRequest request
    ) {
        return response(corrections.resubmit(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new ResubmitCorrectionCaseCommand(correctionCaseId, request.evidenceSetSnapshotId())));
    }

    @PostMapping("/correction-cases/{correctionCaseId}:close")
    CorrectionCaseResponse close(
            @PathVariable UUID correctionCaseId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody(required = false) CloseCorrectionCaseRequest request
    ) {
        String note = request == null ? null : request.note();
        return response(corrections.close(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new CloseCorrectionCaseCommand(correctionCaseId, note)));
    }

    private CorrectionCaseResponse response(CorrectionCaseView correction) {
        return new CorrectionCaseResponse(
                correction.correctionCaseId(), correction.projectId(), correction.taskId(),
                correction.sourceReviewCaseId(), correction.sourceReviewDecisionId(),
                correction.sourceEvidenceSetSnapshotId(), correction.sourceSnapshotContentDigest(),
                correction.reasonCodes(), correction.correctionTaskId(), correction.status(),
                correction.createdBy(),
                correction.createdAt(), correction.latestResubmissionSnapshotId(),
                correction.closedBy(), correction.closedAt(),
                correction.resubmissions().stream().map(this::round).toList());
    }

    private CorrectionResubmissionResponse round(CorrectionResubmissionView round) {
        return new CorrectionResubmissionResponse(
                round.correctionResubmissionId(), round.correctionCaseId(), round.resubmissionOrdinal(),
                round.evidenceSetSnapshotId(), round.snapshotContentDigest(),
                round.submittedBy(), round.submittedAt());
    }

    record ResubmitCorrectionCaseRequest(UUID evidenceSetSnapshotId) {
    }

    record CloseCorrectionCaseRequest(String note) {
    }

    record CorrectionCaseResponse(
            UUID correctionCaseId,
            UUID projectId,
            UUID taskId,
            UUID sourceReviewCaseId,
            UUID sourceReviewDecisionId,
            UUID sourceEvidenceSetSnapshotId,
            String sourceSnapshotContentDigest,
            List<String> reasonCodes,
            UUID correctionTaskId,
            String status,
            String createdBy,
            Instant createdAt,
            UUID latestResubmissionSnapshotId,
            String closedBy,
            Instant closedAt,
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
