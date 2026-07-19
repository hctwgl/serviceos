package com.serviceos.integration.referenceoem.application;

import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.application.InboundMessageRepository;
import com.serviceos.integration.application.InboundUpdateWorkOrderPipeline;
import com.serviceos.integration.referenceoem.api.ReferenceOemInboundOrderResponse;
import com.serviceos.integration.referenceoem.api.ReferenceOemUpdateOrderPayload;
import com.serviceos.integration.referenceoem.infrastructure.ReferenceOemSampleSignature;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.InboundConnectorAuditContext;
import com.serviceos.integration.spi.InboundUpdateWorkOrderResult;
import com.serviceos.integration.spi.UpdateWorkOrderMappedInbound;
import com.serviceos.integration.spi.UpdateWorkOrderRouteHint;
import com.serviceos.shared.Sha256;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * REFERENCE / SAMPLE 更新入站适配器。
 *
 * <p>M343：HMAC 验签后仅构造 {@link UpdateWorkOrderRouteHint}；领域字段由冻结 UPDATE Mapping 物化。
 * 明确 SAMPLE / TBD_EXTERNAL_CONTRACT，不得声称真实车企已接入。</p>
 */
@Service
public class ReferenceOemInboundUpdateOrderService {
    private static final String OBJECT_NAMESPACE = "reference-oem-update";
    private static final String AUTH_POLICY = "REFERENCE_OEM_SAMPLE_HMAC_V1";
    private static final String RECEIVE_CAPABILITY = "integration.receiveInbound";
    private static final String UPDATE_BUSINESS_PREFIX = "REFERENCE_OEM:INSTALL-UPDATE:";

    private final ObjectMapper objectMapper;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final InboundUpdateWorkOrderPipeline updatePipeline;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String appKey;
    private final String appSecret;
    private final String tenantId;

    public ReferenceOemInboundUpdateOrderService(
            ObjectMapper objectMapper,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            InboundUpdateWorkOrderPipeline updatePipeline,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${serviceos.integration.reference-oem.app-key:local-reference-oem-key}") String appKey,
            @Value("${serviceos.integration.reference-oem.app-secret:local-reference-oem-secret-change-me}") String appSecret,
            @Value("${serviceos.integration.reference-oem.tenant-id:tenant-reference-oem-pilot}") String tenantId
    ) {
        this.objectMapper = objectMapper;
        this.messages = messages;
        this.storage = storage;
        this.updatePipeline = updatePipeline;
        this.transactions = transactions;
        this.clock = clock;
        this.appKey = required(appKey, "appKey");
        this.appSecret = required(appSecret, "appSecret");
        this.tenantId = required(tenantId, "tenantId");
    }

