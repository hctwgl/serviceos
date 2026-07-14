package com.serviceos.files.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.files.api.AuthorizeDownloadCommand;
import com.serviceos.files.api.BeginUploadCommand;
import com.serviceos.files.api.DownloadAuthorizationView;
import com.serviceos.files.api.FileCommandService;
import com.serviceos.files.api.FinalizeUploadCommand;
import com.serviceos.files.api.InvalidateStoredFileCommand;
import com.serviceos.files.api.StoredFileView;
import com.serviceos.files.api.UploadSessionView;
import com.serviceos.files.spi.ObjectMetadata;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.files.spi.ObjectTransferAuthorization;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.ScheduleAutomatedTaskCommand;
import com.serviceos.task.api.TaskSchedulingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * FILE-001 三段式文件生命周期编排。
 *
 * <p>对象存储调用始终位于数据库事务之外；只有会话抢占和最终文件/扫描任务写入使用短事务。
 * 这样既避免长事务，也避免“文件已落库但扫描任务丢失”的双写窗口。</p>
 */
@Service
final class DefaultFileCommandService implements FileCommandService {
    static final String SCAN_TASK_TYPE = "file.content-scan";
    private static final String BEGIN_OPERATION = "file.upload.begin";
    private static final String UPLOAD_CAPABILITY = "file.upload";
    private static final String DOWNLOAD_CAPABILITY = "file.download";
    private static final String INVALIDATE_CAPABILITY = "file.invalidate";
    private static final String INVALIDATE_OPERATION = "file.invalidate";
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern MIME = Pattern.compile(
            "[a-z0-9][a-z0-9!#$&^_.+-]{0,126}/[a-z0-9][a-z0-9!#$&^_.+-]{0,126}");

    private final FileLifecycleStore store;
    private final ObjectStorageGateway storage;
    private final AuthorizationService authorization;
    private final AuditAppender audit;
    private final IdempotencyService idempotency;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final TaskSchedulingService tasks;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final long maximumFileSize;
    private final Duration uploadSessionLifetime;
    private final Duration uploadAuthorizationLifetime;
    private final Duration downloadAuthorizationLifetime;
    private final Duration finalizationLease;

    DefaultFileCommandService(
            FileLifecycleStore store,
            ObjectStorageGateway storage,
            AuthorizationService authorization,
            AuditAppender audit,
            IdempotencyService idempotency,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            TaskSchedulingService tasks,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${serviceos.files.maximum-size-bytes:52428800}") long maximumFileSize,
            @Value("${serviceos.files.upload-session-lifetime:PT1H}") Duration uploadSessionLifetime,
            @Value("${serviceos.files.upload-authorization-lifetime:PT15M}") Duration uploadAuthorizationLifetime,
            @Value("${serviceos.files.download-authorization-lifetime:PT5M}") Duration downloadAuthorizationLifetime,
            @Value("${serviceos.files.finalization-lease:PT2M}") Duration finalizationLease
    ) {
        this.store = store;
        this.storage = storage;
        this.authorization = authorization;
        this.audit = audit;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.tasks = tasks;
        this.transactions = transactions;
        this.clock = clock;
        this.maximumFileSize = positive(maximumFileSize, "maximumFileSize");
        this.uploadSessionLifetime = positive(uploadSessionLifetime, "uploadSessionLifetime");
        this.uploadAuthorizationLifetime = positive(uploadAuthorizationLifetime, "uploadAuthorizationLifetime");
        this.downloadAuthorizationLifetime = positive(downloadAuthorizationLifetime, "downloadAuthorizationLifetime");
        this.finalizationLease = positive(finalizationLease, "finalizationLease");
    }

