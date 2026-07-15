package com.serviceos.integration.byd.application;

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
import com.serviceos.integration.application.InboundMessageRepository;
import com.serviceos.integration.byd.api.BydCpimReviewCallbackResponse;
import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;
import com.serviceos.integration.byd.infrastructure.BydCpimPayloadDigest;
import com.serviceos.integration.byd.infrastructure.BydCpimReplayConflictException;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
import com.serviceos.integration.byd.infrastructure.JdbcBydCpimReplayGuard;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.TaskSchedulingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** BYD 2.6 厂端审核回调：一个 transport Envelope 拆成逐订单 Canonical 与领域回执。 */
@Service
public class BydCpimReviewCallbackService {
    private static final String CONNECTOR_VERSION = "byd-cpim-v7.3.1";
    private static final String MESSAGE_TYPE = "RECORD_CLIENT_REVIEW_RESULT";
    private static final String MANUAL_TASK_TYPE = "integration.external-review-manual";
    private static final String AUTH_POLICY = "BYD_CPIM_SIGNATURE_V7_3_1";

    private final ObjectMapper objectMapper;
    private final JdbcBydCpimReplayGuard replayGuard;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final ExternalReviewReceiptService receipts;
    private final TaskSchedulingService tasks;
    private final OutboxAppender outbox;
    private final AuditAppender audit;
    private final TransactionTemplate transactions;
    private final BydCpimSignatureVerifier signatureVerifier;
    private final BydCpimReviewCallbackMapper mapper = new BydCpimReviewCallbackMapper();
    private final Clock clock;
    private final String tenantId;
    private final String adapterPrincipalId;

    public BydCpimReviewCallbackService(
            ObjectMapper objectMapper,
            JdbcBydCpimReplayGuard replayGuard,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            ExternalReviewReceiptService receipts,
            TaskSchedulingService tasks,
            OutboxAppender outbox,
            AuditAppender audit,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${serviceos.integration.byd.cpim.app-key}") String appKey,
            @Value("${serviceos.integration.byd.cpim.app-secret}") String appSecret,
            @Value("${serviceos.integration.byd.cpim.zone-id}") ZoneId protocolZone,
            @Value("${serviceos.integration.byd.cpim.tenant-id}") String tenantId,
            @Value("${serviceos.integration.byd.cpim.adapter-principal-id}") String adapterPrincipalId
    ) {
        this.objectMapper = objectMapper;
        this.replayGuard = replayGuard;
        this.messages = messages;
        this.storage = storage;
        this.receipts = receipts;
        this.tasks = tasks;
        this.outbox = outbox;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
        this.signatureVerifier = new BydCpimSignatureVerifier(appKey, appSecret, clock, protocolZone);
        this.tenantId = text(tenantId, "tenantId");
        this.adapterPrincipalId = text(adapterPrincipalId, "adapterPrincipalId");
    }

