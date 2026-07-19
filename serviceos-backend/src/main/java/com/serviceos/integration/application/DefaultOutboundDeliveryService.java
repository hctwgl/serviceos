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
import com.serviceos.integration.api.QueryRemoteStatusCommand;
import com.serviceos.integration.api.RemoteStatusQueryView;
import com.serviceos.integration.api.RetryOutboundDeliveryCommand;
import com.serviceos.integration.spi.RemoteStatusQueryConnector;
import com.serviceos.integration.spi.RemoteStatusQueryRequest;
import com.serviceos.integration.spi.RemoteStatusQueryResult;
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
import com.serviceos.workorder.api.WorkOrderDetail;
import com.serviceos.workorder.api.WorkOrderQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 创建提审不可变交付意图；不在命令事务内调用外部网络。
 *
 * <p>车企差异通过 {@link OutboundReviewSubmissionProfiles} 解析，禁止本类按 clientCode 分支。</p>
 */
@Service
final class DefaultOutboundDeliveryService implements OutboundDeliveryService {
    private static final String CREATE = "integration.outboundDelivery.createReviewSubmission";
    private static final String RETRY = "integration.outboundDelivery.retryUnknown";
    private static final String SUBMIT_CAPABILITY = "integration.submitClientReview";
    private static final String RETRY_CAPABILITY = "integration.retryUnknownDelivery";
    private static final String READ_CAPABILITY = "integration.readOutbound";

    private final ReviewCaseService reviews;
    private final TaskFulfillmentContextService taskContexts;
    private final InboundMessageRepository inbound;
    private final OutboundDeliveryRepository deliveries;
    private final TaskSchedulingService tasks;
    private final WorkOrderQueryService workOrders;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final ObjectStorageGateway storage;
    private final OutboxAppender outbox;
    private final AuditAppender audit;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ZoneId protocolZone;
    private final OutboundReviewSubmissionProfiles profiles;
    private final RemoteStatusQueryConnectors remoteStatusQueries;

    DefaultOutboundDeliveryService(
            ReviewCaseService reviews,
            TaskFulfillmentContextService taskContexts,
            InboundMessageRepository inbound,
            OutboundDeliveryRepository deliveries,
            TaskSchedulingService tasks,
            WorkOrderQueryService workOrders,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            ObjectStorageGateway storage,
            OutboxAppender outbox,
            AuditAppender audit,
            ObjectMapper objectMapper,
            TransactionTemplate transactions,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${serviceos.integration.byd.cpim.zone-id}")
            ZoneId protocolZone,
            OutboundReviewSubmissionProfiles profiles,
            RemoteStatusQueryConnectors remoteStatusQueries
    ) {
        this.reviews = reviews;
        this.taskContexts = taskContexts;
        this.inbound = inbound;
        this.deliveries = deliveries;
        this.tasks = tasks;
        this.workOrders = workOrders;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.storage = storage;
        this.outbox = outbox;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.clock = clock;
        this.protocolZone = protocolZone;
        this.profiles = profiles;
        this.remoteStatusQueries = remoteStatusQueries;
    }

    @Override
    public OutboundDeliveryView createReviewSubmission(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            CreateReviewSubmissionCommand command
    ) {
        // M137：Admin Portal 以获权 USER 触发提审；SERVICE 适配器仍可调用。
        // 授权继续以 integration.submitClientReview + Tenant/Project Scope 失败关闭，
        // 不因主体类型绕过 Capability，也不猜测未授权操作人。
        if (principal.principalType() != CurrentPrincipal.PrincipalType.SERVICE
                && principal.principalType() != CurrentPrincipal.PrincipalType.USER) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "Review submission delivery requires a USER or SERVICE principal");
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
        ResolvedLineage lineage = resolveInboundLineage(
                principal.tenantId(), source.projectId(), task.workOrderId());
        var profile = lineage.profile();
        CanonicalMessageView canonical = lineage.canonical();

