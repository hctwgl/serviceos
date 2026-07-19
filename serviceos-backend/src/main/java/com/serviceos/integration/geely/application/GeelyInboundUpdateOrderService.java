package com.serviceos.integration.geely.application;

import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.application.InboundMessageRepository;
import com.serviceos.integration.application.InboundUpdateWorkOrderPipeline;
import com.serviceos.integration.geely.api.GeelyNotifyResponse;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 吉利 7.18 用户信息更新通知本地切片：AES 解密后委托通用更新管道。
 */
@Service
public class GeelyInboundUpdateOrderService {
    public static final String ADAPTER_VERSION = GeelyInboundCreateOrderService.ADAPTER_VERSION;
    public static final String MAPPING_VERSION = "geely-haohan-v1.3-update-order-v1";
    private static final String OBJECT_NAMESPACE = "geely-update";
    private static final String AUTH_POLICY = "GEELY_AES_CBC_V1_LOCAL";

    private final ObjectMapper objectMapper;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final InboundUpdateWorkOrderPipeline updatePipeline;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String accessKey;
    private final String aesIv;
    private final String tenantId;

    public GeelyInboundUpdateOrderService(
            ObjectMapper objectMapper,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            InboundUpdateWorkOrderPipeline updatePipeline,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${serviceos.integration.geely.access-key:GENERAL_KEY_DEMO}") String accessKey,
            @Value("${serviceos.integration.geely.aes-iv:IV_DEMO_90123456}") String aesIv,
            @Value("${serviceos.integration.geely.tenant-id:tenant-geely-local}") String tenantId
    ) {
        this.objectMapper = objectMapper;
        this.messages = messages;
        this.storage = storage;
        this.updatePipeline = updatePipeline;
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
            return GeelyNotifyResponse.fail("invalid update payload");
        }

        String installProcessNo = text(payload.get("installProcessNo"));
        String contactName = text(payload.get("contactName"));
        String contactPhone = text(payload.get("contactPhone"));
        String address = text(payload.get("address"));
        String province = text(payload.get("province"));
        String city = text(payload.get("city"));
        String district = text(payload.get("district"));
        if (installProcessNo == null || contactName == null || contactPhone == null
                || address == null || province == null || city == null || district == null) {
            return GeelyNotifyResponse.fail("required update fields missing");
        }

        String updateDigest = Sha256.digest(installProcessNo + "|" + contactName + "|"
                + contactPhone + "|" + address + "|" + province + "|" + city + "|" + district);
        String rawPayloadDigest = Sha256.digest(decrypted.plainBytes());
        String transportDedupKey = Sha256.digest(
                "UPDATE|" + nullToEmpty(decrypted.envelope().providerNo())
                        + "|" + nullToEmpty(decrypted.envelope().timestamp())
                        + "|" + updateDigest);
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
                    UpdateWorkOrderMappedInbound.MESSAGE_TYPE_UPDATE_WORK_ORDER,
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

        UpdateWorkOrderMappedInbound inbound = new UpdateWorkOrderMappedInbound(
                "GEELY:UPDATE:" + installProcessNo + ":" + updateDigest.substring(0, 12),
                installProcessNo,
                GeelyCreateOrderMapper.CLIENT_CODE,
                contactName,
                contactPhone,
                address,
                province,
                city,
                district,
                updateDigest,
                MAPPING_VERSION,
                decrypted.plainBytes());

        InboundUpdateWorkOrderResult result = updatePipeline.processMappedUpdate(
                envelope,
                new ConnectorIdentity(GeelyInboundCreateOrderService.CONNECTOR_CODE, ADAPTER_VERSION),
                tenantId,
                inbound,
                audit,
                safeCorrelationId,
                OBJECT_NAMESPACE);
        return switch (result) {
            case InboundUpdateWorkOrderResult.Accepted ignored -> GeelyNotifyResponse.ok();
            case InboundUpdateWorkOrderResult.Rejected rejected ->
                    GeelyNotifyResponse.fail(rejected.code() + ": " + rejected.message());
        };
    }

    private void store(String objectRef, byte[] content, String digest) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            storage.storeInternal(objectRef, input, content.length, digest, "application/json");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist GEELY update payload", exception);
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
