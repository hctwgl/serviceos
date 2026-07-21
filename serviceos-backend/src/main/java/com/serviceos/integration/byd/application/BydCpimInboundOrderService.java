package com.serviceos.integration.byd.application;

import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.application.InboundCreateWorkOrderPipeline;
import com.serviceos.integration.application.InboundMessageRepository;
import com.serviceos.integration.byd.api.BydCpimInboundOrderResponse;
import com.serviceos.integration.byd.api.BydCpimInstallOrderPayload;
import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;
import com.serviceos.integration.byd.infrastructure.BydCpimPayloadDigest;
import com.serviceos.integration.byd.infrastructure.BydCpimReplayConflictException;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
import com.serviceos.integration.byd.infrastructure.JooqBydCpimReplayGuard;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.CreateWorkOrderMappedInbound;
import com.serviceos.integration.spi.CreateWorkOrderRouteHint;
import com.serviceos.integration.spi.InboundConnectorAuditContext;
import com.serviceos.integration.spi.InboundCreateWorkOrderResult;
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
 * BYD CPIM 安装订单入站适配器。
 *
 * <p>M267/M336：协议验签、Nonce 防重放与路由提示留在本适配器；领域字段由冻结 Mapping
 * 物化。Envelope 登记后的 Bundle 解析、Canonical、建单、Outbox/审计委托
 * {@link InboundCreateWorkOrderPipeline}。适配器不直接写工单表。</p>
 *
 * <p>事务仍保持 M56 两段式：Envelope/Nonce 先独立提交；Canonical 与领域命令在管道事务中提交。</p>
 */
@Service
public class BydCpimInboundOrderService {
    private static final ConnectorIdentity CONNECTOR = new ConnectorIdentity(
            "BYD_CPIM", BydCpimOrderMapper.ADAPTER_VERSION);
    private static final String MESSAGE_TYPE = CreateWorkOrderMappedInbound.MESSAGE_TYPE_CREATE_WORK_ORDER;
    /** 与出站提审、适配契约一致：Canonical business_key = BYD:INSTALL:{orderCode}。 */
    private static final String INSTALL_BUSINESS_PREFIX = BydCpimOrderMapper.BUSINESS_PREFIX;
    private static final String RECEIVE_CAPABILITY = "integration.receiveInbound";
    private static final String AUTH_POLICY = "BYD_CPIM_SIGNATURE_V7_3_1";
    private static final String OBJECT_NAMESPACE = "byd-cpim";

    private final ObjectMapper objectMapper;
    private final JooqBydCpimReplayGuard replayGuard;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final BydCpimOrderMapper mapper;
    private final BydCpimSignatureVerifier signatureVerifier;
    private final InboundCreateWorkOrderPipeline createWorkOrderPipeline;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String tenantId;
    private final String projectCode;

    public BydCpimInboundOrderService(
            ObjectMapper objectMapper,
            JooqBydCpimReplayGuard replayGuard,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            InboundCreateWorkOrderPipeline createWorkOrderPipeline,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${serviceos.integration.byd.cpim.app-key:local-byd-app-key}") String appKey,
            @Value("${serviceos.integration.byd.cpim.app-secret:local-byd-app-secret-change-me}") String appSecret,
            @Value("${serviceos.integration.byd.cpim.zone-id}") ZoneId protocolZone,
            @Value("${serviceos.integration.byd.cpim.tenant-id:tenant-byd-pilot}") String tenantId,
            @Value("${serviceos.integration.byd.cpim.project-code:BYD-OCEAN-SD-PILOT}") String projectCode
    ) {
        this.objectMapper = objectMapper;
        this.replayGuard = replayGuard;
        this.messages = messages;
        this.storage = storage;
        this.createWorkOrderPipeline = createWorkOrderPipeline;
        this.transactions = transactions;
        this.clock = clock;
        this.mapper = new BydCpimOrderMapper();
        this.signatureVerifier = new BydCpimSignatureVerifier(
                appKey, appSecret, clock, protocolZone);
        this.tenantId = requiredText(tenantId, "tenantId");
        this.projectCode = requiredText(projectCode, "projectCode");
    }

