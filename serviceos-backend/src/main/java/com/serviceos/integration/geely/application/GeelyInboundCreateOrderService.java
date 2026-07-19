package com.serviceos.integration.geely.application;

import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.application.InboundCreateWorkOrderPipeline;
import com.serviceos.integration.application.InboundMessageRepository;
import com.serviceos.integration.geely.api.GeelyCreateOrderPayload;
import com.serviceos.integration.geely.api.GeelyNotifyEnvelope;
import com.serviceos.integration.geely.api.GeelyNotifyResponse;
import com.serviceos.integration.geely.infrastructure.GeelyAesCipher;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.CreateWorkOrderMappedInbound;
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
 * 吉利浩瀚 7.1 安装单创建入站适配器（本地可测切片）。
 *
 * <p>使用协议文档示例 AK/IV 做 AES 解密契约测试；开放平台统一签名、Sandbox URL、
 * 生产凭据均为 {@code BLOCKED_EXTERNAL} / {@code TBD_EXTERNAL_CONTRACT}，
 * 不得声称真实吉利全链路已接入。</p>
 */
@Service
public class GeelyInboundCreateOrderService {
    public static final String CONNECTOR_CODE = "GEELY";
    public static final String ADAPTER_VERSION = "geely-haohan-v1.3-local";
    private static final String OBJECT_NAMESPACE = "geely";
    private static final String AUTH_POLICY = "GEELY_AES_CBC_V1_LOCAL";
    private static final String RECEIVE_CAPABILITY = "integration.receiveInbound";

    private final ObjectMapper objectMapper;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final InboundCreateWorkOrderPipeline createWorkOrderPipeline;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String accessKey;
    private final String aesIv;
    private final String tenantId;
    private final String projectCode;

    public GeelyInboundCreateOrderService(
            ObjectMapper objectMapper,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            InboundCreateWorkOrderPipeline createWorkOrderPipeline,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${serviceos.integration.geely.access-key:GENERAL_KEY_DEMO}") String accessKey,
            @Value("${serviceos.integration.geely.aes-iv:IV_DEMO_90123456}") String aesIv,
            @Value("${serviceos.integration.geely.tenant-id:tenant-geely-local}") String tenantId,
            @Value("${serviceos.integration.geely.project-code:GEELY-LOCAL}") String projectCode
    ) {
        this.objectMapper = objectMapper;
        this.messages = messages;
        this.storage = storage;
        this.createWorkOrderPipeline = createWorkOrderPipeline;
        this.transactions = transactions;
        this.clock = clock;
        this.accessKey = required(accessKey, "accessKey");
        this.aesIv = required(aesIv, "aesIv");
        this.tenantId = required(tenantId, "tenantId");
        this.projectCode = required(projectCode, "projectCode");
    }

    public GeelyNotifyResponse receive(byte[] rawEnvelope, String correlationId) {
        String safeCorrelationId = required(correlationId, "correlationId");
        final GeelyNotifyEnvelope envelopeDto;
        try {
            envelopeDto = objectMapper.readValue(rawEnvelope, GeelyNotifyEnvelope.class);
        } catch (RuntimeException exception) {
            return GeelyNotifyResponse.fail("invalid envelope JSON");
        }
        if (envelopeDto.data() == null || envelopeDto.data().isBlank()) {
            return GeelyNotifyResponse.fail("missing encrypted data");
        }

        final String plainJson;
        try {
            plainJson = GeelyAesCipher.decryptFromBase64(envelopeDto.data(), accessKey, aesIv);
        } catch (RuntimeException exception) {
            return GeelyNotifyResponse.fail("AES decrypt failed");
        }

        byte[] plainBytes = plainJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String rawPayloadDigest = Sha256.digest(plainBytes);
        String transportDedupKey = Sha256.digest(
                (envelopeDto.providerNo() == null ? "" : envelopeDto.providerNo())
                        + "|" + (envelopeDto.timestamp() == null ? "" : envelopeDto.timestamp())
                        + "|" + rawPayloadDigest);
        String tenantObjectPrefix = Sha256.digest(tenantId).substring(0, 16);
        String rawObjectRef = "integration/inbound/" + tenantObjectPrefix
                + "/" + OBJECT_NAMESPACE + "/raw/" + rawPayloadDigest + ".json";
        Instant receivedAt = clock.instant();
        InboundConnectorAuditContext audit = new InboundConnectorAuditContext(
                "connector:geely:" + accessKey.substring(0, Math.min(8, accessKey.length())),
                AUTH_POLICY,
                RECEIVE_CAPABILITY,
                rawPayloadDigest);

        InboundEnvelopeView envelope = Objects.requireNonNull(transactions.execute(status -> {
            var registered = messages.registerEnvelope(new InboundMessageRepository.NewInboundEnvelope(
                    UUID.randomUUID(), tenantId, ADAPTER_VERSION,
                    CreateWorkOrderMappedInbound.MESSAGE_TYPE_CREATE_WORK_ORDER,
                    transportDedupKey,
                    envelopeDto.timestamp() == null ? rawPayloadDigest : envelopeDto.timestamp(),
                    receivedAt, rawObjectRef, rawPayloadDigest, safeCorrelationId));
            if (!registered.envelope().view().rawPayloadDigest().equals(rawPayloadDigest)) {
                throw new IllegalStateException("GEELY transport replay conflict");
            }
            return registered.envelope().view();
        }));

        if (!"RECEIVED".equals(envelope.processingStatus())) {
            return replay(envelope);
        }

        store(rawObjectRef, plainBytes, rawPayloadDigest);

        final GeelyCreateOrderPayload payload;
        try {
            payload = objectMapper.readValue(plainBytes, GeelyCreateOrderPayload.class);
        } catch (RuntimeException exception) {
            createWorkOrderPipeline.reject(
                    envelope, tenantId, null, null, null, "INVALID_PAYLOAD",
                    exception.getMessage() == null ? "invalid Geely payload" : exception.getMessage(),
                    audit);
            return GeelyNotifyResponse.fail("invalid create-order payload");
        }

        final CreateWorkOrderMappedInbound inbound;
        try {
            inbound = GeelyCreateOrderMapper.map(payload, objectMapper);
        } catch (RuntimeException exception) {
            createWorkOrderPipeline.reject(
                    envelope, tenantId, null, null, null, "MAPPING_FAILED",
                    exception.getMessage() == null ? "mapping failed" : exception.getMessage(),
                    audit);
            return GeelyNotifyResponse.fail("mapping failed");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> converted = objectMapper.convertValue(payload, Map.class);
        // Jackson 可能产出 null 值；Map.copyOf 拒绝 null，需过滤后再冻结。
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
                inbound,
                audit,
                safeCorrelationId,
                OBJECT_NAMESPACE,
                Map.copyOf(externalSource));
        return switch (result) {
            case InboundCreateWorkOrderResult.Accepted ignored -> GeelyNotifyResponse.ok();
            case InboundCreateWorkOrderResult.Rejected rejected ->
                    GeelyNotifyResponse.fail(rejected.code() + ": " + rejected.message());
        };
    }

    private GeelyNotifyResponse replay(InboundEnvelopeView envelope) {
        if ("COMPLETED".equals(envelope.processingStatus())) {
            return GeelyNotifyResponse.ok();
        }
        return GeelyNotifyResponse.fail(
                envelope.resultCode() == null ? "REJECTED" : envelope.resultCode());
    }

    private void store(String objectRef, byte[] content, String digest) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            storage.storeInternal(objectRef, input, content.length, digest, "application/json");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist GEELY inbound payload", exception);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