    public BydCpimReviewCallbackResponse receive(
            BydCpimSignatureHeaders headers,
            byte[] rawPayload,
            String correlationId
    ) {
        String safeCorrelationId = text(correlationId, "correlationId");
        Map<String, Object> raw;
        try {
            raw = objectMapper.readValue(Objects.requireNonNull(rawPayload, "rawPayload"), new TypeReference<>() { });
        } catch (RuntimeException exception) {
            return BydCpimReviewCallbackResponse.rejected("INVALID_PAYLOAD");
        }
        var verification = signatureVerifier.verify(headers, raw);
        if (!verification.valid()) {
            return BydCpimReviewCallbackResponse.rejected(verification.reason().name());
        }

        final String requestDigest;
        try {
            requestDigest = BydCpimPayloadDigest.sha256(raw);
        } catch (IllegalArgumentException exception) {
            return BydCpimReviewCallbackResponse.rejected("INVALID_PAYLOAD");
        }
        String rawDigest = Sha256.digest(rawPayload);
        String transportKey = Sha256.digest(headers.appKey() + "|" + headers.nonce() + "|" + headers.currentDate());
        String tenantPrefix = Sha256.digest(tenantId).substring(0, 16);
        String rawObjectRef = "integration/inbound/" + tenantPrefix
                + "/byd-cpim/review/raw/" + rawDigest + ".json";
        Instant receivedAt = clock.instant();

        InboundEnvelopeView envelope;
        try {
            envelope = Objects.requireNonNull(transactions.execute(status -> {
                var registration = messages.registerEnvelope(new InboundMessageRepository.NewInboundEnvelope(
                        UUID.randomUUID(), tenantId, CONNECTOR_VERSION, MESSAGE_TYPE, transportKey,
                        headers.nonce(), receivedAt, rawObjectRef, rawDigest, safeCorrelationId));
                var replay = replayGuard.register(
                        headers.appKey(), headers.nonce(), headers.currentDate().toEpochDay(),
                        requestDigest, registration.envelope().view().inboundEnvelopeId());
                if (replay.inboundEnvelopeId() == null
                        || !replay.inboundEnvelopeId().equals(registration.envelope().view().inboundEnvelopeId())
                        || !registration.envelope().view().rawPayloadDigest().equals(rawDigest)) {
                    throw new BydCpimReplayConflictException();
                }
                return registration.envelope().view();
            }));
        } catch (BydCpimReplayConflictException exception) {
            return BydCpimReviewCallbackResponse.rejected("REPLAY_CONFLICT");
        }
        if (!"RECEIVED".equals(envelope.processingStatus())) {
            return responseFor(envelope);
        }

        store(rawObjectRef, rawPayload, rawDigest);
        final BydCpimMappedReviewCallback callback;
        try {
            callback = mapper.map(raw);
        } catch (IllegalArgumentException exception) {
            return rejectEnvelope(envelope, headers, requestDigest, "INVALID_REVIEW_CALLBACK");
        }

        for (String orderCode : callback.orderCodes()) {
            processItem(envelope, callback, orderCode, tenantPrefix, safeCorrelationId);
        }

        BydCpimReviewCallbackResponse response = responseFor(envelope.inboundEnvelopeId());
        String resultCode = response.data().isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS";
        String responseDigest = Sha256.digest(json(response));
        transactions.executeWithoutResult(status -> {
            messages.completeBatchEnvelope(
                    tenantId, envelope.inboundEnvelopeId(), null, requestDigest,
                    BydCpimReviewCallbackMapper.MAPPING_VERSION, resultCode, responseDigest, clock.instant());
            replayGuard.complete(
                    headers.appKey(), headers.nonce(), headers.currentDate().toEpochDay(), responseDigest);
        });
        return response;
    }

    private void processItem(
            InboundEnvelopeView envelope,
            BydCpimMappedReviewCallback callback,
            String orderCode,
            String tenantPrefix,
            String correlationId
    ) {
        byte[] canonicalPayload = canonicalPayload(callback, orderCode);
        String payloadDigest = Sha256.digest(canonicalPayload);
        String objectRef = "integration/inbound/" + tenantPrefix + "/byd-cpim/review/canonical/"
                + payloadDigest + ".json";
        store(objectRef, canonicalPayload, payloadDigest);
        try {
            transactions.executeWithoutResult(status -> processItemTransaction(
                    envelope, callback, orderCode, objectRef, payloadDigest, correlationId));
        } catch (BusinessProblem problem) {
            if (!isDeterministicReviewConflict(problem.code())) {
                throw problem;
            }
            transactions.executeWithoutResult(status -> recordBusinessFailure(
                    envelope, callback, orderCode, objectRef, payloadDigest,
                    problem.code().name(), correlationId));
        }
    }

