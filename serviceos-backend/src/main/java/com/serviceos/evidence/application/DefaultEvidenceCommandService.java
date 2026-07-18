package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.evidence.api.BeginEvidenceUploadCommand;
import com.serviceos.evidence.api.BeginEvidenceUploadOnBehalfCommand;
import com.serviceos.evidence.api.BeginCorrectionEvidenceUploadCommand;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.evidence.api.FinalizeEvidenceUploadCommand;
import com.serviceos.evidence.api.FinalizeCorrectionEvidenceUploadCommand;
import com.serviceos.evidence.api.InvalidateEvidenceRevisionCommand;
import com.serviceos.files.api.BeginUploadCommand;
import com.serviceos.files.api.FileCommandService;
import com.serviceos.files.api.FinalizeUploadCommand;
import com.serviceos.files.api.InvalidateStoredFileCommand;
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
import tools.jackson.databind.node.ObjectNode;

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
    private static final String SUBMIT_ON_BEHALF = "evidence.submitOnBehalf";
    private static final String INVALIDATE = "evidence.invalidate";
    private static final String READ = "evidence.read";
    private static final String FINALIZE_OPERATION = "evidence.upload.finalize";
    private static final String INVALIDATE_OPERATION = "evidence.revision.invalidate";
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
    private final CorrectionTaskAccessValidator correctionAccess;

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
            Clock clock,
            CorrectionTaskAccessValidator correctionAccess
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
        this.correctionAccess = correctionAccess;
    }

    @Override
    public EvidenceUploadSessionView beginUpload(
            CurrentPrincipal principal, CommandMetadata metadata, BeginEvidenceUploadCommand command
    ) {
        TaskFulfillmentContext task = requireTask(principal.tenantId(), command.taskId());
        validateExecutableTask(principal, task);
        AuthorizationDecision auth = requireSubmitAuth(
                principal, metadata.correlationId(), SUBMIT, command.slotId(), task.projectId(), null);
        String captureMetadata = CaptureMetadataValidator.normalize(
                objectMapper, readTree(command.captureMetadataJson()), clock.instant(),
                principal.principalId());
        return beginUploadInternal(
                principal, metadata, task, command.slotId(), command.evidenceItemId(),
                command.originalFileName(), command.declaredMimeType(), command.expectedSize(),
                command.expectedSha256(), captureMetadata, auth, SUBMIT);
    }

    @Override
    public EvidenceUploadSessionView beginUploadOnBehalf(
            CurrentPrincipal principal, CommandMetadata metadata, BeginEvidenceUploadOnBehalfCommand command
    ) {
        TaskFulfillmentContext task = requireTask(principal.tenantId(), command.taskId());
        validateExecutableTaskOnBehalf(task);
        String onBehalfOf = requireText(command.onBehalfOf(), "onBehalfOf", 128);
        String onBehalfReason = requireText(command.onBehalfReason(), "onBehalfReason", 500);
        AuthorizationDecision auth = requireSubmitAuth(
                principal, metadata.correlationId(), SUBMIT_ON_BEHALF, command.slotId(),
                task.projectId(), command.networkId());
        String captureMetadata = CaptureMetadataValidator.normalize(
                objectMapper, readTree(command.captureMetadataJson()), clock.instant(),
                principal.principalId(), onBehalfOf, onBehalfReason);
        return beginUploadInternal(
                principal, metadata, task, command.slotId(), command.evidenceItemId(),
                command.originalFileName(), command.declaredMimeType(), command.expectedSize(),
                command.expectedSha256(), captureMetadata, auth, SUBMIT_ON_BEHALF);
    }

    @Override
    public EvidenceItemView finalizeUpload(
            CurrentPrincipal principal, CommandMetadata metadata, FinalizeEvidenceUploadCommand command
    ) {
        TaskFulfillmentContext task = requireTask(principal.tenantId(), command.taskId());
        validateExecutableTask(principal, task);
        AuthorizationDecision auth = requireSubmitAuth(
                principal, metadata.correlationId(), SUBMIT, command.slotId(), task.projectId(), null);
        return finalizeUploadInternal(principal, metadata, command, task, auth, SUBMIT);
    }

    @Override
    public EvidenceItemView finalizeUploadOnBehalf(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            FinalizeEvidenceUploadCommand command,
            UUID networkId
    ) {
        TaskFulfillmentContext task = requireTask(principal.tenantId(), command.taskId());
        validateExecutableTaskOnBehalf(task);
        AuthorizationDecision auth = requireSubmitAuth(
                principal, metadata.correlationId(), SUBMIT_ON_BEHALF, command.slotId(),
                task.projectId(), networkId);
        return finalizeUploadInternal(principal, metadata, command, task, auth, SUBMIT_ON_BEHALF);
    }

    @Override
    public EvidenceUploadSessionView beginCorrectionUpload(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            BeginCorrectionEvidenceUploadCommand command
    ) {
        CorrectionTaskAccessValidator.Access access = correctionAccess.requireWritable(
                principal, command.correctionCaseId(), command.correctionTaskId(), command.sourceTaskId());
        TaskFulfillmentContext sourceTask = access.sourceTask();
        AuthorizationDecision auth = requireSubmitAuth(
                principal, metadata.correlationId(), SUBMIT, command.slotId(), sourceTask.projectId(), null);
        ObjectNode capture = objectMapper.createObjectNode();
        capture.put("captureSource", command.captureSource());
        if (command.capturedAt() != null) {
            capture.put("capturedAt", command.capturedAt().toString());
        }
        capture.put("offlineFlag", false);
        String captureMetadata = CaptureMetadataValidator.normalize(
                objectMapper, capture, clock.instant(), principal.principalId());
        // 源业务 Task 已完成且不可重开；整改 handling Task 已由专用门禁证明当前责任与运行状态。
        return beginUploadInternal(
                principal, metadata, sourceTask, command.slotId(), command.evidenceItemId(),
                command.originalFileName(), command.declaredMimeType(), command.expectedSize(),
                command.expectedSha256(), captureMetadata, auth, SUBMIT);
    }

    @Override
    public EvidenceItemView finalizeCorrectionUpload(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            FinalizeCorrectionEvidenceUploadCommand command
    ) {
        CorrectionTaskAccessValidator.Access access = correctionAccess.requireWritable(
                principal, command.correctionCaseId(), command.correctionTaskId(), command.sourceTaskId());
        AuthorizationDecision auth = requireSubmitAuth(
                principal, metadata.correlationId(), SUBMIT, command.slotId(),
                access.sourceTask().projectId(), null);
        return finalizeUploadInternal(principal, metadata, new FinalizeEvidenceUploadCommand(
                        command.sourceTaskId(), command.slotId(), command.uploadSessionId(),
                        command.actualSha256(), command.finalizeCommandId()),
                access.sourceTask(), auth, SUBMIT);
    }

    private EvidenceUploadSessionView beginUploadInternal(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            TaskFulfillmentContext task,
            UUID slotId,
            UUID evidenceItemId,
            String originalFileName,
            String declaredMimeType,
            long expectedSize,
            String expectedSha256,
            String captureMetadata,
            AuthorizationDecision auth,
            String auditCapability
    ) {
        EvidenceSlotView slot = requireSlot(principal.tenantId(), task.taskId(), slotId);
        Instant now = clock.instant();
        if (evidenceItemId != null) {
            EvidenceItemView item = repository.findItem(principal.tenantId(), evidenceItemId)
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
                BUSINESS_CONTEXT, slotId.toString(), originalFileName,
                declaredMimeType, expectedSize, normalizeSha(expectedSha256)));

        EvidenceUploadBinding binding = transactions.execute(status -> {
            var existing = repository.findUploadBinding(principal.tenantId(), session.uploadSessionId());
            if (existing.isPresent()) {
                return existing.get();
            }
            EvidenceUploadBinding created = new EvidenceUploadBinding(
                    session.uploadSessionId(), principal.tenantId(), task.projectId(), task.taskId(),
                    slot.slotId(), session.fileId(), evidenceItemId,
                    normalizeSha(expectedSha256), declaredMimeType,
                    expectedSize, originalFileName, captureMetadata,
                    "PENDING", principal.principalId(), now);
            repository.insertUploadBinding(created);
            audit.append(new AuditEntry(
                    UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                    "EVIDENCE_UPLOAD_BEGUN", auditCapability, "EvidenceSlot", slot.slotId().toString(),
                    "ALLOW", auth.matchedGrantIds(), auth.policyVersion(), session.status(), null,
                    Sha256.digest(session.uploadSessionId() + "|" + captureMetadata),
                    metadata.correlationId(), now));
            return created;
        });
        return toSessionView(session, binding);
    }

    private EvidenceItemView finalizeUploadInternal(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            FinalizeEvidenceUploadCommand command,
            TaskFulfillmentContext task,
            AuthorizationDecision auth,
            String auditCapability
    ) {
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
                    principal, metadata, command, task, slot, binding, stored, auth, digest, context,
                    auditCapability);
        });
    }

    private static String requireText(String value, String name, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, name + " is invalid");
        }
        return value.trim();
    }

    private AuthorizationDecision requireSubmitAuth(
            CurrentPrincipal principal,
            String correlationId,
            String capability,
            UUID slotId,
            UUID projectId,
            UUID networkId
    ) {
        AuthorizationRequest request = networkId != null
                ? AuthorizationRequest.networkCapability(
                        capability, principal.tenantId(), "EvidenceSlot", slotId.toString(),
                        networkId.toString())
                : AuthorizationRequest.projectCapability(
                        capability, principal.tenantId(), "EvidenceSlot", slotId.toString(),
                        projectId.toString());
        return authorization.require(principal, request, correlationId);
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
            CommandContext context,
            String auditCapability
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
                principal.principalId(), now, List.of(), null, null, null, null);
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
                "EVIDENCE_REVISION_CREATED", auditCapability, "EvidenceRevision",
                revision.evidenceRevisionId().toString(), "ALLOW", auth.matchedGrantIds(),
                auth.policyVersion(), revision.status(), null, digest,
                metadata.correlationId(), now));
        idempotency.complete(context, FINALIZE_OPERATION, itemId.toString(),
                Sha256.digest(itemId + "|" + revision.evidenceRevisionId()));
        return repository.findItem(principal.tenantId(), itemId).orElseThrow();
    }

    @Override
    @Transactional
    public EvidenceRevisionView invalidate(
            CurrentPrincipal principal, CommandMetadata metadata, InvalidateEvidenceRevisionCommand command
    ) {
        EvidenceRevisionView revision = repository.findRevision(
                        principal.tenantId(), command.evidenceRevisionId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "EvidenceRevision does not exist"));
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(INVALIDATE, principal.tenantId(), "EvidenceRevision",
                        revision.evidenceRevisionId().toString(), revision.projectId().toString()),
                metadata.correlationId());
        String reasonCode = requireReasonCode(command.reasonCode());
        String approvalRef = normalizeApprovalRef(command.approvalRef());
        String requestDigest = Sha256.digest(
                revision.evidenceRevisionId() + "|" + reasonCode + "|" + nullToEmpty(approvalRef));
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision decision = idempotency.begin(context, INVALIDATE_OPERATION, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return repository.findCommandResult(
                            context.tenantId(), INVALIDATE_OPERATION, context.idempotencyKey())
                    .flatMap(id -> repository.findRevision(context.tenantId(), id))
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "Invalidate replay result missing"));
        }

        Instant now = clock.instant();
        int updated = repository.invalidateRevision(
                principal.tenantId(), revision.evidenceRevisionId(), reasonCode, approvalRef,
                principal.principalId(), now);
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.EVIDENCE_REVISION_NOT_INVALIDATABLE,
                    "Only VALIDATED EvidenceRevision can be invalidated");
        }
        files.invalidate(principal, metadata, new InvalidateStoredFileCommand(
                revision.fileObjectId(), reasonCode, "EvidenceRevision",
                revision.evidenceRevisionId().toString()));

        // 历史停用槽位仍允许通过显式作废链路处理；只有仍属最新活动集合时才刷新活动投影。
        repository.findSlot(principal.tenantId(), revision.taskId(), revision.evidenceSlotId())
                .ifPresent(active -> refreshSlotProjection(
                        principal.tenantId(), repository.lockSlot(principal.tenantId(), active.slotId())));
        repository.saveCommandResult(principal.tenantId(), INVALIDATE_OPERATION,
                context.idempotencyKey(), revision.evidenceRevisionId());

        String payload = serialize(new EvidenceRevisionInvalidatedPayload(
                revision.evidenceRevisionId(), revision.evidenceItemId(), revision.evidenceSlotId(),
                revision.taskId(), revision.projectId(), "VALIDATED", "INVALIDATED",
                reasonCode, approvalRef, principal.principalId(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.revision-invalidated", 1,
                "EvidenceRevision", revision.evidenceRevisionId().toString(),
                revision.revisionNumber() + 1L,
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                revision.taskId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "EVIDENCE_REVISION_INVALIDATED", INVALIDATE, "EvidenceRevision",
                revision.evidenceRevisionId().toString(), "ALLOW", auth.matchedGrantIds(),
                auth.policyVersion(), reasonCode, null, requestDigest,
                metadata.correlationId(), now));
        EvidenceRevisionView invalidated = repository.findRevision(
                        principal.tenantId(), revision.evidenceRevisionId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.INTERNAL_ERROR, "Invalidated EvidenceRevision missing"));
        idempotency.complete(context, INVALIDATE_OPERATION, revision.evidenceRevisionId().toString(),
                Sha256.digest(serialize(invalidated)));
        return invalidated;
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
        validateExecutableTaskBase(task);
        if (!principal.principalId().equals(task.responsiblePrincipalId())) {
            throw new BusinessProblem(ProblemCode.TECHNICIAN_ASSIGNMENT_CHANGED,
                    "Task no longer belongs to this technician");
        }
    }

    /**
     * M201 代补：保留 RUNNING HUMAN / 未 Guard，但不要求主体等于 responsiblePrincipalId。
     */
    private static void validateExecutableTaskOnBehalf(TaskFulfillmentContext task) {
        validateExecutableTaskBase(task);
    }

    private static void validateExecutableTaskBase(TaskFulfillmentContext task) {
        if (!"HUMAN".equals(task.taskKind()) || !"RUNNING".equals(task.status())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Evidence upload requires a RUNNING HUMAN Task");
        }
        if (task.executionGuarded()) {
            throw new BusinessProblem(ProblemCode.TASK_EXECUTION_GUARDED,
                    "Evidence upload is disabled while a Task execution guard is ACTIVE");
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

    private static String requireReasonCode(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank() || reasonCode.length() > 80) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "invalidation reasonCode is required and must be at most 80 characters");
        }
        return reasonCode.trim();
    }

    private static String normalizeApprovalRef(String approvalRef) {
        if (approvalRef == null || approvalRef.isBlank()) {
            return null;
        }
        String trimmed = approvalRef.trim();
        if (trimmed.length() > 160) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "invalidation approvalRef must be at most 160 characters");
        }
        return trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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

    private record EvidenceRevisionInvalidatedPayload(
            UUID evidenceRevisionId,
            UUID evidenceItemId,
            UUID evidenceSlotId,
            UUID taskId,
            UUID projectId,
            String previousStatus,
            String status,
            String reasonCode,
            String approvalRef,
            String invalidatedBy,
            Instant invalidatedAt
    ) {
    }
}
