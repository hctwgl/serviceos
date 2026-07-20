package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.ActiveServiceResponsibility;
import com.serviceos.dispatch.api.ActiveServiceResponsibilityService;
import com.serviceos.evidence.api.CloseCorrectionCaseCommand;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.CorrectionResubmissionView;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.ResubmitCorrectionCaseCommand;
import com.serviceos.evidence.api.WaiveCorrectionCaseCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.task.api.CancelHandlingTaskCommand;
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.CompleteHandlingTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import com.serviceos.task.api.TaskResponsibilityQuery;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** CorrectionCase 打开、补传与关闭；补传轮次只追加。 */
@Service
final class DefaultCorrectionCaseService implements CorrectionCaseService {
    private static final String READ = "evidence.read";
    private static final String SUBMIT = "evidence.submit";
    private static final String REVIEW = "evidence.review";
    private static final String WAIVE = "evidence.waiveCorrection";
    private static final String RESUBMIT_OPERATION = "evidence.correction.resubmit";
    private static final String CLOSE_OPERATION = "evidence.correction.close";
    private static final String WAIVE_OPERATION = "evidence.correction.waive";
    private static final String CORRECTION_TASK_TYPE = "evidence.correction";

    private final CorrectionCaseRepository corrections;
    private final EvidenceSetSnapshotRepository snapshots;
    private final TaskSchedulingService tasks;
    private final TaskFulfillmentContextService taskContexts;
    private final TaskResponsibilityQuery responsibilities;
    private final ActiveServiceResponsibilityService serviceResponsibilities;
    private final ReviewCaseHandlingBootstrap reviewBootstrap;
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
            TaskFulfillmentContextService taskContexts,
            TaskResponsibilityQuery responsibilities,
            ActiveServiceResponsibilityService serviceResponsibilities,
            @Lazy ReviewCaseHandlingBootstrap reviewBootstrap,
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
        this.taskContexts = taskContexts;
        this.responsibilities = responsibilities;
        this.serviceResponsibilities = serviceResponsibilities;
        this.reviewBootstrap = reviewBootstrap;
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
                null, "OPEN", actorId, now, null, null, null, null, null, null, null, List.of());
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
        // 源业务 Task 在 M265 提交后已终态完成并撤销活动分派。整改任务必须回派给完成该
        // Task 的最后责任人，不能要求重开源 Task，也不能把失效分派恢复为活动权限。
        List<String> candidates = responsibilities.findCorrectionCandidateUser(tenantId, taskId)
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
                created.closedBy(), created.closedAt(),
                created.waivedBy(), created.waivedAt(), created.waiveApprovalRef(), created.waiveNote(),
                created.resubmissions());

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
        authorization.require(principal, capabilityRequest(
                READ, principal.tenantId(), "CorrectionCase", correctionCaseId.toString(),
                correction.projectId(), correction.taskId()), correlationId);
        return correction;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CorrectionCaseView> listForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId
    ) {
        TaskFulfillmentContext task = taskContexts.find(principal.tenantId(), taskId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        // PROJECT + NETWORK 并入请求，使 Network Portal NETWORK scope evidence.read 可匹配（M201）。
        authorization.require(principal, capabilityRequest(
                        READ, principal.tenantId(), "Task", taskId.toString(),
                        task.projectId(), taskId),
                correlationId);
        return corrections.listByTask(principal.tenantId(), taskId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findBySourceDecision(String tenantId, UUID reviewDecisionId) {
        return corrections.findBySourceDecision(tenantId, reviewDecisionId);
    }

    @Override
    @Transactional
    public CorrectionCaseView resubmit(
            CurrentPrincipal principal, CommandMetadata metadata, ResubmitCorrectionCaseCommand command
    ) {
        CorrectionCaseView current = corrections.find(principal.tenantId(), command.correctionCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "CorrectionCase does not exist"));
        // 同 Appointment：NETWORK scope evidence.submit 可满足 Portal 代补 resubmit（M201）。
        AuthorizationDecision auth = authorization.require(principal,
                capabilityRequest(SUBMIT, principal.tenantId(), "CorrectionCase",
                        current.correctionCaseId().toString(), current.projectId(), current.taskId()),
                metadata.correlationId());
        if ("CLOSED".equals(current.status()) || "WAIVED".equals(current.status())) {
            throw new BusinessProblem(ProblemCode.CORRECTION_CASE_STATE_CONFLICT,
                    "Terminal CorrectionCase cannot accept resubmission");
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
        if (current.correctionTaskId() != null) {
            CorrectionResubmissionView latest = current.resubmissions().stream()
                    .max(java.util.Comparator.comparingInt(CorrectionResubmissionView::resubmissionOrdinal))
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "RESUBMITTED CorrectionCase has no resubmission round"));
            // Case CLOSED 才是整改工作真正完成；多轮 RESUBMITTED 期间保持 handling Task 活跃。
            tasks.completeHandlingTask(new CompleteHandlingTaskCommand(
                    principal.tenantId(), current.correctionTaskId(), CORRECTION_TASK_TYPE,
                    current.correctionCaseId().toString(),
                    "correction-case://" + current.correctionCaseId(), latest.snapshotContentDigest(),
                    principal.principalId(), now, metadata.correlationId()));
        }
        // A4-R：权威 CLOSED 后为补传 Snapshot 打开新 INTERNAL ReviewCase + reviewTaskId；
        // 经 bootstrap 避免 Correction↔Review 循环依赖；失败关闭回滚本事务。
        if (current.latestResubmissionSnapshotId() != null) {
            reviewBootstrap.openInternalForSnapshot(
                    principal.tenantId(), principal.principalId(),
                    metadata.correlationId(), metadata.idempotencyKey(),
                    current.latestResubmissionSnapshotId(), null);
        }

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


    @Override
    @Transactional
    public CorrectionCaseView waive(
            CurrentPrincipal principal, CommandMetadata metadata, WaiveCorrectionCaseCommand command
    ) {
        CorrectionCaseView current = corrections.find(principal.tenantId(), command.correctionCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "CorrectionCase does not exist"));
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(WAIVE, principal.tenantId(), "CorrectionCase",
                        current.correctionCaseId().toString(), current.projectId().toString()),
                metadata.correlationId());
        String reason = normalizeRequiredText(command.reason(), "reason", 1000);
        String approvalRef = normalizeRequiredText(command.approvalRef(), "approvalRef", 160);
        String requestDigest = Sha256.digest(
                current.correctionCaseId() + "|WAIVE|" + reason + "|" + approvalRef);
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision decision = idempotency.begin(context, WAIVE_OPERATION, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return corrections.findCommandResult(context.tenantId(), WAIVE_OPERATION, context.idempotencyKey())
                    .flatMap(id -> corrections.find(context.tenantId(), id))
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "Correction waive replay result missing"));
        }
        if (!java.util.Set.of("OPEN", "IN_PROGRESS", "RESUBMITTED").contains(current.status())) {
            throw new BusinessProblem(ProblemCode.CORRECTION_CASE_STATE_CONFLICT,
                    "Only open CorrectionCase can be waived");
        }
        Instant now = clock.instant();
        UUID waiveEventId = UUID.randomUUID();
        int updated = corrections.markWaived(
                principal.tenantId(), current.correctionCaseId(), current.status(),
                principal.principalId(), now, approvalRef, reason);
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.CORRECTION_CASE_STATE_CONFLICT,
                    "CorrectionCase status changed concurrently");
        }
        if (current.correctionTaskId() != null) {
            tasks.cancelHandlingTask(new CancelHandlingTaskCommand(
                    principal.tenantId(), current.correctionTaskId(), CORRECTION_TASK_TYPE,
                    current.correctionCaseId().toString(), "CORRECTION_WAIVED",
                    waiveEventId, now, metadata.correlationId()));
        }
        corrections.saveCommandResult(
                principal.tenantId(), WAIVE_OPERATION, context.idempotencyKey(), current.correctionCaseId());

        String payload = serialize(new CorrectionWaivedPayload(
                current.correctionCaseId(), current.correctionTaskId(),
                current.taskId(), current.projectId(), principal.principalId(),
                approvalRef, reason, now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), waiveEventId, "evidence", "evidence.correction-waived", 1,
                "CorrectionCase", current.correctionCaseId().toString(), 1L,
                principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                current.taskId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "CORRECTION_CASE_WAIVED", WAIVE, "CorrectionCase",
                current.correctionCaseId().toString(),
                "ALLOW", auth.matchedGrantIds(), auth.policyVersion(), "WAIVED", null,
                requestDigest, metadata.correlationId(), now));
        CorrectionCaseView waived = corrections.find(principal.tenantId(), current.correctionCaseId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.INTERNAL_ERROR, "Waived CorrectionCase missing"));
        idempotency.complete(context, WAIVE_OPERATION, current.correctionCaseId().toString(),
                Sha256.digest(serialize(waived)));
        return waived;
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

    private static String normalizeRequiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    field + " exceeds " + maxLength + " characters");
        }
        return trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 同时携带 projectId 与 ACTIVE NETWORK 责任网点，使 PROJECT/NETWORK RoleGrant 均可匹配。
     */
    private AuthorizationRequest capabilityRequest(
            String capability,
            String tenantId,
            String resourceType,
            String resourceId,
            UUID projectId,
            UUID taskId
    ) {
        String networkId = serviceResponsibilities.find(tenantId, taskId)
                .map(ActiveServiceResponsibility::networkId)
                .orElse(null);
        return new AuthorizationRequest(
                capability, tenantId, resourceType, resourceId,
                projectId == null ? null : projectId.toString(), null, null, networkId);
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

    private record CorrectionWaivedPayload(
            UUID correctionCaseId,
            UUID correctionTaskId,
            UUID taskId,
            UUID projectId,
            String waivedBy,
            String approvalRef,
            String reason,
            Instant waivedAt
    ) {
    }
}
