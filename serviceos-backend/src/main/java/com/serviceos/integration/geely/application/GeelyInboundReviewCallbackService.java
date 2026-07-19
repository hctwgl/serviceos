package com.serviceos.integration.geely.application;

import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.application.InboundMessageRepository;
import com.serviceos.integration.application.InboundReviewCallbackItemPipeline;
import com.serviceos.integration.geely.api.GeelyNotifyResponse;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.InboundConnectorAuditContext;
import com.serviceos.integration.spi.ReviewCallbackMappedItem;
import com.serviceos.shared.BusinessProblem;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 吉利 7.13 核销审核结果通知本地 stub：AES 解密后委托
 * {@link InboundReviewCallbackItemPipeline}。
 */
@Service
public class GeelyInboundReviewCallbackService {
    public static final String MAPPING_VERSION = "geely-haohan-v1.3-settlement-audit-callback-v1";
    private static final String OBJECT_NAMESPACE = "geely-review-callback";
    private static final String AUTH_POLICY = "GEELY_AES_CBC_V1_LOCAL";
    private static final ConnectorIdentity CONNECTOR = new ConnectorIdentity(
            GeelyInboundCreateOrderService.CONNECTOR_CODE,
            GeelyInboundCreateOrderService.ADAPTER_VERSION);

    private final ObjectMapper objectMapper;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final InboundReviewCallbackItemPipeline callbackPipeline;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String accessKey;
    private final String aesIv;
    private final String tenantId;

    public GeelyInboundReviewCallbackService(
            ObjectMapper objectMapper,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            InboundReviewCallbackItemPipeline callbackPipeline,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${serviceos.integration.geely.access-key:GENERAL_KEY_DEMO}") String accessKey,
            @Value("${serviceos.integration.geely.aes-iv:IV_DEMO_90123456}") String aesIv,
            @Value("${serviceos.integration.geely.tenant-id:tenant-geely-local}") String tenantId
    ) {
        this.objectMapper = objectMapper;
        this.messages = messages;
        this.storage = storage;
        this.callbackPipeline = callbackPipeline;
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
            return GeelyNotifyResponse.fail("invalid callback payload");
        }
        String installProcessNo = text(payload.get("installProcessNo"));
        String auditResult = text(payload.get("auditResult"));
        if (installProcessNo == null || auditResult == null) {
            return GeelyNotifyResponse.fail("installProcessNo/auditResult required");
        }
        String domainResult = switch (auditResult.toUpperCase()) {
            case "PASS", "APPROVED", "通过" -> "APPROVED";
            case "REJECT", "REJECTED", "驳回", "拒绝" -> "REJECTED";
            default -> null;
        };
        if (domainResult == null) {
            return GeelyNotifyResponse.fail("unknown auditResult");
        }

        String rawPayloadDigest = Sha256.digest(decrypted.plainBytes());
        String transportDedupKey = Sha256.digest(
                "CB|" + nullToEmpty(decrypted.envelope().providerNo())
                        + "|" + nullToEmpty(decrypted.envelope().timestamp())
                        + "|" + rawPayloadDigest);
        String tenantObjectPrefix = Sha256.digest(tenantId).substring(0, 16);
        String rawObjectRef = "integration/inbound/" + tenantObjectPrefix
                + "/" + OBJECT_NAMESPACE + "/raw/" + rawPayloadDigest + ".json";
        Instant receivedAt = clock.instant();
        InboundConnectorAuditContext audit = new InboundConnectorAuditContext(
                "connector:geely:" + accessKey.substring(0, Math.min(8, accessKey.length())),
                AUTH_POLICY,
                "integration.inbound.reviewCallback",
                rawPayloadDigest);

        InboundEnvelopeView envelope = Objects.requireNonNull(transactions.execute(status -> {
            var registered = messages.registerEnvelope(new InboundMessageRepository.NewInboundEnvelope(
                    UUID.randomUUID(), tenantId, CONNECTOR.connectorVersionId(),
                    ReviewCallbackMappedItem.MESSAGE_TYPE_RECORD_CLIENT_REVIEW_RESULT,
                    transportDedupKey,
                    decrypted.envelope().timestamp() == null
                            ? rawPayloadDigest : decrypted.envelope().timestamp(),
                    receivedAt, rawObjectRef, rawPayloadDigest, safeCorrelationId));
            return registered.envelope().view();
        }));
        if (!"RECEIVED".equals(envelope.processingStatus())) {
            return GeelyNotifyResponse.ok();
        }
        store(rawObjectRef, decrypted.plainBytes(), rawPayloadDigest);

        List<String> reasonCodes = "REJECTED".equals(domainResult)
                ? List.of("GEELY.SETTLEMENT.REJECTED") : List.of();
        ReviewCallbackMappedItem item = new ReviewCallbackMappedItem(
                installProcessNo,
                "GEELY:SETTLEMENT_CB:" + installProcessNo + ":" + rawPayloadDigest.substring(0, 12),
                installProcessNo,
                domainResult,
                reasonCodes,
                MAPPING_VERSION,
                decrypted.plainBytes());
        try {
            callbackPipeline.processMappedItem(
                    envelope, CONNECTOR, tenantId, item, audit, safeCorrelationId, OBJECT_NAMESPACE);
        } catch (BusinessProblem | IllegalArgumentException | IllegalStateException exception) {
            return GeelyNotifyResponse.fail(exception.getMessage() == null
                    ? "callback processing failed" : exception.getMessage());
        }
        transactions.executeWithoutResult(status -> messages.completeBatchEnvelope(
                tenantId, envelope.inboundEnvelopeId(), null, rawPayloadDigest,
                MAPPING_VERSION, "SUCCESS", Sha256.digest("GEELY_CB_OK"), clock.instant()));
        return GeelyNotifyResponse.ok();
    }

    private void store(String objectRef, byte[] content, String digest) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            storage.storeInternal(objectRef, input, content.length, digest, "application/json");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist GEELY callback payload", exception);
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
