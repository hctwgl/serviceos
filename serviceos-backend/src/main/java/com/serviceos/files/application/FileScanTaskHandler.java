package com.serviceos.files.application;

import com.serviceos.files.spi.FileContentScanner;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.files.spi.ScanOutcome;
import com.serviceos.task.spi.AutomatedTaskHandler;
import com.serviceos.task.spi.TaskExecutionContext;
import com.serviceos.task.spi.TaskExecutionException;
import com.serviceos.task.spi.TaskExecutionResult;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * 自动内容扫描处理器。读取私有对象、调用扫描器、再以短事务落扫描结果；重复 attempt 不会生成重复结果。
 */
@Component
final class FileScanTaskHandler implements AutomatedTaskHandler {
    private final FileLifecycleStore store;
    private final ObjectStorageGateway storage;
    private final FileContentScanner scanner;
    private final FileScanCompletionRecorder completionRecorder;
    private final Clock clock;

    FileScanTaskHandler(
            FileLifecycleStore store,
            ObjectStorageGateway storage,
            FileContentScanner scanner,
            FileScanCompletionRecorder completionRecorder,
            Clock clock
    ) {
        this.store = store;
        this.storage = storage;
        this.scanner = scanner;
        this.completionRecorder = completionRecorder;
        this.clock = clock;
    }

    @Override
    public String taskType() {
        return DefaultFileCommandService.SCAN_TASK_TYPE;
    }

    @Override
    public TaskExecutionResult execute(TaskExecutionContext context) throws TaskExecutionException {
        UUID fileId;
        try {
            fileId = UUID.fromString(context.payloadRef());
        } catch (RuntimeException exception) {
            throw TaskExecutionException.finalFailure("FILE_SCAN_PAYLOAD_INVALID", exception);
        }
        StoredFileRecord file = store.findFile(context.tenantId(), fileId).orElse(null);
        if (file == null) {
            throw TaskExecutionException.finalFailure("FILE_SCAN_TARGET_MISSING", null);
        }
        if (!Objects.equals(file.checksumSha256(), context.payloadDigest())) {
            throw TaskExecutionException.finalFailure("FILE_SCAN_DIGEST_MISMATCH", null);
        }
        if ("AVAILABLE".equals(file.lifecycleStatus()) || "MALWARE".equals(file.quarantineReason())) {
            return TaskExecutionResult.succeeded(file.fileId() + ":" + file.lifecycleStatus());
        }

        ScanOutcome outcome;
        try (InputStream content = storage.openForScan(file.objectKey())) {
            outcome = Objects.requireNonNull(
                    scanner.scan(content, file.size(), file.detectedMimeType()),
                    "File scanner result must not be null");
        } catch (Exception exception) {
            throw TaskExecutionException.retryable(
                    "FILE_SCAN_TEMPORARILY_UNAVAILABLE",
                    clock.instant().plus(Duration.ofMinutes(1)), exception);
        }
        if (outcome.scannerName() == null || outcome.scannerName().isBlank()
                || outcome.scannerVersion() == null || outcome.scannerVersion().isBlank()
                || (outcome.result() == ScanOutcome.Result.MALICIOUS
                    && (outcome.reasonCode() == null || outcome.reasonCode().isBlank()))) {
            throw TaskExecutionException.finalFailure("FILE_SCAN_RESULT_INVALID", null);
        }

        StoredFileRecord updated = completionRecorder.record(context, fileId, outcome);
        return TaskExecutionResult.succeeded(updated.fileId() + ":" + updated.lifecycleStatus());
    }
}