    private void processItemTransaction(
            InboundEnvelopeView envelope,
            BydCpimMappedReviewCallback callback,
            String orderCode,
            String objectRef,
            String payloadDigest,
            String correlationId
    ) {
        String businessKey = businessKey(callback, orderCode);
        var existing = messages.findCanonicalByBusinessKey(
                tenantId, CONNECTOR_VERSION, MESSAGE_TYPE, businessKey);
        if (existing.isPresent()) {
            copyOrConflict(envelope, orderCode, payloadDigest, existing.get().view(), correlationId);
            return;
        }

        ExternalReviewRouteView route = messages.findActiveExternalReviewRoute(
                tenantId, CONNECTOR_VERSION, orderCode).orElse(null);
        var registration = messages.registerCanonical(new InboundMessageRepository.NewCanonicalMessage(
                UUID.randomUUID(), tenantId, route == null ? null : route.projectId(), CONNECTOR_VERSION,
                MESSAGE_TYPE, businessKey, objectRef, payloadDigest,
                route == null ? BydCpimReviewCallbackMapper.MAPPING_VERSION : route.mappingVersionId(),
                envelope.inboundEnvelopeId(), clock.instant()));
        CanonicalMessageView canonical = registration.message().view();
        if (!registration.created()) {
            copyOrConflict(envelope, orderCode, payloadDigest, canonical, correlationId);
            return;
        }
        if (route == null) {
            rejectWithManualTask(envelope, orderCode, canonical, "ROUTE_NOT_FOUND", correlationId);
            return;
        }

        ExternalReviewReceiptView receipt = receipts.record(
                adapterPrincipal(), new CommandMetadata(correlationId, canonical.canonicalMessageId().toString()),
                new RecordExternalReviewReceiptCommand(
                        route.reviewCaseId(), envelope.inboundEnvelopeId().toString(),
                        canonical.canonicalMessageId().toString(), businessKey,
                        route.callbackBatchRef(), route.mappingVersionId(), callback.domainResult(),
                        "REJECTED".equals(callback.domainResult())
                                ? List.of("BYD.REVIEW.REJECTED") : List.of(),
                        List.of(), "canonical-message:" + canonical.canonicalMessageId()));
        Instant now = clock.instant();
        messages.completeCanonical(
                tenantId, canonical.canonicalMessageId(), "ACCEPTED",
                "EXTERNAL_REVIEW_RECEIPT", receipt.receiptId().toString(), now);
        messages.completeExternalReviewRoute(
                tenantId, route.reviewRouteId(), canonical.canonicalMessageId(), now);
        messages.insertItemResult(new InboundMessageRepository.InboundItemResult(
                envelope.inboundEnvelopeId(), orderCode, canonical.canonicalMessageId(),
                "ACCEPTED", "ACCEPTED", "EXTERNAL_REVIEW_RECEIPT", receipt.receiptId().toString(), now));
        appendProcessedEvent(canonical, envelope, route, receipt, correlationId, now);
        appendAudit(envelope, orderCode, canonical, "ACCEPTED", null, correlationId, now);
    }

    private void recordBusinessFailure(
            InboundEnvelopeView envelope,
            BydCpimMappedReviewCallback callback,
            String orderCode,
            String objectRef,
            String payloadDigest,
            String code,
            String correlationId
    ) {
        String businessKey = businessKey(callback, orderCode);
        var existing = messages.findCanonicalByBusinessKey(
                tenantId, CONNECTOR_VERSION, MESSAGE_TYPE, businessKey);
        if (existing.isPresent()) {
            copyOrConflict(envelope, orderCode, payloadDigest, existing.get().view(), correlationId);
            return;
        }
        ExternalReviewRouteView route = messages.findActiveExternalReviewRoute(
                tenantId, CONNECTOR_VERSION, orderCode).orElse(null);
        var registration = messages.registerCanonical(new InboundMessageRepository.NewCanonicalMessage(
                UUID.randomUUID(), tenantId, route == null ? null : route.projectId(), CONNECTOR_VERSION,
                MESSAGE_TYPE, businessKey, objectRef, payloadDigest,
                route == null ? BydCpimReviewCallbackMapper.MAPPING_VERSION : route.mappingVersionId(),
                envelope.inboundEnvelopeId(), clock.instant()));
        CanonicalMessageView canonical = registration.message().view();
        if (!registration.created()) {
            copyOrConflict(envelope, orderCode, payloadDigest, canonical, correlationId);
            return;
        }
        rejectWithManualTask(envelope, orderCode, canonical, code, correlationId);
    }

