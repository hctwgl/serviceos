package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.evidence.api.BeginEvidenceUploadCommand;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.evidence.api.FinalizeEvidenceUploadCommand;
import com.serviceos.files.api.BeginUploadCommand;
import com.serviceos.files.api.FileCommandService;
import com.serviceos.files.api.FinalizeUploadCommand;
import com.serviceos.files.api.StoredFileView;
import com.serviceos.files.api.UploadSessionView;
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
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Evidence 编排安全文件 Begin/Finalize；文件事实仍由 files 权威维护。
 *
 * <p>对象存储 I/O 不占用资料事务。Begin 幂等由 files 的 Idempotency-Key 与上传绑定唯一约束共同保证；
 * Finalize 以 finalizeCommandId 作为资料侧幂等键，并在文件已提交但资料写入失败时允许补齐。</p>
 */
@Service
final class DefaultEvidenceCommandService implements EvidenceCommandService {
    private static final String SUBMIT = "evidence.submit";
    private static final String READ = "evidence.read";
    private static final String FINALIZE_OPERATION = "evidence.upload.finalize";
    private static final String BUSINESS_CONTEXT = "EvidenceSlot";

    private final EvidenceItemRepository repository;
    private final EvidenceSlotRepository slots;
    private final TaskFulfillmentContextService tasks;
    private final FileCommandService files;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultEvidenceCommandService(
            EvidenceItemRepository repository,
            EvidenceSlotRepository slots,
            TaskFulfillmentContextService tasks,
            FileCommandService files,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            TransactionTemplate transactions,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = repository;
        this.slots = slots;
        this.tasks = tasks;
        this.files = files;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public EvidenceUploadSessionView beginUpload(
            CurrentPrincipal principal, CommandMetadata metadata, BeginEvidenceUploadCommand command
    ) {
        TaskFulfillmentContext task = requireTask(principal.tenantId(), command.taskId());
        validateExecutableTask(principal, task);
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(SUBMIT, principal.tenantId(), "EvidenceSlot",
                        command.slotId().toString(), task.projectId().toString()),
                metadata.correlationId());
        EvidenceSlotView slot = requireSlot(principal.tenantId(), command.taskId(), command.slotId());
        Instant now = clock.instant();
        String captureMetadata = CaptureMetadataValidator.normalize(
                objectMapper, readTree(command.captureMetadataJson()), now, principal.principalId());
        if (command.evidenceItemId() != null) {
            EvidenceItemView item = repository.findItem(principal.tenantId(), command.evidenceItemId())
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.RESOURCE_NOT_FOUND, "EvidenceItem does not exist"));
            if (!item.evidenceSlotId().equals(slot.slotId()) || !item.taskId().equals(task.taskId())) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "EvidenceItem does not belong to the EvidenceSlot");
            }
        } else {
            softCheckMaxCount(principal.tenantId(), slot);
        }

        UploadSessionView session = files.beginUpload(principal, metadata, new BeginUploadCommand(
                BUSINESS_CONTEXT, command.slotId().toString(), command.originalFileName(),
                command.declaredMimeType(), command.expectedSize(),
                normalizeSha(command.expectedSha256())));

        EvidenceUploadBinding binding = transactions.execute(status -> {
            var existing = repository.findUploadBinding(principal.tenantId(), session.uploadSessionId());
            if (existing.isPresent()) {
                return existing.get();
            }
            EvidenceUploadBinding created = new EvidenceUploadBinding(
                    session.uploadSessionId(), principal.tenantId(), task.projectId(), task.taskId(),
                    slot.slotId(), session.fileId(), command.evidenceItemId(),
                    normalizeSha(command.expectedSha256()), command.declaredMimeType(),
                    command.expectedSize(), command.originalFileName(), captureMetadata,
                    "PENDING", principal.principalId(), now);
            repository.insertUploadBinding(created);
            audit.append(new AuditEntry(
                    UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                    "EVIDENCE_UPLOAD_BEGUN", SUBMIT, "EvidenceSlot", slot.slotId().toString(),
                    "ALLOW", auth.matchedGrantIds(), auth.policyVersion(), session.status(), null,
                    Sha256.digest(session.uploadSessionId() + "|" + captureMetadata),
                    metadata.correlationId(), now));
            return created;
        });
        return toSessionView(session, binding);
    }

    @Override
    public EvidenceItemView finalizeUpload(
            CurrentPrincipal principal, CommandMetadata metadata, FinalizeEvidenceUploadCommand command
    ) {
        TaskFulfillmentContext task = requireTask(principal.tenantId(), command.taskId());
        validateExecutableTask(principal, task);
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(SUBMIT, principal.tenantId(), "EvidenceSlot",
                        command.slotId().toString(), task.projectId().toString()),
                metadata.correlationId());
        EvidenceSlotView slot = requireSlot(principal.tenantId(), command.taskId(), command.slotId());
        EvidenceUploadBinding binding = repository.findUploadBinding(
                        principal.tenantId(), command.uploadSessionId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Evidence upload session does not exist"));
        if (!binding.slotId().equals(slot.slotId()) || !binding.taskId().equals(task.taskId())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Upload session does not belong to the EvidenceSlot");
        }
        if (!principal.principalId().equals(binding.createdBy())) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "Upload session belongs to another principal");
        }

        var already = repository.findRevisionByUploadSession(
                principal.tenantId(), command.uploadSessionId());
        if (already.isPresent()) {
            return repository.findItem(principal.tenantId(), already.get().evidenceItemId()).orElseThrow();
        }

        String digest = Sha256.digest(command.uploadSessionId() + "|"
                + normalizeSha(command.actualSha256()) + "|" + command.finalizeCommandId());
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), command.finalizeCommandId());

        StoredFileView stored = files.finalizeUpload(
                principal,
                new CommandMetadata(metadata.correlationId(), command.finalizeCommandId()),
                command.uploadSessionId(),
                new FinalizeUploadCommand(normalizeSha(command.actualSha256()), command.finalizeCommandId()));
        if (!stored.fileId().equals(binding.fileId())) {
            throw new BusinessProblem(ProblemCode.FILE_OBJECT_MISMATCH,
                    "Finalized file identity does not match upload session");
        }
        if (!normalizeSha(command.actualSha256()).equals(binding.expectedSha256())) {
            throw new BusinessProblem(ProblemCode.FILE_OBJECT_MISMATCH,
                    "Finalize checksum does not match evidence upload session");
        }

        return transactions.execute(status -> {
            IdempotencyDecision decision = idempotency.begin(context, FINALIZE_OPERATION, digest);
            if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
                return repository.findCommandResult(
                                principal.tenantId(), FINALIZE_OPERATION, command.finalizeCommandId())
                        .flatMap(id -> repository.findItem(principal.tenantId(), id))
                        .or(() -> repository.findRevisionByUploadSession(
                                        principal.tenantId(), command.uploadSessionId())
                                .flatMap(revision -> repository.findItem(
                                        principal.tenantId(), revision.evidenceItemId())))
                        .orElseThrow(() -> new BusinessProblem(
                                ProblemCode.INTERNAL_ERROR, "Finalize replay result missing"));
            }
            return persistFinalizedRevision(
                    principal, metadata, command, task, slot, binding, stored, auth, digest, context);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceItemView> listForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId
    ) {
        TaskFulfillmentContext task = requireTask(principal.tenantId(), taskId);
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "Task", taskId.toString(), task.projectId().toString()),
                correlationId);
        if (!slots.resolutionExists(principal.tenantId(), taskId)) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Task evidence slots have not completed reliable resolution");
        }
        return repository.listItems(principal.tenantId(), taskId);
    }

    @Override
    @Transactional(readOnly = true)
    public EvidenceItemView get(CurrentPrincipal principal, String correlationId, UUID evidenceItemId) {
        EvidenceItemView item = repository.findItem(principal.tenantId(), evidenceItemId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "EvidenceItem does not exist"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "EvidenceItem", evidenceItemId.toString(),
                item.projectId().toString()), correlationId);
        return item;
    }

    private EvidenceItemView persistFinalizedRevision(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            FinalizeEvidenceUploadCommand command,
            TaskFulfillmentContext task,
            EvidenceSlotView slot,
            EvidenceUploadBinding binding,
            StoredFileView stored,
            AuthorizationDecision auth,
            String digest,
            CommandContext context
    ) {
        var existing = repository.findRevisionByUploadSession(
                principal.tenantId(), command.uploadSessionId());
        if (existing.isPresent()) {
            EvidenceItemView item = repository.findItem(
                    principal.tenantId(), existing.get().evidenceItemId()).orElseThrow();
            repository.saveCommandResult(principal.tenantId(), FINALIZE_OPERATION,
                    command.finalizeCommandId(), item.evidenceItemId());
            idempotency.complete(context, FINALIZE_OPERATION, item.evidenceItemId().toString(),
                    Sha256.digest(item.evidenceItemId().toString()));
            return item;
        }

        EvidenceSlotView locked = repository.lockSlot(principal.tenantId(), slot.slotId());
        UUID itemId = binding.evidenceItemId();
        int revisionNumber;
        Instant now = clock.instant();
        if (itemId == null) {
            int itemCount = repository.countItems(principal.tenantId(), locked.slotId());
            if (locked.maxCount() != null && itemCount >= locked.maxCount()) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "EvidenceSlot maxCount would be exceeded");
            }
            itemId = UUID.randomUUID();
            int ordinal = repository.nextItemOrdinal(principal.tenantId(), locked.slotId());
            EvidenceItemView item = new EvidenceItemView(
                    itemId, task.taskId(), task.projectId(), locked.slotId(), ordinal, "OPEN",
                    principal.principalId(), now, List.of());
            repository.insertItem(principal.tenantId(), item);
            revisionNumber = 1;
        } else {
            EvidenceItemView item = repository.findItem(principal.tenantId(), itemId)
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.RESOURCE_NOT_FOUND, "EvidenceItem does not exist"));
            if (!item.evidenceSlotId().equals(locked.slotId())) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "EvidenceItem does not belong to the EvidenceSlot");
            }
            revisionNumber = repository.nextRevisionNumber(principal.tenantId(), itemId);
        }

        EvidenceRevisionView revision = new EvidenceRevisionView(
                UUID.randomUUID(), itemId, locked.slotId(), task.taskId(), task.projectId(),
                revisionNumber, stored.fileId(), stored.checksumSha256(),
                stored.detectedMimeType() == null ? stored.declaredMimeType() : stored.detectedMimeType(),
                stored.size(), binding.captureMetadataJson(), "STORED",
                binding.uploadSessionId(), command.finalizeCommandId(),
                principal.principalId(), now, List.of());
        repository.insertRevision(principal.tenantId(), revision);
        repository.markUploadFinalized(principal.tenantId(), binding.uploadSessionId());
        refreshSlotProjection(principal.tenantId(), locked);
        repository.saveCommandResult(principal.tenantId(), FINALIZE_OPERATION,
                command.finalizeCommandId(), itemId);

        String payload = serialize(new EvidenceRevisionCreatedPayload(
                revision.evidenceRevisionId(), itemId, locked.slotId(), task.taskId(), task.projectId(),
                revision.revisionNumber(), revision.fileObjectId(), revision.contentDigest(),
                revision.status(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.revision-created", 1,
                "EvidenceRevision", revision.evidenceRevisionId().toString(), revision.revisionNumber(),
                principal.tenantId(), metadata.correlationId(), command.finalizeCommandId(),
                task.taskId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "EVIDENCE_REVISION_CREATED", SUBMIT, "EvidenceRevision",
                revision.evidenceRevisionId().toString(), "ALLOW", auth.matchedGrantIds(),
                auth.policyVersion(), revision.status(), null, digest,
                metadata.correlationId(), now));
        idempotency.complete(context, FINALIZE_OPERATION, itemId.toString(),
                Sha256.digest(itemId + "|" + revision.evidenceRevisionId()));
        return repository.findItem(principal.tenantId(), itemId).orElseThrow();
    }

    private void softCheckMaxCount(String tenantId, EvidenceSlotView slot) {
        if (slot.maxCount() != null
                && repository.countItems(tenantId, slot.slotId()) >= slot.maxCount()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "EvidenceSlot maxCount would be exceeded");
        }
    }

    private void refreshSlotProjection(String tenantId, EvidenceSlotView slot) {
        int counting = repository.countCountingItems(tenantId, slot.slotId());
        String status = EvidenceSlotStatusProjector.project(slot.minCount(), slot.maxCount(), counting);
        repository.updateSlotStatus(tenantId, slot.slotId(), status);
    }

    private EvidenceSlotView requireSlot(String tenantId, UUID taskId, UUID slotId) {
        if (!slots.resolutionExists(tenantId, taskId)) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Task evidence slots have not completed reliable resolution");
        }
        return repository.findSlot(tenantId, taskId, slotId).orElseThrow(() ->
                new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "EvidenceSlot does not exist"));
    }

    private TaskFulfillmentContext requireTask(String tenantId, UUID taskId) {
        return tasks.find(tenantId, taskId).orElseThrow(() ->
                new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
    }

    private static void validateExecutableTask(CurrentPrincipal principal, TaskFulfillmentContext task) {
        if (!"HUMAN".equals(task.taskKind()) || !"RUNNING".equals(task.status())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Evidence upload requires a RUNNING HUMAN Task");
        }
        if (task.executionGuarded()) {
            throw new BusinessProblem(ProblemCode.TASK_EXECUTION_GUARDED,
                    "Evidence upload is disabled while a Task execution guard is ACTIVE");
        }
        if (!principal.principalId().equals(task.responsiblePrincipalId())) {
            throw new BusinessProblem(ProblemCode.TECHNICIAN_ASSIGNMENT_CHANGED,
                    "Task no longer belongs to this technician");
        }
    }

    private static EvidenceUploadSessionView toSessionView(
            UploadSessionView session, EvidenceUploadBinding binding
    ) {
        return new EvidenceUploadSessionView(
                session.uploadSessionId(), session.fileId(), binding.taskId(), binding.slotId(),
                binding.evidenceItemId(), session.status(), session.uploadMethod(), session.uploadUrl(),
                session.requiredHeaders(), session.uploadAuthorizationExpiresAt(),
                session.sessionExpiresAt());
    }

    private static String normalizeSha(String value) {
        if (value == null || !value.toLowerCase(Locale.ROOT).matches("^[0-9a-f]{64}$")) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "SHA-256 digest is invalid");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private tools.jackson.databind.JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "captureMetadata must be JSON");
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Evidence event serialization failed", exception);
        }
    }

    private record EvidenceRevisionCreatedPayload(
            UUID evidenceRevisionId,
            UUID evidenceItemId,
            UUID evidenceSlotId,
            UUID taskId,
            UUID projectId,
            int revisionNumber,
            UUID fileObjectId,
            String contentDigest,
            String status,
            Instant createdAt
    ) {
    }
}
