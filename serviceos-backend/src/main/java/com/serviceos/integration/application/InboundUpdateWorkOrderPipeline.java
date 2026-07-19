package com.serviceos.integration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.InboundConnectorAuditContext;
import com.serviceos.integration.spi.InboundUpdateWorkOrderResult;
import com.serviceos.integration.spi.UpdateWorkOrderMappedInbound;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.ExternalWorkOrderConflictException;
import com.serviceos.workorder.api.ExternalWorkOrderPointer;
import com.serviceos.workorder.api.UpdateExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderExternalLookup;
import com.serviceos.workorder.api.WorkOrderUpdateReceipt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * UPDATE_WORK_ORDER 通用入站管道。
 *
 * <p>Canonical 存储/登记 → 外部订单定位 → 领域更新命令 → Envelope/Canonical 完成 → 审计。
 * 领域侧写出 {@code workorder.external-details-updated}。</p>
 */
@Service
public class InboundUpdateWorkOrderPipeline {
    private static final String MESSAGE_TYPE = UpdateWorkOrderMappedInbound.MESSAGE_TYPE_UPDATE_WORK_ORDER;

    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final WorkOrderExternalLookup workOrderLookup;
    private final WorkOrderCommandService workOrders;
    private final AuditAppender audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public InboundUpdateWorkOrderPipeline(
            InboundMessageRepository messages,
            ObjectStorageGateway storage,
            WorkOrderExternalLookup workOrderLookup,
            WorkOrderCommandService workOrders,
            AuditAppender audit,
            TransactionTemplate transactions,
            Clock clock
    ) {
        this.messages = messages;
        this.storage = storage;
        this.workOrderLookup = workOrderLookup;
        this.workOrders = workOrders;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    public InboundUpdateWorkOrderResult processMappedUpdate(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            UpdateWorkOrderMappedInbound mapped,
            InboundConnectorAuditContext auditContext,
            String correlationId,
            String objectNamespace
    ) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(connector, "connector must not be null");
        Objects.requireNonNull(mapped, "mapped must not be null");
        Objects.requireNonNull(auditContext, "auditContext must not be null");
        String safeTenant = required(tenantId, "tenantId");
        String safeCorrelation = required(correlationId, "correlationId");
        String safeNamespace = required(objectNamespace, "objectNamespace");

        if (!"RECEIVED".equals(envelope.processingStatus())) {
            return replayFromEnvelope(safeTenant, envelope, connector, mapped);
        }

        ExternalWorkOrderPointer pointer = workOrderLookup.findByExternalOrder(
                        safeTenant, mapped.clientCode(), mapped.externalOrderCode())
                .orElse(null);
        if (pointer == null) {
            return reject(envelope, safeTenant, null, null, mapped.mappingVersionId(),
                    "WORK_ORDER_NOT_FOUND", "No work order matches external order", auditContext);
        }
        if (!"RECEIVED".equals(pointer.status()) && !"ACTIVE".equals(pointer.status())) {
            return reject(envelope, safeTenant, pointer.projectId(), null, mapped.mappingVersionId(),
                    "WORK_ORDER_NOT_UPDATABLE",
                    "Work order status " + pointer.status() + " cannot be updated", auditContext);
        }

        byte[] canonicalPayload = mapped.canonicalPayload();
        String digest = Sha256.digest(canonicalPayload);
        String tenantPrefix = Sha256.digest(safeTenant).substring(0, 16);
        String objectRef = "integration/inbound/" + tenantPrefix + "/" + safeNamespace
                + "/canonical/" + digest + ".json";
        store(objectRef, canonicalPayload, digest);

        try {
            return Objects.requireNonNull(transactions.execute(status -> completeUpdate(
                    envelope, connector, safeTenant, mapped, pointer, objectRef, digest,
                    auditContext, safeCorrelation)));
        } catch (ExternalWorkOrderConflictException exception) {
            return reject(envelope, safeTenant, pointer.projectId(), digest, mapped.mappingVersionId(),
                    "UPDATE_CONFLICT", exception.getMessage(), auditContext);
        }
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
            UpdateWorkOrderMappedInbound mapped
    ) {
        if ("REJECTED".equals(envelope.processingStatus())) {
            return new InboundUpdateWorkOrderResult.Rejected(
                    envelope.resultCode(), "Previous update inbound was rejected");
        }
        ExternalWorkOrderPointer pointer = workOrderLookup.findByExternalOrder(
                        tenantId, mapped.clientCode(), mapped.externalOrderCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Completed update envelope missing work order pointer"));
        return new InboundUpdateWorkOrderResult.Accepted(
                pointer.workOrderId(), pointer.projectId(), mapped.businessKey(),
                connector.connectorVersionId(), mapped.mappingVersionId(), true);
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