    public ReferenceOemInboundOrderResponse receive(
            String providedAppKey,
            String nonce,
            String signature,
            byte[] rawPayload,
            String correlationId
    ) {
        String safeCorrelationId = required(correlationId, "correlationId");
        if (!appKey.equals(providedAppKey)) {
            return ReferenceOemInboundOrderResponse.rejected("INVALID_APP_KEY", "unknown SAMPLE app key");
        }
        if (!ReferenceOemSampleSignature.matches(appSecret, nonce, rawPayload, signature)) {
            return ReferenceOemInboundOrderResponse.rejected("INVALID_SIGNATURE", "SAMPLE signature mismatch");
        }

        String rawPayloadDigest = Sha256.digest(rawPayload);
        String transportDedupKey = Sha256.digest("UPDATE|" + providedAppKey + "|" + nonce);
        String tenantObjectPrefix = Sha256.digest(tenantId).substring(0, 16);
        String rawObjectRef = "integration/inbound/" + tenantObjectPrefix
                + "/" + OBJECT_NAMESPACE + "/raw/" + rawPayloadDigest + ".json";
        Instant receivedAt = clock.instant();
        InboundConnectorAuditContext audit = new InboundConnectorAuditContext(
                "connector:reference-oem:" + providedAppKey,
                AUTH_POLICY,
                RECEIVE_CAPABILITY,
                rawPayloadDigest);

        InboundEnvelopeView envelope = Objects.requireNonNull(transactions.execute(status -> {
            var registered = messages.registerEnvelope(new InboundMessageRepository.NewInboundEnvelope(
                    UUID.randomUUID(), tenantId, ReferenceOemInboundOrderService.ADAPTER_VERSION,
                    UpdateWorkOrderMappedInbound.MESSAGE_TYPE_UPDATE_WORK_ORDER,
                    transportDedupKey, nonce, receivedAt, rawObjectRef,
                    rawPayloadDigest, safeCorrelationId));
            if (!registered.envelope().view().rawPayloadDigest().equals(rawPayloadDigest)) {
                throw new IllegalStateException("REFERENCE_OEM update transport replay conflict");
            }
            return registered.envelope().view();
        }));

        if (!"RECEIVED".equals(envelope.processingStatus())) {
            return replay(envelope);
        }

        store(rawObjectRef, rawPayload, rawPayloadDigest);

        final ReferenceOemUpdateOrderPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, ReferenceOemUpdateOrderPayload.class);
        } catch (RuntimeException exception) {
            InboundUpdateWorkOrderResult rejected = updatePipeline.reject(
                    envelope, tenantId, null, null, null, "INVALID_PAYLOAD",
                    exception.getMessage() == null ? "invalid SAMPLE update payload" : exception.getMessage(),
                    audit);
            return toResponse(rejected, null);
        }

        UpdateWorkOrderRouteHint routeHint = new UpdateWorkOrderRouteHint(
                ReferenceOemInboundOrderService.CLIENT_CODE,
                payload.externalOrderCode(),
                UPDATE_BUSINESS_PREFIX + payload.externalOrderCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> converted = objectMapper.convertValue(payload, Map.class);
        Map<String, Object> externalSource = new LinkedHashMap<>();
        if (converted != null) {
            converted.forEach((key, value) -> {
                if (key != null && value != null) {
                    externalSource.put(key, value);
                }
            });
        }

        InboundUpdateWorkOrderResult result = updatePipeline.processUpdate(
                envelope,
                new ConnectorIdentity(
                        ReferenceOemInboundOrderService.CONNECTOR_CODE,
                        ReferenceOemInboundOrderService.ADAPTER_VERSION),
                tenantId,
                routeHint,
                Map.copyOf(externalSource),
                audit,
                safeCorrelationId,
                OBJECT_NAMESPACE);
        return toResponse(result, payload.externalOrderCode());
    }

    private ReferenceOemInboundOrderResponse replay(InboundEnvelopeView envelope) {
        if ("COMPLETED".equals(envelope.processingStatus()) && envelope.canonicalMessageId() != null) {
            var canonical = messages.findCanonical(tenantId, envelope.canonicalMessageId())
                    .orElseThrow()
                    .view();
            return ReferenceOemInboundOrderResponse.accepted(
                    payloadOrderCode(canonical.businessKey()),
                    canonical.connectorVersionId(),
                    canonical.mappingVersionId(),
                    true);
        }
        return ReferenceOemInboundOrderResponse.rejected(
                envelope.resultCode() == null ? "REJECTED" : envelope.resultCode(),
                "request was already rejected by the SAMPLE update pipeline");
    }

    private static String payloadOrderCode(String businessKey) {
        if (businessKey == null) {
            return null;
        }
        String withoutDigest = businessKey.contains(":")
                ? businessKey.substring(0, businessKey.lastIndexOf(':'))
                : businessKey;
        int last = withoutDigest.lastIndexOf(':');
        return last >= 0 ? withoutDigest.substring(last + 1) : withoutDigest;
    }

    private static ReferenceOemInboundOrderResponse toResponse(
            InboundUpdateWorkOrderResult result,
            String externalOrderCode
    ) {
        return switch (result) {
            case InboundUpdateWorkOrderResult.Accepted accepted -> ReferenceOemInboundOrderResponse.accepted(
                    externalOrderCode != null ? externalOrderCode : accepted.businessKey(),
                    accepted.connectorVersionId(),
                    accepted.mappingVersionId(),
                    accepted.replay());
            case InboundUpdateWorkOrderResult.Rejected rejected -> ReferenceOemInboundOrderResponse.rejected(
                    rejected.code(), rejected.message());
        };
    }

    private void store(String objectRef, byte[] content, String digest) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            storage.storeInternal(objectRef, input, content.length, digest, "application/json");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist REFERENCE_OEM update payload", exception);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
