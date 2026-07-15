package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CreateClientReviewCaseCommand;
import com.serviceos.evidence.api.CreateReviewCaseCommand;
import com.serviceos.evidence.api.DecideReviewCaseCommand;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.ForceApproveReviewCaseCommand;
import com.serviceos.evidence.api.ReopenReviewCaseCommand;
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
    private static final String CREATE_CLIENT = "evidence.createClientReviewCase";
    private static final String FORCE_APPROVE = "evidence.forceApprove";
    private static final String REOPEN = "review.reopen";
    private static final String READ = "evidence.read";
    private static final String CREATE_OPERATION = "evidence.review.create";
    private static final String CREATE_CLIENT_OPERATION = "evidence.review.createClient";
    private static final String DECIDE_OPERATION = "evidence.review.decide";
    private static final String FORCE_OPERATION = "evidence.review.forceApprove";
    private static final String REOPEN_OPERATION = "evidence.review.reopen";
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
        if (reviews.findActiveBySnapshot(
                principal.tenantId(), snapshot.evidenceSetSnapshotId(), "INTERNAL").isPresent()) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_CONFLICT,
                    "A ReviewCase already exists for this EvidenceSetSnapshot");
        }

        Instant now = clock.instant();
        UUID reviewCaseId = UUID.randomUUID();
        ReviewCaseView created = new ReviewCaseView(
                reviewCaseId, snapshot.projectId(), snapshot.taskId(),
                snapshot.evidenceSetSnapshotId(), snapshot.contentDigest(),
                "EVIDENCE_SET_SNAPSHOT", "INTERNAL", policyVersion, "OPEN",
                principal.principalId(), now, null,
                null, null, null, null,
                null, null, List.of());
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
    public ReviewCaseView createClient(
            CurrentPrincipal principal, CommandMetadata metadata, CreateClientReviewCaseCommand command
    ) {
        if (principal.principalType() != CurrentPrincipal.PrincipalType.SERVICE) {
            throw new BusinessProblem(
                    ProblemCode.ACCESS_DENIED, "CLIENT ReviewCase requires a SERVICE principal");
        }
        if (command.sourceReviewCaseId() == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "sourceReviewCaseId is required");
        }
        ReviewCaseView source = reviews.find(principal.tenantId(), command.sourceReviewCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Source ReviewCase does not exist"));
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(
                        CREATE_CLIENT, principal.tenantId(), "ReviewCase",
                        source.reviewCaseId().toString(), source.projectId().toString()),
                metadata.correlationId());
        if (!"INTERNAL".equals(source.origin())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_STATE_CONFLICT,
                    "CLIENT ReviewCase source must be INTERNAL");
        }
        if (!"APPROVED".equals(source.status()) && !"FORCE_APPROVED".equals(source.status())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_STATE_CONFLICT,
                    "CLIENT ReviewCase source must be APPROVED or FORCE_APPROVED");
        }

        String externalSubmissionRef = normalizeRequiredText(
                command.externalSubmissionRef(), "externalSubmissionRef", 160);
        String callbackBatchRef = normalizeRequiredText(
                command.callbackBatchRef(), "callbackBatchRef", 160);
        String mappingVersionId = normalizeRequiredText(
                command.mappingVersionId(), "mappingVersionId", 160);
        String policyVersion = normalizeRequiredText(command.policyVersion(), "policyVersion", 80);
        String requestDigest = Sha256.digest(
                source.reviewCaseId() + "|" + source.evidenceSetSnapshotId() + "|"
                        + source.snapshotContentDigest() + "|" + externalSubmissionRef + "|"
                        + callbackBatchRef + "|" + mappingVersionId + "|" + policyVersion);
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision decision = idempotency.begin(context, CREATE_CLIENT_OPERATION, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return reviews.findCommandResult(
                            context.tenantId(), CREATE_CLIENT_OPERATION, context.idempotencyKey())
                    .flatMap(id -> reviews.find(context.tenantId(), id))
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "CLIENT ReviewCase replay result missing"));
        }
        if (reviews.findClientByExternalSubmissionRef(
                principal.tenantId(), externalSubmissionRef).isPresent()) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_CONFLICT,
                    "externalSubmissionRef already belongs to a CLIENT ReviewCase");
        }
        if (reviews.findActiveBySnapshot(
                principal.tenantId(), source.evidenceSetSnapshotId(), "CLIENT").isPresent()) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_CONFLICT,
                    "A CLIENT ReviewCase already exists for this EvidenceSetSnapshot");
        }

        Instant now = clock.instant();
        UUID reviewCaseId = UUID.randomUUID();
        ReviewCaseView created = new ReviewCaseView(
                reviewCaseId, source.projectId(), source.taskId(),
                source.evidenceSetSnapshotId(), source.snapshotContentDigest(),
                source.scopeType(), "CLIENT", policyVersion, "OPEN",
                principal.principalId(), now, null,
                source.reviewCaseId(), externalSubmissionRef, callbackBatchRef, mappingVersionId,
                null, null, List.of());
        // CLIENT 来源、幂等结果、审计和 Outbox 必须同事务落库；否则外部回执可能命中一个
        // 无法证明已完成车企提交的孤立 Case，或消费方收到尚不可查询的来源事实。
        try {
            reviews.insertCase(principal.tenantId(), created);
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_CONFLICT,
                    "CLIENT ReviewCase already exists for this submission or Snapshot");
        }
        reviews.saveCommandResult(
                principal.tenantId(), CREATE_CLIENT_OPERATION, context.idempotencyKey(), reviewCaseId);

        String payload = serialize(new ClientReviewCaseCreatedPayload(
                reviewCaseId, source.reviewCaseId(), source.evidenceSetSnapshotId(), source.taskId(),
                source.projectId(), source.snapshotContentDigest(), externalSubmissionRef,
                callbackBatchRef, mappingVersionId, policyVersion, now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.client-review-case-created", 1,
                "ReviewCase", reviewCaseId.toString(), 1L,
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                source.taskId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "CLIENT_REVIEW_CASE_CREATED", CREATE_CLIENT, "ReviewCase", reviewCaseId.toString(),
                "ALLOW", auth.matchedGrantIds(), auth.policyVersion(), "OPEN", null,
                requestDigest, metadata.correlationId(), now));
        idempotency.complete(context, CREATE_CLIENT_OPERATION, reviewCaseId.toString(),
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
        if (!"INTERNAL".equals(current.origin())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_STATE_CONFLICT,
                    "CLIENT ReviewCase can only be decided by an external receipt");
        }
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
                UUID.randomUUID(), current.reviewCaseId(), ordinal, decision, "INTERNAL",
                reasonCodes, note, null, principal.principalId(), now);
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
                current.taskId(), current.projectId(), decision, reasonCodes, null,
                principal.principalId(), now));
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
    @Transactional
    public ReviewCaseView forceApprove(
            CurrentPrincipal principal, CommandMetadata metadata, ForceApproveReviewCaseCommand command
    ) {
        ReviewCaseView current = reviews.find(principal.tenantId(), command.reviewCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "ReviewCase does not exist"));
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(FORCE_APPROVE, principal.tenantId(), "ReviewCase",
                        current.reviewCaseId().toString(), current.projectId().toString()),
                metadata.correlationId());
        if (!"INTERNAL".equals(current.origin())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_STATE_CONFLICT,
                    "CLIENT ReviewCase cannot be force-approved internally");
        }
        List<String> reasonCodes = normalizeReasons(command.reasonCodes(), "FORCE_APPROVED");
        String approvalRef = normalizeApprovalRef(command.approvalRef());
        String note = normalizeNote(command.note());
        String requestDigest = Sha256.digest(
                current.reviewCaseId() + "|FORCE_APPROVED|" + serialize(reasonCodes)
                        + "|" + approvalRef + "|" + nullToEmpty(note));
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision idempotencyDecision = idempotency.begin(context, FORCE_OPERATION, requestDigest);
        if (idempotencyDecision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return reviews.findCommandResult(context.tenantId(), FORCE_OPERATION, context.idempotencyKey())
                    .flatMap(id -> reviews.find(context.tenantId(), id))
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "ForceApprove replay result missing"));
        }
        if (!"OPEN".equals(current.status())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_ALREADY_DECIDED,
                    "ReviewCase has already been decided");
        }

        Instant now = clock.instant();
        int updated = reviews.markDecided(
                principal.tenantId(), current.reviewCaseId(), "OPEN", "FORCE_APPROVED", now);
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_ALREADY_DECIDED,
                    "ReviewCase has already been decided");
        }
        int ordinal = reviews.nextDecisionOrdinal(principal.tenantId(), current.reviewCaseId());
        ReviewDecisionView decisionView = new ReviewDecisionView(
                UUID.randomUUID(), current.reviewCaseId(), ordinal, "FORCE_APPROVED", "INTERNAL",
                reasonCodes, note, approvalRef, principal.principalId(), now);
        reviews.insertDecision(principal.tenantId(), current.projectId(), decisionView);
        reviews.saveCommandResult(
                principal.tenantId(), FORCE_OPERATION, context.idempotencyKey(), current.reviewCaseId());

        String payload = serialize(new ReviewDecidedPayload(
                current.reviewCaseId(), decisionView.reviewDecisionId(), current.evidenceSetSnapshotId(),
                current.taskId(), current.projectId(), "FORCE_APPROVED", reasonCodes, approvalRef,
                principal.principalId(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.review-decided", 1,
                "ReviewCase", current.reviewCaseId().toString(), ordinal,
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                current.taskId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "REVIEW_CASE_FORCE_APPROVED", FORCE_APPROVE, "ReviewCase",
                current.reviewCaseId().toString(), "ALLOW", auth.matchedGrantIds(), auth.policyVersion(),
                "FORCE_APPROVED", null, requestDigest, metadata.correlationId(), now));
        ReviewCaseView decided = reviews.find(principal.tenantId(), current.reviewCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.INTERNAL_ERROR, "Force-approved ReviewCase missing"));
        idempotency.complete(context, FORCE_OPERATION, current.reviewCaseId().toString(),
                Sha256.digest(serialize(decided)));
        return decided;
    }

    @Override
    @Transactional
    public ReviewCaseView reopen(
            CurrentPrincipal principal, CommandMetadata metadata, ReopenReviewCaseCommand command
    ) {
        ReviewCaseView current = reviews.find(principal.tenantId(), command.reviewCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "ReviewCase does not exist"));
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(REOPEN, principal.tenantId(), "ReviewCase",
                        current.reviewCaseId().toString(), current.projectId().toString()),
                metadata.correlationId());
        if (!"INTERNAL".equals(current.origin())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_STATE_CONFLICT,
                    "CLIENT ReviewCase cannot be reopened by the internal review command");
        }
        String reason = normalizeRequiredText(command.reason(), "reason", 1000);
        String triggerRef = normalizeRequiredText(command.triggerRef(), "triggerRef", 160);
        String approvalRef = command.approvalRef() == null || command.approvalRef().isBlank()
                ? null : normalizeRequiredText(command.approvalRef(), "approvalRef", 160);
        String requestDigest = Sha256.digest(
                current.reviewCaseId() + "|REOPEN|" + reason + "|" + triggerRef + "|"
                        + nullToEmpty(approvalRef));
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision idempotencyDecision = idempotency.begin(context, REOPEN_OPERATION, requestDigest);
        if (idempotencyDecision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return reviews.findCommandResult(context.tenantId(), REOPEN_OPERATION, context.idempotencyKey())
                    .flatMap(id -> reviews.find(context.tenantId(), id))
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "Reopen replay result missing"));
        }
        if (!"APPROVED".equals(current.status()) && !"FORCE_APPROVED".equals(current.status())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_STATE_CONFLICT,
                    "Only APPROVED or FORCE_APPROVED ReviewCase can be reopened");
        }

        Instant now = clock.instant();
        int updated = reviews.markReopened(principal.tenantId(), current.reviewCaseId(), current.status());
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_STATE_CONFLICT,
                    "ReviewCase could not be reopened");
        }

        UUID newCaseId = UUID.randomUUID();
        String note = approvalRef == null ? reason : reason + " | approvalRef=" + approvalRef;
        ReviewCaseView created = new ReviewCaseView(
                newCaseId, current.projectId(), current.taskId(),
                current.evidenceSetSnapshotId(), current.snapshotContentDigest(),
                current.scopeType(), current.origin(), current.policyVersion(), "OPEN",
                principal.principalId(), now, null,
                current.sourceReviewCaseId(), current.externalSubmissionRef(),
                current.callbackBatchRef(), current.mappingVersionId(),
                current.reviewCaseId(), triggerRef, List.of());
        try {
            reviews.insertCase(principal.tenantId(), created);
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_CONFLICT,
                    "An OPEN ReviewCase already exists for this EvidenceSetSnapshot");
        }
        reviews.saveCommandResult(principal.tenantId(), REOPEN_OPERATION, context.idempotencyKey(), newCaseId);

        String payload = serialize(new ReviewCaseReopenedPayload(
                current.reviewCaseId(), newCaseId, current.evidenceSetSnapshotId(),
                current.taskId(), current.projectId(), triggerRef, reason, principal.principalId(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.review-case-reopened", 1,
                "ReviewCase", newCaseId.toString(), 1L,
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                current.taskId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "REVIEW_CASE_REOPENED", REOPEN, "ReviewCase", newCaseId.toString(),
                "ALLOW", auth.matchedGrantIds(), auth.policyVersion(), note, null,
                requestDigest, metadata.correlationId(), now));
        idempotency.complete(context, REOPEN_OPERATION, newCaseId.toString(),
                Sha256.digest(serialize(created)));
        return created;
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
        if (("REJECTED".equals(decision) || "FORCE_APPROVED".equals(decision)) && codes.isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    decision + " decision requires at least one reasonCode");
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

    private static String normalizeApprovalRef(String approvalRef) {
        return normalizeRequiredText(approvalRef, "approvalRef", 160);
    }

    private static String normalizeRequiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    field + " exceeds " + maxLength + " characters");
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

    private record ClientReviewCaseCreatedPayload(
            UUID reviewCaseId,
            UUID sourceReviewCaseId,
            UUID evidenceSetSnapshotId,
            UUID taskId,
            UUID projectId,
            String snapshotContentDigest,
            String externalSubmissionRef,
            String callbackBatchRef,
            String mappingVersionId,
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
            String approvalRef,
            String decidedBy,
            Instant decidedAt
    ) {
    }

    private record ReviewCaseReopenedPayload(
            UUID sourceReviewCaseId,
            UUID reviewCaseId,
            UUID evidenceSetSnapshotId,
            UUID taskId,
            UUID projectId,
            String triggerRef,
            String reason,
            String reopenedBy,
            Instant reopenedAt
    ) {
    }
}
