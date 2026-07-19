package com.serviceos.integration.byd.application;

import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.application.InboundMessageRepository;
import com.serviceos.integration.application.InboundUpdateWorkOrderPipeline;
import com.serviceos.integration.byd.api.BydCpimInboundOrderResponse;
import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;
import com.serviceos.integration.byd.infrastructure.BydCpimPayloadDigest;
import com.serviceos.integration.byd.infrastructure.BydCpimReplayConflictException;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
import com.serviceos.integration.byd.infrastructure.JdbcBydCpimReplayGuard;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.InboundConnectorAuditContext;
import com.serviceos.integration.spi.InboundUpdateWorkOrderResult;
import com.serviceos.integration.spi.UpdateWorkOrderMappedInbound;
import com.serviceos.shared.Sha256;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * BYD 安装订单更新入站适配器。
 *
 * <p>验签/Nonce 防重放留在本类；详情更新委托 {@link InboundUpdateWorkOrderPipeline}。</p>
 */
@Service
public class BydCpimUpdateOrderService {
    private static final ConnectorIdentity CONNECTOR = new ConnectorIdentity(
            "BYD_CPIM", BydCpimOrderMapper.ADAPTER_VERSION);
    private static final String MESSAGE_TYPE = UpdateWorkOrderMappedInbound.MESSAGE_TYPE_UPDATE_WORK_ORDER;
    private static final String AUTH_POLICY = "BYD_CPIM_SIGNATURE_V7_3_1";
    private static final String OBJECT_NAMESPACE = "byd-cpim/update";

    private final ObjectMapper objectMapper;
    private final JdbcBydCpimReplayGuard replayGuard;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final InboundUpdateWorkOrderPipeline updatePipeline;
    private final BydCpimSignatureVerifier signatureVerifier;
    private final BydCpimUpdateOrderMapper mapper = new BydCpimUpdateOrderMapper();
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String tenantId;
    private final String adapterPrincipalId;

    public BydCpimUpdateOrderService(
            ObjectMapper objectMapper,
            JdbcBydCpimReplayGuard replayGuard,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            InboundUpdateWorkOrderPipeline updatePipeline,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${serviceos.integration.byd.cpim.app-key:local-byd-app-key}") String appKey,
            @Value("${serviceos.integration.byd.cpim.app-secret:local-byd-app-secret-change-me}") String appSecret,
            @Value("${serviceos.integration.byd.cpim.zone-id}") ZoneId protocolZone,
            @Value("${serviceos.integration.byd.cpim.tenant-id:tenant-byd-pilot}") String tenantId,
            @Value("${serviceos.integration.byd.cpim.adapter-principal-id:principal-byd-adapter}")
            String adapterPrincipalId
    ) {
        this.objectMapper = objectMapper;
        this.replayGuard = replayGuard;
        this.messages = messages;
        this.storage = storage;
        this.updatePipeline = updatePipeline;
        this.transactions = transactions;
        this.clock = clock;
        this.signatureVerifier = new BydCpimSignatureVerifier(appKey, appSecret, clock, protocolZone);
        this.tenantId = required(tenantId, "tenantId");
        this.adapterPrincipalId = required(adapterPrincipalId, "adapterPrincipalId");
    }

    public BydCpimInboundOrderResponse receive(
            BydCpimSignatureHeaders headers,
            byte[] rawPayload,
            String correlationId
    ) {
        String safeCorrelationId = required(correlationId, "correlationId");
        Map<String, Object> raw;
        try {
            raw = objectMapper.readValue(Objects.requireNonNull(rawPayload, "rawPayload"), new TypeReference<>() { });
        } catch (RuntimeException exception) {
            return BydCpimInboundOrderResponse.rejected("INVALID_PAYLOAD", "payload is not valid JSON object");
        }
        var verification = signatureVerifier.verify(headers, raw);
        if (!verification.valid()) {
            return BydCpimInboundOrderResponse.rejected(verification.reason().name(), "signature rejected");
        }

        final String requestDigest;
        try {
            requestDigest = BydCpimPayloadDigest.sha256(raw);
        } catch (IllegalArgumentException exception) {
            return BydCpimInboundOrderResponse.rejected("INVALID_PAYLOAD", exception.getMessage());
        }
        String rawDigest = Sha256.digest(rawPayload);
        String transportKey = Sha256.digest(headers.appKey() + "|" + headers.nonce() + "|" + headers.currentDate());
        String tenantPrefix = Sha256.digest(tenantId).substring(0, 16);
        String rawObjectRef = "integration/inbound/" + tenantPrefix
                + "/byd-cpim/update/raw/" + rawDigest + ".json";
        Instant receivedAt = clock.instant();

        InboundEnvelopeView envelope;
        try {
            envelope = Objects.requireNonNull(transactions.execute(status -> {
                var registration = messages.registerEnvelope(new InboundMessageRepository.NewInboundEnvelope(
                        UUID.randomUUID(), tenantId, CONNECTOR.connectorVersionId(), MESSAGE_TYPE,
                        transportKey, headers.nonce(), receivedAt, rawObjectRef, rawDigest,
                        safeCorrelationId));
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
            return BydCpimInboundOrderResponse.rejected("REPLAY_CONFLICT", "nonce replay conflict");
        }

        store(rawObjectRef, rawPayload, rawDigest);
        final UpdateWorkOrderMappedInbound mapped;
        try {
            mapped = mapper.map(raw, objectMapper);
        } catch (IllegalArgumentException exception) {
            InboundConnectorAuditContext auditContext = new InboundConnectorAuditContext(
                    adapterPrincipalId, AUTH_POLICY, "integration.receiveInbound", requestDigest);
            updatePipeline.reject(envelope, tenantId, null, null, BydCpimUpdateOrderMapper.MAPPING_VERSION,
                    "INVALID_UPDATE_PAYLOAD", exception.getMessage(), auditContext);
            return BydCpimInboundOrderResponse.rejected("INVALID_UPDATE_PAYLOAD", exception.getMessage());
        }

        InboundConnectorAuditContext auditContext = new InboundConnectorAuditContext(
                adapterPrincipalId, AUTH_POLICY, "integration.receiveInbound", requestDigest);
        InboundUpdateWorkOrderResult result = updatePipeline.processMappedUpdate(
                envelope, CONNECTOR, tenantId, mapped, auditContext, safeCorrelationId, OBJECT_NAMESPACE);
        return switch (result) {
            case InboundUpdateWorkOrderResult.Accepted accepted -> BydCpimInboundOrderResponse.accepted(
                    mapped.externalOrderCode(), CONNECTOR.connectorVersionId(),
                    accepted.mappingVersionId(), accepted.replay());
            case InboundUpdateWorkOrderResult.Rejected rejected -> BydCpimInboundOrderResponse.rejected(
                    rejected.code(), rejected.message());
        };
    }

    private void store(String objectRef, byte[] content, String digest) {
        try {
            storage.storeInternal(
                    objectRef, new ByteArrayInputStream(content), content.length, digest, "application/json");
        } catch (IOException exception) {
            throw new IllegalStateException("Private inbound object storage failed", exception);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