    private void copyOrConflict(
            InboundEnvelopeView envelope,
            String orderCode,
            String payloadDigest,
            CanonicalMessageView canonical,
            String correlationId
    ) {
        if (!payloadDigest.equals(canonical.payloadDigest())) {
            UUID taskId = manualTask(canonical.businessKey(), payloadDigest, "CANONICAL_CONFLICT", correlationId);
            messages.insertItemResult(new InboundMessageRepository.InboundItemResult(
                    envelope.inboundEnvelopeId(), orderCode, canonical.canonicalMessageId(),
                    "REJECTED", "CANONICAL_CONFLICT", "MANUAL_TASK", taskId.toString(), clock.instant()));
            appendAudit(envelope, orderCode, canonical, "REJECTED", "CANONICAL_CONFLICT",
                    correlationId, clock.instant());
            return;
        }
        if (!"COMPLETED".equals(canonical.processingStatus())) {
            throw new IllegalStateException("CanonicalMessage is not recoverably completed");
        }
        String itemResult = "ACCEPTED".equals(canonical.resultCode()) ? "ACCEPTED" : "REJECTED";
        messages.insertItemResult(new InboundMessageRepository.InboundItemResult(
                envelope.inboundEnvelopeId(), orderCode, canonical.canonicalMessageId(), itemResult,
                canonical.resultCode(), canonical.resultType(), canonical.resultId(), clock.instant()));
    }

    private void rejectWithManualTask(
            InboundEnvelopeView envelope,
            String orderCode,
            CanonicalMessageView canonical,
            String code,
            String correlationId
    ) {
        Instant now = clock.instant();
        UUID taskId = manualTask(canonical.businessKey(), canonical.payloadDigest(), code, correlationId);
        messages.completeCanonical(
                tenantId, canonical.canonicalMessageId(), code, "MANUAL_TASK", taskId.toString(), now);
        messages.insertItemResult(new InboundMessageRepository.InboundItemResult(
                envelope.inboundEnvelopeId(), orderCode, canonical.canonicalMessageId(),
                "REJECTED", code, "MANUAL_TASK", taskId.toString(), now));
        appendAudit(envelope, orderCode, canonical, "REJECTED", code, correlationId, now);
    }

    private UUID manualTask(String businessKey, String payloadDigest, String code, String correlationId) {
        String taskKey = "byd-review-manual:" + Sha256.digest(businessKey + "|" + payloadDigest + "|" + code);
        String taskDigest = Sha256.digest(taskKey + "|" + code);
        return tasks.createHandlingTask(new CreateHandlingTaskCommand(
                tenantId, MANUAL_TASK_TYPE, taskKey, "canonical-business-key:" + Sha256.digest(businessKey),
                taskDigest, 800, clock.instant(), correlationId)).taskId();
    }

