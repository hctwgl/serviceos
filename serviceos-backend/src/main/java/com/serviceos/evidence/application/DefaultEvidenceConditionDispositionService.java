package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceConditionDispositionService;
import com.serviceos.evidence.api.EvidenceConditionDispositionView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.InvalidateEvidenceRevisionCommand;
import com.serviceos.evidence.api.ResolveEvidenceConditionChangeCommand;
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
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 条件变化人工处置。该服务只处理精确 generation 的未决 REVIEW_REQUIRED 成员；KEEP 不改写历史，
 * INVALIDATE 逐个复用既有 EvidenceRevision/StoredFile 作废链路，任一副作用失败则整笔事务回滚。
 */
@Service
final class DefaultEvidenceConditionDispositionService implements EvidenceConditionDispositionService {
    private static final String CAPABILITY = "evidence.condition-disposition";
    private static final String OPERATION = "evidence.condition-disposition.resolve";

    private final EvidenceSlotRepository slots;
    private final EvidenceItemRepository items;
    private final EvidenceCommandService evidenceCommands;
    private final TaskFulfillmentContextService tasks;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultEvidenceConditionDispositionService(
            EvidenceSlotRepository slots,
            EvidenceItemRepository items,
            EvidenceCommandService evidenceCommands,
            TaskFulfillmentContextService tasks,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.slots = slots;
        this.items = items;
        this.evidenceCommands = evidenceCommands;
        this.tasks = tasks;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EvidenceConditionDispositionView resolve(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ResolveEvidenceConditionChangeCommand command
    ) {
        TaskFulfillmentContext task = tasks.find(principal.tenantId(), command.taskId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "条件资料处置失败：Task 不存在"));
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(
                        CAPABILITY, principal.tenantId(), "EvidenceSlot", command.slotId().toString(),
                        task.projectId().toString()), metadata.correlationId());

        String decision = normalizeDecision(command.decision());
        String reasonCode = requireText(command.reasonCode(), "reasonCode", 100);
        String reviewRef = requireText(command.reviewRef(), "reviewRef", 200);
        String requestDigest = Sha256.digest(command.taskId() + "|" + command.slotId() + "|"
                + command.expectedResolutionId() + "|" + decision + "|" + reasonCode + "|" + reviewRef);
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision idempotencyDecision = idempotency.begin(context, OPERATION, requestDigest);
        if (idempotencyDecision.kind() == IdempotencyDecision.Kind.REPLAY) {
            UUID dispositionId = UUID.fromString(idempotencyDecision.resourceId()
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "条件资料处置重放引用不存在")));
            return slots.findDispositionById(principal.tenantId(), dispositionId)
                    .map(DefaultEvidenceConditionDispositionService::view)
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "条件资料处置重放结果不存在"));
        }

        // 串行锁与重解析共用同一 Task stream，避免处置写入与新 generation 在同一时刻交错。
        slots.lockResolutionStream(principal.tenantId(), command.taskId());
        slots.latestResolutionId(principal.tenantId(), command.taskId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.TASK_STATE_CONFLICT, "Task 尚无资料解析代次，不能执行条件资料处置"));
        // REVIEW_REQUIRED 是历史 generation 的不可变未决事实；后续表单代次不能让它消失。
        // expectedResolutionId 精确指向产生待处置状态的那一代，而不是被动漂移到最新代次。
        PendingEvidenceConditionDisposition pending = slots.findPendingDisposition(
                        principal.tenantId(), command.expectedResolutionId(), command.slotId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.TASK_STATE_CONFLICT,
                        "指定资料槽位与解析代次没有待处理的条件变化"));
        if (!pending.taskId().equals(task.taskId()) || !pending.projectId().equals(task.projectId())) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                    "条件资料处置范围与 Task 不一致");
        }

        List<EvidenceRevisionView> revisions = items.listCountingRevisionsForSlot(
                principal.tenantId(), command.slotId());
        if ("INVALIDATE".equals(decision)) {
            invalidateValidatedRevisions(principal, metadata, reasonCode, reviewRef, revisions);
        }

        Instant now = clock.instant();
        EvidenceConditionDisposition disposition = new EvidenceConditionDisposition(
                UUID.randomUUID(), principal.tenantId(), task.projectId(), task.taskId(),
                command.expectedResolutionId(), pending.memberId(), command.slotId(), decision,
                reasonCode, reviewRef, principal.principalId(), now, requestDigest);
        slots.insertDisposition(disposition);

        String payload = serialize(new DispositionRecordedPayload(
                disposition.dispositionId(), task.taskId(), task.projectId(), command.expectedResolutionId(),
                command.slotId(), decision, reasonCode, reviewRef, revisions.size(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence",
                "evidence.condition-disposition-recorded", 1,
                "EvidenceConditionDisposition", disposition.dispositionId().toString(), 1L,
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                task.taskId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "EVIDENCE_CONDITION_DISPOSITION_RECORDED", CAPABILITY,
                "EvidenceConditionDisposition", disposition.dispositionId().toString(),
                "ALLOW", auth.matchedGrantIds(), auth.policyVersion(), decision,
                null, requestDigest, metadata.correlationId(), now));
        idempotency.complete(context, OPERATION, disposition.dispositionId().toString(),
                Sha256.digest(payload));
        return view(disposition);
    }

    private void invalidateValidatedRevisions(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String reasonCode,
            String reviewRef,
            List<EvidenceRevisionView> revisions
    ) {
        // STORED/VALIDATING 资料尚未达到既有作废命令的前置状态，必须等待校验终态，禁止静默遗漏文件。
        List<EvidenceRevisionView> unfinished = revisions.stream()
                .filter(revision -> !"VALIDATED".equals(revision.status())).toList();
        if (!unfinished.isEmpty()) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "资料校验尚未结束，不能执行条件变化作废处置");
        }
        for (EvidenceRevisionView revision : revisions) {
            String childKey = "condition-disposition-" + Sha256.digest(
                    metadata.idempotencyKey() + "|" + revision.evidenceRevisionId()).substring(0, 32);
            evidenceCommands.invalidate(principal,
                    new CommandMetadata(metadata.correlationId(), childKey),
                    new InvalidateEvidenceRevisionCommand(
                            revision.evidenceRevisionId(), reasonCode, reviewRef));
        }
    }

    private static String normalizeDecision(String value) {
        String normalized = requireText(value, "decision", 24).toUpperCase(Locale.ROOT);
        if (!"KEEP".equals(normalized) && !"INVALIDATE".equals(normalized)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "decision 只能是 KEEP 或 INVALIDATE");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.strip().length() > maxLength) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    field + " 必填且长度不能超过 " + maxLength + " 个字符");
        }
        return value.strip();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("条件资料处置事件序列化失败", exception);
        }
    }

    private static EvidenceConditionDispositionView view(EvidenceConditionDisposition disposition) {
        return new EvidenceConditionDispositionView(
                disposition.dispositionId(), disposition.taskId(), disposition.slotId(),
                disposition.resolutionId(), disposition.decision(), disposition.reasonCode(),
                disposition.reviewRef(), disposition.decidedBy(), disposition.decidedAt());
    }

    private record DispositionRecordedPayload(
            UUID dispositionId,
            UUID taskId,
            UUID projectId,
            UUID resolutionId,
            UUID slotId,
            String decision,
            String reasonCode,
            String reviewRef,
            int affectedRevisionCount,
            Instant decidedAt
    ) {
    }
}