    @Override
    public UploadSessionView beginUpload(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            BeginUploadCommand rawCommand
    ) {
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        BeginUploadCommand command = validateWithMaximum(rawCommand);
        AuthorizationDecision decision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        UPLOAD_CAPABILITY, principal.tenantId(),
                        command.businessContextType(), command.businessContextId()),
                metadata.correlationId());

        String requestDigest = beginDigest(command);
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        UploadSessionRecord session = transactions.execute(status -> {
            IdempotencyDecision idempotencyDecision = idempotency.begin(context, BEGIN_OPERATION, requestDigest);
            if (idempotencyDecision.kind() == IdempotencyDecision.Kind.REPLAY) {
                UUID existingSessionId = UUID.fromString(idempotencyDecision.resourceId().orElseThrow());
                return store.findSession(principal.tenantId(), existingSessionId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Idempotency result references a missing upload session"));
            }

            Instant createdAt = clock.instant();
            UUID sessionId = UUID.randomUUID();
            UUID fileId = UUID.randomUUID();
            String objectKey = FileObjectKeyFactory.create(principal.tenantId(), sessionId, createdAt);
            UploadSessionRecord created = new UploadSessionRecord(
                    sessionId, fileId, principal.tenantId(), objectKey,
                    command.businessContextType(), command.businessContextId(), command.originalFileName(),
                    command.declaredMimeType(), command.expectedSize(), command.expectedSha256(),
                    "CREATED", createdAt.plus(uploadSessionLifetime), null, null, null, null,
                    principal.principalId(), createdAt);
            store.insertSession(created);
            audit.append(allowAudit(
                    principal, decision, "FILE_UPLOAD_SESSION_CREATED", UPLOAD_CAPABILITY,
                    "UploadSession", sessionId.toString(), requestDigest,
                    metadata.correlationId(), createdAt));
            idempotency.complete(
                    context, BEGIN_OPERATION, sessionId.toString(), Sha256.digest(fileId.toString()));
            return created;
        });
        if (session == null) {
            throw new IllegalStateException("Begin upload transaction returned no session");
        }
        ObjectTransferAuthorization transfer = null;
        if ("CREATED".equals(session.status()) || "UPLOADING".equals(session.status())) {
            transfer = storage.authorizeUpload(
                    session.objectKey(), session.expectedSize(), session.declaredMimeType(),
                    uploadAuthorizationLifetime);
        }
        return new UploadSessionView(
                session.sessionId(), session.fileId(), session.status(),
                transfer == null ? null : transfer.method(), transfer == null ? null : transfer.url(),
                transfer == null ? java.util.Map.of() : transfer.requiredHeaders(),
                transfer == null ? null : transfer.expiresAt(), session.expiresAt());
    }

    @Override
    public StoredFileView finalizeUpload(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID uploadSessionId,
            FinalizeUploadCommand rawCommand
    ) {
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(uploadSessionId, "uploadSessionId must not be null");
        FinalizeUploadCommand command = validate(rawCommand);
        AuthorizationDecision decision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        UPLOAD_CAPABILITY, principal.tenantId(),
                        "UploadSession", uploadSessionId.toString()),
                metadata.correlationId());

        String requestDigest = Sha256.digest(command.actualSha256() + "\n" + command.finalizeCommandId());
        Instant reservedAt = clock.instant();
        FinalizationReservation reservation = transactions.execute(status -> store.reserveFinalization(
                principal.tenantId(), uploadSessionId, command.actualSha256(),
                command.finalizeCommandId(), requestDigest, reservedAt, finalizationLease));
        if (reservation == null) {
            throw new IllegalStateException("Finalization reservation transaction returned no result");
        }
        if (reservation.expired()) {
            throw new BusinessProblem(ProblemCode.FILE_UPLOAD_EXPIRED, "Upload session has expired");
        }
        if (reservation.replay()) {
            return store.findFile(principal.tenantId(), reservation.session().fileId())
                    .map(StoredFileRecord::toView)
                    .orElseThrow(() -> new IllegalStateException(
                            "Completed upload session references a missing file"));
        }

        ObjectMetadata objectMetadata;
        try {
            objectMetadata = storage.inspect(reservation.session().objectKey());
        } catch (IOException exception) {
            transactions.executeWithoutResult(status -> store.releaseFinalization(
                    principal.tenantId(), uploadSessionId, reservation.token(),
                    "OBJECT_NOT_READY", clock.instant()));
            throw new BusinessProblem(ProblemCode.FILE_UPLOAD_CONFLICT,
                    "Uploaded object is not ready for finalization");
        }

        try {
            validateObject(reservation.session(), objectMetadata);
        } catch (BusinessProblem problem) {
            transactions.executeWithoutResult(status -> store.failFinalization(
                    principal.tenantId(), uploadSessionId, reservation.token(),
                    problem.code().name(), clock.instant()));
            throw problem;
        }

        Instant completedAt = clock.instant();
        try {
            StoredFileRecord stored = transactions.execute(status -> {
                StoredFileRecord file = store.completeFinalization(
                        reservation.session(), reservation.token(), objectMetadata, completedAt);
                tasks.schedule(new ScheduleAutomatedTaskCommand(
                        file.tenantId(), SCAN_TASK_TYPE, file.fileId().toString(),
                        file.fileId().toString(), file.checksumSha256(), 900,
                        completedAt, 3, metadata.correlationId()));
                audit.append(allowAudit(
                        principal, decision, "FILE_UPLOAD_FINALIZED", UPLOAD_CAPABILITY,
                        "StoredFile", file.fileId().toString(), requestDigest,
                        metadata.correlationId(), completedAt));
                return file;
            });
            if (stored == null) {
                throw new IllegalStateException("Finalization transaction returned no file");
            }
            return stored.toView();
        } catch (RuntimeException exception) {
            // 完成事务整体回滚后释放抢占，客户端可用同一命令安全重试。
            transactions.executeWithoutResult(status -> store.releaseFinalization(
                    principal.tenantId(), uploadSessionId, reservation.token(),
                    "FINALIZATION_TRANSACTION_FAILED", clock.instant()));
            throw exception;
        }
    }

    @Override
    public DownloadAuthorizationView authorizeDownload(
            CurrentPrincipal principal,
            String correlationId,
            UUID fileId,
            AuthorizeDownloadCommand rawCommand
    ) {
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(fileId, "fileId must not be null");
        String effectiveCorrelationId = requireText(correlationId, "correlationId", 128);
        AuthorizeDownloadCommand command = validate(rawCommand);
        AuthorizationDecision decision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        DOWNLOAD_CAPABILITY, principal.tenantId(), "StoredFile", fileId.toString()),
                effectiveCorrelationId);
        StoredFileRecord file = store.findFile(principal.tenantId(), fileId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "File was not found"));
        if (!"AVAILABLE".equals(file.lifecycleStatus())) {
            throw new BusinessProblem(ProblemCode.FILE_NOT_AVAILABLE,
                    "File has not passed content scanning");
        }

        Instant now = clock.instant();
        ObjectTransferAuthorization transfer = storage.authorizeDownload(
                file.objectKey(), file.detectedMimeType(), downloadAuthorizationLifetime);
        UUID authorizationId = UUID.randomUUID();
        String requestDigest = Sha256.digest(fileId + "\n" + command.purpose());
        transactions.executeWithoutResult(status -> {
            store.insertDownloadAuthorization(
                    authorizationId, file, principal.principalId(), command.purpose(),
                    effectiveCorrelationId, now, transfer.expiresAt());
            audit.append(allowAudit(
                    principal, decision, "FILE_DOWNLOAD_AUTHORIZED", DOWNLOAD_CAPABILITY,
                    "StoredFile", fileId.toString(), requestDigest, effectiveCorrelationId, now));
        });
        return new DownloadAuthorizationView(
                authorizationId, fileId, transfer.method(), transfer.url(),
                transfer.requiredHeaders(), transfer.expiresAt());
    }

    private void validateObject(UploadSessionRecord session, ObjectMetadata metadata) {
        if (metadata.size() != session.expectedSize()
                || !metadata.checksumSha256().equals(session.expectedSha256())
                || !mimeCompatible(session.declaredMimeType(), metadata.detectedMimeType())) {
            throw new BusinessProblem(ProblemCode.FILE_OBJECT_MISMATCH,
                    "Uploaded object does not match the declared size, MIME type, or checksum");
        }
    }

    private static BeginUploadCommand validate(BeginUploadCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String contextType = requireToken(command.businessContextType(), "businessContextType", 80);
        String contextId = requireText(command.businessContextId(), "businessContextId", 160);
        String fileName = requireText(command.originalFileName(), "originalFileName", 255);
        if (fileName.contains("/") || fileName.contains("\\")
                || fileName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("originalFileName must be a plain file name");
        }
        String mime = normalizeMime(command.declaredMimeType());
        String sha256 = normalizeSha256(command.expectedSha256(), "expectedSha256");
        if (command.expectedSize() <= 0) {
            throw new IllegalArgumentException("expectedSize must be positive");
        }
        return new BeginUploadCommand(
                contextType, contextId, fileName, mime, command.expectedSize(), sha256);
    }


    @Override
    @Transactional
    public StoredFileView invalidate(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            InvalidateStoredFileCommand rawCommand
    ) {
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(rawCommand, "command must not be null");
        UUID fileId = Objects.requireNonNull(rawCommand.fileId(), "fileId must not be null");
        String reasonCode = requireText(rawCommand.reasonCode(), "reasonCode", 80);
        String sourceType = requireText(rawCommand.sourceType(), "sourceType", 80);
        String sourceId = requireText(rawCommand.sourceId(), "sourceId", 128);
        AuthorizationDecision decision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        INVALIDATE_CAPABILITY, principal.tenantId(), "StoredFile", fileId.toString()),
                metadata.correlationId());
        StoredFileRecord file = store.findFile(principal.tenantId(), fileId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "File was not found"));
        String requestDigest = Sha256.digest(
                fileId + "|" + reasonCode + "|" + sourceType + "|" + sourceId);
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision idempotencyDecision = idempotency.begin(context, INVALIDATE_OPERATION, requestDigest);
        if (idempotencyDecision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return store.findFile(principal.tenantId(), fileId)
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "Invalidate file replay result missing"))
                    .toView();
        }
        if ("INVALIDATED".equals(file.lifecycleStatus())) {
            idempotency.complete(context, INVALIDATE_OPERATION, fileId.toString(), requestDigest);
            return file.toView();
        }
        if (!"AVAILABLE".equals(file.lifecycleStatus())) {
            throw new BusinessProblem(ProblemCode.FILE_NOT_AVAILABLE,
                    "Only AVAILABLE StoredFile can be invalidated");
        }
        Instant now = clock.instant();
        int updated = store.invalidateFile(principal.tenantId(), fileId, "AVAILABLE", now);
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.FILE_NOT_AVAILABLE,
                    "Only AVAILABLE StoredFile can be invalidated");
        }
        StoredFileRecord invalidated = store.findFile(principal.tenantId(), fileId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.INTERNAL_ERROR, "Invalidated file missing"));
        String payload = json(new FileInvalidatedPayload(
                fileId, reasonCode, sourceType, sourceId, principal.principalId(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "files", "file.invalidated", 1,
                "StoredFile", fileId.toString(), invalidated.version(),
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                fileId.toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "FILE_INVALIDATED", INVALIDATE_CAPABILITY, "StoredFile", fileId.toString(),
                "ALLOW", decision.matchedGrantIds(), decision.policyVersion(), "INVALIDATED", null,
                requestDigest, metadata.correlationId(), now));
        idempotency.complete(context, INVALIDATE_OPERATION, fileId.toString(), requestDigest);
        return invalidated.toView();
    }

    private BeginUploadCommand validateWithMaximum(BeginUploadCommand command) {
        BeginUploadCommand validated = validate(command);
        if (validated.expectedSize() > maximumFileSize) {
            throw new IllegalArgumentException("expectedSize exceeds configured maximum");
        }
        return validated;
    }

    private static FinalizeUploadCommand validate(FinalizeUploadCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return new FinalizeUploadCommand(
                normalizeSha256(command.actualSha256(), "actualSha256"),
                requireText(command.finalizeCommandId(), "finalizeCommandId", 160));
    }

    private static AuthorizeDownloadCommand validate(AuthorizeDownloadCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return new AuthorizeDownloadCommand(requireText(command.purpose(), "purpose", 500));
    }

    private static AuditEntry allowAudit(
            CurrentPrincipal principal,
            AuthorizationDecision decision,
            String action,
            String capability,
            String targetType,
            String targetId,
            String requestDigest,
            String correlationId,
            Instant occurredAt
    ) {
        return new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(), action, capability,
                targetType, targetId, "ALLOW", decision.matchedGrantIds(), decision.policyVersion(),
                "SUCCEEDED", null, requestDigest, correlationId, occurredAt);
    }

    private static String beginDigest(BeginUploadCommand command) {
        return Sha256.digest(String.join("\n",
                command.businessContextType(), command.businessContextId(), command.originalFileName(),
                command.declaredMimeType(), Long.toString(command.expectedSize()), command.expectedSha256()));
    }

    private static boolean mimeCompatible(String declared, String detected) {
        return declared.equals(detected) || "application/octet-stream".equals(declared);
    }

    private static String normalizeMime(String value) {
        String normalized = requireText(value, "declaredMimeType", 255)
                .split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        if (!MIME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("declaredMimeType is invalid");
        }
        return normalized;
    }

    private static String normalizeSha256(String value, String field) {
        String normalized = requireText(value, field, 64).toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a SHA-256 hex digest");
        }
        return normalized;
    }

    private static String requireToken(String value, String field, int maximumLength) {
        String normalized = requireText(value, field, maximumLength);
        if (!normalized.matches("[A-Za-z][A-Za-z0-9_.-]*")) {
            throw new IllegalArgumentException(field + " contains invalid characters");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " exceeds maximum length");
        }
        return normalized;
    }

    private static long positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("File invalidate payload cannot be serialized", exception);
        }
    }

    private record FileInvalidatedPayload(
            UUID fileId,
            String reasonCode,
            String sourceType,
            String sourceId,
            String invalidatedBy,
            Instant invalidatedAt
    ) {
    }

}
