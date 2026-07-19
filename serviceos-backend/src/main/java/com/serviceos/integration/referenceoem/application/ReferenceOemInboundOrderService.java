package com.serviceos.integration.referenceoem.application;

import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.application.InboundCreateWorkOrderPipeline;
import com.serviceos.integration.application.InboundMessageRepository;
import com.serviceos.integration.referenceoem.api.ReferenceOemInboundOrderResponse;
import com.serviceos.integration.referenceoem.api.ReferenceOemInstallOrderPayload;
import com.serviceos.integration.referenceoem.infrastructure.ReferenceOemSampleSignature;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.CreateWorkOrderMappedInbound;
import com.serviceos.integration.spi.CreateWorkOrderRouteHint;
import com.serviceos.integration.spi.InboundConnectorAuditContext;
import com.serviceos.integration.spi.InboundCreateWorkOrderResult;
import com.serviceos.shared.Sha256;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * REFERENCE / SAMPLE 第二家车企入站适配器。
 *
 * <p>明确标记：REFERENCE、SAMPLE、TBD_EXTERNAL_CONTRACT。不得声称真实车企已接入。
 * 验签仅为演示 HMAC；协议字段、错误码与 Sandbox 仍待外部资料。</p>
 */
@Service
public class ReferenceOemInboundOrderService {
    public static final String CONNECTOR_CODE = "REFERENCE_OEM";
    public static final String ADAPTER_VERSION = "reference-oem-sample-v1";
    public static final String CLIENT_CODE = "REFERENCE_OEM";
    private static final String BUSINESS_PREFIX = "REFERENCE_OEM:INSTALL:";
    private static final String OBJECT_NAMESPACE = "reference-oem";
    private static final String AUTH_POLICY = "REFERENCE_OEM_SAMPLE_HMAC_V1";
    private static final String RECEIVE_CAPABILITY = "integration.receiveInbound";

    private final ObjectMapper objectMapper;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final InboundCreateWorkOrderPipeline createWorkOrderPipeline;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String appKey;
    private final String appSecret;
    private final String tenantId;
    private final String projectCode;

    public ReferenceOemInboundOrderService(
            ObjectMapper objectMapper,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            InboundCreateWorkOrderPipeline createWorkOrderPipeline,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${serviceos.integration.reference-oem.app-key:local-reference-oem-key}") String appKey,
            @Value("${serviceos.integration.reference-oem.app-secret:local-reference-oem-secret-change-me}") String appSecret,
            @Value("${serviceos.integration.reference-oem.tenant-id:tenant-reference-oem-pilot}") String tenantId,
            @Value("${serviceos.integration.reference-oem.project-code:REFERENCE-OEM-PILOT}") String projectCode
    ) {
        this.objectMapper = objectMapper;
        this.messages = messages;
        this.storage = storage;
        this.createWorkOrderPipeline = createWorkOrderPipeline;
        this.transactions = transactions;
        this.clock = clock;
        this.appKey = required(appKey, "appKey");
        this.appSecret = required(appSecret, "appSecret");
        this.tenantId = required(tenantId, "tenantId");
        this.projectCode = required(projectCode, "projectCode");
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
        String transportDedupKey = Sha256.digest(providedAppKey + "|" + nonce);
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
                    UUID.randomUUID(), tenantId, ADAPTER_VERSION,
                    CreateWorkOrderMappedInbound.MESSAGE_TYPE_CREATE_WORK_ORDER,
                    transportDedupKey, nonce, receivedAt, rawObjectRef,
                    rawPayloadDigest, safeCorrelationId));
            if (!registered.envelope().view().rawPayloadDigest().equals(rawPayloadDigest)) {
                throw new IllegalStateException("REFERENCE_OEM transport replay conflict");
            }
            return registered.envelope().view();
        }));

        if (!"RECEIVED".equals(envelope.processingStatus())) {
            return replay(envelope);
        }

        store(rawObjectRef, rawPayload, rawPayloadDigest);

        final ReferenceOemInstallOrderPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, ReferenceOemInstallOrderPayload.class);
        } catch (RuntimeException exception) {
            InboundCreateWorkOrderResult rejected = createWorkOrderPipeline.reject(
                    envelope, tenantId, null, null, null, "INVALID_PAYLOAD",
                    exception.getMessage() == null ? "invalid SAMPLE payload" : exception.getMessage(),
                    audit);
            return toResponse(rejected, null);
        }

        // M336：仅提交路由提示；领域字段由冻结 INBOUND Mapping 物化。
        CreateWorkOrderRouteHint routeHint = new CreateWorkOrderRouteHint(
                BUSINESS_PREFIX + payload.externalOrderCode(),
                payload.externalOrderCode(),
                CLIENT_CODE,
                payload.brandCode(),
                payload.serviceProductCode(),
                payload.provinceCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> converted = objectMapper.convertValue(payload, Map.class);
        Map<String, Object> externalSource = new java.util.LinkedHashMap<>();
        if (converted != null) {
            converted.forEach((key, value) -> {
                if (key != null && value != null) {
                    externalSource.put(key, value);
                }
            });
        }
        InboundCreateWorkOrderResult result = createWorkOrderPipeline.processMappedCreateWorkOrder(
                envelope,
                new ConnectorIdentity(CONNECTOR_CODE, ADAPTER_VERSION),
                tenantId,
                projectCode,
                routeHint,
                audit,
                safeCorrelationId,
                OBJECT_NAMESPACE,
                Map.copyOf(externalSource));
        return toResponse(result, payload.externalOrderCode());
    }

    private ReferenceOemInboundOrderResponse replay(InboundEnvelopeView envelope) {
        if ("COMPLETED".equals(envelope.processingStatus()) && envelope.canonicalMessageId() != null) {
            var canonical = messages.findCanonical(tenantId, envelope.canonicalMessageId())
                    .orElseThrow()
                    .view();
            String orderCode = canonical.businessKey().startsWith(BUSINESS_PREFIX)
                    ? canonical.businessKey().substring(BUSINESS_PREFIX.length())
                    : canonical.businessKey();
            return ReferenceOemInboundOrderResponse.accepted(
                    orderCode, canonical.connectorVersionId(), canonical.mappingVersionId(), true);
        }
        return ReferenceOemInboundOrderResponse.rejected(
                envelope.resultCode() == null ? "REJECTED" : envelope.resultCode(),
                "request was already rejected by the SAMPLE inbound pipeline");
    }

    private static ReferenceOemInboundOrderResponse toResponse(
            InboundCreateWorkOrderResult result,
            String externalOrderCode
    ) {
        return switch (result) {
            case InboundCreateWorkOrderResult.Accepted accepted -> ReferenceOemInboundOrderResponse.accepted(
                    externalOrderCode != null ? externalOrderCode : accepted.businessKey(),
                    accepted.connectorVersionId(),
                    accepted.mappingVersionId(),
                    accepted.replay());
            case InboundCreateWorkOrderResult.Rejected rejected -> ReferenceOemInboundOrderResponse.rejected(
                    rejected.code(), rejected.message());
        };
    }

    private void store(String objectRef, byte[] content, String digest) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            storage.storeInternal(objectRef, input, content.length, digest, "application/json");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist REFERENCE_OEM inbound payload", exception);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
