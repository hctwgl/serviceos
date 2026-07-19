package com.serviceos.integration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.configuration.api.IntegrationMappingResult;
import com.serviceos.configuration.api.IntegrationMappingRuntime;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.InboundConnectorAuditContext;
import com.serviceos.integration.spi.InboundUpdateWorkOrderResult;
import com.serviceos.integration.spi.UpdateWorkOrderMappedInbound;
import com.serviceos.integration.spi.UpdateWorkOrderRouteHint;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.ExternalWorkOrderConflictException;
import com.serviceos.workorder.api.ExternalWorkOrderPointer;
import com.serviceos.workorder.api.UpdateExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderExternalLookup;
import com.serviceos.workorder.api.WorkOrderUpdateReceipt;
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
import java.util.UUID;

/**
 * UPDATE_WORK_ORDER 通用入站管道。
 *
 * <p>M339：按 RouteHint 定位工单冻结 Bundle → 强制 INBOUND Mapping（messageType=
 * UPDATE_WORK_ORDER）→ 物化 → Canonical 存储/登记 → 领域更新 → 审计。
 * 缺失 Mapping 失败关闭，不回退适配器领域字段。</p>
 */
@Service
public class InboundUpdateWorkOrderPipeline {
    private static final String MESSAGE_TYPE = UpdateWorkOrderMappedInbound.MESSAGE_TYPE_UPDATE_WORK_ORDER;

