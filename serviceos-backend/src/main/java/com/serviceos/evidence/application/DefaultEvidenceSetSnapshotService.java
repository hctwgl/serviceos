package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.evidence.api.CreateEvidenceSetSnapshotCommand;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSetSnapshotMemberView;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceValidationView;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** EvidenceSetSnapshot 创建与查询；成员资格、审计、Outbox 与幂等同事务。 */
@Service
final class DefaultEvidenceSetSnapshotService implements EvidenceSetSnapshotService {
    private static final String SUBMIT = "evidence.submit";
    private static final String READ = "evidence.read";
    private static final String OPERATION = "evidence.snapshot.create";

    private final EvidenceSetSnapshotRepository snapshots;
    private final EvidenceItemRepository items;
    private final EvidenceSlotRepository slots;
    private final TaskFulfillmentContextService tasks;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final EvidenceSetSnapshotValidator validator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultEvidenceSetSnapshotService(
            EvidenceSetSnapshotRepository snapshots,
            EvidenceItemRepository items,
            EvidenceSlotRepository slots,
            TaskFulfillmentContextService tasks,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.snapshots = snapshots;
        this.items = items;
        this.slots = slots;
        this.tasks = tasks;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.validator = new EvidenceSetSnapshotValidator(objectMapper);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EvidenceSetSnapshotView create(
            CurrentPrincipal principal, CommandMetadata metadata, CreateEvidenceSetSnapshotCommand command
    ) {
        TaskFulfillmentContext task = requireTask(principal.tenantId(), command.taskId());
        validateExecutableTask(principal, task);
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(SUBMIT, principal.tenantId(), "Task",
                        task.taskId().toString(), task.projectId().toString()),
                metadata.correlationId());
        if (!slots.resolutionExists(principal.tenantId(), task.taskId())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Task evidence slots have not completed reliable resolution");
        }
        // 条件从 true 变为 false 且已有计数资料时，必须先人工决定保留或失效；Snapshot 不得越过该门禁。
        if (slots.hasPendingDisposition(principal.tenantId(), task.taskId())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Task 存在未处置的资料条件变化，不能创建资料快照");
        }

        List<EvidenceSlotView> taskSlots = slots.listSlots(principal.tenantId(), task.taskId());
        List<UUID> revisionIds = command.memberRevisionIds() == null
                ? List.of() : List.copyOf(command.memberRevisionIds());
        List<EvidenceRevisionView> loaded = items.findRevisionsByIds(
                principal.tenantId(), task.taskId(), revisionIds);
        EvidenceSetSnapshotValidator.ValidatedMembers validated = validator.validate(
                task.taskId(), command.purpose(), revisionIds, loaded, taskSlots);

        Instant now = clock.instant();
        List<EvidenceSetSnapshotMemberView> members = new ArrayList<>();
        int ordinal = 1;
        for (EvidenceRevisionView revision : validated.revisions()) {
            List<EvidenceValidationView> validations = items.listValidations(
                    principal.tenantId(), revision.evidenceRevisionId());
            members.add(new EvidenceSetSnapshotMemberView(
                    UUID.randomUUID(), revision.evidenceSlotId(), revision.evidenceItemId(),
                    revision.evidenceRevisionId(), revision.revisionNumber(), revision.status(),
                    revision.contentDigest(), validator.validationDigest(validations), ordinal++));
        }

        String contentDigest = validator.contentDigest(
                task.taskId(), validated.resolutionId(), command.purpose(), members);
        String requestDigest = Sha256.digest(task.taskId() + "|" + command.purpose() + "|" + contentDigest);
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision decision = idempotency.begin(context, OPERATION, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return snapshots.findCommandResult(context.tenantId(), OPERATION, context.idempotencyKey())
                    .flatMap(id -> snapshots.find(context.tenantId(), id))
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "Snapshot replay result missing"));
        }

        UUID snapshotId = UUID.randomUUID();
        EvidenceSetSnapshotView snapshot = new EvidenceSetSnapshotView(
                snapshotId, task.taskId(), task.projectId(), validated.resolutionId(),
                command.purpose(), members.size(), contentDigest,
                validator.eligibilitySummaryJson(command.purpose(), members.size()),
                principal.principalId(), now, List.copyOf(members));
        snapshots.insert(principal.tenantId(), snapshot);
        snapshots.saveCommandResult(principal.tenantId(), OPERATION, context.idempotencyKey(), snapshotId);

        String payload = serialize(new SnapshotCreatedPayload(
                snapshotId, task.taskId(), task.projectId(), command.purpose(),
                validated.resolutionId(), members.size(), contentDigest, now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence",
                "evidence.set-snapshotted", 1,
                "EvidenceSetSnapshot", snapshotId.toString(), 1L,
                principal.tenantId(), metadata.correlationId(),
                metadata.idempotencyKey(), task.taskId().toString(),
                payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "EVIDENCE_SET_SNAPSHOT_CREATED", SUBMIT, "EvidenceSetSnapshot",
                snapshotId.toString(), "ALLOW", auth.matchedGrantIds(),
                auth.policyVersion(), command.purpose(), null, requestDigest,
                metadata.correlationId(), now));
        idempotency.complete(context, OPERATION, snapshotId.toString(),
                Sha256.digest(serialize(snapshot)));
        return snapshot;
    }

    @Override
    @Transactional(readOnly = true)
    public EvidenceSetSnapshotView get(
            CurrentPrincipal principal, String correlationId, UUID snapshotId
    ) {
        EvidenceSetSnapshotView snapshot = snapshots.find(principal.tenantId(), snapshotId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "EvidenceSetSnapshot does not exist"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "EvidenceSetSnapshot", snapshotId.toString(),
                snapshot.projectId().toString()), correlationId);
        return snapshot;
    }

    private TaskFulfillmentContext requireTask(String tenantId, UUID taskId) {
        return tasks.find(tenantId, taskId).orElseThrow(() ->
                new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
    }

    private static void validateExecutableTask(CurrentPrincipal principal, TaskFulfillmentContext task) {
        if (!"HUMAN".equals(task.taskKind()) || !"RUNNING".equals(task.status())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "EvidenceSetSnapshot requires a RUNNING HUMAN Task");
        }
        if (task.executionGuarded()) {
            throw new BusinessProblem(ProblemCode.TASK_EXECUTION_GUARDED,
                    "EvidenceSetSnapshot is disabled while a Task execution guard is ACTIVE");
        }
        if (!principal.principalId().equals(task.responsiblePrincipalId())) {
            throw new BusinessProblem(ProblemCode.TECHNICIAN_ASSIGNMENT_CHANGED,
                    "Task no longer belongs to this technician");
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("EvidenceSetSnapshot event serialization failed", exception);
        }
    }

    private record SnapshotCreatedPayload(
            UUID evidenceSetSnapshotId,
            UUID taskId,
            UUID projectId,
            String purpose,
            UUID resolutionId,
            int memberCount,
            String contentDigest,
            Instant createdAt
    ) {
    }
}