    private BydCpimReviewCallbackResponse rejectEnvelope(
            InboundEnvelopeView envelope,
            BydCpimSignatureHeaders headers,
            String requestDigest,
            String code
    ) {
        BydCpimReviewCallbackResponse response = BydCpimReviewCallbackResponse.rejected(code);
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            boolean transitioned = messages.rejectEnvelope(
                    tenantId, envelope.inboundEnvelopeId(), null, null, null, code, now);
            if (transitioned) {
                appendAudit(envelope, null, null, "REJECTED", code, envelope.correlationId(), now);
            }
            replayGuard.complete(headers.appKey(), headers.nonce(), headers.currentDate().toEpochDay(),
                    Sha256.digest(json(response)));
        });
        return response;
    }

    private BydCpimReviewCallbackResponse responseFor(InboundEnvelopeView envelope) {
        if ("REJECTED".equals(envelope.processingStatus())) {
            return BydCpimReviewCallbackResponse.rejected(envelope.resultCode());
        }
        return responseFor(envelope.inboundEnvelopeId());
    }

    private BydCpimReviewCallbackResponse responseFor(UUID envelopeId) {
        List<InboundMessageRepository.InboundItemResult> items = messages.findItemResults(tenantId, envelopeId);
        List<BydCpimReviewCallbackResponse.Failure> failures = items.stream()
                .filter(item -> "REJECTED".equals(item.processingResult()))
                .sorted(Comparator.comparing(InboundMessageRepository.InboundItemResult::itemKey))
                .map(item -> new BydCpimReviewCallbackResponse.Failure(item.itemKey(), item.resultCode()))
                .toList();
        return new BydCpimReviewCallbackResponse(
                failures.isEmpty() ? "success" : "partially success", failures);
    }

    private void appendProcessedEvent(
            CanonicalMessageView canonical,
            InboundEnvelopeView envelope,
            ExternalReviewRouteView route,
            ExternalReviewReceiptView receipt,
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
            String orderCode,
            CanonicalMessageView canonical,
            String result,
            String errorCode,
            String correlationId,
            Instant now
    ) {
        String resourceId = canonical == null
                ? envelope.inboundEnvelopeId().toString()
                : canonical.canonicalMessageId().toString();
        String digest = canonical == null ? envelope.rawPayloadDigest() : canonical.payloadDigest();
        audit.append(new AuditEntry(
                UUID.randomUUID(), tenantId, adapterPrincipalId,
                "BYD_EXTERNAL_REVIEW_CALLBACK_PROCESSED", AUTH_POLICY,
                canonical == null ? "InboundEnvelope" : "CanonicalMessage", resourceId,
                "ALLOW", List.of(), CONNECTOR_VERSION, result, errorCode,
                Sha256.digest(digest + "|" + Objects.toString(orderCode, "")), correlationId, now));
    }

    private CurrentPrincipal adapterPrincipal() {
        return new CurrentPrincipal(
                adapterPrincipalId, tenantId, CurrentPrincipal.PrincipalType.SERVICE,
                "byd-cpim-adapter", Set.of());
    }

    private byte[] canonicalPayload(BydCpimMappedReviewCallback callback, String orderCode) {
        try {
            return objectMapper.writeValueAsBytes(new CanonicalReviewResult(
                    orderCode, callback.externalResult(), callback.domainResult(), callback.remark(),
                    callback.examinePerson(), callback.examineDateText(), BydCpimReviewCallbackMapper.MAPPING_VERSION));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Review callback canonical serialization failed", exception);
        }
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

    private static String businessKey(BydCpimMappedReviewCallback callback, String orderCode) {
        return "BYD:REVIEW:" + orderCode + ":" + callback.externalResult() + ":" + callback.examineDateText();
    }

    private static boolean isDeterministicReviewConflict(ProblemCode code) {
        return code == ProblemCode.VALIDATION_FAILED
                || code == ProblemCode.RESOURCE_NOT_FOUND
                || code == ProblemCode.REVIEW_CASE_CONFLICT
                || code == ProblemCode.REVIEW_CASE_ALREADY_DECIDED
                || code == ProblemCode.REVIEW_CASE_STATE_CONFLICT;
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private record CanonicalReviewResult(
            String orderCode,
            String externalResult,
            String domainResult,
            String remark,
            String examinePerson,
            String examineDate,
            String callbackMappingVersion
    ) {
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
