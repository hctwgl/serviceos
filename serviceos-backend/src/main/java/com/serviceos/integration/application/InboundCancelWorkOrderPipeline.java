package com.serviceos.integration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.spi.CancelWorkOrderMappedInbound;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.InboundCancelWorkOrderResult;
import com.serviceos.integration.spi.InboundConnectorAuditContext;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.CancelWorkOrderCommand;
import com.serviceos.workorder.api.ExternalWorkOrderConflictException;
import com.serviceos.workorder.api.ExternalWorkOrderPointer;
import com.serviceos.workorder.api.WorkOrderCancellationReceipt;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderExternalLookup;
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
 * CANCEL_WORK_ORDER 通用入站管道。
 *
 * <p>适配器完成验签与 Envelope 登记后进入本管道：Canonical 存储/登记 → 外部订单定位 →
 * 领域取消命令 → Envelope/Canonical 完成 → 审计。领域侧写出 {@code workorder.cancelled}。
 * 适配器不得直接写工单表。</p>
 */
@Service
public class InboundCancelWorkOrderPipeline {
    private static final String MESSAGE_TYPE = CancelWorkOrderMappedInbound.MESSAGE_TYPE_CANCEL_WORK_ORDER;

    private final InboundMessageRepository messages;
    private final ObjectStorageGateway storage;
    private final WorkOrderExternalLookup workOrderLookup;
    private final WorkOrderCommandService workOrders;
    private final AuditAppender audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public InboundCancelWorkOrderPipeline(
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

    public InboundCancelWorkOrderResult processMappedCancel(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            CancelWorkOrderMappedInbound mapped,
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
                    "WORK_ORDER_NOT_CANCELLABLE",
                    "Work order status " + pointer.status() + " cannot be cancelled", auditContext);
        }

        byte[] canonicalPayload = mapped.canonicalPayload();
        String digest = Sha256.digest(canonicalPayload);
        String tenantPrefix = Sha256.digest(safeTenant).substring(0, 16);
        String objectRef = "integration/inbound/" + tenantPrefix + "/" + safeNamespace
                + "/canonical/" + digest + ".json";
        store(objectRef, canonicalPayload, digest);

        try {
            return Objects.requireNonNull(transactions.execute(status -> completeCancel(
                    envelope, connector, safeTenant, mapped, pointer, objectRef, digest,
                    auditContext, safeCorrelation)));
        } catch (ExternalWorkOrderConflictException exception) {
            return reject(envelope, safeTenant, pointer.projectId(), digest, mapped.mappingVersionId(),
                    "CANCEL_CONFLICT", exception.getMessage(), auditContext);
        }
    }

    public InboundCancelWorkOrderResult reject(
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
                ? "inbound cancel-work-order rejected" : message.trim();
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            boolean transitioned = messages.rejectEnvelope(
                    safeTenant, envelope.inboundEnvelopeId(), projectId, canonicalDigest,
                    mappingVersion, safeCode, now);
            if (transitioned) {
                audit.append(new AuditEntry(
                        UUID.randomUUID(), safeTenant, auditContext.actorId(),
                        "INBOUND_CANCEL_REJECTED", auditContext.authPolicy(),
                        "InboundEnvelope", envelope.inboundEnvelopeId().toString(),
                        "ALLOW", List.of(), null, "REJECTED", safeCode,
                        Sha256.digest(envelope.rawPayloadDigest() + "|" + safeCode),
                        envelope.correlationId(), now));
            }
        });
        return new InboundCancelWorkOrderResult.Rejected(safeCode, safeMessage);
    }

    private InboundCancelWorkOrderResult completeCancel(
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            String tenantId,
            CancelWorkOrderMappedInbound mapped,
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
                        "Cancel CanonicalMessage business key conflict with different payload");
            }
            if (!"COMPLETED".equals(canonical.processingStatus())) {
                throw new IllegalStateException("Cancel CanonicalMessage is not recoverably completed");
            }
            messages.completeEnvelope(
                    tenantId, envelope.inboundEnvelopeId(), pointer.projectId(), digest,
                    mapped.mappingVersionId(), canonical.canonicalMessageId(),
                    "ACCEPTED", "WORK_ORDER_CANCELLED", pointer.workOrderId().toString(),
                    clock.instant());
            return new InboundCancelWorkOrderResult.Accepted(
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
                        "Cancel CanonicalMessage business key conflict with different payload");
            }
            return new InboundCancelWorkOrderResult.Accepted(
                    pointer.workOrderId(), pointer.projectId(), mapped.businessKey(),
                    connector.connectorVersionId(), mapped.mappingVersionId(), true);
        }

        WorkOrderCancellationReceipt receipt = workOrders.cancel(new CancelWorkOrderCommand(
                tenantId, pointer.workOrderId(), pointer.version(), mapped.reasonCode(),
                mapped.approvalRef(), correlationId, canonical.canonicalMessageId().toString()));
        Instant now = clock.instant();
        messages.completeCanonical(
                tenantId, canonical.canonicalMessageId(), "ACCEPTED",
                "WORK_ORDER_CANCELLED", receipt.workOrderId().toString(), now);
        messages.completeEnvelope(
                tenantId, envelope.inboundEnvelopeId(), pointer.projectId(), digest,
                mapped.mappingVersionId(), canonical.canonicalMessageId(),
                "ACCEPTED", "WORK_ORDER_CANCELLED", receipt.workOrderId().toString(), now);
        // 领域 cancel 已写出 workorder.cancelled；本管道不另发明未版本化的集成事件。
        audit.append(new AuditEntry(
                UUID.randomUUID(), tenantId, auditContext.actorId(),
                "INBOUND_CANCEL_PROCESSED", auditContext.authPolicy(),
                "CanonicalMessage", canonical.canonicalMessageId().toString(),
                "ALLOW", List.of(), connector.connectorVersionId(), "ACCEPTED", null,
                Sha256.digest(digest + "|" + mapped.externalOrderCode()), correlationId, now));
        return new InboundCancelWorkOrderResult.Accepted(
                receipt.workOrderId(), pointer.projectId(), mapped.businessKey(),
                connector.connectorVersionId(), mapped.mappingVersionId(), receipt.replay());
    }

    private InboundCancelWorkOrderResult replayFromEnvelope(
            String tenantId,
            InboundEnvelopeView envelope,
            ConnectorIdentity connector,
            CancelWorkOrderMappedInbound mapped
    ) {
        if ("REJECTED".equals(envelope.processingStatus())) {
            return new InboundCancelWorkOrderResult.Rejected(
                    envelope.resultCode(), "Previous cancel inbound was rejected");
        }
        ExternalWorkOrderPointer pointer = workOrderLookup.findByExternalOrder(
                        tenantId, mapped.clientCode(), mapped.externalOrderCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Completed cancel envelope missing work order pointer"));
        return new InboundCancelWorkOrderResult.Accepted(
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
