package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 消费 file.scan-completed@v1：CLEAN → VALIDATING，MALWARE → QUARANTINED，并刷新槽位数量投影。
 * 非 Evidence 绑定的文件扫描事件直接成功跳过。
 */
@Service
final class EvidenceFileScanCompletedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "evidence.file-scan-completed.revision-state.v1";
    private static final String SYSTEM_ACTOR = "system:evidence-scan-projection";

    private final InboxService inbox;
    private final EvidenceItemRepository repository;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    EvidenceFileScanCompletedHandler(
            InboxService inbox,
            EvidenceItemRepository repository,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.inbox = inbox;
        this.repository = repository;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return "file.scan-completed".equals(eventType) && schemaVersion == 1;
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        InboxDecision decision = inbox.begin(
                message.tenantId(), CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }

        FileScanPayload payload = read(message.payload());
        if (!message.tenantId().equals(payload.tenantId())) {
            throw new IllegalArgumentException("file.scan-completed tenant mismatch");
        }
        var revision = repository.findRevisionByFileObjectId(message.tenantId(), payload.fileId());
        if (revision.isEmpty()) {
            if (repository.findUploadBindingByFileId(message.tenantId(), payload.fileId()).isPresent()) {
                // Finalize 资料写入与扫描完成存在竞态：绑定已存在则稍后重试，避免丢状态。
                throw new IllegalStateException(
                        "EvidenceRevision not yet created for scanned evidence file");
            }
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest("ignored|" + payload.fileId()));
            return;
        }

        EvidenceRevisionView current = revision.get();
        String nextStatus = mapStatus(payload.lifecycleStatus(), current.status());
        Instant now = clock.instant();
        if (!nextStatus.equals(current.status())) {
            repository.updateRevisionStatus(
                    message.tenantId(), current.evidenceRevisionId(), nextStatus);
            EvidenceSlotView slot = repository.lockSlot(message.tenantId(), current.evidenceSlotId());
            int counting = repository.countCountingItems(message.tenantId(), slot.slotId());
            String projection = EvidenceSlotStatusProjector.project(
                    slot.minCount(), slot.maxCount(), counting);
            repository.updateSlotStatus(message.tenantId(), slot.slotId(), projection);

            String eventPayload = write(new RevisionStateChangedPayload(
                    current.evidenceRevisionId(), current.evidenceItemId(), current.evidenceSlotId(),
                    current.taskId(), current.projectId(), current.status(), nextStatus,
                    payload.lifecycleStatus(), now));
            outbox.append(new OutboxEvent(
                    UUID.randomUUID(), UUID.randomUUID(), "evidence",
                    "evidence.revision-validation-state-changed", 1,
                    "EvidenceRevision", current.evidenceRevisionId().toString(),
                    current.revisionNumber(), message.tenantId(), message.correlationId(),
                    message.eventId().toString(), current.taskId().toString(),
                    eventPayload, Sha256.digest(eventPayload), now));
            audit.append(new AuditEntry(
                    UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                    "EVIDENCE_REVISION_SCAN_STATE_CHANGED", null, "EvidenceRevision",
                    current.evidenceRevisionId().toString(), "SYSTEM", List.of(),
                    "FILE_SCAN_V1", nextStatus, null,
                    Sha256.digest(payload.fileId() + "|" + nextStatus),
                    message.correlationId(), now));
        }
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(current.evidenceRevisionId() + "|" + nextStatus));
    }

    private static String mapStatus(String fileLifecycleStatus, String currentStatus) {
        if ("AVAILABLE".equals(fileLifecycleStatus)) {
            if ("QUARANTINED".equals(currentStatus) || "INVALIDATED".equals(currentStatus)) {
                return currentStatus;
            }
            return "VALIDATING";
        }
        if ("QUARANTINED".equals(fileLifecycleStatus)) {
            return "QUARANTINED";
        }
        throw new IllegalArgumentException("unsupported file lifecycle status: " + fileLifecycleStatus);
    }

    private FileScanPayload read(String payload) {
        try {
            return objectMapper.readValue(payload, FileScanPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("file.scan-completed payload cannot be decoded", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Evidence scan event serialization failed", exception);
        }
    }

    private record FileScanPayload(
            UUID fileId,
            String tenantId,
            String checksumSha256,
            String detectedMimeType,
            String lifecycleStatus,
            String quarantineReason,
            String scannerName,
            String scannerVersion,
            String reasonCode,
            long aggregateVersion,
            Instant occurredAt
    ) {
    }

    private record RevisionStateChangedPayload(
            UUID evidenceRevisionId,
            UUID evidenceItemId,
            UUID evidenceSlotId,
            UUID taskId,
            UUID projectId,
            String previousStatus,
            String status,
            String fileLifecycleStatus,
            Instant changedAt
    ) {
    }
}
