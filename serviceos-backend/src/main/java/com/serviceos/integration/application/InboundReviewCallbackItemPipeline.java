package com.serviceos.integration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.evidence.api.ExternalReviewReceiptService;
import com.serviceos.evidence.api.ExternalReviewReceiptView;
import com.serviceos.evidence.api.RecordExternalReviewReceiptCommand;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.ExternalReviewRouteView;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.InboundConnectorAuditContext;
import com.serviceos.integration.spi.ReviewCallbackMappedItem;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.TaskSchedulingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 审核回调单条 Canonical 处理管道。
 *
 * <p>适配器完成验签、transport Envelope 登记与反腐映射后，对每个订单项调用本管道。
 * 管道负责：Canonical 私有存储 → 登记/冲突检测 → 路由解析 → 领域回执 → Outbox/审计。
 * 适配器不得直接写 Evidence/Task 业务表。</p>
 */
@Service
public class InboundReviewCallbackItemPipeline {
    private static final String MESSAGE_TYPE = ReviewCallbackMappedItem.MESSAGE_TYPE_RECORD_CLIENT_REVIEW_RESULT;
    private static final String MANUAL_TASK_TYPE = "integration.external-review-manual";

    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final ExternalReviewReceiptService receipts;
    private final TaskSchedulingService tasks;
    private final OutboxAppender outbox;
    private final AuditAppender audit;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InboundReviewCallbackItemPipeline(
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            ExternalReviewReceiptService receipts,
            TaskSchedulingService tasks,
            OutboxAppender outbox,
            AuditAppender audit,
            TransactionTemplate transactions,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.messages = messages;
        this.storage = storage;
        this.receipts = receipts;
        this.tasks = tasks;
        this.outbox = outbox;
        this.audit = audit;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void processMappedItem(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            ReviewCallbackMappedItem item,
            InboundConnectorAuditContext auditContext,
            String correlationId,
            String objectNamespace
    ) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(connector, "connector must not be null");
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(auditContext, "auditContext must not be null");
        String safeTenant = required(tenantId, "tenantId");
        String safeCorrelation = required(correlationId, "correlationId");
        String safeNamespace = required(objectNamespace, "objectNamespace");

        String payloadDigest = Sha256.digest(item.canonicalPayload());
        String tenantPrefix = Sha256.digest(safeTenant).substring(0, 16);
        String objectRef = "integration/inbound/" + tenantPrefix + "/" + safeNamespace
                + "/canonical/" + payloadDigest + ".json";
        store(objectRef, item.canonicalPayload(), payloadDigest);

        try {
            transactions.executeWithoutResult(status -> processItemTransaction(
                    envelope, connector, safeTenant, item, objectRef, payloadDigest,
                    auditContext, safeCorrelation));
        } catch (BusinessProblem problem) {
            if (!isDeterministicReviewConflict(problem.code())) {
                throw problem;
            }
            transactions.executeWithoutResult(status -> recordBusinessFailure(
                    envelope, connector, safeTenant, item, objectRef, payloadDigest,
                    problem.code().name(), auditContext, safeCorrelation));
        }
    }

    private void processItemTransaction(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            ReviewCallbackMappedItem item,
            String objectRef,
            String payloadDigest,
            InboundConnectorAuditContext auditContext,
            String correlationId
    ) {
        var existing = messages.findCanonicalByBusinessKey(
                tenantId, connector.connectorVersionId(), MESSAGE_TYPE, item.businessKey());
        if (existing.isPresent()) {
            copyOrConflict(envelope, connector, tenantId, item, payloadDigest,
                    existing.get().view(), auditContext, correlationId);
            return;
        }

        ExternalReviewRouteView route = messages.findActiveExternalReviewRoute(
                tenantId, connector.connectorVersionId(), item.externalOrderCode()).orElse(null);
        var registration = messages.registerCanonical(new InboundMessageRepository.NewCanonicalMessage(
                UUID.randomUUID(), tenantId, route == null ? null : route.projectId(),
                connector.connectorVersionId(), MESSAGE_TYPE, item.businessKey(), objectRef, payloadDigest,
                route == null ? item.mappingVersionId() : route.mappingVersionId(),
                envelope.inboundEnvelopeId(), clock.instant()));
        CanonicalMessageView canonical = registration.message().view();
        if (!registration.created()) {
            copyOrConflict(envelope, connector, tenantId, item, payloadDigest,
                    canonical, auditContext, correlationId);
            return;
        }
        if (route == null) {
            rejectWithManualTask(envelope, connector, tenantId, item, canonical,
                    "ROUTE_NOT_FOUND", auditContext, correlationId);
            return;
        }

        ExternalReviewReceiptView receipt = receipts.record(
                adapterPrincipal(auditContext, tenantId),
                new CommandMetadata(correlationId, canonical.canonicalMessageId().toString()),
                new RecordExternalReviewReceiptCommand(
                        route.reviewCaseId(), envelope.inboundEnvelopeId().toString(),
                        canonical.canonicalMessageId().toString(), item.businessKey(),
                        route.callbackBatchRef(), route.mappingVersionId(), item.domainResult(),
                        item.reasonCodes(), List.of(),
                        "canonical-message:" + canonical.canonicalMessageId()));
        Instant now = clock.instant();
        messages.completeCanonical(
                tenantId, canonical.canonicalMessageId(), "ACCEPTED",
                "EXTERNAL_REVIEW_RECEIPT", receipt.receiptId().toString(), now);
        messages.completeExternalReviewRoute(
                tenantId, route.reviewRouteId(), canonical.canonicalMessageId(), now);
        messages.insertItemResult(new InboundMessageRepository.InboundItemResult(
                envelope.inboundEnvelopeId(), item.itemKey(), canonical.canonicalMessageId(),
                "ACCEPTED", "ACCEPTED", "EXTERNAL_REVIEW_RECEIPT", receipt.receiptId().toString(), now));
        appendProcessedEvent(canonical, envelope, route, receipt, tenantId, correlationId, now);
        appendAudit(envelope, connector, tenantId, item.itemKey(), canonical, "ACCEPTED", null,
                auditContext, correlationId, now);
    }

