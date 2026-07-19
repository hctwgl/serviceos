package com.serviceos.integration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationResolutionException;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.IntegrationMappingResult;
import com.serviceos.configuration.api.IntegrationMappingRuntime;
import com.serviceos.configuration.api.ResolveConfigurationBundleQuery;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.CreateWorkOrderMappedInbound;
import com.serviceos.integration.spi.InboundConnectorAuditContext;
import com.serviceos.integration.spi.InboundCreateWorkOrderResult;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.ExternalWorkOrderConflictException;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderReceipt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * CREATE_WORK_ORDER 通用入站管道。
 *
 * <p>适配器完成协议验签、transport Envelope 登记与反腐映射后进入本管道。管道负责：
 * Bundle 唯一解析 →（可选）冻结 INTEGRATION Mapping 校验 → Canonical 私有存储 →
 * CanonicalMessage 登记 → 领域建单命令 → Envelope/Canonical 完成 → Outbox/审计。
 * 适配器不得直接写工单表。</p>
 *
 * <p>事务：调用方应已独立提交 RECEIVED Envelope；本管道在单一本地事务中提交
 * Canonical、工单、审计与 Outbox。配置失败或业务键冲突会 reject Envelope。</p>
 */
@Service
public class InboundCreateWorkOrderPipeline {
    private final ConfigurationService configurationService;
    private final IntegrationMappingRuntime integrationMappingRuntime;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final WorkOrderCommandService workOrderCommandService;
    private final OutboxAppender outbox;
    private final AuditAppender audit;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InboundCreateWorkOrderPipeline(
            ConfigurationService configurationService,
            IntegrationMappingRuntime integrationMappingRuntime,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            WorkOrderCommandService workOrderCommandService,
            OutboxAppender outbox,
            AuditAppender audit,
            TransactionTemplate transactions,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.configurationService = configurationService;
        this.integrationMappingRuntime = integrationMappingRuntime;
        this.messages = messages;
        this.storage = storage;
        this.workOrderCommandService = workOrderCommandService;
        this.outbox = outbox;
        this.audit = audit;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 处理已映射的建单意图（无外部原文时跳过 INTEGRATION Mapping 闸门）。
     */
    public InboundCreateWorkOrderResult processMappedCreateWorkOrder(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            String projectCode,
            CreateWorkOrderMappedInbound mapped,
            InboundConnectorAuditContext auditContext,
            String correlationId,
            String objectNamespace
    ) {
        return processMappedCreateWorkOrder(
                envelope, connector, tenantId, projectCode, mapped, auditContext,
                correlationId, objectNamespace, null);
    }

    /**
     * 处理已映射的建单意图。
     *
     * @param objectNamespace 对象键命名空间，例如 {@code byd-cpim}，不得包含租户明文
     * @param externalSourcePayload OEM 原文 Map；当冻结 Bundle 含该 connector 的 INBOUND
     *        INTEGRATION Mapping 时必须提供，用于冻结 Mapping 校验
     */
    public InboundCreateWorkOrderResult processMappedCreateWorkOrder(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            String projectCode,
            CreateWorkOrderMappedInbound mapped,
            InboundConnectorAuditContext auditContext,
            String correlationId,
            String objectNamespace,
            Map<String, Object> externalSourcePayload
    ) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(connector, "connector must not be null");
        Objects.requireNonNull(mapped, "mapped must not be null");
        Objects.requireNonNull(auditContext, "auditContext must not be null");
        String safeTenant = requiredText(tenantId, "tenantId");
        String safeProjectCode = requiredText(projectCode, "projectCode");
        String safeCorrelationId = requiredText(correlationId, "correlationId");
        String safeNamespace = requiredText(objectNamespace, "objectNamespace");

        if (!"RECEIVED".equals(envelope.processingStatus())) {
            return replayFromEnvelope(safeTenant, envelope);
        }

        final ConfigurationBundleReference bundle;
        try {
            bundle = configurationService.resolve(new ResolveConfigurationBundleQuery(
                    safeTenant, safeProjectCode, mapped.brandCode(), mapped.serviceProductCode(),
                    mapped.provinceCode(), envelope.receivedAt(), false,
                    mapped.externalOrderCode()));
        } catch (ConfigurationResolutionException exception) {
            String code = "CONFIGURATION_" + exception.reason().name();
            return reject(
                    envelope, safeTenant, null, null, mapped.mappingVersionId(),
                    "INVALID_ORDER", code + ": " + exception.getMessage(), auditContext);
        }

        final Optional<IntegrationMappingResult> mappingResult;
        try {
            mappingResult = applyFrozenIntegrationMapping(
                    safeTenant, connector, bundle, externalSourcePayload);
        } catch (BusinessProblem exception) {
            return reject(
                    envelope, safeTenant, bundle.projectId(), null, mapped.mappingVersionId(),
                    "INTEGRATION_MAPPING_FAILED", exception.getMessage(), auditContext);
        }

        byte[] canonicalPayload = mapped.canonicalPayload();
        String canonicalPayloadDigest = Sha256.digest(canonicalPayload);
        String tenantObjectPrefix = Sha256.digest(safeTenant).substring(0, 16);
        String canonicalObjectRef = "integration/inbound/" + tenantObjectPrefix
                + "/" + safeNamespace + "/canonical/" + canonicalPayloadDigest + ".json";
        store(canonicalObjectRef, canonicalPayload, canonicalPayloadDigest);

        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                InboundCreateWorkOrderResult result = completeCreateWorkOrder(
                        envelope, connector, safeTenant, mapped, bundle, canonicalObjectRef,
                        canonicalPayloadDigest, auditContext, safeCorrelationId);
                mappingResult.ifPresent(applied -> appendMappingAudit(
                        safeTenant, auditContext, envelope.inboundEnvelopeId(), bundle.projectId(),
                        applied, safeCorrelationId, clock.instant()));
                return result;
            }));
        } catch (ExternalWorkOrderConflictException exception) {
            return reject(
                    envelope, safeTenant, bundle.projectId(), canonicalPayloadDigest,
                    mapped.mappingVersionId(), "REPLAY_CONFLICT",
                    "ORDER_CONFLICT: " + exception.getMessage(), auditContext);
        }
    }

    private Optional<IntegrationMappingResult> applyFrozenIntegrationMapping(
            String tenantId,
            ConnectorIdentity connector,
            ConfigurationBundleReference bundle,
            Map<String, Object> externalSourcePayload
    ) {
        boolean mappingConfigured = integrationMappingRuntime.hasInboundMappingForConnector(
                tenantId, bundle.bundleId(), bundle.manifestDigest(), connector.connectorCode());
        if (!mappingConfigured) {
            return Optional.empty();
        }
        if (externalSourcePayload == null || externalSourcePayload.isEmpty()) {
            throw new BusinessProblem(
                    com.serviceos.shared.ProblemCode.VALIDATION_FAILED,
                    "Frozen INTEGRATION mapping requires external source payload");
        }
        return integrationMappingRuntime.applyInboundForConnectorIfPresent(
                tenantId, bundle.bundleId(), bundle.manifestDigest(),
                connector.connectorCode(), externalSourcePayload);
    }

    private void appendMappingAudit(
            String tenantId,
            InboundConnectorAuditContext auditContext,
            UUID envelopeId,
            UUID projectId,
            IntegrationMappingResult mapping,
            String correlationId,
            Instant now
    ) {
        String explanation = String.join("; ", mapping.explanations());
        if (explanation.length() > 1800) {
            explanation = explanation.substring(0, 1800);
        }
        audit.append(new AuditEntry(
                UUID.randomUUID(), tenantId, auditContext.actorId(),
                "INBOUND_INTEGRATION_MAPPING_APPLIED", auditContext.authPolicy(),
                "InboundEnvelope", envelopeId.toString(), "ALLOW", List.of(),
                mapping.mappingKey(), "APPLIED", null,
                Sha256.digest(mapping.contentDigest() + "|" + mapping.assetVersionId()
                        + "|" + explanation),
                correlationId, now));
    }

    /**
     * 适配器在映射失败等协议后置错误时复用的 Envelope 拒绝入口。
     */
    public InboundCreateWorkOrderResult reject(
            InboundEnvelopeView envelope,
            String tenantId,
            UUID projectId,
            String canonicalDigest,
            String mappingVersion,
            String code,
            String message,
            InboundConnectorAuditContext auditContext
    ) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(auditContext, "auditContext must not be null");
        String safeTenant = requiredText(tenantId, "tenantId");
        String safeCode = requiredText(code, "code");
        String safeMessage = message == null || message.isBlank()
                ? "inbound create-work-order rejected" : message.trim();
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            boolean transitioned = messages.rejectEnvelope(
                    safeTenant, envelope.inboundEnvelopeId(), projectId, canonicalDigest,
                    mappingVersion, safeCode, now);
            if (transitioned) {
                appendAudit(
                        safeTenant, auditContext, envelope.inboundEnvelopeId(), projectId,
                        "INBOUND_MESSAGE_REJECTED", "REJECTED", safeCode,
                        envelope.correlationId(), now);
            }
        });
        return new InboundCreateWorkOrderResult.Rejected(safeCode, safeMessage);
    }

    private InboundCreateWorkOrderResult completeCreateWorkOrder(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            CreateWorkOrderMappedInbound mapped,
            ConfigurationBundleReference bundle,
            String canonicalObjectRef,
            String canonicalPayloadDigest,
            InboundConnectorAuditContext auditContext,
            String correlationId
    ) {
        Instant now = clock.instant();
        var registered = messages.registerCanonical(new InboundMessageRepository.NewCanonicalMessage(
                UUID.randomUUID(), tenantId, bundle.projectId(), connector.connectorVersionId(),
                CreateWorkOrderMappedInbound.MESSAGE_TYPE_CREATE_WORK_ORDER,
                mapped.businessKey(), canonicalObjectRef, canonicalPayloadDigest,
                mapped.mappingVersionId(), envelope.inboundEnvelopeId(), now));
        CanonicalMessageView canonical = registered.message().view();
        if (!canonical.payloadDigest().equals(canonicalPayloadDigest)) {
            messages.rejectEnvelope(
                    tenantId, envelope.inboundEnvelopeId(), bundle.projectId(),
                    canonicalPayloadDigest, mapped.mappingVersionId(), "REPLAY_CONFLICT", now);
            appendAudit(
                    tenantId, auditContext, envelope.inboundEnvelopeId(), bundle.projectId(),
                    "INBOUND_MESSAGE_REJECTED", "REJECTED", "REPLAY_CONFLICT",
                    envelope.correlationId(), now);
            return new InboundCreateWorkOrderResult.Rejected(
                    "REPLAY_CONFLICT",
                    "ORDER_CONFLICT: business key was already used with a different payload");
        }
        if (!registered.created()) {
            if (!"COMPLETED".equals(canonical.processingStatus())) {
                throw new IllegalStateException("CanonicalMessage is not recoverably completed");
            }
            messages.completeEnvelope(
                    tenantId, envelope.inboundEnvelopeId(), canonical.projectId(),
                    canonical.payloadDigest(), canonical.mappingVersionId(),
                    canonical.canonicalMessageId(), canonical.resultCode(),
                    canonical.resultType(), canonical.resultId(), now);
            UUID workOrderId = UUID.fromString(canonical.resultId());
            return new InboundCreateWorkOrderResult.Accepted(
                    workOrderId, canonical.projectId(), canonical.businessKey(),
                    canonical.connectorVersionId(), canonical.mappingVersionId(), true);
        }

        WorkOrderReceipt receipt = workOrderCommandService.receive(new ReceiveExternalWorkOrderCommand(
                tenantId, bundle.projectId(), mapped.clientCode(), mapped.brandCode(),
                mapped.serviceProductCode(), mapped.externalOrderCode(), auditContext.requestDigest(),
                bundle.bundleId(), bundle.bundleCode(), bundle.bundleVersion(), bundle.manifestDigest(),
                mapped.provinceCode(), mapped.cityCode(), mapped.districtCode(), mapped.customerName(),
                mapped.customerMobile(), mapped.serviceAddress(), mapped.vehicleVin(), mapped.dispatchedAt(),
                correlationId, "canonical-message:" + canonical.canonicalMessageId()));

        String resultId = receipt.workOrderId().toString();
        messages.completeCanonical(
                tenantId, canonical.canonicalMessageId(), "ACCEPTED", "WORK_ORDER", resultId, now);
        messages.completeEnvelope(
                tenantId, envelope.inboundEnvelopeId(), bundle.projectId(), canonicalPayloadDigest,
                mapped.mappingVersionId(), canonical.canonicalMessageId(), "ACCEPTED", "WORK_ORDER",
                resultId, now);
        appendProcessedEvent(canonical, envelope, receipt, correlationId, tenantId, now);
        appendAudit(
                tenantId, auditContext, envelope.inboundEnvelopeId(), bundle.projectId(),
                "INBOUND_MESSAGE_PROCESSED", "ACCEPTED", null, correlationId, now);
        return new InboundCreateWorkOrderResult.Accepted(
                receipt.workOrderId(), receipt.projectId(), mapped.businessKey(),
                connector.connectorVersionId(), mapped.mappingVersionId(), receipt.replay());
    }

    private InboundCreateWorkOrderResult replayFromEnvelope(String tenantId, InboundEnvelopeView envelope) {
        if ("COMPLETED".equals(envelope.processingStatus()) && envelope.canonicalMessageId() != null) {
            CanonicalMessageView canonical = messages.findCanonical(tenantId, envelope.canonicalMessageId())
                    .map(InboundMessageRepository.CanonicalMessageRecord::view)
                    .orElseThrow(() -> new IllegalStateException("Completed Envelope lost CanonicalMessage"));
            return new InboundCreateWorkOrderResult.Accepted(
                    UUID.fromString(canonical.resultId()),
                    canonical.projectId(),
                    canonical.businessKey(),
                    canonical.connectorVersionId(),
                    canonical.mappingVersionId(),
                    true);
        }
        return new InboundCreateWorkOrderResult.Rejected(
                envelope.resultCode() == null ? "REJECTED" : envelope.resultCode(),
                "request was already rejected by the inbound pipeline");
    }

    private void appendProcessedEvent(
            CanonicalMessageView canonical,
            InboundEnvelopeView envelope,
            WorkOrderReceipt receipt,
            String correlationId,
            String tenantId,
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
            String tenantId,
            InboundConnectorAuditContext auditContext,
            UUID envelopeId,
            UUID projectId,
            String action,
            String result,
            String errorCode,
            String correlationId,
            Instant now
    ) {
        audit.append(new AuditEntry(
                UUID.randomUUID(), tenantId, auditContext.actorId(),
                action, auditContext.capability(), "InboundEnvelope", envelopeId.toString(),
                "ALLOW", List.of(), auditContext.authPolicy(), result, errorCode,
                auditContext.requestDigest(), correlationId, now));
    }

    private void store(String objectRef, byte[] content, String digest) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            storage.storeInternal(
                    objectRef, input, content.length, digest, "application/json");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist authenticated inbound payload", exception);
        }
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
