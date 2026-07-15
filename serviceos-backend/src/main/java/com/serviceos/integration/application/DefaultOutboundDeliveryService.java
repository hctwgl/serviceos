package com.serviceos.integration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.evidence.api.ReviewDecisionView;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.CreateReviewSubmissionCommand;
import com.serviceos.integration.api.DeliveryReplayRequestView;
import com.serviceos.integration.api.OutboundDeliveryService;
import com.serviceos.integration.api.OutboundDeliveryView;
import com.serviceos.integration.api.RetryOutboundDeliveryCommand;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.ScheduleAutomatedTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import com.serviceos.task.api.TaskSchedulingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 创建 BYD 提审的不可变交付意图；不在命令事务内调用外部网络。 */
@Service
final class DefaultOutboundDeliveryService implements OutboundDeliveryService {
    static final String CONNECTOR_VERSION = "byd-cpim-v7.3.1";
    static final String OUTBOUND_MAPPING_VERSION = "byd-ocean-shandong-submit-review-v1";
    static final String CALLBACK_MAPPING_VERSION = "byd-ocean-shandong-review-callback-v1";
    static final String BUSINESS_MESSAGE_TYPE = "SUBMIT_CLIENT_REVIEW";
    static final String TASK_TYPE = "integration.byd.submit-review";
    static final String FAILURE_POLICY = "byd-submit-review-fail-closed-v1";
    static final String CLIENT_POLICY = "byd-client-review-v1";
    private static final String CREATE = "integration.outboundDelivery.createReviewSubmission";
    private static final String RETRY = "integration.outboundDelivery.retryUnknown";
    private static final String SUBMIT_CAPABILITY = "integration.submitClientReview";
    private static final String RETRY_CAPABILITY = "integration.retryUnknownDelivery";
    private static final String READ_CAPABILITY = "integration.readOutbound";
    private static final String INSTALL_BUSINESS_PREFIX = "BYD:INSTALL:";
    private static final DateTimeFormatter CPIM_DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    private final ReviewCaseService reviews;
    private final TaskFulfillmentContextService taskContexts;
    private final InboundMessageRepository inbound;
    private final OutboundDeliveryRepository deliveries;
    private final TaskSchedulingService tasks;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final ObjectStorageGateway storage;
    private final OutboxAppender outbox;
    private final AuditAppender audit;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ZoneId protocolZone;

    DefaultOutboundDeliveryService(
            ReviewCaseService reviews,
            TaskFulfillmentContextService taskContexts,
            InboundMessageRepository inbound,
            OutboundDeliveryRepository deliveries,
            TaskSchedulingService tasks,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            ObjectStorageGateway storage,
            OutboxAppender outbox,
            AuditAppender audit,
            ObjectMapper objectMapper,
            TransactionTemplate transactions,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${serviceos.integration.byd.cpim.zone-id}")
            ZoneId protocolZone
    ) {
        this.reviews = reviews;
        this.taskContexts = taskContexts;
        this.inbound = inbound;
        this.deliveries = deliveries;
        this.tasks = tasks;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.storage = storage;
        this.outbox = outbox;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.clock = clock;
        this.protocolZone = protocolZone;
    }

