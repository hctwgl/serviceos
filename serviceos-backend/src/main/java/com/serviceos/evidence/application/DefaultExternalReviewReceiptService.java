package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.evidence.api.EvidenceSetSnapshotMemberView;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.ExternalReviewAffectedTarget;
import com.serviceos.evidence.api.ExternalReviewReceiptService;
import com.serviceos.evidence.api.ExternalReviewReceiptView;
import com.serviceos.evidence.api.RecordExternalReviewReceiptCommand;
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
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskSchedulingService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.UUID;

/** 适配层外部审核回执：只追加 EXTERNAL 决定，驳回只开客服协调 Task。 */
@Service
final class DefaultExternalReviewReceiptService implements ExternalReviewReceiptService {
    private static final String CAPABILITY = "evidence.recordExternalReceipt";
    private static final String READ = "evidence.read";
    private static final String OPERATION = "evidence.externalReceipt.record";
    private static final String COORDINATION_TASK_TYPE = "evidence.external-coordination";

    private final ExternalReviewReceiptRepository receipts;
    private final ReviewCaseRepository reviews;
    private final EvidenceSetSnapshotRepository snapshots;
    private final TaskSchedulingService tasks;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ReviewRuleGate reviewRuleGate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultExternalReviewReceiptService(
            ExternalReviewReceiptRepository receipts,
            ReviewCaseRepository reviews,
            EvidenceSetSnapshotRepository snapshots,
            TaskSchedulingService tasks,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            ReviewRuleGate reviewRuleGate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.receipts = receipts;
        this.reviews = reviews;
        this.snapshots = snapshots;
        this.tasks = tasks;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.reviewRuleGate = reviewRuleGate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ExternalReviewReceiptView record(
            CurrentPrincipal principal, CommandMetadata metadata, RecordExternalReviewReceiptCommand command
    ) {
        if (principal.principalType() != CurrentPrincipal.PrincipalType.SERVICE) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "ExternalReviewReceipt requires a SERVICE principal");
        }
        ReviewCaseView current = reviews.find(principal.tenantId(), command.reviewCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "ReviewCase does not exist"));
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(CAPABILITY, principal.tenantId(), "ReviewCase",
                        current.reviewCaseId().toString(), current.projectId().toString()),
                metadata.correlationId());
        if (!"CLIENT".equals(current.origin())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_STATE_CONFLICT,
                    "ExternalReviewReceipt requires a CLIENT ReviewCase");
        }

        String inboundEnvelopeId = requireText(command.inboundEnvelopeId(), "inboundEnvelopeId", 160);
        String canonicalMessageId = requireText(command.canonicalMessageId(), "canonicalMessageId", 160);
        String externalKey = requireText(command.externalKey(), "externalKey", 160);
        String callbackBatchRef = requireText(command.callbackBatchRef(), "callbackBatchRef", 160);
        String mappingVersionId = requireText(command.mappingVersionId(), "mappingVersionId", 160);
        if (!callbackBatchRef.equals(current.callbackBatchRef())
                || !mappingVersionId.equals(current.mappingVersionId())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "ExternalReviewReceipt batch and mapping version must match the CLIENT ReviewCase");
        }
        String result = normalizeResult(command.result());
        List<String> reasonCodes = normalizeReasons(command.reasonCodes(), result);
        List<ExternalReviewAffectedTarget> targets = validateTargets(
                principal.tenantId(), current, command.affectedTargets());
        String payloadRef = normalizeOptional(command.payloadRef(), "payloadRef", 160);

        String requestDigest = Sha256.digest(
                inboundEnvelopeId + "|" + current.reviewCaseId() + "|" + result + "|"
                        + serialize(reasonCodes) + "|" + serialize(targets));
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision idempotencyDecision = idempotency.begin(context, OPERATION, requestDigest);
        if (idempotencyDecision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return receipts.findCommandResult(context.tenantId(), OPERATION, context.idempotencyKey())
                    .flatMap(id -> receipts.find(context.tenantId(), id))
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "ExternalReviewReceipt replay result missing"));
        }

        Instant now = clock.instant();
        var existing = receipts.findByCanonicalMessage(principal.tenantId(), canonicalMessageId)
                .flatMap(id -> receipts.find(principal.tenantId(), id));
        if (existing.isPresent()) {
            ExternalReviewReceiptView replayed = existing.get();
            receipts.saveCommandResult(
                    principal.tenantId(), OPERATION, context.idempotencyKey(), replayed.receiptId());
            idempotency.complete(context, OPERATION, replayed.receiptId().toString(),
                    Sha256.digest(serialize(replayed)));
            return replayed;
        }

        if (!"OPEN".equals(current.status())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_ALREADY_DECIDED,
                    "ExternalReviewReceipt requires an OPEN ReviewCase");
        }

        // M329：CLIENT APPROVED 回执前复用冻结 RULE 门禁；REJECTED 不阻断。
        // 与 Inbox/幂等结果同事务：阻断时整笔回滚，拒绝审计由 REQUIRES_NEW 独立提交。
        reviewRuleGate.assertDecideAllowed(
                principal.tenantId(),
                principal.principalId(),
                metadata.correlationId(),
                current.reviewCaseId(),
                current.taskId(),
                result);

        int updated = reviews.markDecided(
                principal.tenantId(), current.reviewCaseId(), "OPEN",
                current.aggregateVersion(), result, now);
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_ALREADY_DECIDED,
                    "ReviewCase has already been decided");
        }
        int ordinal = reviews.nextDecisionOrdinal(principal.tenantId(), current.reviewCaseId());
        UUID decisionId = UUID.randomUUID();
        ReviewDecisionView decision = new ReviewDecisionView(
                decisionId, current.reviewCaseId(), ordinal, result, "EXTERNAL",
                reasonCodes, "externalKey=" + externalKey, null, principal.principalId(), now);
        reviews.insertDecision(principal.tenantId(), current.projectId(), decision);

        UUID receiptId = UUID.randomUUID();
        UUID coordinationTaskId = null;
        if ("REJECTED".equals(result)) {
            String digest = Sha256.digest("external-coordination:" + receiptId);
            ScheduledTaskView coordination = tasks.createHandlingTask(new CreateHandlingTaskCommand(
                    principal.tenantId(), COORDINATION_TASK_TYPE, receiptId.toString(),
                    "external-receipt:" + receiptId, digest, 700, now, metadata.correlationId()));
            coordinationTaskId = coordination.taskId();
        }

        ExternalReviewReceiptView created = new ExternalReviewReceiptView(
                receiptId, current.projectId(), current.reviewCaseId(), decisionId,
                inboundEnvelopeId, canonicalMessageId, externalKey, callbackBatchRef, mappingVersionId,
                result, reasonCodes, targets, payloadRef, coordinationTaskId,
                principal.principalId(), now);
        try {
            receipts.insert(principal.tenantId(), created);
        } catch (DuplicateKeyException exception) {
            return receipts.findByCanonicalMessage(principal.tenantId(), canonicalMessageId)
                    .flatMap(id -> receipts.find(principal.tenantId(), id))
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.REVIEW_CASE_CONFLICT, "ExternalReviewReceipt already exists"));
        }
        receipts.saveCommandResult(principal.tenantId(), OPERATION, context.idempotencyKey(), receiptId);

        String decidedPayload = serialize(new ReviewDecidedPayload(
                current.reviewCaseId(), decisionId, current.evidenceSetSnapshotId(),
                current.taskId(), current.projectId(), result, "EXTERNAL", reasonCodes,
                principal.principalId(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.review-decided", 1,
                "ReviewCase", current.reviewCaseId().toString(), ordinal,
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                current.taskId().toString(), decidedPayload, Sha256.digest(decidedPayload), now));

        String receiptPayload = serialize(new ExternalReceiptPayload(
                receiptId, current.reviewCaseId(), decisionId, current.projectId(),
                inboundEnvelopeId, canonicalMessageId, externalKey, result,
                coordinationTaskId, principal.principalId(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.external-review-receipt-recorded", 1,
                "ExternalReviewReceipt", receiptId.toString(), 1L,
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                current.reviewCaseId().toString(), receiptPayload, Sha256.digest(receiptPayload), now));

        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "EXTERNAL_REVIEW_RECEIPT_RECORDED", CAPABILITY, "ExternalReviewReceipt",
                receiptId.toString(), "ALLOW", auth.matchedGrantIds(), auth.policyVersion(),
                result, null, requestDigest, metadata.correlationId(), now));
        idempotency.complete(context, OPERATION, receiptId.toString(), Sha256.digest(serialize(created)));
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public ExternalReviewReceiptView get(CurrentPrincipal principal, String correlationId, UUID receiptId) {
        ExternalReviewReceiptView receipt = receipts.find(principal.tenantId(), receiptId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "ExternalReviewReceipt does not exist"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "ExternalReviewReceipt", receiptId.toString(),
                receipt.projectId().toString()), correlationId);
        return receipt;
    }

    private static String normalizeResult(String result) {
        if (result == null || result.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "result is required");
        }
        String normalized = result.trim().toUpperCase(Locale.ROOT);
        if (!"APPROVED".equals(normalized) && !"REJECTED".equals(normalized)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "result must be APPROVED or REJECTED");
        }
        return normalized;
    }

    private static List<String> normalizeReasons(List<String> reasonCodes, String result) {
        List<String> codes = reasonCodes == null ? List.of() : reasonCodes.stream()
                .map(code -> code == null ? "" : code.trim())
                .filter(code -> !code.isEmpty())
                .toList();
        if ("REJECTED".equals(result) && codes.isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "REJECTED result requires at least one reasonCode");
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

    /**
     * 外部适配器只能声明 ReviewCase 冻结 Snapshot 中已经存在的精确成员。
     *
     * <p>这里同时比较 slot/item/revision 三元组，防止调用方拿一个合法 revisionId 配上其他槽位，
     * 也防止跨 Snapshot 或跨租户引用。Snapshot 与成员本身由数据库触发器保证不可变，因此校验通过后
     * 可与回执、决定、审计和 Outbox 在同一事务内安全冻结。</p>
     */
    private List<ExternalReviewAffectedTarget> validateTargets(
            String tenantId,
            ReviewCaseView reviewCase,
            List<ExternalReviewAffectedTarget> affectedTargets
    ) {
        if (affectedTargets == null || affectedTargets.isEmpty()) {
            return List.of();
        }
        if (affectedTargets.size() > 100) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "affectedTargets exceeds max size 100");
        }
        EvidenceSetSnapshotView snapshot = snapshots.find(tenantId, reviewCase.evidenceSetSnapshotId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.INTERNAL_ERROR, "ReviewCase EvidenceSetSnapshot does not exist"));
        var authoritativeMembers = snapshot.members().stream()
                .collect(java.util.stream.Collectors.toMap(
                        EvidenceSetSnapshotMemberView::evidenceRevisionId,
                        member -> member));
        var seenRevisionIds = new HashSet<UUID>();
        for (ExternalReviewAffectedTarget target : affectedTargets) {
            if (target == null
                    || !"EVIDENCE_REVISION".equals(target.targetType())
                    || target.evidenceSlotId() == null
                    || target.evidenceItemId() == null
                    || target.evidenceRevisionId() == null) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "affectedTarget must be an exact EVIDENCE_REVISION reference");
            }
            EvidenceSetSnapshotMemberView member = authoritativeMembers.get(target.evidenceRevisionId());
            if (member == null
                    || !member.evidenceSlotId().equals(target.evidenceSlotId())
                    || !member.evidenceItemId().equals(target.evidenceItemId())) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "affectedTarget is not a member of the ReviewCase EvidenceSetSnapshot");
            }
            if (!seenRevisionIds.add(target.evidenceRevisionId())) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "affectedTargets contains a duplicate evidenceRevisionId");
            }
        }
        return List.copyOf(affectedTargets);
    }

    private static String requireText(String value, String field, int maxLength) {
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

    private static String normalizeOptional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    field + " exceeds " + maxLength + " characters");
        }
        return trimmed;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("External receipt serialization failed", exception);
        }
    }

    private record ReviewDecidedPayload(
            UUID reviewCaseId,
            UUID reviewDecisionId,
            UUID evidenceSetSnapshotId,
            UUID taskId,
            UUID projectId,
            String decision,
            String decisionSource,
            List<String> reasonCodes,
            String decidedBy,
            Instant decidedAt
    ) {
    }

    private record ExternalReceiptPayload(
            UUID receiptId,
            UUID reviewCaseId,
            UUID reviewDecisionId,
            UUID projectId,
            String inboundEnvelopeId,
            String canonicalMessageId,
            String externalKey,
            String result,
            UUID coordinationTaskId,
            String receivedBy,
            Instant receivedAt
    ) {
    }
}