    private void recordBusinessFailure(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            ReviewCallbackMappedItem item,
            String objectRef,
            String payloadDigest,
            String code,
            InboundConnectorAuditContext auditContext,
            String correlationId
    ) {
        var existing = messages.findCanonicalByBusinessKey(
                tenantId, connector.connectorVersionId(), MESSAGE_TYPE, item.businessKey());
        if (existing.isPresent()) {
            copyOrConflict(envelope, connector, tenantId, item, payloadDigest,
                    existing.get().view(), auditContext, correlationId);
            return;
        }
        ExternalReviewRouteView route = messages.findActiveExternalReviewRoute(
                tenantId, connector.connectorVersionId(), item.externalOrderCode()).orElse(null);
        var registration = messages.registerCanonical(new InboundMessageRepository.NewCanonicalMessage(
                UUID.randomUUID(), tenantId, route == null ? null : route.projectId(),
                connector.connectorVersionId(), MESSAGE_TYPE, item.businessKey(), objectRef, payloadDigest,
                route == null ? item.mappingVersionId() : route.mappingVersionId(),
                envelope.inboundEnvelopeId(), clock.instant()));
        CanonicalMessageView canonical = registration.message().view();
        if (!registration.created()) {
            copyOrConflict(envelope, connector, tenantId, item, payloadDigest,
                    canonical, auditContext, correlationId);
            return;
        }
        rejectWithManualTask(envelope, connector, tenantId, item, canonical, code,
                auditContext, correlationId);
    }

    private void copyOrConflict(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            ReviewCallbackMappedItem item,
            String payloadDigest,
            CanonicalMessageView canonical,
            InboundConnectorAuditContext auditContext,
            String correlationId
    ) {
        if (!payloadDigest.equals(canonical.payloadDigest())) {
            UUID taskId = manualTask(tenantId, item.businessKey(), payloadDigest,
                    "CANONICAL_CONFLICT", correlationId);
            messages.insertItemResult(new InboundMessageRepository.InboundItemResult(
                    envelope.inboundEnvelopeId(), item.itemKey(), canonical.canonicalMessageId(),
                    "REJECTED", "CANONICAL_CONFLICT", "MANUAL_TASK", taskId.toString(), clock.instant()));
            appendAudit(envelope, connector, tenantId, item.itemKey(), canonical, "REJECTED",
                    "CANONICAL_CONFLICT", auditContext, correlationId, clock.instant());
            return;
        }
        if (!"COMPLETED".equals(canonical.processingStatus())) {
            throw new IllegalStateException("CanonicalMessage is not recoverably completed");
        }
        String itemResult = "ACCEPTED".equals(canonical.resultCode()) ? "ACCEPTED" : "REJECTED";
        messages.insertItemResult(new InboundMessageRepository.InboundItemResult(
                envelope.inboundEnvelopeId(), item.itemKey(), canonical.canonicalMessageId(), itemResult,
                canonical.resultCode(), canonical.resultType(), canonical.resultId(), clock.instant()));
    }

