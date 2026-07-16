package com.serviceos.readmodel.application;

import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.TaskCancelledPayload;
import com.serviceos.task.api.TaskClaimedPayload;
import com.serviceos.task.api.TaskCompletedPayload;
import com.serviceos.task.api.TaskCreatedPayload;
import com.serviceos.task.api.TaskReleasedPayload;
import com.serviceos.task.api.TaskStartedPayload;
import com.serviceos.task.api.TaskTimelineContext;
import com.serviceos.task.api.TaskTimelineContextQuery;
import com.serviceos.workflow.api.StageActivatedPayload;
import com.serviceos.workflow.api.StageCompletedPayload;
import com.serviceos.workflow.api.WorkflowCompletedPayload;
import com.serviceos.workflow.api.WorkflowStartedPayload;
import com.serviceos.workorder.api.WorkOrderActivatedPayload;
import com.serviceos.workorder.api.WorkOrderFulfilledPayload;
import com.serviceos.workorder.api.WorkOrderReceivedPayload;
import com.serviceos.workorder.api.WorkOrderScope;
import com.serviceos.workorder.api.WorkOrderScopeQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 把已发布的核心执行、现场履约、SLA 与资料审核事件规范化为工单时间线。
 * Inbox 与投影写入同事务，任何身份错配都会整体回滚；不保存 PII、自由文本或 GPS。
 */
