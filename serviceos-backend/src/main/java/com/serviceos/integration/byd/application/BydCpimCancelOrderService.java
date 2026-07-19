package com.serviceos.integration.byd.application;

import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.application.InboundCancelWorkOrderPipeline;
import com.serviceos.integration.application.InboundMessageRepository;
import com.serviceos.integration.byd.api.BydCpimInboundOrderResponse;
import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;
import com.serviceos.integration.byd.infrastructure.BydCpimPayloadDigest;
import com.serviceos.integration.byd.infrastructure.BydCpimReplayConflictException;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
import com.serviceos.integration.byd.infrastructure.JdbcBydCpimReplayGuard;
import com.serviceos.integration.spi.CancelWorkOrderMappedInbound;
import com.serviceos.integration.spi.CancelWorkOrderRouteHint;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.InboundCancelWorkOrderResult;
import com.serviceos.integration.spi.InboundConnectorAuditContext;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * BYD 用户取消订单入站适配器。
 *
 * <p>M339：验签/Nonce 防重放留在本类；领域 reasonCode/approvalRef 由冻结 CANCEL Mapping 物化。
 * {@code cancelDate} 仅用于 businessKey 后缀，写入 RouteHint。</p>
 */
@Service
public class BydCpimCancelOrderService {
    private static final ConnectorIdentity CONNECTOR = new ConnectorIdentity(
            "BYD_CPIM", BydCpimOrderMapper.ADAPTER_VERSION);
    private static final String MESSAGE_TYPE = CancelWorkOrderMappedInbound.MESSAGE_TYPE_CANCEL_WORK_ORDER;
    private static final String AUTH_POLICY = "BYD_CPIM_SIGNATURE_V7_3_1";
    private static final String OBJECT_NAMESPACE = "byd-cpim/cancel";
    private static final String CANCEL_BUSINESS_PREFIX = "BYD:CANCEL:";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    private final ObjectMapper objectMapper;
    private final JdbcBydCpimReplayGuard replayGuard;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final InboundCancelWorkOrderPipeline cancelPipeline;
    private final BydCpimSignatureVerifier signatureVerifier;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String tenantId;
    private final String adapterPrincipalId;

    public BydCpimCancelOrderService(
            ObjectMapper objectMapper,
            JdbcBydCpimReplayGuard replayGuard,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            InboundCancelWorkOrderPipeline cancelPipeline,
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
        this.cancelPipeline = cancelPipeline;
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
                + "/byd-cpim/cancel/raw/" + rawDigest + ".json";
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

        InboundConnectorAuditContext auditContext = new InboundConnectorAuditContext(
                adapterPrincipalId, AUTH_POLICY, "integration.receiveInbound", requestDigest);
        final CancelWorkOrderRouteHint routeHint;
        try {
            routeHint = toRouteHint(raw);
        } catch (IllegalArgumentException exception) {
            cancelPipeline.reject(envelope, tenantId, null, null, null,
                    "INVALID_CANCEL_PAYLOAD", exception.getMessage(), auditContext);
            return BydCpimInboundOrderResponse.rejected("INVALID_CANCEL_PAYLOAD", exception.getMessage());
        }

        InboundCancelWorkOrderResult result = cancelPipeline.processCancel(
                envelope, CONNECTOR, tenantId, routeHint, Map.copyOf(raw),
                auditContext, safeCorrelationId, OBJECT_NAMESPACE);
        return switch (result) {
            case InboundCancelWorkOrderResult.Accepted accepted -> BydCpimInboundOrderResponse.accepted(
                    routeHint.externalOrderCode(), CONNECTOR.connectorVersionId(),
                    accepted.mappingVersionId(), accepted.replay());
            case InboundCancelWorkOrderResult.Rejected rejected -> BydCpimInboundOrderResponse.rejected(
                    rejected.code(), rejected.message());
        };
    }

    private static CancelWorkOrderRouteHint toRouteHint(Map<String, Object> source) {
        String orderCode = requiredText(source.get("orderCode"), "orderCode", 50);
        String cancelDate = requiredText(source.get("cancelDate"), "cancelDate", 50);
        try {
            LocalDateTime.parse(cancelDate, DATE_TIME);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("cancelDate must use yyyy-MM-dd HH:mm:ss", exception);
        }
        return new CancelWorkOrderRouteHint(
                BydCpimOrderMapper.CLIENT_CODE,
                orderCode,
                BydCpimOrderMapper.BUSINESS_PREFIX + orderCode,
                CANCEL_BUSINESS_PREFIX + orderCode,
                cancelDate);
    }

    private static String requiredText(Object raw, String field, int maximum) {
        if (!(raw instanceof String value) || value.isBlank()
                || !value.equals(value.trim()) || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
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
