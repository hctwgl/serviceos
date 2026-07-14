package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.evidence.api.CloseCorrectionCaseCommand;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.CorrectionResubmissionView;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.ResubmitCorrectionCaseCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskResponsibilityQuery;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** CorrectionCase 打开、补传与关闭；补传轮次只追加。 */
@Service
final class DefaultCorrectionCaseService implements CorrectionCaseService {
    private static final String READ = "evidence.read";
    private static final String SUBMIT = "evidence.submit";
    private static final String REVIEW = "evidence.review";
    private static final String RESUBMIT_OPERATION = "evidence.correction.resubmit";
    private static final String CLOSE_OPERATION = "evidence.correction.close";
    private static final String CORRECTION_TASK_TYPE = "evidence.correction";

    private final CorrectionCaseRepository corrections;
    private final EvidenceSetSnapshotRepository snapshots;
    private final TaskSchedulingService tasks;
    private final TaskResponsibilityQuery responsibilities;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultCorrectionCaseService(
            CorrectionCaseRepository corrections,
            EvidenceSetSnapshotRepository snapshots,
            TaskSchedulingService tasks,
            TaskResponsibilityQuery responsibilities,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.corrections = corrections;
        this.snapshots = snapshots;
        this.tasks = tasks;
        this.responsibilities = responsibilities;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CorrectionCaseView openFromRejectedDecision(
            String tenantId,
            String actorId,
            String correlationId,
            String causationId,
            UUID projectId,
            UUID taskId,
            UUID reviewCaseId,
            UUID reviewDecisionId,
            UUID evidenceSetSnapshotId,
            String snapshotContentDigest,
            List<String> reasonCodes
    ) {
        Instant now = clock.instant();
        UUID correctionCaseId = UUID.randomUUID();
        CorrectionCaseView created = new CorrectionCaseView(
                correctionCaseId, projectId, taskId, reviewCaseId, reviewDecisionId,
                evidenceSetSnapshotId, snapshotContentDigest, List.copyOf(reasonCodes),
                null, "OPEN", actorId, now, null, null, null, List.of());
        try {
            corrections.insertCase(tenantId, created);
        } catch (DuplicateKeyException exception) {
            return corrections.findBySourceDecision(tenantId, reviewDecisionId)
                    .flatMap(id -> corrections.find(tenantId, id))
                    
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.CORRECTION_CASE_CONFLICT,
                            "CorrectionCase already exists for this ReviewDecision"));
        }

