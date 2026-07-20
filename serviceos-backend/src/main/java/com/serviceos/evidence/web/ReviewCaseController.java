package com.serviceos.evidence.web;

import com.serviceos.evidence.api.CreateReviewCaseCommand;
import com.serviceos.evidence.api.CreateClientReviewCaseCommand;
import com.serviceos.evidence.api.DecideReviewCaseCommand;
import com.serviceos.evidence.api.DecideReviewCaseResult;
import com.serviceos.evidence.api.ForceApproveReviewCaseCommand;
import com.serviceos.evidence.api.ReopenReviewCaseCommand;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.evidence.api.ReviewDecisionView;
import com.serviceos.evidence.api.ReviewTargetDecisionCommand;
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

    @PostMapping("/internal/client-review-cases")
    ResponseEntity<ReviewCaseResponse> createClient(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody CreateClientReviewCaseRequest request
    ) {
        ReviewCaseView reviewCase = reviews.createClient(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new CreateClientReviewCaseCommand(
                        request.sourceReviewCaseId(), request.externalSubmissionRef(),
                        request.callbackBatchRef(), request.mappingVersionId(), request.policyVersion()));
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
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody DecideReviewCaseRequest request
    ) {
        List<ReviewTargetDecisionCommand> targets = request.targetDecisions() == null
                ? List.of()
                : request.targetDecisions().stream()
                        .map(item -> new ReviewTargetDecisionCommand(
                                item.targetType(), item.targetId(), item.targetVersion(),
                                item.decision(), item.reasonCodes(), item.note()))
                        .toList();
        DecideReviewCaseResult result = reviews.decide(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new DecideReviewCaseCommand(
                        reviewCaseId, targets, request.note(), aggregateVersion(ifMatch)));
        return response(result.reviewCase(), result.correctionCaseId());
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
        return response(reviewCase, null);
    }

    private ReviewCaseResponse response(ReviewCaseView reviewCase, UUID correctionCaseId) {
        String derived = "OPEN".equals(reviewCase.status()) || "REOPENED".equals(reviewCase.status())
                ? null
                : reviewCase.status();
        return new ReviewCaseResponse(
                reviewCase.reviewCaseId(), reviewCase.projectId(), reviewCase.taskId(),
                reviewCase.reviewTaskId(),
                reviewCase.evidenceSetSnapshotId(), reviewCase.snapshotContentDigest(),
                reviewCase.scopeType(), reviewCase.origin(), reviewCase.policyVersion(), reviewCase.status(),
                reviewCase.createdBy(), reviewCase.createdAt(), reviewCase.decidedAt(),
                reviewCase.sourceReviewCaseId(), reviewCase.externalSubmissionRef(),
                reviewCase.callbackBatchRef(), reviewCase.mappingVersionId(),
                reviewCase.reopenedFromReviewCaseId(), reviewCase.reopenTriggerRef(),
                reviewCase.decisions().stream().map(this::decision).toList(),
                reviewCase.aggregateVersion(),
                derived,
                correctionCaseId);
    }

    private static long aggregateVersion(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("\"[1-9][0-9]*\"")) {
            throw new IllegalArgumentException("If-Match must contain one quoted positive aggregate version");
        }
        return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
    }

    private ReviewDecisionResponse decision(ReviewDecisionView decision) {
        return new ReviewDecisionResponse(
                decision.reviewDecisionId(), decision.reviewCaseId(), decision.decisionOrdinal(),
                decision.decision(), decision.decisionSource(), decision.reasonCodes(), decision.note(),
                decision.approvalRef(), decision.decidedBy(), decision.decidedAt());
    }

    record CreateReviewCaseRequest(UUID evidenceSetSnapshotId, String policyVersion) {
    }

    record CreateClientReviewCaseRequest(
            UUID sourceReviewCaseId,
            String externalSubmissionRef,
            String callbackBatchRef,
            String mappingVersionId,
            String policyVersion
    ) {
    }

    record DecideReviewCaseRequest(List<TargetDecisionRequest> targetDecisions, String note) {
    }

    record TargetDecisionRequest(
            String targetType,
            UUID targetId,
            int targetVersion,
            String decision,
            List<String> reasonCodes,
            String note
    ) {
    }

    record ForceApproveReviewCaseRequest(List<String> reasonCodes, String approvalRef, String note) {
    }

    record ReopenReviewCaseRequest(String reason, String triggerRef, String approvalRef) {
    }

    record ReviewCaseResponse(
            UUID reviewCaseId,
            UUID projectId,
            UUID taskId,
            UUID reviewTaskId,
            UUID evidenceSetSnapshotId,
            String snapshotContentDigest,
            String scopeType,
            String origin,
            String policyVersion,
            String status,
            String createdBy,
            Instant createdAt,
            Instant decidedAt,
            UUID sourceReviewCaseId,
            String externalSubmissionRef,
            String callbackBatchRef,
            String mappingVersionId,
            UUID reopenedFromReviewCaseId,
            String reopenTriggerRef,
            List<ReviewDecisionResponse> decisions,
            long aggregateVersion,
            String derivedOverallDecision,
            UUID correctionCaseId
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