    public BydCpimInboundOrderResponse receive(
            BydCpimSignatureHeaders headers,
            byte[] rawPayload,
            String correlationId
    ) {
        String safeCorrelationId = requiredText(correlationId, "correlationId");
        Map<String, Object> rawParameters;
        try {
            rawParameters = objectMapper.readValue(
                    Objects.requireNonNull(rawPayload, "rawPayload must not be null"),
                    new TypeReference<>() { });
        } catch (RuntimeException exception) {
            return BydCpimInboundOrderResponse.rejected("INVALID_PAYLOAD", "request body is not valid JSON");
        }

        var verification = signatureVerifier.verify(headers, rawParameters);
        if (!verification.valid()) {
            return BydCpimInboundOrderResponse.rejected(
                    verification.reason().name(), "request signature verification failed");
        }

        final String canonicalRequestDigest;
        try {
            canonicalRequestDigest = BydCpimPayloadDigest.sha256(rawParameters);
        } catch (IllegalArgumentException exception) {
            return BydCpimInboundOrderResponse.rejected("INVALID_PAYLOAD", exception.getMessage());
        }
        String rawPayloadDigest = Sha256.digest(rawPayload);
        String transportDedupKey = Sha256.digest(
                headers.appKey() + "|" + headers.nonce() + "|" + headers.currentDate());
        String tenantObjectPrefix = Sha256.digest(tenantId).substring(0, 16);
        String rawObjectRef = "integration/inbound/" + tenantObjectPrefix
                + "/" + OBJECT_NAMESPACE + "/raw/" + rawPayloadDigest + ".json";
        Instant receivedAt = clock.instant();
        InboundConnectorAuditContext auditContext = new InboundConnectorAuditContext(
                "connector:byd-cpim:" + headers.appKey(),
                AUTH_POLICY,
                RECEIVE_CAPABILITY,
                canonicalRequestDigest);

        InboundEnvelopeView envelope;
        try {
            envelope = Objects.requireNonNull(transactions.execute(status -> {
                UUID proposedEnvelopeId = UUID.randomUUID();
                var registered = messages.registerEnvelope(new InboundMessageRepository.NewInboundEnvelope(
                        proposedEnvelopeId, tenantId, CONNECTOR.connectorVersionId(), MESSAGE_TYPE,
                        transportDedupKey, headers.nonce(), receivedAt, rawObjectRef,
                        rawPayloadDigest, safeCorrelationId));
                var replay = replayGuard.register(
                        headers.appKey(), headers.nonce(), headers.currentDate().toEpochDay(),
                        canonicalRequestDigest, registered.envelope().view().inboundEnvelopeId());
                if (replay.inboundEnvelopeId() == null
                        || !replay.inboundEnvelopeId().equals(
                        registered.envelope().view().inboundEnvelopeId())) {
                    throw new BydCpimReplayConflictException();
                }
                if (!registered.envelope().view().rawPayloadDigest().equals(rawPayloadDigest)) {
                    throw new BydCpimReplayConflictException();
                }
                return registered.envelope().view();
            }));
        } catch (BydCpimReplayConflictException exception) {
            return BydCpimInboundOrderResponse.rejected(
                    "REPLAY_CONFLICT", "nonce was already used with a different payload");
        }

        if (!"RECEIVED".equals(envelope.processingStatus())) {
            // 首次请求已写入 result_digest；安全重放只回放业务结果，不再次 complete。
            return replayResponse(envelope);
        }

        store(rawObjectRef, rawPayload, rawPayloadDigest);

        final CreateWorkOrderRouteHint routeHint;
        try {
            BydCpimInstallOrderPayload payload = objectMapper.convertValue(
                    rawParameters, BydCpimInstallOrderPayload.class);
            routeHint = mapper.toRouteHint(payload);
        } catch (RuntimeException exception) {
            if (!isPayloadMappingFailure(exception)) {
                throw exception;
            }
            InboundCreateWorkOrderResult rejected = createWorkOrderPipeline.reject(
                    envelope, tenantId, null, null, null, "INVALID_ORDER",
                    safeMessage(exception), auditContext);
            return finish(toResponse(rejected, null), headers);
        }

        InboundCreateWorkOrderResult result = createWorkOrderPipeline.processMappedCreateWorkOrder(
                envelope, CONNECTOR, tenantId, projectCode, routeHint, auditContext,
                safeCorrelationId, OBJECT_NAMESPACE, Map.copyOf(rawParameters));
        return finish(toResponse(result, routeHint.externalOrderCode()), headers);
    }

