package com.serviceos.files.application;

import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.files.api.BeginUploadCommand;
import com.serviceos.files.api.FinalizeUploadCommand;
import com.serviceos.files.spi.ObjectMetadata;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.files.spi.ObjectTransferAuthorization;
import com.serviceos.files.spi.ScanOutcome;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.ScheduleAutomatedTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskSchedulingService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultFileCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-13T06:00:00Z");
    private static final String SHA = "a".repeat(64);

    @Test
    void beginAndFinalizeDeriveIdentityCreateQuarantinedFileAndScheduleOneScan() {
        FakeStore store = new FakeStore();
        FakeStorage storage = new FakeStorage(new ObjectMetadata(8, SHA, "image/png"));
        CapturingTasks tasks = new CapturingTasks();
        List<AuditEntry> audits = new ArrayList<>();
        CapturingIdempotency idempotency = new CapturingIdempotency();
        DefaultFileCommandService service = service(store, storage, tasks, audits, idempotency);

        var session = service.beginUpload(
                principal(), metadata("begin"),
                new BeginUploadCommand("Task", "task-1", "nameplate.png", "image/png", 8, SHA));
        var file = service.finalizeUpload(
                principal(), metadata("finalize"), session.uploadSessionId(),
                new FinalizeUploadCommand(SHA, "device-command-1"));

        assertThat(store.session.tenantId()).isEqualTo("tenant-trusted");
        assertThat(store.session.createdBy()).isEqualTo("actor-trusted");
        assertThat(store.session.objectKey()).doesNotContain("nameplate", "task-1", "actor-trusted");
        assertThat(file.lifecycleStatus()).isEqualTo("QUARANTINED");
        assertThat(file.quarantineReason()).isEqualTo("PENDING_SCAN");
        assertThat(tasks.scheduled).singleElement().satisfies(task -> {
            assertThat(task.taskType()).isEqualTo("file.content-scan");
            assertThat(task.businessKey()).isEqualTo(file.fileId().toString());
            assertThat(task.payloadDigest()).isEqualTo(SHA);
        });
        assertThat(audits).extracting(AuditEntry::action)
                .containsExactly("FILE_UPLOAD_SESSION_CREATED", "FILE_UPLOAD_FINALIZED");
        assertThat(idempotency.context.tenantId()).isEqualTo("tenant-trusted");
    }

    @Test
    void finalizeRejectsMagicMimeMismatchAndDoesNotScheduleScan() {
        FakeStore store = new FakeStore();
        FakeStorage storage = new FakeStorage(new ObjectMetadata(8, SHA, "application/pdf"));
        CapturingTasks tasks = new CapturingTasks();
        DefaultFileCommandService service = service(
                store, storage, tasks, new ArrayList<>(), new CapturingIdempotency());
        var session = service.beginUpload(
                principal(), metadata("mismatch-begin"),
                new BeginUploadCommand("Task", "task-2", "fake.png", "image/png", 8, SHA));

        assertThatThrownBy(() -> service.finalizeUpload(
                principal(), metadata("mismatch-finalize"), session.uploadSessionId(),
                new FinalizeUploadCommand(SHA, "device-command-2")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.FILE_OBJECT_MISMATCH));
        assertThat(store.failed).isTrue();
        assertThat(store.file).isNull();
        assertThat(tasks.scheduled).isEmpty();
    }

    @Test
    void beginRejectsConfiguredOversizeBeforeCreatingSessionOrStorageGrant() {
        FakeStore store = new FakeStore();
        FakeStorage storage = new FakeStorage(new ObjectMetadata(8, SHA, "image/png"));
        DefaultFileCommandService service = service(
                store, storage, new CapturingTasks(), new ArrayList<>(), new CapturingIdempotency());

        assertThatThrownBy(() -> service.beginUpload(
                principal(), metadata("oversize"),
                new BeginUploadCommand("Task", "task-3", "large.png", "image/png", 1025, SHA)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.session).isNull();
        assertThat(storage.uploadAuthorizations).isZero();
    }

    private static DefaultFileCommandService service(
            FakeStore store,
            FakeStorage storage,
            CapturingTasks tasks,
            List<AuditEntry> audits,
            CapturingIdempotency idempotency
    ) {
        return new DefaultFileCommandService(
                store, storage, allowAuthorization(), audits::add, idempotency, tasks,
                new TransactionTemplate(noOpTransactions()),
                Clock.fixed(NOW, ZoneOffset.UTC), 1024,
                Duration.ofHours(1), Duration.ofMinutes(15), Duration.ofMinutes(5), Duration.ofMinutes(2));
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(
                "actor-trusted", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "technician-app", Set.of("file.upload", "file.download"));
    }

    private static CommandMetadata metadata(String suffix) {
        return new CommandMetadata("corr-" + suffix, "idem-" + suffix);
    }

    private static AuthorizationService allowAuthorization() {
        AuthorizationDecision allowed = new AuthorizationDecision(
                AuthorizationDecision.Effect.ALLOW,
                List.of(), List.of("grant-file-1"), List.of("TENANT:tenant-trusted"),
                List.of(), "policy-file-v1");
        return new AuthorizationService() {
            @Override
            public AuthorizationDecision authorize(
                    CurrentPrincipal principal, AuthorizationRequest request, String correlationId
            ) {
                return allowed;
            }

            @Override
            public AuthorizationDecision require(
                    CurrentPrincipal principal, AuthorizationRequest request, String correlationId
            ) {
                return allowed;
            }
        };
    }

    private static PlatformTransactionManager noOpTransactions() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    private static final class CapturingIdempotency implements IdempotencyService {
        private CommandContext context;

        @Override
        public IdempotencyDecision begin(CommandContext context, String operationType, String requestDigest) {
            this.context = context;
            return IdempotencyDecision.newCommand();
        }

        @Override
        public void complete(
                CommandContext context, String operationType, String resourceId, String responseDigest
        ) {
            this.context = context;
        }
    }

    private static final class CapturingTasks implements TaskSchedulingService {
        private final List<ScheduleAutomatedTaskCommand> scheduled = new ArrayList<>();

        @Override
        public ScheduledTaskView schedule(ScheduleAutomatedTaskCommand command) {
            scheduled.add(command);
            return new ScheduledTaskView(
                    UUID.randomUUID(), command.tenantId(), command.taskType(), command.businessKey(),
                    "PENDING", command.nextRunAt(), 0, command.maxAttempts(), 1);
        }

        @Override
        public ScheduledTaskView createHandlingTask(CreateHandlingTaskCommand command) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeStorage implements ObjectStorageGateway {
        private final ObjectMetadata metadata;
        private int uploadAuthorizations;

        private FakeStorage(ObjectMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public ObjectTransferAuthorization authorizeUpload(
                String objectKey, long exactSize, String declaredMimeType, Duration lifetime
        ) {
            uploadAuthorizations++;
            return new ObjectTransferAuthorization(
                    "PUT", "https://storage.test/upload", Map.of(), NOW.plus(lifetime));
        }

        @Override
        public ObjectMetadata inspect(String objectKey) {
            return metadata;
        }

        @Override
        public InputStream openForScan(String objectKey) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public ObjectTransferAuthorization authorizeDownload(
                String objectKey, String responseMimeType, Duration lifetime
        ) {
            return new ObjectTransferAuthorization(
                    "GET", "https://storage.test/download", Map.of(), NOW.plus(lifetime));
        }
    }

    private static final class FakeStore implements FileLifecycleStore {
        private UploadSessionRecord session;
        private StoredFileRecord file;
        private boolean failed;

        @Override
        public void insertSession(UploadSessionRecord session) {
            this.session = session;
        }

        @Override
        public Optional<UploadSessionRecord> findSession(String tenantId, UUID sessionId) {
            return Optional.ofNullable(session)
                    .filter(value -> value.tenantId().equals(tenantId) && value.sessionId().equals(sessionId));
        }

        @Override
        public FinalizationReservation reserveFinalization(
                String tenantId, UUID sessionId, String actualSha256, String finalizeCommandId,
                String requestDigest, Instant now, Duration lease
        ) {
            UUID token = UUID.randomUUID();
            return FinalizationReservation.reserved(session, token);
        }

        @Override
        public StoredFileRecord completeFinalization(
                UploadSessionRecord session, UUID token, ObjectMetadata metadata, Instant now
        ) {
            file = new StoredFileRecord(
                    session.fileId(), session.tenantId(), session.sessionId(), session.objectKey(),
                    session.originalFileName(), metadata.checksumSha256(), metadata.size(),
                    session.declaredMimeType(), metadata.detectedMimeType(),
                    "QUARANTINED", "PENDING_SCAN", now, 1);
            return file;
        }

        @Override
        public void failFinalization(
                String tenantId, UUID sessionId, UUID token, String errorCode, Instant now
        ) {
            failed = true;
        }

        @Override
        public void releaseFinalization(
                String tenantId, UUID sessionId, UUID token, String errorCode, Instant now
        ) {
        }

        @Override
        public Optional<StoredFileRecord> findFile(String tenantId, UUID fileId) {
            return Optional.ofNullable(file)
                    .filter(value -> value.tenantId().equals(tenantId) && value.fileId().equals(fileId));
        }

        @Override
        public StoredFileRecord recordScanResult(
                String tenantId, UUID fileId, UUID taskId, UUID attemptId,
                ScanOutcome outcome, Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insertDownloadAuthorization(
                UUID authorizationId, StoredFileRecord file, String principalId,
                String purpose, String correlationId, Instant issuedAt, Instant expiresAt
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
