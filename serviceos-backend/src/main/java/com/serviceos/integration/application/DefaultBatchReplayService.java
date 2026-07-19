package com.serviceos.integration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.ApproveBatchReplayCommand;
import com.serviceos.integration.api.BatchReplayRequestView;
import com.serviceos.integration.api.BatchReplayService;
import com.serviceos.integration.api.CreateBatchReplayCommand;
import com.serviceos.integration.api.OutboundDeliveryService;
import com.serviceos.integration.api.OutboundDeliveryView;
import com.serviceos.integration.api.RetryOutboundDeliveryCommand;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 批量 UNKNOWN 重放：预演资格 → 提交审批 → 批准后逐条复用单笔 retryUnknown。
 *
 * <p>限流默认 20、硬上限 50；已有人工处置或非 UNKNOWN 的条目记为 INELIGIBLE。</p>
 */
@Service
final class DefaultBatchReplayService implements BatchReplayService {
    private static final String CREATE = "integration.batchReplay.create";
    private static final String APPROVE = "integration.batchReplay.approve";
    private static final String CAPABILITY = "integration.batchReplayUnknownDelivery";
    private static final int DEFAULT_MAX = 20;
    private static final int HARD_MAX = 50;

    private final BatchReplayRepository batches;
    private final OutboundDeliveryRepository deliveries;
    private final OutboundDeliveryService outboundDeliveries;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultBatchReplayService(
            BatchReplayRepository batches,
            OutboundDeliveryRepository deliveries,
            OutboundDeliveryService outboundDeliveries,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            TransactionTemplate transactions,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.batches = batches;
        this.deliveries = deliveries;
        this.outboundDeliveries = outboundDeliveries;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public BatchReplayRequestView create(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            CreateBatchReplayCommand command
    ) {
        requireUser(principal);
        String mode = required(command.mode(), "mode").toUpperCase();
        if (!"PREVIEW".equals(mode) && !"SUBMIT".equals(mode)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "mode must be PREVIEW or SUBMIT");
        }
        String reason = required(command.reason(), "reason");
        if (reason.length() > 1000) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "reason is too long");
        }
        final String approvalRef;
        if ("SUBMIT".equals(mode)) {
            String ref = required(command.approvalRef(), "approvalRef");
            if (ref.length() > 160) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "approvalRef is too long");
            }
            approvalRef = ref;
        } else {
            approvalRef = null;
        }
        int maxItems = command.maxItems() == null ? DEFAULT_MAX : command.maxItems();
        if (maxItems < 1 || maxItems > HARD_MAX) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "maxItems must be between 1 and " + HARD_MAX);
        }
        List<UUID> deliveryIds = normalizeIds(command.deliveryIds(), maxItems);
        List<EvaluatedItem> evaluated = evaluate(principal, metadata.correlationId(), deliveryIds);

        UUID batchId = UUID.randomUUID();
        Instant createdAt = clock.instant();
        String status = "PREVIEW".equals(mode) ? "PREVIEWED" : "PENDING_APPROVAL";
        String itemStatus = "PREVIEW".equals(mode) ? "PREVIEWED" : "PENDING";
        String digest = Sha256.digest(mode + "|" + reason + "|" + Objects.toString(approvalRef, "")
                + "|" + maxItems + "|" + deliveryIds);
        return Objects.requireNonNull(transactions.execute(tx -> {
            CommandContext context = new CommandContext(
                    principal.tenantId(), principal.principalId(),
                    metadata.correlationId(), metadata.idempotencyKey());
            IdempotencyDecision decision = idempotency.begin(context, CREATE, digest);
            if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
                UUID id = decision.resourceId().map(UUID::fromString)
                        .orElseThrow(() -> new BusinessProblem(
                                ProblemCode.INTERNAL_ERROR, "Batch replay id missing"));
                return batches.find(principal.tenantId(), id)
                        .orElseThrow(() -> new BusinessProblem(
                                ProblemCode.INTERNAL_ERROR, "Batch replay result missing"));
            }
            List<BatchReplayRepository.NewItem> items = evaluated.stream()
                    .map(item -> new BatchReplayRepository.NewItem(
                            batchId, item.deliveryId(), principal.tenantId(), item.projectId(),
                            item.eligibility(), item.ineligibilityCode(),
                            item.expectedVersion(), itemStatus))
                    .toList();
            batches.insert(new BatchReplayRepository.NewBatch(
                    batchId, principal.tenantId(), mode, status, reason, approvalRef,
                    principal.principalId(), maxItems, createdAt), items);
            audit.append(new AuditEntry(
                    UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                    "BATCH_REPLAY_REQUESTED", CAPABILITY,
                    "BatchReplayRequest", batchId.toString(), "ALLOW",
                    List.of(), null, status, null, digest,
                    metadata.correlationId(), createdAt));
            idempotency.complete(context, CREATE, batchId.toString(), Sha256.digest(batchId.toString()));
            return batches.find(principal.tenantId(), batchId).orElseThrow();
        }));
    }

    @Override
    public BatchReplayRequestView approve(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ApproveBatchReplayCommand command
    ) {
        requireUser(principal);
        if (command == null || command.batchId() == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "batchId is required");
        }
        String decision = required(command.decision(), "decision").toUpperCase();
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "decision must be APPROVE or REJECT");
        }
        String note = command.decisionNote() == null || command.decisionNote().isBlank()
                ? null : command.decisionNote().trim();
        BatchReplayRequestView batch = batches.find(principal.tenantId(), command.batchId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Batch replay request does not exist"));
        if (!"PENDING_APPROVAL".equals(batch.status())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "Only PENDING_APPROVAL batch can be decided");
        }
        // 对批次中每个 ELIGIBLE 项目做 project scope 授权
        for (var item : batch.items()) {
            if (!"ELIGIBLE".equals(item.eligibility())) {
                continue;
            }
            authorization.require(principal,
                    AuthorizationRequest.projectCapability(
                            CAPABILITY, principal.tenantId(), "OutboundDelivery",
                            item.deliveryId().toString(), item.projectId().toString()),
                    metadata.correlationId());
        }
        Instant decidedAt = clock.instant();
        if ("REJECT".equals(decision)) {
            transactions.executeWithoutResult(tx -> {
                batches.markDecision(principal.tenantId(), batch.batchId(), "REJECTED",
                        decision, principal.principalId(), note, decidedAt);
                audit.append(new AuditEntry(
                        UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                        "BATCH_REPLAY_REJECTED", CAPABILITY,
                        "BatchReplayRequest", batch.batchId().toString(), "ALLOW",
                        List.of(), null, "REJECTED", null,
                        Sha256.digest(batch.batchId() + "|REJECT"), metadata.correlationId(), decidedAt));
            });
            return batches.find(principal.tenantId(), batch.batchId()).orElseThrow();
        }

        int limit = command.maxItems() == null ? batch.maxItems() : Math.min(command.maxItems(), batch.maxItems());
        if (limit < 1) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "maxItems must be >= 1");
        }
        transactions.executeWithoutResult(tx -> batches.markDecision(
                principal.tenantId(), batch.batchId(), "APPROVED",
                decision, principal.principalId(), note, decidedAt));

        int scheduled = 0;
        for (var item : batch.items()) {
            if (!"ELIGIBLE".equals(item.eligibility()) || !"PENDING".equals(item.itemStatus())) {
                continue;
            }
            if (scheduled >= limit) {
                batches.markItemFailed(principal.tenantId(), batch.batchId(), item.deliveryId(),
                        "BATCH_LIMIT_REACHED");
                continue;
            }
            try {
                var replay = outboundDeliveries.retryUnknown(
                        principal,
                        new CommandMetadata(
                                metadata.correlationId(),
                                metadata.idempotencyKey() + ":" + item.deliveryId()),
                        new RetryOutboundDeliveryCommand(
                                item.deliveryId(),
                                item.expectedDeliveryVersion(),
                                batch.reason(),
                                batch.approvalRef()));
                batches.markItemScheduled(principal.tenantId(), batch.batchId(),
                        item.deliveryId(), replay.replayRequestId());
                scheduled++;
            } catch (BusinessProblem problem) {
                batches.markItemFailed(principal.tenantId(), batch.batchId(), item.deliveryId(),
                        problem.code().name());
            }
        }
        batches.markCompleted(principal.tenantId(), batch.batchId());
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "BATCH_REPLAY_APPROVED", CAPABILITY,
                "BatchReplayRequest", batch.batchId().toString(), "ALLOW",
                List.of(), null, "COMPLETED", null,
                Sha256.digest(batch.batchId() + "|APPROVE|" + scheduled),
                metadata.correlationId(), clock.instant()));
        return batches.find(principal.tenantId(), batch.batchId()).orElseThrow();
    }

    @Override
    public BatchReplayRequestView get(
            CurrentPrincipal principal, String correlationId, UUID batchId
    ) {
        requireUser(principal);
        BatchReplayRequestView batch = batches.find(principal.tenantId(), batchId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Batch replay request does not exist"));
        // 至少对一个项目有读/批能力即可查看；逐项 scope 在 approve 时再校验
        boolean authorized = false;
        for (var item : batch.items()) {
            if (item.projectId() == null) {
                continue;
            }
            try {
                authorization.require(principal,
                        AuthorizationRequest.projectCapability(
                                CAPABILITY, principal.tenantId(), "BatchReplayRequest",
                                batchId.toString(), item.projectId().toString()),
                        correlationId);
                authorized = true;
                break;
            } catch (BusinessProblem ignored) {
                // try next project
            }
        }
        if (!authorized && batch.requestedBy().equals(principal.principalId())) {
            authorized = true;
        }
        if (!authorized) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "Batch replay request is not readable");
        }
        return batch;
    }

    private List<EvaluatedItem> evaluate(
            CurrentPrincipal principal, String correlationId, List<UUID> deliveryIds
    ) {
        List<EvaluatedItem> items = new ArrayList<>();
        for (UUID deliveryId : deliveryIds) {
            var record = deliveries.find(principal.tenantId(), deliveryId);
            if (record.isEmpty()) {
                items.add(EvaluatedItem.ineligible(deliveryId, "DELIVERY_NOT_FOUND"));
                continue;
            }
            OutboundDeliveryView delivery = record.get().view();
            try {
                AuthorizationDecision auth = authorization.require(principal,
                        AuthorizationRequest.projectCapability(
                                CAPABILITY, principal.tenantId(), "OutboundDelivery",
                                delivery.deliveryId().toString(), delivery.projectId().toString()),
                        correlationId);
                Objects.requireNonNull(auth);
            } catch (BusinessProblem problem) {
                items.add(EvaluatedItem.ineligible(deliveryId, "ACCESS_DENIED"));
                continue;
            }
            if (!"UNKNOWN".equals(delivery.status())) {
                items.add(EvaluatedItem.ineligible(deliveryId, "NOT_UNKNOWN"));
                continue;
            }
            if (deliveries.hasManualDisposition(principal.tenantId(), deliveryId)) {
                items.add(EvaluatedItem.ineligible(deliveryId, "MANUAL_DISPOSITION_EXISTS"));
                continue;
            }
            items.add(EvaluatedItem.eligible(
                    deliveryId, delivery.projectId(), delivery.aggregateVersion()));
        }
        return items;
    }

    private static List<UUID> normalizeIds(List<UUID> deliveryIds, int maxItems) {
        if (deliveryIds == null || deliveryIds.isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "deliveryIds must not be empty");
        }
        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        for (UUID id : deliveryIds) {
            if (id == null) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "deliveryIds must not contain null");
            }
            unique.add(id);
        }
        if (unique.size() > maxItems) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "deliveryIds exceeds maxItems=" + maxItems);
        }
        return List.copyOf(unique);
    }

    private static void requireUser(CurrentPrincipal principal) {
        if (principal.principalType() != CurrentPrincipal.PrincipalType.USER) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "Batch replay requires a USER principal");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is required");
        }
        return value.trim();
    }

    private record EvaluatedItem(
            UUID deliveryId,
            UUID projectId,
            String eligibility,
            String ineligibilityCode,
            Long expectedVersion
    ) {
        static EvaluatedItem eligible(UUID deliveryId, UUID projectId, long version) {
            return new EvaluatedItem(deliveryId, projectId, "ELIGIBLE", null, version);
        }

        static EvaluatedItem ineligible(UUID deliveryId, String code) {
            return new EvaluatedItem(deliveryId, null, "INELIGIBLE", code, null);
        }
    }
}
