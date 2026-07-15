package com.serviceos.integration.byd.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationResolutionException;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ResolveConfigurationBundleQuery;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.application.InboundMessageRepository;
import com.serviceos.integration.byd.api.BydCpimInboundOrderResponse;
import com.serviceos.integration.byd.api.BydCpimInstallOrderPayload;
import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;
import com.serviceos.integration.byd.infrastructure.BydCpimPayloadDigest;
import com.serviceos.integration.byd.infrastructure.BydCpimReplayConflictException;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
import com.serviceos.integration.byd.infrastructure.JdbcBydCpimReplayGuard;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.ExternalWorkOrderConflictException;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderReceipt;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * BYD CPIM 安装订单入站安全边界。
 *
 * <p>M56 后固定顺序为：解析并验签 → 注册 Envelope/Nonce → 私有存储原文 → 反腐映射与配置解析
 * → 注册 CanonicalMessage → 领域命令。Envelope 注册先独立提交，使进程在对象写入或领域事务期间
 * 退出后，相同 transport key 能继续同一条 RECEIVED 记录；CanonicalMessage、工单结果、审计和
 * Outbox 则在第二个本地事务原子提交。</p>
 */
@Service
public class BydCpimInboundOrderService {
    private static final String CONNECTOR_VERSION = BydCpimOrderMapper.ADAPTER_VERSION;
    private static final String MESSAGE_TYPE = "CREATE_WORK_ORDER";
    private static final String RECEIVE_CAPABILITY = "integration.receiveInbound";
    private static final String AUTH_POLICY = "BYD_CPIM_SIGNATURE_V7_3_1";

    private final ObjectMapper objectMapper;
    private final JdbcBydCpimReplayGuard replayGuard;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final BydCpimOrderMapper mapper;
    private final BydCpimSignatureVerifier signatureVerifier;
    private final ConfigurationService configurationService;
    private final WorkOrderCommandService workOrderCommandService;
    private final OutboxAppender outbox;
    private final AuditAppender audit;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String tenantId;
    private final String projectCode;