    private final IntegrationMappingRuntime integrationMappingRuntime;
    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final WorkOrderExternalLookup workOrderLookup;
    private final WorkOrderCommandService workOrders;
    private final AuditAppender audit;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InboundUpdateWorkOrderPipeline(
            IntegrationMappingRuntime integrationMappingRuntime,
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            WorkOrderExternalLookup workOrderLookup,
            WorkOrderCommandService workOrders,
            AuditAppender audit,
            TransactionTemplate transactions,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.integrationMappingRuntime = integrationMappingRuntime;
        this.messages = messages;
        this.storage = storage;
        this.workOrderLookup = workOrderLookup;
        this.workOrders = workOrders;
        this.audit = audit;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 处理更新路由提示 + OEM 原文（强制 INBOUND Mapping 物化）。
     */
    public InboundUpdateWorkOrderResult processUpdate(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            UpdateWorkOrderRouteHint routeHint,
            Map<String, Object> externalPayload,
            InboundConnectorAuditContext auditContext,
            String correlationId,
            String objectNamespace
    ) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(connector, "connector must not be null");
        Objects.requireNonNull(routeHint, "routeHint must not be null");
        Objects.requireNonNull(auditContext, "auditContext must not be null");
        String safeTenant = required(tenantId, "tenantId");
        String safeCorrelation = required(correlationId, "correlationId");
        String safeNamespace = required(objectNamespace, "objectNamespace");

        if (!"RECEIVED".equals(envelope.processingStatus())) {
            return replayFromEnvelope(safeTenant, envelope, connector, routeHint);
        }

        // 先按 hint 定位工单以取得冻结 Bundle，再强制 Mapping；找不到则协议失败关闭。
        ExternalWorkOrderPointer pointer = workOrderLookup.findByExternalOrder(
                        safeTenant, routeHint.clientCode(), routeHint.externalOrderCode())
                .orElse(null);
        if (pointer == null) {
            return reject(envelope, safeTenant, null, null, null,
                    "WORK_ORDER_NOT_FOUND", "No work order matches external order", auditContext);
        }
        if (!"RECEIVED".equals(pointer.status()) && !"ACTIVE".equals(pointer.status())) {
            return reject(envelope, safeTenant, pointer.projectId(), null, null,
                    "WORK_ORDER_NOT_UPDATABLE",
                    "Work order status " + pointer.status() + " cannot be updated", auditContext);
        }

        final IntegrationMappingResult mappingResult;
        final UpdateWorkOrderMappedInbound mapped;
        try {
            mappingResult = requireFrozenIntegrationMapping(
                    safeTenant, connector, pointer, externalPayload);
            mapped = UpdateWorkOrderMappingMaterializer.materialize(
                    routeHint, mappingResult, objectMapper);
        } catch (BusinessProblem exception) {
            return reject(envelope, safeTenant, pointer.projectId(), null, null,
                    "INTEGRATION_MAPPING_FAILED", exception.getMessage(), auditContext);
        }

        byte[] canonicalPayload = mapped.canonicalPayload();
        String digest = Sha256.digest(canonicalPayload);
        String tenantPrefix = Sha256.digest(safeTenant).substring(0, 16);
        String objectRef = "integration/inbound/" + tenantPrefix + "/" + safeNamespace
                + "/canonical/" + digest + ".json";
        store(objectRef, canonicalPayload, digest);

        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                InboundUpdateWorkOrderResult result = completeUpdate(
                        envelope, connector, safeTenant, mapped, pointer, objectRef, digest,
                        auditContext, safeCorrelation);
                appendMappingAudit(
                        safeTenant, auditContext, envelope.inboundEnvelopeId(), pointer.projectId(),
                        mappingResult, safeCorrelation, clock.instant());
                return result;
            }));
        } catch (ExternalWorkOrderConflictException exception) {
            return reject(envelope, safeTenant, pointer.projectId(), digest, mapped.mappingVersionId(),
                    "UPDATE_CONFLICT", exception.getMessage(), auditContext);
        }
    }

    private IntegrationMappingResult requireFrozenIntegrationMapping(
            String tenantId,
            ConnectorIdentity connector,
            ExternalWorkOrderPointer pointer,
            Map<String, Object> externalPayload
    ) {
        if (pointer.configurationBundleId() == null
                || pointer.configurationBundleDigest() == null
                || pointer.configurationBundleDigest().isBlank()) {
            throw new BusinessProblem(
                    com.serviceos.shared.ProblemCode.VALIDATION_FAILED,
                    "UPDATE_WORK_ORDER requires work order frozen configuration bundle reference");
        }
        boolean mappingConfigured = integrationMappingRuntime.hasInboundMappingForConnector(
                tenantId, pointer.configurationBundleId(), pointer.configurationBundleDigest(),
                connector.connectorCode(), MESSAGE_TYPE);
        if (!mappingConfigured) {
            throw new BusinessProblem(
                    com.serviceos.shared.ProblemCode.VALIDATION_FAILED,
                    "UPDATE_WORK_ORDER requires frozen INBOUND INTEGRATION mapping for connector: "
                            + connector.connectorCode());
        }
        if (externalPayload == null || externalPayload.isEmpty()) {
            throw new BusinessProblem(
                    com.serviceos.shared.ProblemCode.VALIDATION_FAILED,
                    "Frozen INTEGRATION mapping requires external source payload");
        }
        return integrationMappingRuntime.applyInboundForConnectorIfPresent(
                tenantId, pointer.configurationBundleId(), pointer.configurationBundleDigest(),
                connector.connectorCode(), MESSAGE_TYPE, externalPayload)
                .orElseThrow(() -> new BusinessProblem(
                        com.serviceos.shared.ProblemCode.VALIDATION_FAILED,
                        "UPDATE_WORK_ORDER requires frozen INBOUND INTEGRATION mapping for connector: "
                                + connector.connectorCode()));
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

    public InboundUpdateWorkOrderResult reject(
            InboundEnvelopeView envelope,
            String tenantId,
            UUID projectId,
            String canonicalDigest,
            String mappingVersion,
            String code,
            String message,
            InboundConnectorAuditContext auditContext
    ) {
        String safeTenant = required(tenantId, "tenantId");
        String safeCode = required(code, "code");
        String safeMessage = message == null || message.isBlank()
                ? "inbound update-work-order rejected" : message.trim();
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            boolean transitioned = messages.rejectEnvelope(
                    safeTenant, envelope.inboundEnvelopeId(), projectId, canonicalDigest,
                    mappingVersion, safeCode, now);
            if (transitioned) {
                audit.append(new AuditEntry(
                        UUID.randomUUID(), safeTenant, auditContext.actorId(),
                        "INBOUND_UPDATE_REJECTED", auditContext.authPolicy(),
                        "InboundEnvelope", envelope.inboundEnvelopeId().toString(),
                        "ALLOW", List.of(), null, "REJECTED", safeCode,
                        Sha256.digest(envelope.rawPayloadDigest() + "|" + safeCode),
                        envelope.correlationId(), now));
            }
        });
        return new InboundUpdateWorkOrderResult.Rejected(safeCode, safeMessage);
    }

    private InboundUpdateWorkOrderResult completeUpdate(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            UpdateWorkOrderMappedInbound mapped,
            ExternalWorkOrderPointer pointer,
            String objectRef,
            String digest,
            InboundConnectorAuditContext auditContext,
            String correlationId
    ) {
        var existing = messages.findCanonicalByBusinessKey(
                tenantId, connector.connectorVersionId(), MESSAGE_TYPE, mapped.businessKey());
        if (existing.isPresent()) {
            CanonicalMessageView canonical = existing.get().view();
            if (!digest.equals(canonical.payloadDigest())) {
                throw new ExternalWorkOrderConflictException(
                        "Update CanonicalMessage business key conflict with different payload");
            }
            messages.completeEnvelope(
                    tenantId, envelope.inboundEnvelopeId(), pointer.projectId(), digest,
                    mapped.mappingVersionId(), canonical.canonicalMessageId(),
                    "ACCEPTED", "WORK_ORDER_UPDATED", pointer.workOrderId().toString(),
                    clock.instant());
            return new InboundUpdateWorkOrderResult.Accepted(
                    pointer.workOrderId(), pointer.projectId(), mapped.businessKey(),
                    connector.connectorVersionId(), mapped.mappingVersionId(), true);
        }

        var registration = messages.registerCanonical(new InboundMessageRepository.NewCanonicalMessage(
                UUID.randomUUID(), tenantId, pointer.projectId(), connector.connectorVersionId(),
                MESSAGE_TYPE, mapped.businessKey(), objectRef, digest, mapped.mappingVersionId(),
                envelope.inboundEnvelopeId(), clock.instant()));
        CanonicalMessageView canonical = registration.message().view();
        if (!registration.created()) {
            if (!digest.equals(canonical.payloadDigest())) {
                throw new ExternalWorkOrderConflictException(
                        "Update CanonicalMessage business key conflict with different payload");
            }
            return new InboundUpdateWorkOrderResult.Accepted(
                    pointer.workOrderId(), pointer.projectId(), mapped.businessKey(),
                    connector.connectorVersionId(), mapped.mappingVersionId(), true);
        }

        WorkOrderUpdateReceipt receipt = workOrders.updateExternalDetails(new UpdateExternalWorkOrderCommand(
                tenantId, pointer.workOrderId(), pointer.version(),
                mapped.customerName(), mapped.customerMobile(), mapped.serviceAddress(),
                mapped.provinceCode(), mapped.cityCode(), mapped.districtCode(),
                mapped.updateDigest(), correlationId, canonical.canonicalMessageId().toString()));
        Instant now = clock.instant();
        messages.completeCanonical(
                tenantId, canonical.canonicalMessageId(), "ACCEPTED",
                "WORK_ORDER_UPDATED", receipt.workOrderId().toString(), now);
        messages.completeEnvelope(
                tenantId, envelope.inboundEnvelopeId(), pointer.projectId(), digest,
                mapped.mappingVersionId(), canonical.canonicalMessageId(),
                "ACCEPTED", "WORK_ORDER_UPDATED", receipt.workOrderId().toString(), now);
        audit.append(new AuditEntry(
                UUID.randomUUID(), tenantId, auditContext.actorId(),
                "INBOUND_UPDATE_PROCESSED", auditContext.authPolicy(),
                "CanonicalMessage", canonical.canonicalMessageId().toString(),
                "ALLOW", List.of(), connector.connectorVersionId(), "ACCEPTED", null,
                Sha256.digest(digest + "|" + mapped.externalOrderCode()), correlationId, now));
        return new InboundUpdateWorkOrderResult.Accepted(
                receipt.workOrderId(), pointer.projectId(), mapped.businessKey(),
                connector.connectorVersionId(), mapped.mappingVersionId(), receipt.replay());
    }

    private InboundUpdateWorkOrderResult replayFromEnvelope(
            String tenantId,
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            UpdateWorkOrderRouteHint routeHint
    ) {
        if ("REJECTED".equals(envelope.processingStatus())) {
            return new InboundUpdateWorkOrderResult.Rejected(
                    envelope.resultCode(), "Previous update inbound was rejected");
        }
        ExternalWorkOrderPointer pointer = workOrderLookup.findByExternalOrder(
                        tenantId, routeHint.clientCode(), routeHint.externalOrderCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Completed update envelope missing work order pointer"));
        String mappingVersion = envelope.mappingVersionId() == null
                ? "REPLAY" : envelope.mappingVersionId();
        return new InboundUpdateWorkOrderResult.Accepted(
                pointer.workOrderId(), pointer.projectId(),
                "REPLAY:" + envelope.inboundEnvelopeId(),
                connector.connectorVersionId(), mappingVersion, true);
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