        String payloadDigest = Sha256.digest(
                correctionCaseId + "|" + reviewDecisionId + "|" + evidenceSetSnapshotId);
        List<String> candidates = responsibilities.findActiveResponsibleUser(tenantId, taskId)
                .map(List::of)
                .orElse(List.of());
        ScheduledTaskView correctionTask = tasks.createHandlingTask(new CreateHandlingTaskCommand(
                tenantId, CORRECTION_TASK_TYPE, correctionCaseId.toString(),
                "correction-case:" + correctionCaseId, payloadDigest,
                800, now, correlationId, candidates));
        int linked = corrections.linkCorrectionTask(
                tenantId, correctionCaseId, correctionTask.taskId());
        if (linked != 1) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Failed to link CorrectionCase to correction Task");
        }
        int progressed = corrections.markInProgress(tenantId, correctionCaseId, "OPEN");
        String status = progressed == 1 ? "IN_PROGRESS" : "OPEN";
        created = new CorrectionCaseView(
                created.correctionCaseId(), created.projectId(), created.taskId(),
                created.sourceReviewCaseId(), created.sourceReviewDecisionId(),
                created.sourceEvidenceSetSnapshotId(), created.sourceSnapshotContentDigest(),
                created.reasonCodes(), correctionTask.taskId(), status,
                created.createdBy(), created.createdAt(), created.latestResubmissionSnapshotId(),
                created.closedBy(), created.closedAt(), created.resubmissions());

        String payload = serialize(new CorrectionCreatedPayload(
                correctionCaseId, reviewCaseId, reviewDecisionId, evidenceSetSnapshotId,
                taskId, projectId, correctionTask.taskId(), reasonCodes, now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.correction-case-created", 1,
                "CorrectionCase", correctionCaseId.toString(), 1L,
                tenantId, correlationId, causationId,
                taskId.toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), tenantId, actorId,
                "CORRECTION_CASE_CREATED", REVIEW, "CorrectionCase", correctionCaseId.toString(),
                "ALLOW", List.of(), "review-reject-v1", created.status(), null,
                Sha256.digest(reviewDecisionId + "|" + evidenceSetSnapshotId), correlationId, now));
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public CorrectionCaseView get(CurrentPrincipal principal, String correlationId, UUID correctionCaseId) {
        CorrectionCaseView correction = corrections.find(principal.tenantId(), correctionCaseId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "CorrectionCase does not exist"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "CorrectionCase", correctionCaseId.toString(),
                correction.projectId().toString()), correlationId);
        return correction;
    }

    @Override
    @Transactional
    public CorrectionCaseView resubmit(
            CurrentPrincipal principal, CommandMetadata metadata, ResubmitCorrectionCaseCommand command
    ) {
        CorrectionCaseView current = corrections.find(principal.tenantId(), command.correctionCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "CorrectionCase does not exist"));
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(SUBMIT, principal.tenantId(), "CorrectionCase",
                        current.correctionCaseId().toString(), current.projectId().toString()),
                metadata.correlationId());
        if ("CLOSED".equals(current.status())) {
            throw new BusinessProblem(ProblemCode.CORRECTION_CASE_STATE_CONFLICT,
                    "Closed CorrectionCase cannot accept resubmission");
        }
        EvidenceSetSnapshotView snapshot = snapshots.find(
                        principal.tenantId(), command.evidenceSetSnapshotId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "EvidenceSetSnapshot does not exist"));
        if (!"TASK_SUBMISSION".equals(snapshot.purpose())) {
            throw new BusinessProblem(ProblemCode.EVIDENCE_SNAPSHOT_PURPOSE_UNSUPPORTED,
                    "Correction resubmission only accepts TASK_SUBMISSION EvidenceSetSnapshot");
        }
        if (!snapshot.taskId().equals(current.taskId())
                || !snapshot.projectId().equals(current.projectId())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Resubmission snapshot must belong to the same task and project");
        }
        if (snapshot.evidenceSetSnapshotId().equals(current.sourceEvidenceSetSnapshotId())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Resubmission must reference a new EvidenceSetSnapshot");
        }

        String requestDigest = Sha256.digest(
                current.correctionCaseId() + "|" + snapshot.evidenceSetSnapshotId() + "|"
                        + snapshot.contentDigest());
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision decision = idempotency.begin(context, RESUBMIT_OPERATION, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return corrections.findCommandResult(context.tenantId(), RESUBMIT_OPERATION, context.idempotencyKey())
                    .flatMap(id -> corrections.find(context.tenantId(), id))
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "Correction resubmit replay result missing"));
        }

        Instant now = clock.instant();
        int updated = corrections.markResubmitted(
                principal.tenantId(), current.correctionCaseId(), current.status(),
                snapshot.evidenceSetSnapshotId(), now);
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.CORRECTION_CASE_STATE_CONFLICT,
                    "CorrectionCase status changed concurrently");
        }
        int ordinal = corrections.nextResubmissionOrdinal(principal.tenantId(), current.correctionCaseId());
        CorrectionResubmissionView round = new CorrectionResubmissionView(
                UUID.randomUUID(), current.correctionCaseId(), ordinal,
                snapshot.evidenceSetSnapshotId(), snapshot.contentDigest(),
                principal.principalId(), now);
        corrections.insertResubmission(principal.tenantId(), current.projectId(), round);
        corrections.saveCommandResult(
                principal.tenantId(), RESUBMIT_OPERATION, context.idempotencyKey(), current.correctionCaseId());

        String payload = serialize(new CorrectionResubmittedPayload(
                current.correctionCaseId(), round.correctionResubmissionId(),
                snapshot.evidenceSetSnapshotId(), snapshot.contentDigest(),
                current.taskId(), current.projectId(), ordinal, principal.principalId(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.correction-resubmitted", 1,
                "CorrectionCase", current.correctionCaseId().toString(), ordinal,
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                current.taskId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "CORRECTION_CASE_RESUBMITTED", SUBMIT, "CorrectionCase",
                current.correctionCaseId().toString(),
                "ALLOW", auth.matchedGrantIds(), auth.policyVersion(), "RESUBMITTED", null,
                requestDigest, metadata.correlationId(), now));
        CorrectionCaseView updatedView = corrections.find(principal.tenantId(), current.correctionCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.INTERNAL_ERROR, "Resubmitted CorrectionCase missing"));
        idempotency.complete(context, RESUBMIT_OPERATION, current.correctionCaseId().toString(),
                Sha256.digest(serialize(updatedView)));
        return updatedView;
    }

    @Override
    @Transactional
    public CorrectionCaseView close(
            CurrentPrincipal principal, CommandMetadata metadata, CloseCorrectionCaseCommand command
    ) {
        CorrectionCaseView current = corrections.find(principal.tenantId(), command.correctionCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "CorrectionCase does not exist"));
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(REVIEW, principal.tenantId(), "CorrectionCase",
                        current.correctionCaseId().toString(), current.projectId().toString()),
                metadata.correlationId());
        String note = normalizeNote(command.note());
        String requestDigest = Sha256.digest(current.correctionCaseId() + "|CLOSE|" + nullToEmpty(note));
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision decision = idempotency.begin(context, CLOSE_OPERATION, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return corrections.findCommandResult(context.tenantId(), CLOSE_OPERATION, context.idempotencyKey())
                    .flatMap(id -> corrections.find(context.tenantId(), id))
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "Correction close replay result missing"));
        }
        if (!"RESUBMITTED".equals(current.status())) {
            throw new BusinessProblem(ProblemCode.CORRECTION_CASE_STATE_CONFLICT,
                    "Only RESUBMITTED CorrectionCase can be closed");
        }
        Instant now = clock.instant();
        int updated = corrections.markClosed(
                principal.tenantId(), current.correctionCaseId(), "RESUBMITTED",
                principal.principalId(), now);
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.CORRECTION_CASE_STATE_CONFLICT,
                    "CorrectionCase status changed concurrently");
        }
        corrections.saveCommandResult(
                principal.tenantId(), CLOSE_OPERATION, context.idempotencyKey(), current.correctionCaseId());

        String payload = serialize(new CorrectionClosedPayload(
                current.correctionCaseId(), current.latestResubmissionSnapshotId(),
                current.taskId(), current.projectId(), principal.principalId(), note, now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.correction-closed", 1,
                "CorrectionCase", current.correctionCaseId().toString(),
                current.resubmissions().size(),
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                current.taskId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "CORRECTION_CASE_CLOSED", REVIEW, "CorrectionCase",
                current.correctionCaseId().toString(),
                "ALLOW", auth.matchedGrantIds(), auth.policyVersion(), "CLOSED", null,
                requestDigest, metadata.correlationId(), now));
        CorrectionCaseView closed = corrections.find(principal.tenantId(), current.correctionCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.INTERNAL_ERROR, "Closed CorrectionCase missing"));
        idempotency.complete(context, CLOSE_OPERATION, current.correctionCaseId().toString(),
                Sha256.digest(serialize(closed)));
        return closed;
    }

    private static String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > 1000) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "note exceeds 1000 characters");
        }
        return trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Correction event serialization failed", exception);
        }
    }

    private record CorrectionCreatedPayload(
            UUID correctionCaseId,
            UUID sourceReviewCaseId,
            UUID sourceReviewDecisionId,
            UUID sourceEvidenceSetSnapshotId,
            UUID taskId,
            UUID projectId,
            UUID correctionTaskId,
            List<String> reasonCodes,
            Instant createdAt
    ) {
    }

    private record CorrectionResubmittedPayload(
            UUID correctionCaseId,
            UUID correctionResubmissionId,
            UUID evidenceSetSnapshotId,
            String snapshotContentDigest,
            UUID taskId,
            UUID projectId,
            int resubmissionOrdinal,
            String submittedBy,
            Instant submittedAt
    ) {
    }

    private record CorrectionClosedPayload(
            UUID correctionCaseId,
            UUID latestResubmissionSnapshotId,
            UUID taskId,
            UUID projectId,
            String closedBy,
            String note,
            Instant closedAt
    ) {
    }
}
