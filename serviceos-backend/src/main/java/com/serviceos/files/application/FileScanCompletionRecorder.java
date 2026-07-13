package com.serviceos.files.application;

import com.serviceos.files.spi.ScanOutcome;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.Sha256;
import com.serviceos.task.spi.TaskExecutionContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** 扫描状态和下游事件在同一本地事务提交，避免“文件可用但资料模块永远收不到事件”。 */
@Service
final class FileScanCompletionRecorder {
    private final FileLifecycleStore store;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    FileScanCompletionRecorder(
            FileLifecycleStore store,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.store = store;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public StoredFileRecord record(TaskExecutionContext context, UUID fileId, ScanOutcome outcome) {
        Instant now = clock.instant();
        StoredFileRecord updated = store.recordScanResult(
                context.tenantId(), fileId, context.taskId(), context.attemptId(), outcome, now);
        String payload = json(new FileScanCompletedPayload(
                updated.fileId(), updated.tenantId(), updated.checksumSha256(),
                updated.detectedMimeType(), updated.lifecycleStatus(), updated.quarantineReason(),
                outcome.scannerName(), outcome.scannerVersion(), outcome.reasonCode(),
                updated.version(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "files", "file.scan-completed", 1,
                "StoredFile", updated.fileId().toString(), updated.version(), updated.tenantId(),
                context.correlationId(), context.attemptId().toString(), updated.fileId().toString(),
                payload, Sha256.digest(payload), now));
        return updated;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("File scan result cannot be serialized", exception);
        }
    }

    private record FileScanCompletedPayload(
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
}
