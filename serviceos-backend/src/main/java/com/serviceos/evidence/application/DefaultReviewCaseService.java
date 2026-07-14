package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CreateReviewCaseCommand;
import com.serviceos.evidence.api.DecideReviewCaseCommand;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.evidence.api.ReviewDecisionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** ReviewCase 创建与裁决；决定只追加，Case 状态为可重建投影。 */
@Service
final class DefaultReviewCaseService implements ReviewCaseService {
    private static final String REVIEW = "evidence.review";
    private static final String READ = "evidence.read";
    private static final String CREATE_OPERATION = "evidence.review.create";
    private static final String DECIDE_OPERATION = "evidence.review.decide";
    private static final String DEFAULT_POLICY = "REVIEW_POLICY_V1";

    private final ReviewCaseRepository reviews;
    private final EvidenceSetSnapshotRepository snapshots;
    private final CorrectionCaseService corrections;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultReviewCaseService(
            ReviewCaseRepository reviews,
            EvidenceSetSnapshotRepository snapshots,
            CorrectionCaseService corrections,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.reviews = reviews;
        this.snapshots = snapshots;
        this.corrections = corrections;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReviewCaseView create(
            CurrentPrincipal principal, CommandMetadata metadata, CreateReviewCaseCommand command
    ) {
        EvidenceSetSnapshotView snapshot = snapshots.find(
                        principal.tenantId(), command.evidenceSetSnapshotId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "EvidenceSetSnapshot does not exist"));
        if (!"TASK_SUBMISSION".equals(snapshot.purpose())) {
            throw new BusinessProblem(ProblemCode.EVIDENCE_SNAPSHOT_PURPOSE_UNSUPPORTED,
                    "ReviewCase only accepts TASK_SUBMISSION EvidenceSetSnapshot");
        }
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(REVIEW, principal.tenantId(), "EvidenceSetSnapshot",
                        snapshot.evidenceSetSnapshotId().toString(), snapshot.projectId().toString()),
                metadata.correlationId());
        String policyVersion = normalizePolicy(command.policyVersion());
        String requestDigest = Sha256.digest(
                snapshot.evidenceSetSnapshotId() + "|" + snapshot.contentDigest() + "|" + policyVersion);
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision decision = idempotency.begin(context, CREATE_OPERATION, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return reviews.findCommandResult(context.tenantId(), CREATE_OPERATION, context.idempotencyKey())
                    .flatMap(id -> reviews.find(context.tenantId(), id))
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "ReviewCase replay result missing"));
        }

        Instant now = clock.instant();
        UUID reviewCaseId = UUID.randomUUID();
        ReviewCaseView created = new ReviewCaseView(
                reviewCaseId, snapshot.projectId(), snapshot.taskId(),
                snapshot.evidenceSetSnapshotId(), snapshot.contentDigest(),
                "EVIDENCE_SET_SNAPSHOT", policyVersion, "OPEN",
                principal.principalId(), now, null, List.of());
        try {
            reviews.insertCase(principal.tenantId(), created);
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_CONFLICT,
                    "A ReviewCase already exists for this EvidenceSetSnapshot");
        }
        reviews.saveCommandResult(principal.tenantId(), CREATE_OPERATION, context.idempotencyKey(), reviewCaseId);

        String payload = serialize(new ReviewCaseCreatedPayload(
                reviewCaseId, snapshot.evidenceSetSnapshotId(), snapshot.taskId(),
                snapshot.projectId(), snapshot.contentDigest(), policyVersion, now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.review-case-created", 1,
                "ReviewCase", reviewCaseId.toString(), 1L,
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                snapshot.taskId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "REVIEW_CASE_CREATED", REVIEW, "ReviewCase", reviewCaseId.toString(),
                "ALLOW", auth.matchedGrantIds(), auth.policyVersion(), "OPEN", null,
                requestDigest, metadata.correlationId(), now));
        idempotency.complete(context, CREATE_OPERATION, reviewCaseId.toString(),
                Sha256.digest(serialize(created)));
        return created;
    }

    @Override
    @Transactional
    public ReviewCaseView decide(
            CurrentPrincipal principal, CommandMetadata metadata, DecideReviewCaseCommand command
    ) {
        ReviewCaseView current = reviews.find(principal.tenantId(), command.reviewCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "ReviewCase does not exist"));
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(REVIEW, principal.tenantId(), "ReviewCase",
                        current.reviewCaseId().toString(), current.projectId().toString()),
                metadata.correlationId());
        String decision = normalizeDecision(command.decision());
        List<String> reasonCodes = normalizeReasons(command.reasonCodes(), decision);
        String note = normalizeNote(command.note());
        String requestDigest = Sha256.digest(
                current.reviewCaseId() + "|" + decision + "|" + serialize(reasonCodes) + "|" + nullToEmpty(note));
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision idempotencyDecision = idempotency.begin(context, DECIDE_OPERATION, requestDigest);
        if (idempotencyDecision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return reviews.findCommandResult(context.tenantId(), DECIDE_OPERATION, context.idempotencyKey())
                    .flatMap(id -> reviews.find(context.tenantId(), id))
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "ReviewDecision replay result missing"));
        }
        if (!"OPEN".equals(current.status())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_ALREADY_DECIDED,
                    "ReviewCase has already been decided");
        }

        Instant now = clock.instant();
        int updated = reviews.markDecided(
                principal.tenantId(), current.reviewCaseId(), "OPEN", decision, now);
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_ALREADY_DECIDED,
                    "ReviewCase has already been decided");
        }
        int ordinal = reviews.nextDecisionOrdinal(principal.tenantId(), current.reviewCaseId());
        ReviewDecisionView decisionView = new ReviewDecisionView(
                UUID.randomUUID(), current.reviewCaseId(), ordinal, decision,
                reasonCodes, note, principal.principalId(), now);
        reviews.insertDecision(principal.tenantId(), current.projectId(), decisionView);
        reviews.saveCommandResult(
                principal.tenantId(), DECIDE_OPERATION, context.idempotencyKey(), current.reviewCaseId());
        if ("REJECTED".equals(decision)) {
            corrections.openFromRejectedDecision(
                    principal.tenantId(), principal.principalId(),
                    metadata.correlationId(), metadata.idempotencyKey(),
                    current.projectId(), current.taskId(), current.reviewCaseId(),
                    decisionView.reviewDecisionId(), current.evidenceSetSnapshotId(),
                    current.snapshotContentDigest(), reasonCodes);
        }

        String payload = serialize(new ReviewDecidedPayload(
                current.reviewCaseId(), decisionView.reviewDecisionId(), current.evidenceSetSnapshotId(),
                current.taskId(), current.projectId(), decision, reasonCodes, principal.principalId(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.review-decided", 1,
                "ReviewCase", current.reviewCaseId().toString(), ordinal,
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                current.taskId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "REVIEW_CASE_DECIDED", REVIEW, "ReviewCase", current.reviewCaseId().toString(),
                "ALLOW", auth.matchedGrantIds(), auth.policyVersion(), decision, null,
                requestDigest, metadata.correlationId(), now));
        ReviewCaseView decided = reviews.find(principal.tenantId(), current.reviewCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.INTERNAL_ERROR, "Decided ReviewCase missing"));
        idempotency.complete(context, DECIDE_OPERATION, current.reviewCaseId().toString(),
                Sha256.digest(serialize(decided)));
        return decided;
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewCaseView get(CurrentPrincipal principal, String correlationId, UUID reviewCaseId) {
        ReviewCaseView reviewCase = reviews.find(principal.tenantId(), reviewCaseId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "ReviewCase does not exist"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "ReviewCase", reviewCaseId.toString(),
                reviewCase.projectId().toString()), correlationId);
        return reviewCase;
    }

    private static String normalizePolicy(String policyVersion) {
        if (policyVersion == null || policyVersion.isBlank()) {
            return DEFAULT_POLICY;
        }
        String trimmed = policyVersion.trim();
        if (trimmed.length() > 80) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "policyVersion exceeds 80 characters");
        }
        return trimmed;
    }

    private static String normalizeDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "decision is required");
        }
        String normalized = decision.trim().toUpperCase(Locale.ROOT);
        if (!"APPROVED".equals(normalized) && !"REJECTED".equals(normalized)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "decision must be APPROVED or REJECTED");
        }
        return normalized;
    }

    private static List<String> normalizeReasons(List<String> reasonCodes, String decision) {
        List<String> codes = reasonCodes == null ? List.of() : reasonCodes.stream()
                .map(code -> code == null ? "" : code.trim())
                .filter(code -> !code.isEmpty())
                .toList();
        if ("REJECTED".equals(decision) && codes.isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "REJECTED decision requires at least one reasonCode");
        }
        if (codes.size() > 20) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "reasonCodes exceeds max size 20");
        }
        for (String code : codes) {
            if (code.length() > 80) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "reasonCode exceeds 80 characters");
            }
        }
        return List.copyOf(codes);
    }

    private static String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > 1000) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "note exceeds 1000 characters");
        }
        return trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Review event serialization failed", exception);
        }
    }

    private record ReviewCaseCreatedPayload(
            UUID reviewCaseId,
            UUID evidenceSetSnapshotId,
            UUID taskId,
            UUID projectId,
            String snapshotContentDigest,
            String policyVersion,
            Instant createdAt
    ) {
    }

    private record ReviewDecidedPayload(
            UUID reviewCaseId,
            UUID reviewDecisionId,
            UUID evidenceSetSnapshotId,
            UUID taskId,
            UUID projectId,
            String decision,
            List<String> reasonCodes,
            String decidedBy,
            Instant decidedAt
    ) {
    }
}