    private BydCpimInboundOrderResponse finish(
            BydCpimInboundOrderResponse response,
            BydCpimSignatureHeaders headers
    ) {
        replayGuard.complete(
                headers.appKey(), headers.nonce(), headers.currentDate().toEpochDay(),
                responseDigest(response));
        return response;
    }

    private BydCpimInboundOrderResponse toResponse(
            InboundCreateWorkOrderResult result,
            String mappedExternalOrderCode
    ) {
        return switch (result) {
            case InboundCreateWorkOrderResult.Accepted accepted -> BydCpimInboundOrderResponse.accepted(
                    mappedExternalOrderCode != null
                            ? mappedExternalOrderCode
                            : orderCodeFromBusinessKey(accepted.businessKey()),
                    accepted.connectorVersionId(),
                    accepted.mappingVersionId(),
                    accepted.replay());
            case InboundCreateWorkOrderResult.Rejected rejected -> BydCpimInboundOrderResponse.rejected(
                    rejected.code(), rejected.message());
        };
    }

    private BydCpimInboundOrderResponse replayResponse(InboundEnvelopeView envelope) {
        if ("COMPLETED".equals(envelope.processingStatus()) && envelope.canonicalMessageId() != null) {
            CanonicalMessageView canonical = messages.findCanonical(
                            tenantId, envelope.canonicalMessageId())
                    .map(InboundMessageRepository.CanonicalMessageRecord::view)
                    .orElseThrow(() -> new IllegalStateException("Completed Envelope lost CanonicalMessage"));
            return BydCpimInboundOrderResponse.accepted(
                    orderCodeFromBusinessKey(canonical.businessKey()),
                    canonical.connectorVersionId(), canonical.mappingVersionId(), true);
        }
        return BydCpimInboundOrderResponse.rejected(
                envelope.resultCode(), "request was already rejected by the inbound pipeline");
    }

    private static String orderCodeFromBusinessKey(String businessKey) {
        String normalized = requiredText(businessKey, "businessKey");
        if (normalized.startsWith(INSTALL_BUSINESS_PREFIX)) {
            return requiredText(
                    normalized.substring(INSTALL_BUSINESS_PREFIX.length()), "externalOrderCode");
        }
        // 新系统默认使用前缀键；若历史测试夹具仍写入裸 orderCode，响应保持可解析。
        return normalized;
    }

    private void store(String objectRef, byte[] content, String digest) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            storage.storeInternal(
                    objectRef, input, content.length, digest, "application/json");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist authenticated inbound payload", exception);
        }
    }

    private static boolean isPayloadMappingFailure(RuntimeException exception) {
        return exception instanceof IllegalArgumentException
                || exception.getClass().getName().startsWith("tools.jackson.databind.");
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "request payload cannot be mapped" : message;
    }

    private static String responseDigest(BydCpimInboundOrderResponse response) {
        return BydCpimPayloadDigest.sha256(Map.of(
                "success", response.success(), "code", response.code(),
                "orderCode", response.orderCode() == null ? "" : response.orderCode(),
                "adapterVersion", response.adapterVersion() == null ? "" : response.adapterVersion(),
                "mappingVersion", response.mappingVersion() == null ? "" : response.mappingVersion()));
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