@Service
final class WorkOrderCoreTimelineHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "readmodel.work-order-core-timeline.v1";
    private static final Set<String> V1_EVENTS = Set.of(
            "workorder.received", "workorder.activated", "workorder.fulfilled",
            "workflow.started", "workflow.completed",
            "stage.activated", "stage.completed",
            "task.created", "task.claimed", "task.started", "task.released",
            "task.cancelled", "task.completed",
            "contact.attempt.recorded",
            "appointment.proposed", "appointment.confirmed", "appointment.rescheduled",
            "appointment.cancelled", "appointment.no-show-marked",
            "visit.checked-in", "visit.checked-out", "visit.interrupted",
            "sla.started", "sla.breached", "sla.met",
            "form.submitted",
            "evidence.set-snapshotted",
            "evidence.review-case-created", "evidence.client-review-case-created",
            "evidence.review-decided", "evidence.review-case-reopened",
            "evidence.correction-case-created", "evidence.correction-resubmitted",
            "evidence.correction-closed", "evidence.correction-waived");

    private final InboxService inbox;
    private final WorkOrderTimelineRepository timelines;
    private final WorkOrderScopeQuery workOrderScopes;
    private final TaskTimelineContextQuery taskContexts;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    WorkOrderCoreTimelineHandler(
            InboxService inbox,
            WorkOrderTimelineRepository timelines,
            WorkOrderScopeQuery workOrderScopes,
            TaskTimelineContextQuery taskContexts,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.inbox = inbox;
        this.timelines = timelines;
        this.workOrderScopes = workOrderScopes;
        this.taskContexts = taskContexts;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return V1_EVENTS.contains(eventType)
                && (schemaVersion == 1
                || ("task.completed".equals(eventType) && schemaVersion == 2));
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        if (!supports(message.eventType(), message.schemaVersion())) {
            throw new IllegalArgumentException("unsupported work order timeline event");
        }
        InboxDecision decision = inbox.begin(
                message.tenantId(), CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }

        Optional<TimelineFact> normalized = normalize(message);
        if (normalized.isEmpty()) {
            // 非工单 Task 明确记为已消费但不产生工单投影，不能阻塞独立运营任务的事件发布。
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest("IGNORED_NO_WORK_ORDER|" + message.aggregateId()));
            return;
        }
        TimelineFact fact = normalized.orElseThrow();
        WorkOrderScope scope = workOrderScopes.find(message.tenantId(), fact.workOrderId())
                .orElseThrow(() -> new IllegalStateException("时间线事件引用的工单不存在"));
        if (fact.projectId() != null && !scope.projectId().equals(fact.projectId())) {
            throw new IllegalArgumentException("时间线事件 Project 与工单权威范围不一致");
        }
        if (!message.occurredAt().equals(fact.occurredAt())) {
            throw new IllegalArgumentException("时间线事件发生时间与载荷不一致");
        }

        timelines.append(new WorkOrderTimelineRepository.TimelineEntry(
                message.eventId(),
                message.tenantId(),
                scope.projectId(),
                fact.workOrderId(),
                message.eventId(),
                message.module(),
                message.eventType(),
                message.schemaVersion(),
                fact.category(),
                fact.resourceType(),
                fact.resourceId(),
                message.aggregateVersion(),
                fact.resourceCode(),
                fact.outcomeCode(),
                fact.actorId(),
                message.correlationId(),
                templateCode(message.eventType()),
                1,
                fact.occurredAt(),
                clock.instant()));
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(message.eventId() + "|" + fact.workOrderId()));
    }

    private Optional<TimelineFact> normalize(OutboxMessage message) {
        return switch (message.eventType()) {
            case "workorder.received" -> Optional.of(workOrderReceived(message));
            case "workorder.activated" -> Optional.of(workOrderActivated(message));
            case "workorder.fulfilled" -> Optional.of(workOrderFulfilled(message));
            case "workflow.started" -> Optional.of(workflowStarted(message));
            case "workflow.completed" -> Optional.of(workflowCompleted(message));
            case "stage.activated" -> Optional.of(stageActivated(message));
            case "stage.completed" -> Optional.of(stageCompleted(message));
            case "task.created" -> Optional.of(taskCreated(message));
            case "task.completed" -> Optional.of(taskCompleted(message));
            case "task.claimed" -> taskClaimed(message);
            case "task.started" -> taskStarted(message);
            case "task.released" -> taskReleased(message);
            case "task.cancelled" -> taskCancelled(message);
            case "contact.attempt.recorded" -> Optional.of(contactAttemptRecorded(message));
            case "appointment.proposed" -> Optional.of(appointmentProposed(message));
            case "appointment.confirmed" -> Optional.of(appointmentConfirmed(message));
            case "appointment.rescheduled" -> Optional.of(appointmentRescheduled(message));
            case "appointment.cancelled" -> Optional.of(appointmentCancelled(message));
            case "appointment.no-show-marked" -> Optional.of(appointmentNoShowMarked(message));
            case "visit.checked-in" -> Optional.of(visitCheckedIn(message));
            case "visit.checked-out" -> Optional.of(visitCheckedOut(message));
            case "visit.interrupted" -> Optional.of(visitInterrupted(message));
            case "sla.started" -> Optional.of(slaStarted(message));
            case "sla.breached" -> slaBreached(message);
            case "sla.met" -> slaMet(message);
            case "form.submitted" -> formSubmitted(message);
            case "evidence.set-snapshotted" -> evidenceSnapshotted(message);
            case "evidence.review-case-created" -> reviewCaseCreated(message);
            case "evidence.client-review-case-created" -> clientReviewCaseCreated(message);
            case "evidence.review-decided" -> reviewDecided(message);
            case "evidence.review-case-reopened" -> reviewCaseReopened(message);
            case "evidence.correction-case-created" -> correctionCaseCreated(message);
            case "evidence.correction-resubmitted" -> correctionResubmitted(message);
            case "evidence.correction-closed" -> correctionClosed(message);
            case "evidence.correction-waived" -> correctionWaived(message);
            default -> throw new IllegalArgumentException("unsupported work order timeline event");
        };
    }

    private TimelineFact workOrderReceived(OutboxMessage message) {
        WorkOrderReceivedPayload payload = read(message, WorkOrderReceivedPayload.class);
        requireEnvelope(message, "workorder", "WorkOrder", payload.workOrderId());
        return fact(payload.workOrderId(), payload.projectId(), "WORK_ORDER", "WorkOrder",
                payload.workOrderId(), null, "RECEIVED", null, payload.receivedAt());
    }

    private TimelineFact workOrderActivated(OutboxMessage message) {
        WorkOrderActivatedPayload payload = read(message, WorkOrderActivatedPayload.class);
        requireEnvelope(message, "workorder", "WorkOrder", payload.workOrderId());
        return fact(payload.workOrderId(), null, "WORK_ORDER", "WorkOrder",
                payload.workOrderId(), null, "ACTIVE", null, payload.activatedAt());
    }

    private TimelineFact workOrderFulfilled(OutboxMessage message) {
        WorkOrderFulfilledPayload payload = read(message, WorkOrderFulfilledPayload.class);
        requireEnvelope(message, "workorder", "WorkOrder", payload.workOrderId());
        return fact(payload.workOrderId(), null, "WORK_ORDER", "WorkOrder",
                payload.workOrderId(), null, "FULFILLED", null, payload.fulfilledAt());
    }

    private TimelineFact workflowStarted(OutboxMessage message) {
        WorkflowStartedPayload payload = read(message, WorkflowStartedPayload.class);
        requireEnvelope(message, "workflow", "Workflow", payload.workflowInstanceId());
        return fact(payload.workOrderId(), payload.projectId(), "WORKFLOW", "Workflow",
                payload.workflowInstanceId(), payload.workflowKey(), "STARTED", null, payload.startedAt());
    }

    private TimelineFact workflowCompleted(OutboxMessage message) {
        WorkflowCompletedPayload payload = read(message, WorkflowCompletedPayload.class);
        requireEnvelope(message, "workflow", "Workflow", payload.workflowInstanceId());
        return fact(payload.workOrderId(), payload.projectId(), "WORKFLOW", "Workflow",
                payload.workflowInstanceId(), null, "COMPLETED", null, payload.completedAt());
    }

    private TimelineFact stageActivated(OutboxMessage message) {
        StageActivatedPayload payload = read(message, StageActivatedPayload.class);
        requireEnvelope(message, "workflow", "Stage", payload.stageInstanceId());
        return fact(payload.workOrderId(), null, "STAGE", "Stage", payload.stageInstanceId(),
                payload.stageCode(), "ACTIVATED", null, payload.activatedAt());
    }

    private TimelineFact stageCompleted(OutboxMessage message) {
        StageCompletedPayload payload = read(message, StageCompletedPayload.class);
        requireEnvelope(message, "workflow", "Stage", payload.stageInstanceId());
        return fact(payload.workOrderId(), null, "STAGE", "Stage", payload.stageInstanceId(),
                payload.stageCode(), "COMPLETED", null, payload.completedAt());
    }

    private TimelineFact taskCreated(OutboxMessage message) {
        TaskCreatedPayload payload = read(message, TaskCreatedPayload.class);
        requireEnvelope(message, "task", "Task", payload.taskId());
        return fact(payload.workOrderId(), payload.projectId(), "TASK", "Task", payload.taskId(),
                payload.taskType(), payload.status(), null, payload.createdAt());
    }

    private TimelineFact taskCompleted(OutboxMessage message) {
        TaskCompletedPayload payload = read(message, TaskCompletedPayload.class);
        requireEnvelope(message, "task", "Task", payload.taskId());
        return fact(payload.workOrderId(), payload.projectId(), "TASK", "Task", payload.taskId(),
                payload.taskType(), "COMPLETED", null, payload.completedAt());
    }

    private Optional<TimelineFact> taskClaimed(OutboxMessage message) {
        TaskClaimedPayload payload = read(message, TaskClaimedPayload.class);
        return taskContextFact(message, payload.taskId(), "CLAIMED", null,
                payload.actorId(), payload.claimedAt());
    }

    private Optional<TimelineFact> taskStarted(OutboxMessage message) {
        TaskStartedPayload payload = read(message, TaskStartedPayload.class);
        return taskContextFact(message, payload.taskId(), "STARTED", null,
                payload.actorId(), payload.startedAt());
    }

    private Optional<TimelineFact> taskReleased(OutboxMessage message) {
        TaskReleasedPayload payload = read(message, TaskReleasedPayload.class);
        return taskContextFact(message, payload.taskId(), "RELEASED", payload.reasonCode(),
                payload.actorId(), payload.releasedAt());
    }

    private Optional<TimelineFact> taskCancelled(OutboxMessage message) {
        TaskCancelledPayload payload = read(message, TaskCancelledPayload.class);
        return taskContextFact(message, payload.taskId(), "CANCELLED", payload.reasonCode(),
                null, payload.cancelledAt());
    }

    private TimelineFact contactAttemptRecorded(OutboxMessage message) {
        ContactAttemptRecordedPayload payload = read(message, ContactAttemptRecordedPayload.class);
        requireEnvelope(message, "appointment", "ContactAttempt", payload.contactAttemptId());
        return fact(payload.workOrderId(), payload.projectId(), "CONTACT_ATTEMPT", "ContactAttempt",
                payload.contactAttemptId(), payload.channel(), payload.resultCode(),
                payload.actorId(), payload.occurredAt());
    }

    private TimelineFact appointmentProposed(OutboxMessage message) {
        AppointmentLifecyclePayload payload = read(message, AppointmentLifecyclePayload.class);
        requireEnvelope(message, "appointment", "Appointment", payload.appointmentId());
        return appointmentFact(payload, payload.status(), null);
    }

    private TimelineFact appointmentConfirmed(OutboxMessage message) {
        AppointmentLifecyclePayload payload = read(message, AppointmentLifecyclePayload.class);
        requireEnvelope(message, "appointment", "Appointment", payload.appointmentId());
        return appointmentFact(payload, payload.status(), null);
    }

    private TimelineFact appointmentRescheduled(OutboxMessage message) {
        AppointmentLifecyclePayload payload = read(message, AppointmentLifecyclePayload.class);
        requireEnvelope(message, "appointment", "Appointment", payload.appointmentId());
        // 改约后状态回到 PROPOSED，时间线用稳定 RESCHEDULED 区分“首次提出”与“改约后再提出”。
        return appointmentFact(payload, "RESCHEDULED", null);
    }

    private TimelineFact appointmentCancelled(OutboxMessage message) {
        AppointmentTerminalPayload payload = read(message, AppointmentTerminalPayload.class);
        requireEnvelope(message, "appointment", "Appointment", payload.appointmentId());
        return appointmentTerminalFact(payload);
    }

    private TimelineFact appointmentNoShowMarked(OutboxMessage message) {
        AppointmentTerminalPayload payload = read(message, AppointmentTerminalPayload.class);
        requireEnvelope(message, "appointment", "Appointment", payload.appointmentId());
        // noShowPartyRef / evidenceRefs 仅留在权威聚合与审计，时间线不得投影敏感引用。
        return appointmentTerminalFact(payload);
    }

    private TimelineFact visitCheckedIn(OutboxMessage message) {
        VisitLifecyclePayload payload = read(message, VisitLifecyclePayload.class);
        requireEnvelope(message, "fieldwork", "Visit", payload.visitId());
        return visitFact(payload, payload.status());
    }

    private TimelineFact visitCheckedOut(OutboxMessage message) {
        VisitLifecyclePayload payload = read(message, VisitLifecyclePayload.class);
        requireEnvelope(message, "fieldwork", "Visit", payload.visitId());
        return visitFact(payload, requireCode(payload.resultCode(), "visit checkout resultCode"));
    }

    private TimelineFact visitInterrupted(OutboxMessage message) {
        VisitLifecyclePayload payload = read(message, VisitLifecyclePayload.class);
        requireEnvelope(message, "fieldwork", "Visit", payload.visitId());
        return visitFact(payload, requireCode(payload.exceptionCode(), "visit interrupt exceptionCode"));
    }

    private TimelineFact slaStarted(OutboxMessage message) {
        SlaStartedPayload payload = read(message, SlaStartedPayload.class);
        requireEnvelope(message, "sla", "SlaInstance", payload.slaInstanceId());
        return fact(payload.workOrderId(), payload.projectId(), "SLA", "SlaInstance",
                payload.slaInstanceId(), payload.slaRef(), "STARTED", null, payload.startedAt());
    }

    private Optional<TimelineFact> slaBreached(OutboxMessage message) {
        SlaBreachedPayload payload = read(message, SlaBreachedPayload.class);
        // 违约检测时间是发布信封的权威 occurredAt；不把 deadline/digest 投影进用户时间线。
        return slaTaskFact(message, payload.slaInstanceId(), payload.taskId(), "BREACHED",
                payload.detectedAt());
    }

    private Optional<TimelineFact> slaMet(OutboxMessage message) {
        SlaMetPayload payload = read(message, SlaMetPayload.class);
        return slaTaskFact(message, payload.slaInstanceId(), payload.taskId(),
                requireCode(payload.status(), "sla.met status"), payload.completedAt());
    }

    private Optional<TimelineFact> formSubmitted(OutboxMessage message) {
        FormSubmittedPayload payload = read(message, FormSubmittedPayload.class);
        return taskScopedFact(
                message, "forms", "FormSubmission", payload.submissionId(), payload.taskId(),
                payload.projectId(), "FORM", "FormSubmission", payload.formKey(),
                requireCode(payload.validationStatus(), "form validationStatus"), null,
                payload.occurredAt());
    }

    private Optional<TimelineFact> evidenceSnapshotted(OutboxMessage message) {
        EvidenceSnapshottedPayload payload = read(message, EvidenceSnapshottedPayload.class);
        return taskScopedFact(
                message, "evidence", "EvidenceSetSnapshot", payload.evidenceSetSnapshotId(),
                payload.taskId(), payload.projectId(), "EVIDENCE", "EvidenceSetSnapshot", null,
                requireCode(payload.purpose(), "evidence snapshot purpose"), null,
                payload.createdAt());
    }

    private Optional<TimelineFact> reviewCaseCreated(OutboxMessage message) {
        ReviewCaseCreatedPayload payload = read(message, ReviewCaseCreatedPayload.class);
        return taskScopedFact(
                message, "evidence", "ReviewCase", payload.reviewCaseId(), payload.taskId(),
                payload.projectId(), "REVIEW", "ReviewCase", null, "CREATED", null,
                payload.createdAt());
    }

    private Optional<TimelineFact> clientReviewCaseCreated(OutboxMessage message) {
        ClientReviewCaseCreatedPayload payload = read(message, ClientReviewCaseCreatedPayload.class);
        return taskScopedFact(
                message, "evidence", "ReviewCase", payload.reviewCaseId(), payload.taskId(),
                payload.projectId(), "REVIEW", "ReviewCase", null, "CLIENT_CREATED", null,
                payload.createdAt());
    }

    private Optional<TimelineFact> reviewDecided(OutboxMessage message) {
        ReviewDecidedPayload payload = read(message, ReviewDecidedPayload.class);
        return taskScopedFact(
                message, "evidence", "ReviewCase", payload.reviewCaseId(), payload.taskId(),
                payload.projectId(), "REVIEW", "ReviewCase", null,
                requireCode(payload.decision(), "review decision"), payload.decidedBy(),
                payload.decidedAt());
    }

    private Optional<TimelineFact> reviewCaseReopened(OutboxMessage message) {
        ReviewCaseReopenedPayload payload = read(message, ReviewCaseReopenedPayload.class);
        // reason 正文不得进入时间线；只保留稳定 REOPENED outcome。
        return taskScopedFact(
                message, "evidence", "ReviewCase", payload.reviewCaseId(), payload.taskId(),
                payload.projectId(), "REVIEW", "ReviewCase", null, "REOPENED",
                payload.reopenedBy(), payload.reopenedAt());
    }

    private Optional<TimelineFact> correctionCaseCreated(OutboxMessage message) {
        CorrectionCaseCreatedPayload payload = read(message, CorrectionCaseCreatedPayload.class);
        return taskScopedFact(
                message, "evidence", "CorrectionCase", payload.correctionCaseId(), payload.taskId(),
                payload.projectId(), "CORRECTION", "CorrectionCase", null, "CREATED", null,
                payload.createdAt());
    }

    private Optional<TimelineFact> correctionResubmitted(OutboxMessage message) {
        CorrectionResubmittedPayload payload = read(message, CorrectionResubmittedPayload.class);
        return taskScopedFact(
                message, "evidence", "CorrectionCase", payload.correctionCaseId(), payload.taskId(),
                payload.projectId(), "CORRECTION", "CorrectionCase", null, "RESUBMITTED",
                payload.submittedBy(), payload.submittedAt());
    }

    private Optional<TimelineFact> correctionClosed(OutboxMessage message) {
        CorrectionClosedPayload payload = read(message, CorrectionClosedPayload.class);
        return taskScopedFact(
                message, "evidence", "CorrectionCase", payload.correctionCaseId(), payload.taskId(),
                payload.projectId(), "CORRECTION", "CorrectionCase", null, "CLOSED",
                payload.closedBy(), payload.closedAt());
    }

    private Optional<TimelineFact> correctionWaived(OutboxMessage message) {
        CorrectionWaivedPayload payload = read(message, CorrectionWaivedPayload.class);
        return taskScopedFact(
                message, "evidence", "CorrectionCase", payload.correctionCaseId(), payload.taskId(),
                payload.projectId(), "CORRECTION", "CorrectionCase", null, "WAIVED",
                payload.waivedBy(), payload.waivedAt());
    }

    private Optional<TimelineFact> taskScopedFact(
            OutboxMessage message,
            String module,
            String aggregateType,
            UUID resourceId,
            UUID taskId,
            UUID projectId,
            String category,
            String resourceType,
            String resourceCode,
            String outcomeCode,
            String actorId,
            Instant occurredAt
    ) {
        requireEnvelope(message, module, aggregateType, resourceId);
        TaskTimelineContext context = taskContexts.find(message.tenantId(), taskId)
                .orElseThrow(() -> new IllegalStateException("时间线事件引用的 Task 不存在"));
        if (context.workOrderId() == null) {
            return Optional.empty();
        }
        if (projectId != null && !projectId.equals(context.projectId())) {
            throw new IllegalArgumentException("时间线事件 Project 与 Task 权威范围不一致");
        }
        return Optional.of(fact(
                context.workOrderId(), context.projectId(), category, resourceType, resourceId,
                resourceCode, outcomeCode, actorId, occurredAt));
    }

    private Optional<TimelineFact> slaTaskFact(
            OutboxMessage message,
            UUID slaInstanceId,
            UUID taskId,
            String outcomeCode,
            Instant occurredAt
    ) {
        requireEnvelope(message, "sla", "SlaInstance", slaInstanceId);
        TaskTimelineContext context = taskContexts.find(message.tenantId(), taskId)
                .orElseThrow(() -> new IllegalStateException("时间线事件引用的 Task 不存在"));
        if (context.workOrderId() == null) {
            return Optional.empty();
        }
        return Optional.of(fact(
                context.workOrderId(), context.projectId(), "SLA", "SlaInstance", slaInstanceId,
                null, outcomeCode, null, occurredAt));
    }

    private static TimelineFact appointmentFact(
            AppointmentLifecyclePayload payload,
            String outcomeCode,
            String actorId
    ) {
        return fact(payload.workOrderId(), payload.projectId(), "APPOINTMENT", "Appointment",
                payload.appointmentId(), payload.appointmentType(), outcomeCode, actorId,
                payload.occurredAt());
    }

    private static TimelineFact appointmentTerminalFact(AppointmentTerminalPayload payload) {
        return fact(payload.workOrderId(), payload.projectId(), "APPOINTMENT", "Appointment",
                payload.appointmentId(), payload.appointmentType(),
                requireCode(payload.reasonCode(), "appointment reasonCode"), null,
                payload.occurredAt());
    }

    private static TimelineFact visitFact(VisitLifecyclePayload payload, String outcomeCode) {
        return fact(payload.workOrderId(), payload.projectId(), "VISIT", "Visit",
                payload.visitId(), null, outcomeCode, payload.technicianId(), payload.occurredAt());
    }

    private static String requireCode(String code, String fieldName) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("时间线事件缺少 " + fieldName);
        }
        return code;
    }

    private Optional<TimelineFact> taskContextFact(
            OutboxMessage message,
            UUID taskId,
            String outcomeCode,
            String reasonCode,
            String actorId,
            Instant occurredAt
    ) {
        requireEnvelope(message, "task", "Task", taskId);
        TaskTimelineContext context = taskContexts.find(message.tenantId(), taskId)
                .orElseThrow(() -> new IllegalStateException("时间线事件引用的 Task 不存在"));
        if (context.workOrderId() == null) {
            return Optional.empty();
        }
        return Optional.of(fact(
                context.workOrderId(), context.projectId(), "TASK", "Task", taskId,
                context.taskType(), reasonCode == null ? outcomeCode : reasonCode,
                actorId, occurredAt));
    }

    private static TimelineFact fact(
            UUID workOrderId,
            UUID projectId,
            String category,
            String resourceType,
            UUID resourceId,
            String resourceCode,
            String outcomeCode,
            String actorId,
            Instant occurredAt
    ) {
        if (workOrderId == null || resourceId == null || occurredAt == null) {
            throw new IllegalArgumentException("时间线事件缺少权威资源身份或发生时间");
        }
        return new TimelineFact(workOrderId, projectId, category, resourceType, resourceId,
                resourceCode, outcomeCode, actorId, occurredAt);
    }

    private <T> T read(OutboxMessage message, Class<T> type) {
        try {
            return objectMapper.readValue(message.payload(), type);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("时间线事件载荷无法按已发布 Schema 解码", exception);
        }
    }

    private static void requireEnvelope(
            OutboxMessage message,
            String module,
            String aggregateType,
            UUID resourceId
    ) {
        if (!module.equals(message.module())
                || !aggregateType.equals(message.aggregateType())
                || !resourceId.toString().equals(message.aggregateId())
                || message.aggregateVersion() < 1) {
            throw new IllegalArgumentException("时间线事件信封与资源身份不一致");
        }
    }

    private static String templateCode(String eventType) {
        return eventType.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    private record TimelineFact(
            UUID workOrderId,
            UUID projectId,
            String category,
            String resourceType,
            UUID resourceId,
            String resourceCode,
            String outcomeCode,
            String actorId,
            Instant occurredAt
    ) {
    }

    /**
     * 仅用于按已发布 Schema 解码 Outbox payload；字段保持最小，不含 partyRef / note。
     */
    private record ContactAttemptRecordedPayload(
            UUID contactAttemptId,
            UUID projectId,
            UUID workOrderId,
            UUID taskId,
            String channel,
            String resultCode,
            String actorId,
            Instant occurredAt
    ) {
    }

    private record AppointmentLifecyclePayload(
            UUID appointmentId,
            UUID projectId,
            UUID workOrderId,
            UUID taskId,
            String appointmentType,
            String status,
            Instant occurredAt
    ) {
    }

    private record AppointmentTerminalPayload(
            UUID appointmentId,
            UUID projectId,
            UUID workOrderId,
            UUID taskId,
            String appointmentType,
            String status,
            String reasonCode,
            Instant occurredAt
    ) {
    }

    private record VisitLifecyclePayload(
            UUID visitId,
            UUID projectId,
            UUID workOrderId,
            UUID taskId,
            String technicianId,
            String status,
            String resultCode,
            String exceptionCode,
            Instant occurredAt
    ) {
    }

    private record SlaStartedPayload(
            UUID slaInstanceId,
            UUID taskId,
            UUID projectId,
            UUID workOrderId,
            String slaRef,
            Instant startedAt
    ) {
    }

    private record SlaBreachedPayload(
            UUID slaInstanceId,
            UUID taskId,
            Instant detectedAt
    ) {
    }

    private record SlaMetPayload(
            UUID slaInstanceId,
            UUID taskId,
            String status,
            Instant completedAt
    ) {
    }

    private record FormSubmittedPayload(
            UUID submissionId,
            UUID taskId,
            UUID projectId,
            String formKey,
            String validationStatus,
            Instant occurredAt
    ) {
    }

    private record EvidenceSnapshottedPayload(
            UUID evidenceSetSnapshotId,
            UUID taskId,
            UUID projectId,
            String purpose,
            Instant createdAt
    ) {
    }

    private record ReviewCaseCreatedPayload(
            UUID reviewCaseId,
            UUID taskId,
            UUID projectId,
            Instant createdAt
    ) {
    }

    private record ClientReviewCaseCreatedPayload(
            UUID reviewCaseId,
            UUID taskId,
            UUID projectId,
            Instant createdAt
    ) {
    }

    private record ReviewDecidedPayload(
            UUID reviewCaseId,
            UUID taskId,
            UUID projectId,
            String decision,
            String decidedBy,
            Instant decidedAt
    ) {
    }

    private record ReviewCaseReopenedPayload(
            UUID reviewCaseId,
            UUID taskId,
            UUID projectId,
            String reopenedBy,
            Instant reopenedAt
    ) {
    }

    private record CorrectionCaseCreatedPayload(
            UUID correctionCaseId,
            UUID taskId,
            UUID projectId,
            Instant createdAt
    ) {
    }

    private record CorrectionResubmittedPayload(
            UUID correctionCaseId,
            UUID taskId,
            UUID projectId,
            String submittedBy,
            Instant submittedAt
    ) {
    }

    private record CorrectionClosedPayload(
            UUID correctionCaseId,
            UUID taskId,
            UUID projectId,
            String closedBy,
            Instant closedAt
    ) {
    }

    private record CorrectionWaivedPayload(
            UUID correctionCaseId,
            UUID taskId,
            UUID projectId,
            String waivedBy,
            Instant waivedAt
    ) {
    }
}
