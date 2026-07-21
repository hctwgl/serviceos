package com.serviceos.integration.byd.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.application.InboundMessageRepository;
import com.serviceos.integration.application.InboundReviewCallbackItemPipeline;
import com.serviceos.integration.byd.api.BydCpimReviewCallbackResponse;
import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;
import com.serviceos.integration.byd.infrastructure.BydCpimPayloadDigest;
import com.serviceos.integration.byd.infrastructure.BydCpimReplayConflictException;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
import com.serviceos.integration.byd.infrastructure.JooqBydCpimReplayGuard;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.InboundConnectorAuditContext;
import com.serviceos.integration.spi.ReviewCallbackMappedItem;
import com.serviceos.shared.Sha256;
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
import java.util.UUID;

/**
 * BYD 2.6 厂端审核回调适配器。
 *
 * <p>协议验签、防重放与 batch Envelope 留在本类；逐订单 Canonical/领域回执委托
 * {@link InboundReviewCallbackItemPipeline}。</p>
 */
@Service
public class BydCpimReviewCallbackService {
    private static final ConnectorIdentity CONNECTOR = new ConnectorIdentity(
            "BYD_CPIM", "byd-cpim-v7.3.1");
    private static final String MESSAGE_TYPE = ReviewCallbackMappedItem.MESSAGE_TYPE_RECORD_CLIENT_REVIEW_RESULT;
    private static final String AUTH_POLICY = "BYD_CPIM_SIGNATURE_V7_3_1";
    private static final String OBJECT_NAMESPACE = "byd-cpim/review";

    private final ObjectMapper objectMapper;
    private final JooqBydCpimReplayGuard replayGuard;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final InboundReviewCallbackItemPipeline callbackPipeline;
    private final TransactionTemplate transactions;
    private final AuditAppender audit;
    private final BydCpimSignatureVerifier signatureVerifier;
    private final BydCpimReviewCallbackMapper mapper = new BydCpimReviewCallbackMapper();
    private final Clock clock;
    private final String tenantId;
    private final String adapterPrincipalId;

    public BydCpimReviewCallbackService(
            ObjectMapper objectMapper,
            JooqBydCpimReplayGuard replayGuard,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            InboundReviewCallbackItemPipeline callbackPipeline,
            TransactionTemplate transactions,
            AuditAppender audit,
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
        this.callbackPipeline = callbackPipeline;
        this.transactions = transactions;
        this.audit = audit;
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
                        UUID.randomUUID(), tenantId, CONNECTOR.connectorVersionId(), MESSAGE_TYPE, transportKey,
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

        InboundConnectorAuditContext auditContext = new InboundConnectorAuditContext(
                adapterPrincipalId, AUTH_POLICY, "integration.inbound.reviewCallback", requestDigest);
        for (String orderCode : callback.orderCodes()) {
            ReviewCallbackMappedItem item = toMappedItem(callback, orderCode);
            callbackPipeline.processMappedItem(
                    envelope, CONNECTOR, tenantId, item, auditContext, safeCorrelationId, OBJECT_NAMESPACE);
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

    private ReviewCallbackMappedItem toMappedItem(BydCpimMappedReviewCallback callback, String orderCode) {
        List<String> reasonCodes = "REJECTED".equals(callback.domainResult())
                ? List.of("BYD.REVIEW.REJECTED")
                : List.of();
        return new ReviewCallbackMappedItem(
                orderCode,
                businessKey(callback, orderCode),
                orderCode,
                callback.domainResult(),
                reasonCodes,
                BydCpimReviewCallbackMapper.MAPPING_VERSION,
                canonicalPayload(callback, orderCode));
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
                audit.append(new AuditEntry(
                        UUID.randomUUID(), tenantId, adapterPrincipalId,
                        "EXTERNAL_REVIEW_CALLBACK_PROCESSED", AUTH_POLICY,
                        "InboundEnvelope", envelope.inboundEnvelopeId().toString(),
                        "ALLOW", List.of(), CONNECTOR.connectorVersionId(), "REJECTED", code,
                        Sha256.digest(envelope.rawPayloadDigest() + "|"), envelope.correlationId(), now));
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
}
