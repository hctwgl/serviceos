package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceValidationView;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.Sha256;
import com.serviceos.task.spi.AutomatedTaskHandler;
import com.serviceos.task.spi.TaskExecutionContext;
import com.serviceos.task.spi.TaskExecutionException;
import com.serviceos.task.spi.TaskExecutionResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 对 VALIDATING Revision 执行确定性机器校验，写入 EvidenceValidation 并推进生命周期。
 */
@Component
final class EvidenceMachineValidationTaskHandler implements AutomatedTaskHandler {
    private static final String SYSTEM_ACTOR = "system:evidence-machine-validation";

    private final EvidenceItemRepository repository;
    private final EvidenceMachineValidator validator;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    EvidenceMachineValidationTaskHandler(
            EvidenceItemRepository repository,
            ObjectMapper objectMapper,
            AuditAppender audit,
            OutboxAppender outbox,
            Clock clock
    ) {
        this.repository = repository;
        this.validator = new EvidenceMachineValidator(objectMapper);
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String taskType() {
        return EvidenceFileScanCompletedHandler.VALIDATION_TASK_TYPE;
    }

    @Override
    @Transactional
    public TaskExecutionResult execute(TaskExecutionContext context) throws TaskExecutionException {
        UUID revisionId;
        try {
            revisionId = UUID.fromString(context.payloadRef());
        } catch (IllegalArgumentException exception) {
            throw TaskExecutionException.finalFailure("EVIDENCE_VALIDATION_PAYLOAD_INVALID", exception);
        }

        EvidenceRevisionView revision = repository.findRevision(context.tenantId(), revisionId)
                .orElse(null);
        if (revision == null) {
            throw TaskExecutionException.finalFailure("EVIDENCE_REVISION_NOT_FOUND", null);
        }

        if ("VALIDATED".equals(revision.status()) || "VALIDATION_FAILED".equals(revision.status())) {
            return TaskExecutionResult.succeeded(revision.evidenceRevisionId() + ":" + revision.status());
        }
        if ("QUARANTINED".equals(revision.status()) || "INVALIDATED".equals(revision.status())) {
            return TaskExecutionResult.succeeded(revision.evidenceRevisionId() + ":" + revision.status());
        }
        if (!"VALIDATING".equals(revision.status())) {
            throw TaskExecutionException.retryable(
                    "EVIDENCE_REVISION_NOT_READY_FOR_VALIDATION",
                    clock.instant().plus(Duration.ofSeconds(15)), null);
        }

        Instant now = clock.instant();
        List<EvidenceValidationView> existing = repository.listValidations(
                context.tenantId(), revision.evidenceRevisionId());
        List<EvidenceValidationView> validations;
        if (existing.isEmpty()) {
            EvidenceSlotView slot = repository.findSlot(
                            context.tenantId(), revision.taskId(), revision.evidenceSlotId())
                    .orElse(null);
            if (slot == null) {
                throw TaskExecutionException.finalFailure("EVIDENCE_SLOT_NOT_FOUND", null);
            }
            boolean duplicate = repository.existsOtherCountingDigest(
                    context.tenantId(), revision.projectId(),
                    revision.contentDigest(), revision.evidenceRevisionId());
            validations = validator.evaluate(revision, slot, duplicate, now);
            for (EvidenceValidationView validation : validations) {
                repository.insertValidation(
                        context.tenantId(), revision.projectId(), revision.taskId(),
                        revision.evidenceSlotId(), revision.evidenceItemId(), validation);
            }
            // 冲突时读取权威已写入结果，保证重试幂等。
            validations = repository.listValidations(context.tenantId(), revision.evidenceRevisionId());
        } else {
            validations = existing;
        }

        String nextStatus = EvidenceMachineValidator.terminalStatus(validations);
        int updated = repository.updateRevisionStatus(
                context.tenantId(), revision.evidenceRevisionId(), "VALIDATING", nextStatus);
        if (updated == 0) {
            EvidenceRevisionView latest = repository.findRevision(context.tenantId(), revisionId)
                    .orElse(revision);
            return TaskExecutionResult.succeeded(latest.evidenceRevisionId() + ":" + latest.status());
        }

        EvidenceSlotView slot = repository.lockSlot(context.tenantId(), revision.evidenceSlotId());
        int counting = repository.countCountingItems(context.tenantId(), slot.slotId());
        repository.updateSlotStatus(
                context.tenantId(), slot.slotId(),
                EvidenceSlotStatusProjector.project(slot.minCount(), slot.maxCount(), counting));

        long blocked = validations.stream()
                .filter(v -> "BLOCK".equals(v.severity()) && "FAILED".equals(v.result()))
                .count();
        long warnings = validations.stream()
                .filter(v -> "WARN".equals(v.severity()) && !"PASSED".equals(v.result()))
                .count();

        String statePayload = write(new RevisionStateChangedPayload(
                revision.evidenceRevisionId(), revision.evidenceItemId(), revision.evidenceSlotId(),
                revision.taskId(), revision.projectId(), "VALIDATING", nextStatus,
                "AVAILABLE", now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence",
                "evidence.revision-validation-state-changed", 1,
                "EvidenceRevision", revision.evidenceRevisionId().toString(),
                revision.revisionNumber() + 1L, context.tenantId(), context.correlationId(),
                context.attemptId().toString(), revision.taskId().toString(),
                statePayload, Sha256.digest(statePayload), now));

        String completedPayload = write(new ValidationCompletedPayload(
                revision.evidenceRevisionId(), revision.evidenceItemId(), revision.evidenceSlotId(),
                revision.taskId(), revision.projectId(), nextStatus, blocked, warnings, now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence",
                "evidence.validation-completed", 1,
                "EvidenceRevision", revision.evidenceRevisionId().toString(),
                revision.revisionNumber() + 2L, context.tenantId(), context.correlationId(),
                context.attemptId().toString(), revision.taskId().toString(),
                completedPayload, Sha256.digest(completedPayload), now));

        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), SYSTEM_ACTOR,
                "EVIDENCE_MACHINE_VALIDATION_COMPLETED", null, "EvidenceRevision",
                revision.evidenceRevisionId().toString(), "SYSTEM", List.of(),
                EvidenceMachineValidator.VALIDATOR_VERSION, nextStatus, null,
                Sha256.digest(revision.evidenceRevisionId() + "|" + nextStatus + "|" + blocked),
                context.correlationId(), now));

        return TaskExecutionResult.succeeded(revision.evidenceRevisionId() + ":" + nextStatus);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Evidence validation event serialization failed", exception);
        }
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

    private record ValidationCompletedPayload(
            UUID evidenceRevisionId,
            UUID evidenceItemId,
            UUID evidenceSlotId,
            UUID taskId,
            UUID projectId,
            String status,
            long blockedFailureCount,
            long warningCount,
            Instant completedAt
    ) {
    }
}