    public BydCpimInboundOrderService(
            ObjectMapper objectMapper,
            JdbcBydCpimReplayGuard replayGuard,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            ConfigurationService configurationService,
            WorkOrderCommandService workOrderCommandService,
            OutboxAppender outbox,
            AuditAppender audit,
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
        this.configurationService = configurationService;
        this.workOrderCommandService = workOrderCommandService;
        this.outbox = outbox;
        this.audit = audit;
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
                + "/byd-cpim/raw/" + rawPayloadDigest + ".json";
        Instant receivedAt = clock.instant();

        InboundEnvelopeView envelope;
        try {
            envelope = Objects.requireNonNull(transactions.execute(status -> {
                UUID proposedEnvelopeId = UUID.randomUUID();
                var registered = messages.registerEnvelope(new InboundMessageRepository.NewInboundEnvelope(
                        proposedEnvelopeId, tenantId, CONNECTOR_VERSION, MESSAGE_TYPE,
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
            return replayResponse(envelope);
        }

        store(rawObjectRef, rawPayload, rawPayloadDigest);

        final BydCpimMappedOrder mapped;
        try {
            BydCpimInstallOrderPayload payload = objectMapper.convertValue(
                    rawParameters, BydCpimInstallOrderPayload.class);
            mapped = mapper.map(payload);
        } catch (RuntimeException exception) {
            if (!isPayloadMappingFailure(exception)) {
                throw exception;
            }
            return rejectEnvelope(
                    envelope, null, null, null, "INVALID_ORDER",
                    safeMessage(exception), headers, canonicalRequestDigest);
        }

        final ConfigurationBundleReference bundle;
        try {
            bundle = configurationService.resolve(new ResolveConfigurationBundleQuery(
                    tenantId, projectCode, mapped.brandCode(), mapped.serviceProductCode(),
                    mapped.provinceCode(), receivedAt));
        } catch (ConfigurationResolutionException exception) {
            String code = "CONFIGURATION_" + exception.reason().name();
            return rejectEnvelope(
                    envelope, null, null, mapped.mappingVersion(), "INVALID_ORDER",
                    code + ": " + exception.getMessage(), headers, canonicalRequestDigest);
        }

        byte[] canonicalPayload = writeCanonical(mapped);
        String canonicalPayloadDigest = Sha256.digest(canonicalPayload);
        String canonicalObjectRef = "integration/inbound/" + tenantObjectPrefix
                + "/byd-cpim/canonical/"
                + canonicalPayloadDigest + ".json";
        store(canonicalObjectRef, canonicalPayload, canonicalPayloadDigest);

        try {
            return Objects.requireNonNull(transactions.execute(status -> processCanonical(
                    envelope, mapped, bundle, canonicalObjectRef, canonicalPayloadDigest,
                    canonicalRequestDigest, safeCorrelationId, headers)));
        } catch (ExternalWorkOrderConflictException exception) {
            return rejectEnvelope(
                    envelope, canonicalPayloadDigest, bundle.projectId(), mapped.mappingVersion(),
                    "REPLAY_CONFLICT", "ORDER_CONFLICT: " + exception.getMessage(), headers,
                    canonicalRequestDigest);
        }
    }

    private BydCpimInboundOrderResponse processCanonical(
            InboundEnvelopeView envelope,
            BydCpimMappedOrder mapped,
            ConfigurationBundleReference bundle,
            String canonicalObjectRef,
            String canonicalPayloadDigest,
            String requestDigest,
            String correlationId,
            BydCpimSignatureHeaders headers
    ) {
        Instant now = clock.instant();
        var registered = messages.registerCanonical(new InboundMessageRepository.NewCanonicalMessage(
                UUID.randomUUID(), tenantId, bundle.projectId(), CONNECTOR_VERSION, MESSAGE_TYPE,
                mapped.externalOrderCode(), canonicalObjectRef, canonicalPayloadDigest,
                mapped.mappingVersion(), envelope.inboundEnvelopeId(), now));
        CanonicalMessageView canonical = registered.message().view();
        if (!canonical.payloadDigest().equals(canonicalPayloadDigest)) {
            markRejected(
                    envelope, bundle.projectId(), canonicalPayloadDigest, mapped.mappingVersion(),
                    "REPLAY_CONFLICT", requestDigest, headers, now);
            BydCpimInboundOrderResponse response = BydCpimInboundOrderResponse.rejected(
                    "REPLAY_CONFLICT",
                    "ORDER_CONFLICT: business key was already used with a different payload");
            replayGuard.complete(
                    headers.appKey(), headers.nonce(), headers.currentDate().toEpochDay(),
                    responseDigest(response));
            return response;
        }
        if (!registered.created()) {
            if (!"COMPLETED".equals(canonical.processingStatus())) {
                throw new IllegalStateException("CanonicalMessage is not recoverably completed");
            }
            messages.completeEnvelope(
                    tenantId, envelope.inboundEnvelopeId(), canonical.projectId(), canonical.payloadDigest(),
                    canonical.mappingVersionId(), canonical.canonicalMessageId(), canonical.resultCode(),
                    canonical.resultType(), canonical.resultId(), now);
            replayGuard.complete(
                    headers.appKey(), headers.nonce(), headers.currentDate().toEpochDay(),
                    responseDigest(BydCpimInboundOrderResponse.accepted(
                            canonical.businessKey(), CONNECTOR_VERSION, canonical.mappingVersionId(), true)));
            return BydCpimInboundOrderResponse.accepted(
                    canonical.businessKey(), CONNECTOR_VERSION, canonical.mappingVersionId(), true);
        }

        WorkOrderReceipt receipt = workOrderCommandService.receive(new ReceiveExternalWorkOrderCommand(
                tenantId, bundle.projectId(), mapped.clientCode(), mapped.brandCode(),
                mapped.serviceProductCode(), mapped.externalOrderCode(), requestDigest,
                bundle.bundleId(), bundle.bundleCode(), bundle.bundleVersion(), bundle.manifestDigest(),
                mapped.provinceCode(), mapped.cityCode(), mapped.districtCode(), mapped.customerName(),
                mapped.customerMobile(), mapped.serviceAddress(), mapped.vehicleVin(), mapped.dispatchedAt(),
                correlationId, "canonical-message:" + canonical.canonicalMessageId()));

        String resultId = receipt.workOrderId().toString();
        messages.completeCanonical(
                tenantId, canonical.canonicalMessageId(), "ACCEPTED", "WORK_ORDER", resultId, now);
        messages.completeEnvelope(
                tenantId, envelope.inboundEnvelopeId(), bundle.projectId(), canonicalPayloadDigest,
                mapped.mappingVersion(), canonical.canonicalMessageId(), "ACCEPTED", "WORK_ORDER",
                resultId, now);
        appendProcessedEvent(canonical, envelope, receipt, correlationId, now);
        appendAudit(
                headers, envelope.inboundEnvelopeId(), bundle.projectId(), requestDigest,
                "INBOUND_MESSAGE_PROCESSED", "ACCEPTED", null, correlationId, now);

        BydCpimInboundOrderResponse response = BydCpimInboundOrderResponse.accepted(
                mapped.externalOrderCode(), mapped.adapterVersion(), mapped.mappingVersion(), receipt.replay());
        replayGuard.complete(
                headers.appKey(), headers.nonce(), headers.currentDate().toEpochDay(),
                responseDigest(response));
        return response;
    }

    private BydCpimInboundOrderResponse rejectEnvelope(
            InboundEnvelopeView envelope,
            String canonicalDigest,
            UUID projectId,
            String mappingVersion,
            String code,
            String message,
            BydCpimSignatureHeaders headers,
            String requestDigest
    ) {
        BydCpimInboundOrderResponse response = BydCpimInboundOrderResponse.rejected(code, message);
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            markRejected(
                    envelope, projectId, canonicalDigest, mappingVersion, code,
                    requestDigest, headers, now);
            replayGuard.complete(
                    headers.appKey(), headers.nonce(), headers.currentDate().toEpochDay(),
                    responseDigest(response));
        });
        return response;
    }

    private void markRejected(
            InboundEnvelopeView envelope,
            UUID projectId,
            String canonicalDigest,
            String mappingVersion,
            String code,
            String requestDigest,
            BydCpimSignatureHeaders headers,
            Instant now
    ) {
        boolean transitioned = messages.rejectEnvelope(
                tenantId, envelope.inboundEnvelopeId(), projectId, canonicalDigest,
                mappingVersion, code, now);
        if (transitioned) {
            appendAudit(
                    headers, envelope.inboundEnvelopeId(), projectId, requestDigest,
                    "INBOUND_MESSAGE_REJECTED", "REJECTED", code, envelope.correlationId(), now);
        }
    }

    private BydCpimInboundOrderResponse replayResponse(InboundEnvelopeView envelope) {
        if ("COMPLETED".equals(envelope.processingStatus()) && envelope.canonicalMessageId() != null) {
            CanonicalMessageView canonical = messages.findCanonical(
                            tenantId, envelope.canonicalMessageId())
                    .map(InboundMessageRepository.CanonicalMessageRecord::view)
                    .orElseThrow(() -> new IllegalStateException("Completed Envelope lost CanonicalMessage"));
            return BydCpimInboundOrderResponse.accepted(
                    canonical.businessKey(), canonical.connectorVersionId(),
                    canonical.mappingVersionId(), true);
        }
        return BydCpimInboundOrderResponse.rejected(
                envelope.resultCode(), "request was already rejected by the inbound pipeline");
    }

    private void appendProcessedEvent(
            CanonicalMessageView canonical,
            InboundEnvelopeView envelope,
            WorkOrderReceipt receipt,
            String correlationId,
            Instant now
    ) {
        String payload = json(new CanonicalMessageProcessedPayload(
                canonical.canonicalMessageId(), envelope.inboundEnvelopeId(), receipt.projectId(),
                canonical.connectorVersionId(), canonical.messageType(), canonical.businessKey(),
                canonical.payloadDigest(), canonical.mappingVersionId(), "WORK_ORDER",
                receipt.workOrderId().toString(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "integration",
                "integration.canonical-message-processed", 1,
                "CanonicalMessage", canonical.canonicalMessageId().toString(), 1L,
                tenantId, correlationId, envelope.inboundEnvelopeId().toString(),
                canonical.businessKey(), payload, Sha256.digest(payload), now));
    }

    private void appendAudit(
            BydCpimSignatureHeaders headers,
            UUID envelopeId,
            UUID projectId,
            String requestDigest,
            String action,
            String result,
            String errorCode,
            String correlationId,
            Instant now
    ) {
        audit.append(new AuditEntry(
                UUID.randomUUID(), tenantId, "connector:byd-cpim:" + headers.appKey(),
                action, RECEIVE_CAPABILITY, "InboundEnvelope", envelopeId.toString(),
                "ALLOW", List.of(), AUTH_POLICY, result, errorCode, requestDigest,
                correlationId, now));
    }

    private void store(String objectRef, byte[] content, String digest) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            storage.storeInternal(
                    objectRef, input, content.length, digest, "application/json");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist authenticated inbound payload", exception);
        }
    }

    private byte[] writeCanonical(BydCpimMappedOrder mapped) {
        try {
            return objectMapper.writeValueAsBytes(mapped);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Cannot serialize canonical inbound message", exception);
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

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Cannot serialize integration event", exception);
        }
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private record CanonicalMessageProcessedPayload(
            UUID canonicalMessageId,
            UUID inboundEnvelopeId,
            UUID projectId,
            String connectorVersionId,
            String messageType,
            String businessKey,
            String payloadDigest,
            String mappingVersionId,
            String resultType,
            String resultId,
            Instant processedAt
    ) {
    }
}