    @Override
    public OutboundDeliveryView createReviewSubmission(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            CreateReviewSubmissionCommand command
    ) {
        if (principal.principalType() != CurrentPrincipal.PrincipalType.SERVICE) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "Review submission delivery requires a SERVICE principal");
        }
        if (command.sourceReviewCaseId() == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "sourceReviewCaseId is required");
        }
        ReviewCaseView source = reviews.get(principal, metadata.correlationId(), command.sourceReviewCaseId());
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(
                        SUBMIT_CAPABILITY, principal.tenantId(), "OutboundDelivery",
                        source.reviewCaseId().toString(), source.projectId().toString()),
                metadata.correlationId());
        requireApprovedInternal(source);

        TaskFulfillmentContext task = taskContexts.find(principal.tenantId(), source.taskId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Source ReviewCase Task does not exist"));
        if (!source.projectId().equals(task.projectId()) || task.workOrderId() == null) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_CONFLICT,
                    "Source ReviewCase Task ownership conflicts");
        }
        CanonicalMessageView canonical = inbound.findCanonicalByResult(
                        principal.tenantId(), CONNECTOR_VERSION, "CREATE_WORK_ORDER",
                        "WORK_ORDER", task.workOrderId().toString())
                .map(InboundMessageRepository.CanonicalMessageRecord::view)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND,
                        "Source WorkOrder has no authoritative BYD inbound CanonicalMessage"));
        if (!source.projectId().equals(canonical.projectId()) || !"COMPLETED".equals(canonical.processingStatus())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_CONFLICT,
                    "BYD inbound lineage does not match the ReviewCase project");
        }

        ReviewDecisionView decision = source.decisions().stream()
                .max(java.util.Comparator.comparingInt(ReviewDecisionView::decisionOrdinal))
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.REVIEW_CASE_STATE_CONFLICT, "Approved ReviewCase has no decision"));
        String operator = exactText(decision.decidedBy(), "decidedBy", 50);
        String orderCode = orderCode(canonical.businessKey());
        String businessKey = "BYD:SUBMIT_REVIEW:" + orderCode + ":" + source.snapshotContentDigest();
        String requestDigest = Sha256.digest(
                source.reviewCaseId() + "|" + source.evidenceSetSnapshotId() + "|"
                        + source.snapshotContentDigest() + "|" + decision.reviewDecisionId() + "|"
                        + operator + "|" + orderCode + "|" + OUTBOUND_MAPPING_VERSION);
        OutboundDeliveryRepository.DeliveryRecord existing = deliveries.findBySourceReview(
                principal.tenantId(), source.reviewCaseId(), BUSINESS_MESSAGE_TYPE).orElse(null);
        if (existing != null) {
            if (!businessKey.equals(existing.view().businessKey())) {
                throw new BusinessProblem(ProblemCode.REVIEW_CASE_CONFLICT,
                        "Source ReviewCase already has a different delivery intent");
            }
            return existing.view();
        }

        UUID deliveryId = UUID.randomUUID();
        Instant createdAt = clock.instant();
        SubmitReviewPayload payload = new SubmitReviewPayload(
                operator, orderCode, CPIM_DATE_TIME.format(createdAt.atZone(protocolZone)));
        byte[] payloadBytes = jsonBytes(payload);
        String payloadDigest = Sha256.digest(payloadBytes);
        String tenantPrefix = Sha256.digest(principal.tenantId()).substring(0, 16);
        String objectRef = "integration/outbound/" + tenantPrefix
                + "/byd-cpim/submit-review/" + deliveryId + "/" + payloadDigest + ".json";
        store(objectRef, payloadBytes, payloadDigest);

        OutboundDeliveryView created = Objects.requireNonNull(transactions.execute(status -> {
            CommandContext context = new CommandContext(
                    principal.tenantId(), principal.principalId(),
                    metadata.correlationId(), metadata.idempotencyKey());
            IdempotencyDecision idempotencyDecision = idempotency.begin(context, CREATE, requestDigest);
            if (idempotencyDecision.kind() == IdempotencyDecision.Kind.REPLAY) {
                UUID replayId = idempotencyDecision.resourceId().map(UUID::fromString)
                        .orElseThrow(() -> new BusinessProblem(
                                ProblemCode.INTERNAL_ERROR, "OutboundDelivery replay id missing"));
                return deliveries.find(principal.tenantId(), replayId)
                        .map(OutboundDeliveryRepository.DeliveryRecord::view)
                        .orElseThrow(() -> new BusinessProblem(
                                ProblemCode.INTERNAL_ERROR, "OutboundDelivery replay result missing"));
            }
            var registration = deliveries.register(new OutboundDeliveryRepository.NewDelivery(
                    deliveryId, principal.tenantId(), source.projectId(), CONNECTOR_VERSION,
                    OUTBOUND_MAPPING_VERSION, BUSINESS_MESSAGE_TYPE, businessKey,
                    source.reviewCaseId(), source.taskId(), task.workOrderId(),
                    source.evidenceSetSnapshotId(), source.snapshotContentDigest(), orderCode,
                    decision.decidedBy(), operator, objectRef, payloadDigest,
                    Sha256.digest(businessKey + "|" + source.reviewCaseId()), FAILURE_POLICY,
                    principal.principalId(), createdAt));
            OutboundDeliveryView delivery = registration.delivery().view();
            if (!businessKey.equals(delivery.businessKey())
                    || !source.snapshotContentDigest().equals(delivery.sourceSnapshotDigest())) {
                throw new BusinessProblem(ProblemCode.REVIEW_CASE_CONFLICT,
                        "Concurrent OutboundDelivery has different frozen input");
            }
            if (delivery.executionTaskId() == null) {
                ScheduledTaskView executionTask = tasks.schedule(new ScheduleAutomatedTaskCommand(
                        principal.tenantId(), TASK_TYPE, delivery.deliveryId().toString(),
                        "outbound-delivery:" + delivery.deliveryId(), delivery.payloadDigest(),
                        700, createdAt, 3, metadata.correlationId()));
                deliveries.attachExecutionTask(
                        principal.tenantId(), delivery.deliveryId(), executionTask.taskId(), createdAt);
                delivery = deliveries.find(principal.tenantId(), delivery.deliveryId())
                        .orElseThrow().view();
            }
            if (registration.created()) {
                appendCreatedEvent(principal, metadata, delivery, createdAt);
                audit.append(new AuditEntry(
                        UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                        "OUTBOUND_REVIEW_SUBMISSION_CREATED", SUBMIT_CAPABILITY,
                        "OutboundDelivery", delivery.deliveryId().toString(), "ALLOW",
                        auth.matchedGrantIds(), auth.policyVersion(), "PENDING", null,
                        requestDigest, metadata.correlationId(), createdAt));
            }
            idempotency.complete(context, CREATE, delivery.deliveryId().toString(),
                    Sha256.digest(json(delivery)));
            return delivery;
        }));
        return created;
    }

    @Override
    public OutboundDeliveryView get(CurrentPrincipal principal, String correlationId, UUID deliveryId) {
        OutboundDeliveryView view = deliveries.find(principal.tenantId(), deliveryId)
                .map(OutboundDeliveryRepository.DeliveryRecord::view)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "OutboundDelivery does not exist"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ_CAPABILITY, principal.tenantId(), "OutboundDelivery",
                deliveryId.toString(), view.projectId().toString()), correlationId);
        return view;
    }

    @Override
    public DeliveryReplayRequestView retryUnknown(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            RetryOutboundDeliveryCommand command
    ) {
        if (principal.principalType() != CurrentPrincipal.PrincipalType.USER) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "UNKNOWN delivery replay requires a USER principal");
        }
        if (command.deliveryId() == null || command.expectedAggregateVersion() < 1) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "deliveryId and positive expectedAggregateVersion are required");
        }
        String reason = requiredText(command.reason(), "reason", 1000);
        String approvalRef = requiredText(command.approvalRef(), "approvalRef", 160);
        OutboundDeliveryView delivery = deliveries.find(principal.tenantId(), command.deliveryId())
                .map(OutboundDeliveryRepository.DeliveryRecord::view)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "OutboundDelivery does not exist"));
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(
                        RETRY_CAPABILITY, principal.tenantId(), "OutboundDelivery",
                        delivery.deliveryId().toString(), delivery.projectId().toString()),
                metadata.correlationId());
        if (!"UNKNOWN".equals(delivery.status())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "Only UNKNOWN OutboundDelivery can be replayed");
        }
        if (delivery.aggregateVersion() != command.expectedAggregateVersion()) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "OutboundDelivery aggregate version changed");
        }

        String requestDigest = Sha256.digest(delivery.deliveryId() + "|"
                + command.expectedAggregateVersion() + "|" + reason + "|" + approvalRef);
        return Objects.requireNonNull(transactions.execute(status -> {
            CommandContext context = new CommandContext(
                    principal.tenantId(), principal.principalId(),
                    metadata.correlationId(), metadata.idempotencyKey());
            IdempotencyDecision decision = idempotency.begin(context, RETRY, requestDigest);
            if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
                UUID replayId = decision.resourceId().map(UUID::fromString)
                        .orElseThrow(() -> new BusinessProblem(
                                ProblemCode.INTERNAL_ERROR, "Delivery replay id missing"));
                return deliveries.findReplay(principal.tenantId(), replayId)
                        .orElseThrow(() -> new BusinessProblem(
                                ProblemCode.INTERNAL_ERROR, "Delivery replay result missing"));
            }

            UUID replayId = UUID.randomUUID();
            Instant requestedAt = clock.instant();
            String replayBusinessKey = delivery.deliveryId() + ":replay:" + replayId;
            ScheduledTaskView task = tasks.schedule(new ScheduleAutomatedTaskCommand(
                    principal.tenantId(), TASK_TYPE, replayBusinessKey,
                    "outbound-delivery:" + replayBusinessKey, delivery.payloadDigest(),
                    900, requestedAt, 3, metadata.correlationId()));
            DeliveryReplayRequestView replay = deliveries.registerReplay(
                    new OutboundDeliveryRepository.NewReplayRequest(
                            replayId, delivery.deliveryId(), principal.tenantId(),
                            command.expectedAggregateVersion(), reason, approvalRef,
                            principal.principalId(), task.taskId(), requestedAt));
            appendReplayRequestedEvent(principal, metadata, delivery, replay);
            audit.append(new AuditEntry(
                    UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                    "OUTBOUND_DELIVERY_REPLAY_REQUESTED", RETRY_CAPABILITY,
                    "OutboundDelivery", delivery.deliveryId().toString(), "ALLOW",
                    auth.matchedGrantIds(), auth.policyVersion(), "REQUESTED", null,
                    requestDigest, metadata.correlationId(), requestedAt));
            idempotency.complete(context, RETRY, replay.replayRequestId().toString(),
                    Sha256.digest(json(replay)));
            return replay;
        }));
    }

    private static void requireApprovedInternal(ReviewCaseView source) {
        if (!"INTERNAL".equals(source.origin())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_STATE_CONFLICT,
                    "Review submission source must be INTERNAL");
        }
        if (!"APPROVED".equals(source.status()) && !"FORCE_APPROVED".equals(source.status())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_STATE_CONFLICT,
                    "Review submission source must be APPROVED or FORCE_APPROVED");
        }
    }

    private static String orderCode(String businessKey) {
        if (businessKey == null || !businessKey.startsWith(INSTALL_BUSINESS_PREFIX)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "BYD inbound CanonicalMessage has an invalid business key");
        }
        return exactText(businessKey.substring(INSTALL_BUSINESS_PREFIX.length()), "orderCode", 50);
    }

    private static String exactText(String value, String field, int maximum) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > maximum) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is invalid");
        }
        return value;
    }

    private static String requiredText(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is too long");
        }
        return normalized;
    }

    private void appendCreatedEvent(
            CurrentPrincipal principal, CommandMetadata metadata,
            OutboundDeliveryView delivery, Instant createdAt
    ) {
        String payload = json(new DeliveryCreatedPayload(
                delivery.deliveryId(), delivery.projectId(), delivery.sourceReviewCaseId(),
                delivery.sourceTaskId(), delivery.sourceWorkOrderId(), delivery.sourceSnapshotId(),
                delivery.externalOrderCode(), delivery.mappingVersionId(), delivery.payloadDigest(),
                delivery.externalIdempotencyKey(), delivery.executionTaskId(), createdAt));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "integration",
                "integration.outbound-delivery-created", 1,
                "OutboundDelivery", delivery.deliveryId().toString(), delivery.aggregateVersion(),
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                delivery.sourceReviewCaseId().toString(), payload, Sha256.digest(payload), createdAt));
    }

    private void appendReplayRequestedEvent(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            OutboundDeliveryView delivery,
            DeliveryReplayRequestView replay
    ) {
        String payload = json(new DeliveryReplayRequestedPayload(
                replay.replayRequestId(), replay.deliveryId(), delivery.projectId(),
                replay.executionTaskId(), delivery.payloadDigest(), delivery.externalIdempotencyKey(),
                replay.reason(), replay.approvalRef(), replay.requestedBy(), replay.requestedAt()));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "integration",
                "integration.outbound-delivery-replay-requested", 1,
                "OutboundDelivery", delivery.deliveryId().toString(), delivery.aggregateVersion(),
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                delivery.deliveryId().toString(), payload, Sha256.digest(payload), replay.requestedAt()));
    }

    private void store(String objectRef, byte[] content, String digest) {
        try {
            storage.storeInternal(
                    objectRef, new ByteArrayInputStream(content), content.length, digest, "application/json");
        } catch (IOException exception) {
            throw new IllegalStateException("Private outbound payload storage failed", exception);
        }
    }

    private byte[] jsonBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("OutboundDelivery payload serialization failed", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("OutboundDelivery serialization failed", exception);
        }
    }

    private record SubmitReviewPayload(String operatePerson, String orderCode, String commitDate) {
    }

    private record DeliveryCreatedPayload(
            UUID deliveryId,
            UUID projectId,
            UUID sourceReviewCaseId,
            UUID sourceTaskId,
            UUID sourceWorkOrderId,
            UUID sourceSnapshotId,
            String externalOrderCode,
            String mappingVersionId,
            String payloadDigest,
            String externalIdempotencyKey,
            UUID executionTaskId,
            Instant createdAt
    ) {
    }

    private record DeliveryReplayRequestedPayload(
            UUID replayRequestId,
            UUID deliveryId,
            UUID projectId,
            UUID executionTaskId,
            String payloadDigest,
            String externalIdempotencyKey,
            String reason,
            String approvalRef,
            String requestedBy,
            Instant requestedAt
    ) {
    }
}