    private void rejectWithManualTask(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            ReviewCallbackMappedItem item,
            CanonicalMessageView canonical,
            String code,
            InboundConnectorAuditContext auditContext,
            String correlationId
    ) {
        Instant now = clock.instant();
        UUID taskId = manualTask(tenantId, canonical.businessKey(), canonical.payloadDigest(),
                code, correlationId);
        messages.completeCanonical(
                tenantId, canonical.canonicalMessageId(), code, "MANUAL_TASK", taskId.toString(), now);
        messages.insertItemResult(new InboundMessageRepository.InboundItemResult(
                envelope.inboundEnvelopeId(), item.itemKey(), canonical.canonicalMessageId(),
                "REJECTED", code, "MANUAL_TASK", taskId.toString(), now));
        appendAudit(envelope, connector, tenantId, item.itemKey(), canonical, "REJECTED", code,
                auditContext, correlationId, now);
    }

    private UUID manualTask(
            String tenantId,
            String businessKey,
            String payloadDigest,
            String code,
            String correlationId
    ) {
        String taskKey = "review-callback-manual:"
                + Sha256.digest(businessKey + "|" + payloadDigest + "|" + code);
        String taskDigest = Sha256.digest(taskKey + "|" + code);
        return tasks.createHandlingTask(new CreateHandlingTaskCommand(
                tenantId, MANUAL_TASK_TYPE, taskKey,
                "canonical-business-key:" + Sha256.digest(businessKey),
                taskDigest, 800, clock.instant(), correlationId)).taskId();
    }

    private void appendProcessedEvent(
            CanonicalMessageView canonical,
            InboundEnvelopeView envelope,
            ExternalReviewRouteView route,
            ExternalReviewReceiptView receipt,
            String tenantId,
            String correlationId,
            Instant now
    ) {
        String payload = json(new ReviewCallbackProcessedPayload(
                canonical.canonicalMessageId(), envelope.inboundEnvelopeId(), route.reviewRouteId(),
                route.reviewCaseId(), receipt.receiptId(), route.projectId(), canonical.businessKey(),
                canonical.payloadDigest(), route.mappingVersionId(), receipt.result(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "integration",
                "integration.external-review-callback-processed", 1,
                "CanonicalMessage", canonical.canonicalMessageId().toString(), 1L,
                tenantId, correlationId, envelope.inboundEnvelopeId().toString(),
                route.reviewCaseId().toString(), payload, Sha256.digest(payload), now));
    }

    private void appendAudit(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            String itemKey,
            CanonicalMessageView canonical,
            String result,
            String errorCode,
            InboundConnectorAuditContext auditContext,
            String correlationId,
            Instant now
    ) {
        String resourceId = canonical == null
                ? envelope.inboundEnvelopeId().toString()
                : canonical.canonicalMessageId().toString();
        String digest = canonical == null ? envelope.rawPayloadDigest() : canonical.payloadDigest();
        audit.append(new AuditEntry(
                UUID.randomUUID(), tenantId, auditContext.actorId(),
                "EXTERNAL_REVIEW_CALLBACK_PROCESSED", auditContext.authPolicy(),
                canonical == null ? "InboundEnvelope" : "CanonicalMessage", resourceId,
                "ALLOW", List.of(), connector.connectorVersionId(), result, errorCode,
                Sha256.digest(digest + "|" + Objects.toString(itemKey, "")), correlationId, now));
    }

    private CurrentPrincipal adapterPrincipal(InboundConnectorAuditContext auditContext, String tenantId) {
        return new CurrentPrincipal(
                auditContext.actorId(), tenantId, CurrentPrincipal.PrincipalType.SERVICE,
                "review-callback-adapter", Set.of());
    }

    private void store(String objectRef, byte[] content, String digest) {
        try {
            storage.storeInternal(
                    objectRef, new ByteArrayInputStream(content), content.length, digest, "application/json");
        } catch (IOException exception) {
            throw new IllegalStateException("Private inbound object storage failed", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Review callback serialization failed", exception);
        }
    }

    private static boolean isDeterministicReviewConflict(ProblemCode code) {
        return code == ProblemCode.VALIDATION_FAILED
                || code == ProblemCode.RESOURCE_NOT_FOUND
                || code == ProblemCode.REVIEW_CASE_CONFLICT
                || code == ProblemCode.REVIEW_CASE_ALREADY_DECIDED
                || code == ProblemCode.REVIEW_CASE_STATE_CONFLICT;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private record ReviewCallbackProcessedPayload(
            UUID canonicalMessageId,
            UUID inboundEnvelopeId,
            UUID reviewRouteId,
            UUID reviewCaseId,
            UUID externalReviewReceiptId,
            UUID projectId,
            String businessKey,
            String payloadDigest,
            String mappingVersionId,
            String reviewResult,
            Instant processedAt
    ) {
    }
}