        ReviewDecisionView decision = source.decisions().stream()
                .max(java.util.Comparator.comparingInt(ReviewDecisionView::decisionOrdinal))
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.REVIEW_CASE_STATE_CONFLICT, "Approved ReviewCase has no decision"));
        String operator = exactText(decision.decidedBy(), "decidedBy", 50);
        String orderCode = profile.extractExternalOrderCode(canonical.businessKey());
        String businessKey = profile.submitBusinessKey(orderCode, source.snapshotContentDigest());
        String requestDigest = Sha256.digest(
                source.reviewCaseId() + "|" + source.evidenceSetSnapshotId() + "|"
                        + source.snapshotContentDigest() + "|" + decision.reviewDecisionId() + "|"
                        + operator + "|" + orderCode + "|" + profile.outboundMappingVersion());
        OutboundDeliveryRepository.DeliveryRecord existing = deliveries.findBySourceReview(
                principal.tenantId(), source.reviewCaseId(), profile.businessMessageType()).orElse(null);
        if (existing != null) {
            if (!businessKey.equals(existing.view().businessKey())) {
                throw new BusinessProblem(ProblemCode.REVIEW_CASE_CONFLICT,
                        "Source ReviewCase already has a different delivery intent");
            }
            return existing.view();
        }

        UUID deliveryId = UUID.randomUUID();
        Instant createdAt = clock.instant();
        byte[] payloadBytes = profile.buildSubmitPayload(operator, orderCode, createdAt, protocolZone);
        String payloadDigest = Sha256.digest(payloadBytes);
        String tenantPrefix = Sha256.digest(principal.tenantId()).substring(0, 16);
        String objectRef = "integration/outbound/" + tenantPrefix + "/"
                + profile.payloadStorageSegment() + "/" + deliveryId + "/" + payloadDigest + ".json";
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
                    deliveryId, principal.tenantId(), source.projectId(),
                    profile.identity().connectorVersionId(),
                    profile.outboundMappingVersion(), profile.businessMessageType(), businessKey,
                    source.reviewCaseId(), source.taskId(), task.workOrderId(),
                    source.evidenceSetSnapshotId(), source.snapshotContentDigest(), orderCode,
                    decision.decidedBy(), operator, objectRef, payloadDigest,
                    Sha256.digest(businessKey + "|" + source.reviewCaseId()), profile.failurePolicy(),
                    principal.principalId(), createdAt));
            OutboundDeliveryView delivery = registration.delivery().view();
            if (!businessKey.equals(delivery.businessKey())
                    || !source.snapshotContentDigest().equals(delivery.sourceSnapshotDigest())) {
                throw new BusinessProblem(ProblemCode.REVIEW_CASE_CONFLICT,
                        "Concurrent OutboundDelivery has different frozen input");
            }
            if (delivery.executionTaskId() == null) {
                ScheduledTaskView executionTask = tasks.schedule(new ScheduleAutomatedTaskCommand(
                        principal.tenantId(), profile.taskType(), delivery.deliveryId().toString(),
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

    private ResolvedLineage resolveInboundLineage(String tenantId, UUID projectId, UUID workOrderId) {
        List<ResolvedLineage> matches = profiles.all().stream()
                .map(profile -> inbound.findCanonicalByResult(
                                tenantId, profile.identity().connectorVersionId(), "CREATE_WORK_ORDER",
                                "WORK_ORDER", workOrderId.toString())
                        .map(InboundMessageRepository.CanonicalMessageRecord::view)
                        .filter(canonical -> projectId.equals(canonical.projectId())
                                && "COMPLETED".equals(canonical.processingStatus())
                                && profile.supportsInboundLineage(
                                        canonical.connectorVersionId(), canonical.messageType()))
                        .map(canonical -> new ResolvedLineage(profile, canonical))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
        if (matches.isEmpty()) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                    "Source WorkOrder has no authoritative inbound CREATE_WORK_ORDER CanonicalMessage");
        }
        if (matches.size() > 1) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Multiple outbound profiles matched inbound CREATE_WORK_ORDER lineage");
        }
        return matches.getFirst();
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
    public List<OutboundDeliveryView> listForWorkOrder(
            CurrentPrincipal principal, String correlationId, UUID workOrderId, int limit
    ) {
        if (limit < 1 || limit > 100) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "limit must be between 1 and 100");
        }
        WorkOrderDetail workOrder = workOrders.get(principal, correlationId, workOrderId);
        UUID projectId = workOrder.workOrder().projectId();
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ_CAPABILITY, principal.tenantId(), "WorkOrder",
                workOrderId.toString(), projectId.toString()), correlationId);
        return deliveries.listByWorkOrder(principal.tenantId(), projectId, workOrderId, limit)
                .stream()
                .map(OutboundDeliveryRepository.DeliveryRecord::view)
                .toList();
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
            var profile = profiles.requireByConnectorVersion(delivery.connectorVersionId());
            ScheduledTaskView task = tasks.schedule(new ScheduleAutomatedTaskCommand(
                    principal.tenantId(), profile.taskType(), replayBusinessKey,
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

    @Override
    public RemoteStatusQueryView queryRemoteStatus(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            QueryRemoteStatusCommand command
    ) {
        if (principal.principalType() != CurrentPrincipal.PrincipalType.USER) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "Remote status query requires a USER principal");
        }
        if (command == null || command.deliveryId() == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "deliveryId is required");
        }
        String reason = requiredText(command.reason(), "reason", 1000);
        var record = deliveries.find(principal.tenantId(), command.deliveryId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "OutboundDelivery does not exist"));
        OutboundDeliveryView delivery = record.view();
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(
                        RETRY_CAPABILITY, principal.tenantId(), "OutboundDelivery",
                        delivery.deliveryId().toString(), delivery.projectId().toString()),
                metadata.correlationId());
        if (!"UNKNOWN".equals(delivery.status())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "Only UNKNOWN OutboundDelivery can be queried for remote status");
        }

        byte[] frozenPayload;
        try (var input = storage.openForScan(record.payloadObjectRef())) {
            frozenPayload = input.readAllBytes();
        } catch (IOException exception) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Cannot load frozen outbound payload: " + exception.getMessage());
        }
        if (!Sha256.digest(frozenPayload).equals(delivery.payloadDigest())) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Frozen outbound payload digest mismatch");
        }

        RemoteStatusQueryConnector connector = remoteStatusQueries.requireForConnectorVersion(
                delivery.connectorVersionId());
        // 网络探询在事务外；结果只审计观察，不自动改写 Delivery 状态。
        RemoteStatusQueryResult result = connector.query(new RemoteStatusQueryRequest(
                principal.tenantId(),
                delivery.deliveryId(),
                delivery.connectorVersionId(),
                delivery.externalOrderCode(),
                delivery.businessKey(),
                delivery.payloadDigest(),
                frozenPayload));
        Instant queriedAt = clock.instant();
        RemoteStatusQueryView view = toView(delivery, result, queriedAt);
        String requestDigest = Sha256.digest(delivery.deliveryId() + "|" + reason + "|"
                + view.outcome() + "|" + view.reasonCode());
        transactions.executeWithoutResult(status -> audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "OUTBOUND_DELIVERY_REMOTE_STATUS_QUERIED", RETRY_CAPABILITY,
                "OutboundDelivery", delivery.deliveryId().toString(), "ALLOW",
                auth.matchedGrantIds(), auth.policyVersion(), view.outcome(), null,
                requestDigest, metadata.correlationId(), queriedAt)));
        return view;
    }

    private static RemoteStatusQueryView toView(
            OutboundDeliveryView delivery,
            RemoteStatusQueryResult result,
            Instant queriedAt
    ) {
        return switch (result) {
            case RemoteStatusQueryResult.ConfirmedAccepted accepted -> new RemoteStatusQueryView(
                    delivery.deliveryId(), delivery.connectorVersionId(), "CONFIRMED_ACCEPTED",
                    "REMOTE_CONFIRMED_ACCEPTED", accepted.detail(), accepted.externalRef(), queriedAt);
            case RemoteStatusQueryResult.ConfirmedRejected rejected -> new RemoteStatusQueryView(
                    delivery.deliveryId(), delivery.connectorVersionId(), "CONFIRMED_REJECTED",
                    "REMOTE_CONFIRMED_REJECTED", rejected.detail(), rejected.externalRef(), queriedAt);
            case RemoteStatusQueryResult.StillUnknown unknown -> new RemoteStatusQueryView(
                    delivery.deliveryId(), delivery.connectorVersionId(), "STILL_UNKNOWN",
                    unknown.reasonCode(), unknown.detail(), null, queriedAt);
            case RemoteStatusQueryResult.NotSupported unsupported -> new RemoteStatusQueryView(
                    delivery.deliveryId(), delivery.connectorVersionId(), "NOT_SUPPORTED",
                    unsupported.reasonCode(), unsupported.detail(), null, queriedAt);
        };
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

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("OutboundDelivery serialization failed", exception);
        }
    }

    private record ResolvedLineage(
            com.serviceos.integration.spi.OutboundReviewSubmissionProfile profile,
            CanonicalMessageView canonical
    ) {
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
