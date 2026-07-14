package com.serviceos.evidence.web;

import com.serviceos.evidence.api.CreateReviewCaseCommand;
import com.serviceos.evidence.api.DecideReviewCaseCommand;
import com.serviceos.evidence.api.ForceApproveReviewCaseCommand;
import com.serviceos.evidence.api.ReopenReviewCaseCommand;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.evidence.api.ReviewDecisionView;
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

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** ReviewCase HTTP 边界；租户与主体只从受信 JWT 获取。 */
@RestController
@RequestMapping("/api/v1")
final class ReviewCaseController {
    private final ReviewCaseService reviews;
    private final CurrentPrincipalProvider principals;

    ReviewCaseController(ReviewCaseService reviews, CurrentPrincipalProvider principals) {
        this.reviews = reviews;
        this.principals = principals;
    }

    @PostMapping("/review-cases")
    ResponseEntity<ReviewCaseResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody CreateReviewCaseRequest request
    ) {
        ReviewCaseView reviewCase = reviews.create(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new CreateReviewCaseCommand(request.evidenceSetSnapshotId(), request.policyVersion()));
        return ResponseEntity
                .created(URI.create("/api/v1/review-cases/" + reviewCase.reviewCaseId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(response(reviewCase));
    }

    @GetMapping("/review-cases/{reviewCaseId}")
    ReviewCaseResponse get(
            @PathVariable UUID reviewCaseId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(reviews.get(principals.current(), correlationId, reviewCaseId));
    }

    @PostMapping("/review-cases/{reviewCaseId}:decide")
    ReviewCaseResponse decide(
            @PathVariable UUID reviewCaseId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody DecideReviewCaseRequest request
    ) {
        return response(reviews.decide(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new DecideReviewCaseCommand(
                        reviewCaseId, request.decision(), request.reasonCodes(), request.note())));
    }

    @PostMapping("/review-cases/{reviewCaseId}:force-approve")
    ReviewCaseResponse forceApprove(
            @PathVariable UUID reviewCaseId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody ForceApproveReviewCaseRequest request
    ) {
        return response(reviews.forceApprove(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new ForceApproveReviewCaseCommand(
                        reviewCaseId, request.reasonCodes(), request.approvalRef(), request.note())));
    }

    @PostMapping("/review-cases/{reviewCaseId}:reopen")
    ResponseEntity<ReviewCaseResponse> reopen(
            @PathVariable UUID reviewCaseId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody ReopenReviewCaseRequest request
    ) {
        ReviewCaseView reviewCase = reviews.reopen(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new ReopenReviewCaseCommand(
                        reviewCaseId, request.reason(), request.triggerRef(), request.approvalRef()));
        return ResponseEntity
                .created(URI.create("/api/v1/review-cases/" + reviewCase.reviewCaseId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(response(reviewCase));
    }

    private ReviewCaseResponse response(ReviewCaseView reviewCase) {
        return new ReviewCaseResponse(
                reviewCase.reviewCaseId(), reviewCase.projectId(), reviewCase.taskId(),
                reviewCase.evidenceSetSnapshotId(), reviewCase.snapshotContentDigest(),
                reviewCase.scopeType(), reviewCase.policyVersion(), reviewCase.status(),
                reviewCase.createdBy(), reviewCase.createdAt(), reviewCase.decidedAt(),
                reviewCase.reopenedFromReviewCaseId(), reviewCase.reopenTriggerRef(),
                reviewCase.decisions().stream().map(this::decision).toList());
    }

    private ReviewDecisionResponse decision(ReviewDecisionView decision) {
        return new ReviewDecisionResponse(
                decision.reviewDecisionId(), decision.reviewCaseId(), decision.decisionOrdinal(),
                decision.decision(), decision.decisionSource(), decision.reasonCodes(), decision.note(),
                decision.approvalRef(), decision.decidedBy(), decision.decidedAt());
    }

    record CreateReviewCaseRequest(UUID evidenceSetSnapshotId, String policyVersion) {
    }

    record DecideReviewCaseRequest(String decision, List<String> reasonCodes, String note) {
    }

    record ForceApproveReviewCaseRequest(List<String> reasonCodes, String approvalRef, String note) {
    }

    record ReopenReviewCaseRequest(String reason, String triggerRef, String approvalRef) {
    }

    record ReviewCaseResponse(
            UUID reviewCaseId,
            UUID projectId,
            UUID taskId,
            UUID evidenceSetSnapshotId,
            String snapshotContentDigest,
            String scopeType,
            String policyVersion,
            String status,
            String createdBy,
            Instant createdAt,
            Instant decidedAt,
            UUID reopenedFromReviewCaseId,
            String reopenTriggerRef,
            List<ReviewDecisionResponse> decisions
    ) {
    }

    record ReviewDecisionResponse(
            UUID reviewDecisionId,
            UUID reviewCaseId,
            int decisionOrdinal,
            String decision,
            String decisionSource,
            List<String> reasonCodes,
            String note,
            String approvalRef,
            String decidedBy,
            Instant decidedAt
    ) {
    }
}
