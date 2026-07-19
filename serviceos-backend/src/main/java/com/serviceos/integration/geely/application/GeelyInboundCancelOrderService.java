package com.serviceos.integration.geely.application;

import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.application.InboundCancelWorkOrderPipeline;
import com.serviceos.integration.application.InboundMessageRepository;
import com.serviceos.integration.geely.api.GeelyNotifyResponse;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 吉利 7.14/7.17 关闭/取消类通知本地切片。
 *
 * <p>M339：AES 解密后仅构造 RouteHint；reasonCode 由冻结 CANCEL Mapping 物化。
 * Sandbox/开放平台签名仍 BLOCKED_EXTERNAL。</p>
 */
@Service
public class GeelyInboundCancelOrderService {
    public static final String ADAPTER_VERSION = GeelyInboundCreateOrderService.ADAPTER_VERSION;
    private static final String OBJECT_NAMESPACE = "geely-cancel";
    private static final String AUTH_POLICY = "GEELY_AES_CBC_V1_LOCAL";
    private static final String CANCEL_BUSINESS_PREFIX = "GEELY:CANCEL:";

    private final ObjectMapper objectMapper;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final InboundCancelWorkOrderPipeline cancelPipeline;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String accessKey;
    private final String aesIv;
    private final String tenantId;

    public GeelyInboundCancelOrderService(
            ObjectMapper objectMapper,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            InboundCancelWorkOrderPipeline cancelPipeline,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${serviceos.integration.geely.access-key:GENERAL_KEY_DEMO}") String accessKey,
            @Value("${serviceos.integration.geely.aes-iv:IV_DEMO_90123456}") String aesIv,
            @Value("${serviceos.integration.geely.tenant-id:tenant-geely-local}") String tenantId
    ) {
        this.objectMapper = objectMapper;
        this.messages = messages;
        this.storage = storage;
        this.cancelPipeline = cancelPipeline;
        this.transactions = transactions;
        this.clock = clock;
        this.accessKey = accessKey.trim();
        this.aesIv = aesIv.trim();
        this.tenantId = tenantId.trim();
    }

    public GeelyNotifyResponse receive(byte[] rawEnvelope, String correlationId) {
        String safeCorrelationId = Objects.requireNonNull(correlationId, "correlationId").trim();
        final GeelyAesInboundSupport.DecryptedPayload decrypted;
        try {
            decrypted = GeelyAesInboundSupport.decrypt(objectMapper, rawEnvelope, accessKey, aesIv);
        } catch (RuntimeException exception) {
            return GeelyNotifyResponse.fail("AES/envelope rejected");
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(decrypted.plainBytes(), new TypeReference<>() { });
        } catch (RuntimeException exception) {
            return GeelyNotifyResponse.fail("invalid cancel payload");
        }
        String installProcessNo = text(payload.get("installProcessNo"));
        if (installProcessNo == null) {
            return GeelyNotifyResponse.fail("installProcessNo required");
        }

        String rawPayloadDigest = Sha256.digest(decrypted.plainBytes());
        String transportDedupKey = Sha256.digest(
                "CANCEL|" + nullToEmpty(decrypted.envelope().providerNo())
                        + "|" + nullToEmpty(decrypted.envelope().timestamp())
                        + "|" + rawPayloadDigest);
        String tenantObjectPrefix = Sha256.digest(tenantId).substring(0, 16);
        String rawObjectRef = "integration/inbound/" + tenantObjectPrefix
                + "/" + OBJECT_NAMESPACE + "/raw/" + rawPayloadDigest + ".json";
        Instant receivedAt = clock.instant();
        InboundConnectorAuditContext audit = new InboundConnectorAuditContext(
                "connector:geely:" + accessKey.substring(0, Math.min(8, accessKey.length())),
                AUTH_POLICY,
                "integration.receiveInbound",
                rawPayloadDigest);

        InboundEnvelopeView envelope = Objects.requireNonNull(transactions.execute(status -> {
            var registered = messages.registerEnvelope(new InboundMessageRepository.NewInboundEnvelope(
                    UUID.randomUUID(), tenantId, ADAPTER_VERSION,
                    CancelWorkOrderMappedInbound.MESSAGE_TYPE_CANCEL_WORK_ORDER,
                    transportDedupKey,
                    decrypted.envelope().timestamp() == null
                            ? rawPayloadDigest : decrypted.envelope().timestamp(),
                    receivedAt, rawObjectRef, rawPayloadDigest, safeCorrelationId));
            return registered.envelope().view();
        }));
        if (!"RECEIVED".equals(envelope.processingStatus())) {
            return "COMPLETED".equals(envelope.processingStatus())
                    ? GeelyNotifyResponse.ok()
                    : GeelyNotifyResponse.fail(envelope.resultCode() == null
                    ? "REJECTED" : envelope.resultCode());
        }
        store(rawObjectRef, decrypted.plainBytes(), rawPayloadDigest);

        // businessKey 后缀用原文 digest 前缀，保证同报文幂等且不依赖 Mapping 输出。
        CancelWorkOrderRouteHint routeHint = new CancelWorkOrderRouteHint(
                GeelyCreateOrderMapper.CLIENT_CODE,
                installProcessNo,
                GeelyCreateOrderMapper.BUSINESS_PREFIX + installProcessNo,
                CANCEL_BUSINESS_PREFIX + installProcessNo,
                rawPayloadDigest.substring(0, 12));

        InboundCancelWorkOrderResult result = cancelPipeline.processCancel(
                envelope,
                new ConnectorIdentity(GeelyInboundCreateOrderService.CONNECTOR_CODE, ADAPTER_VERSION),
                tenantId,
                routeHint,
                Map.copyOf(payload),
                audit,
                safeCorrelationId,
                OBJECT_NAMESPACE);
        return switch (result) {
            case InboundCancelWorkOrderResult.Accepted ignored -> GeelyNotifyResponse.ok();
            case InboundCancelWorkOrderResult.Rejected rejected ->
                    GeelyNotifyResponse.fail(rejected.code() + ": " + rejected.message());
        };
    }

    private void store(String objectRef, byte[] content, String digest) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            storage.storeInternal(objectRef, input, content.length, digest, "application/json");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist GEELY cancel payload", exception);
        }
    }

    private static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
