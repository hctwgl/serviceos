package com.serviceos.appointment.application;

import com.serviceos.appointment.api.AppointmentCommandReceipt;
import com.serviceos.appointment.api.AppointmentRevisionView;
import com.serviceos.appointment.api.AppointmentService;
import com.serviceos.appointment.api.AppointmentView;
import com.serviceos.appointment.api.CancelAppointmentCommand;
import com.serviceos.appointment.api.ConfirmAppointmentCommand;
import com.serviceos.appointment.api.ContactAttemptView;
import com.serviceos.appointment.api.MarkAppointmentNoShowCommand;
import com.serviceos.appointment.api.ProposeAppointmentCommand;
import com.serviceos.appointment.api.RecordContactAttemptCommand;
import com.serviceos.appointment.api.RescheduleAppointmentCommand;
import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.ActiveServiceResponsibility;
import com.serviceos.dispatch.api.ActiveServiceResponsibilityService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 预约状态机应用服务：聚合、修订、历史、审计、幂等和 Outbox 在同一事务提交。 */
@Service
final class DefaultAppointmentService implements AppointmentService {
    private static final String READ = "appointment.read";
    private static final String PROPOSE = "appointment.propose";
    private static final String MANAGE = "appointment.manage";
    private static final String RECORD_CONTACT = "appointment.recordContact";
    private static final String CANCEL = "appointment.cancel";
    private static final String OP_PROPOSE = "appointment.propose";
    private static final String OP_CONFIRM = "appointment.confirm";
    private static final String OP_RESCHEDULE = "appointment.reschedule";
    private static final String OP_RECORD_CONTACT = "appointment.record-contact";
    private static final String OP_CANCEL = "appointment.cancel";
    private static final String OP_NO_SHOW = "appointment.mark-no-show";
    private static final Set<String> APPOINTABLE_TASK_STATES = Set.of("READY", "CLAIMED", "RUNNING");

    private final AppointmentRepository repository;
    private final TaskFulfillmentContextService tasks;
    private final ActiveServiceResponsibilityService responsibilities;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultAppointmentService(
            AppointmentRepository repository,
            TaskFulfillmentContextService tasks,
            ActiveServiceResponsibilityService responsibilities,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = repository;
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
    @Transactional(readOnly = true)
    public List<AppointmentView> listByTask(
            CurrentPrincipal principal, String correlationId, UUID taskId
    ) {
        TaskFulfillmentContext task = task(principal.tenantId(), taskId);
        ActiveServiceResponsibility responsibility = responsibility(principal.tenantId(), taskId);
        require(principal, READ, task.projectId(), responsibility.networkId(), "Task", taskId.toString(),
                correlationId);
        boolean canManage = can(principal, MANAGE, task.projectId(), responsibility.networkId(),
                "Task", taskId.toString(), correlationId);
        boolean canCancel = can(principal, CANCEL, task.projectId(), responsibility.networkId(),
                "Task", taskId.toString(), correlationId);
        return repository.findByTask(principal.tenantId(), taskId).stream()
                .map(appointment -> view(appointment, canManage, canCancel)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentView get(
            CurrentPrincipal principal, String correlationId, UUID appointmentId
    ) {
        AppointmentAggregate appointment = appointment(principal.tenantId(), appointmentId);
        require(principal, READ, appointment.projectId(), appointment.assignedNetworkId(),
                "Appointment", appointmentId.toString(), correlationId);
        boolean canManage = can(principal, MANAGE, appointment.projectId(), appointment.assignedNetworkId(),
                "Appointment", appointmentId.toString(), correlationId);
        boolean canCancel = can(principal, CANCEL, appointment.projectId(), appointment.assignedNetworkId(),
                "Appointment", appointmentId.toString(), correlationId);
        return view(appointment, canManage, canCancel);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactAttemptView> listContactAttempts(
            CurrentPrincipal principal, String correlationId, UUID taskId
    ) {
        TaskFulfillmentContext task = task(principal.tenantId(), taskId);
        ActiveServiceResponsibility responsibility = responsibility(principal.tenantId(), taskId);
        require(principal, READ, task.projectId(), responsibility.networkId(),
                "Task", taskId.toString(), correlationId);
        return repository.findContactAttempts(principal.tenantId(), taskId);
    }

    @Override
    @Transactional(readOnly = true)
    public ContactAttemptView getContactAttempt(
            CurrentPrincipal principal, String correlationId, UUID contactAttemptId
    ) {
        // 不可变事实：按 ID 读取后仍走与列表一致的 appointment.read + project/network scope。
        ContactAttemptView attempt = repository.findContactAttemptById(principal.tenantId(), contactAttemptId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                        "ContactAttempt does not exist"));
        ActiveServiceResponsibility responsibility = responsibility(principal.tenantId(), attempt.taskId());
        require(principal, READ, attempt.projectId(), responsibility.networkId(),
                "ContactAttempt", contactAttemptId.toString(), correlationId);
        return attempt;
    }

    @Override
    @Transactional
    public ContactAttemptView recordContactAttempt(
            CurrentPrincipal principal, CommandMetadata metadata, RecordContactAttemptCommand command
    ) {
        TaskFulfillmentContext task = task(principal.tenantId(), command.taskId());
        validateTask(task);
        ActiveServiceResponsibility responsibility = responsibility(principal.tenantId(), command.taskId());
        AuthorizationDecision auth = require(principal, RECORD_CONTACT, task.projectId(), responsibility.networkId(),
                "Task", command.taskId().toString(), metadata.correlationId());
        String digest = Sha256.digest(command.toString());
        CommandContext context = context(principal, metadata);
        IdempotencyDecision decision = idempotency.begin(context, OP_RECORD_CONTACT, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return repository.findContactResult(context.tenantId(), OP_RECORD_CONTACT, context.idempotencyKey());
        }

        Instant now = clock.instant();
        ContactAttemptView attempt = new ContactAttemptView(
                UUID.randomUUID(), task.projectId(), task.workOrderId(), task.taskId(), command.channel(),
                command.contactedPartyRef(), command.startedAt(), command.endedAt(), command.resultCode(),
                command.note(), command.nextContactAt(), command.recordingRef(), context.actorId(), now);
        repository.appendContactAttempt(context.tenantId(), attempt);
        repository.saveContactResult(context.tenantId(), OP_RECORD_CONTACT, context.idempotencyKey(),
                attempt.contactAttemptId());
        String payload = contactEventPayload(attempt);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "appointment", "contact.attempt.recorded", 1,
                "ContactAttempt", attempt.contactAttemptId().toString(), 1,
                context.tenantId(), context.correlationId(), context.idempotencyKey(),
                attempt.contactAttemptId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(), "CONTACT_ATTEMPT_RECORD",
                OP_RECORD_CONTACT, "Task", command.taskId().toString(), "ALLOW",
                auth.matchedGrantIds(), auth.policyVersion(), "SUCCEEDED", null,
                digest, context.correlationId(), now));
        idempotency.complete(context, OP_RECORD_CONTACT, attempt.contactAttemptId().toString(),
                Sha256.digest(serialize(attempt)));
        return attempt;
    }

    @Override
    @Transactional
    public AppointmentCommandReceipt propose(
            CurrentPrincipal principal, CommandMetadata metadata, ProposeAppointmentCommand command
    ) {
        TaskFulfillmentContext task = task(principal.tenantId(), command.taskId());
        validateTask(task);
        ActiveServiceResponsibility responsibility = responsibility(principal.tenantId(), command.taskId());
        String technicianId = alignedTechnician(task, responsibility);
        AuthorizationDecision auth = require(
                principal, PROPOSE, task.projectId(), responsibility.networkId(),
                "Task", command.taskId().toString(), metadata.correlationId());
        String digest = Sha256.digest(command.toString());
        CommandContext context = context(principal, metadata);
        IdempotencyDecision decision = idempotency.begin(context, OP_PROPOSE, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return repository.findResult(context.tenantId(), OP_PROPOSE, context.idempotencyKey());
        }

        Instant now = clock.instant();
        UUID appointmentId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        AppointmentRevisionView revision = new AppointmentRevisionView(
                revisionId, 1, null, "PROPOSE", command.window(), command.addressRef(), command.addressVersion(),
                null, null, null, null, null, null, null, null, List.of(), context.actorId(), now);
        AppointmentAggregate appointment = new AppointmentAggregate(
                appointmentId, context.tenantId(), task.projectId(), task.workOrderId(), task.taskId(),
                command.type(), "PROPOSED", responsibility.networkId(), technicianId,
                1, 1, now, context.actorId(), revision);
        repository.create(appointment);
        repository.appendHistory(context.tenantId(), appointmentId, 1, null, "PROPOSED",
                "PROPOSE_APPOINTMENT", context.actorId(), null, revisionId, now);
        AppointmentCommandReceipt receipt = new AppointmentCommandReceipt(
                appointmentId, revisionId, "PROPOSED", 1, 1, now);
        finish(context, auth, OP_PROPOSE, "APPOINTMENT_PROPOSE", "appointment.proposed",
                appointment, receipt, digest);
        return receipt;
    }

    @Override
    @Transactional
    public AppointmentCommandReceipt confirm(
            CurrentPrincipal principal, CommandMetadata metadata, ConfirmAppointmentCommand command
    ) {
        AppointmentAggregate current = appointment(principal.tenantId(), command.appointmentId());
        AuthorizationDecision auth = require(
                principal, MANAGE, current.projectId(), current.assignedNetworkId(),
                "Appointment", command.appointmentId().toString(), metadata.correlationId());
        String digest = Sha256.digest(command.toString());
        CommandContext context = context(principal, metadata);
        IdempotencyDecision decision = idempotency.begin(context, OP_CONFIRM, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return repository.findResult(context.tenantId(), OP_CONFIRM, context.idempotencyKey());
        }
        if (!"PROPOSED".equals(current.status())) conflict(current, command.expectedVersion());

        Instant now = clock.instant();
        int revisionNo = current.currentRevisionNo() + 1;
        UUID revisionId = UUID.randomUUID();
        AppointmentRevisionView previous = current.currentRevision();
        AppointmentRevisionView revision = new AppointmentRevisionView(
                revisionId, revisionNo, previous.revisionId(), "CONFIRM", previous.window(),
                previous.addressRef(), previous.addressVersion(), command.confirmedPartyType(),
                command.confirmedPartyRef(), command.confirmationChannel(), now,
                null, null, null, null, List.of(), context.actorId(), now);
        if (!repository.advance(context.tenantId(), command.appointmentId(), command.expectedVersion(),
                "PROPOSED", "CONFIRMED", revisionNo, revisionId)) {
            conflict(appointment(context.tenantId(), command.appointmentId()), command.expectedVersion());
        }
        repository.appendRevision(context.tenantId(), command.appointmentId(), revision);
        repository.appendHistory(context.tenantId(), command.appointmentId(), command.expectedVersion() + 1,
                "PROPOSED", "CONFIRMED", "CONFIRM_APPOINTMENT", context.actorId(),
                null, revisionId, now);
        AppointmentCommandReceipt receipt = new AppointmentCommandReceipt(
                command.appointmentId(), revisionId, "CONFIRMED", revisionNo,
                command.expectedVersion() + 1, now);
        finish(context, auth, OP_CONFIRM, "APPOINTMENT_CONFIRM", "appointment.confirmed",
                current, receipt, digest);
        return receipt;
    }

    @Override
    @Transactional
    public AppointmentCommandReceipt reschedule(
            CurrentPrincipal principal, CommandMetadata metadata, RescheduleAppointmentCommand command
    ) {
        AppointmentAggregate current = appointment(principal.tenantId(), command.appointmentId());
        AuthorizationDecision auth = require(
                principal, MANAGE, current.projectId(), current.assignedNetworkId(),
                "Appointment", command.appointmentId().toString(), metadata.correlationId());
        String digest = Sha256.digest(command.toString());
        CommandContext context = context(principal, metadata);
        IdempotencyDecision decision = idempotency.begin(context, OP_RESCHEDULE, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return repository.findResult(context.tenantId(), OP_RESCHEDULE, context.idempotencyKey());
        }
        if (!"CONFIRMED".equals(current.status())) conflict(current, command.expectedVersion());

        Instant now = clock.instant();
        int revisionNo = current.currentRevisionNo() + 1;
        UUID revisionId = UUID.randomUUID();
        AppointmentRevisionView previous = current.currentRevision();
        AppointmentRevisionView revision = new AppointmentRevisionView(
                revisionId, revisionNo, previous.revisionId(), "RESCHEDULE", command.newWindow(),
                previous.addressRef(), previous.addressVersion(), null, null, null, null,
                command.reasonCode(), command.note(), null, null, List.of(), context.actorId(), now);
        if (!repository.advance(context.tenantId(), command.appointmentId(), command.expectedVersion(),
                "CONFIRMED", "PROPOSED", revisionNo, revisionId)) {
            conflict(appointment(context.tenantId(), command.appointmentId()), command.expectedVersion());
        }
        repository.appendRevision(context.tenantId(), command.appointmentId(), revision);
        repository.appendHistory(context.tenantId(), command.appointmentId(), command.expectedVersion() + 1,
                "CONFIRMED", "PROPOSED", "RESCHEDULE_APPOINTMENT", context.actorId(),
                command.reasonCode(), revisionId, now);
        AppointmentCommandReceipt receipt = new AppointmentCommandReceipt(
                command.appointmentId(), revisionId, "PROPOSED", revisionNo,
                command.expectedVersion() + 1, now);
        finish(context, auth, OP_RESCHEDULE, "APPOINTMENT_RESCHEDULE", "appointment.rescheduled",
                current, receipt, digest);
        return receipt;
    }

    @Override
    @Transactional
    public AppointmentCommandReceipt cancel(
            CurrentPrincipal principal, CommandMetadata metadata, CancelAppointmentCommand command
    ) {
        AppointmentAggregate current = appointment(principal.tenantId(), command.appointmentId());
        AuthorizationDecision auth = require(principal, CANCEL, current.projectId(), current.assignedNetworkId(),
                "Appointment", command.appointmentId().toString(), metadata.correlationId());
        return terminate(principal, metadata, current, command.expectedVersion(), "CANCELLED",
                "CANCEL", command.reasonCode(), command.note(), null, null, List.of(),
                OP_CANCEL, "APPOINTMENT_CANCEL", "appointment.cancelled", auth);
    }

    @Override
    @Transactional
    public AppointmentCommandReceipt markNoShow(
            CurrentPrincipal principal, CommandMetadata metadata, MarkAppointmentNoShowCommand command
    ) {
        AppointmentAggregate current = appointment(principal.tenantId(), command.appointmentId());
        AuthorizationDecision auth = require(principal, MANAGE, current.projectId(), current.assignedNetworkId(),
                "Appointment", command.appointmentId().toString(), metadata.correlationId());
        return terminate(principal, metadata, current, command.expectedVersion(), "NO_SHOW",
                "NO_SHOW", command.reasonCode(), null, command.noShowPartyType(), command.noShowPartyRef(),
                command.evidenceRefs(), OP_NO_SHOW, "APPOINTMENT_MARK_NO_SHOW",
                "appointment.no-show-marked", auth);
    }

    private AppointmentCommandReceipt terminate(
            CurrentPrincipal principal, CommandMetadata metadata, AppointmentAggregate current,
            long expectedVersion, String newStatus, String revisionKind, String reasonCode, String note,
            String noShowPartyType, String noShowPartyRef, List<String> evidenceRefs,
            String operation, String auditAction, String eventType, AuthorizationDecision auth
    ) {
        String digest = Sha256.digest(List.of(current.appointmentId(), expectedVersion, newStatus,
                reasonCode, note == null ? "" : note, noShowPartyType == null ? "" : noShowPartyType,
                noShowPartyRef == null ? "" : noShowPartyRef, evidenceRefs).toString());
        CommandContext context = context(principal, metadata);
        IdempotencyDecision decision = idempotency.begin(context, operation, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return repository.findResult(context.tenantId(), operation, context.idempotencyKey());
        }
        if (current.aggregateVersion() != expectedVersion) conflict(current, expectedVersion);
        if ("NO_SHOW".equals(newStatus)) {
            if (!"CONFIRMED".equals(current.status())) conflict(current, expectedVersion);
            if (current.currentRevision().window().end().isAfter(clock.instant())) {
                throw new BusinessProblem(ProblemCode.APPOINTMENT_WINDOW_NOT_ENDED,
                        "Appointment window has not ended");
            }
        } else if (!("PROPOSED".equals(current.status()) || "CONFIRMED".equals(current.status()))) {
            conflict(current, expectedVersion);
        }

        Instant now = clock.instant();
        int revisionNo = current.currentRevisionNo() + 1;
        UUID revisionId = UUID.randomUUID();
        AppointmentRevisionView previous = current.currentRevision();
        AppointmentRevisionView revision = new AppointmentRevisionView(
                revisionId, revisionNo, previous.revisionId(), revisionKind, previous.window(),
                previous.addressRef(), previous.addressVersion(), previous.confirmedPartyType(),
                previous.confirmedPartyRef(), previous.confirmationChannel(), previous.confirmedAt(),
                reasonCode, note, noShowPartyType, noShowPartyRef, evidenceRefs, context.actorId(), now);
        if (!repository.advance(context.tenantId(), current.appointmentId(), expectedVersion,
                current.status(), newStatus, revisionNo, revisionId)) {
            conflict(appointment(context.tenantId(), current.appointmentId()), expectedVersion);
        }
        repository.appendRevision(context.tenantId(), current.appointmentId(), revision);
        repository.appendHistory(context.tenantId(), current.appointmentId(), expectedVersion + 1,
                current.status(), newStatus, auditAction, context.actorId(), reasonCode, revisionId, now);
        AppointmentCommandReceipt receipt = new AppointmentCommandReceipt(
                current.appointmentId(), revisionId, newStatus, revisionNo, expectedVersion + 1, now);
        repository.saveResult(context.tenantId(), operation, context.idempotencyKey(), receipt);
        String payload = terminalEventPayload(eventType, current, receipt, revision);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "appointment", eventType, 1,
                "Appointment", receipt.appointmentId().toString(), receipt.aggregateVersion(),
                context.tenantId(), context.correlationId(), context.idempotencyKey(),
                receipt.appointmentId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(), auditAction,
                operation, "Appointment", receipt.appointmentId().toString(), "ALLOW",
                auth.matchedGrantIds(), auth.policyVersion(), "SUCCEEDED", null,
                digest, context.correlationId(), now));
        idempotency.complete(context, operation, receipt.appointmentId().toString(),
                Sha256.digest(serialize(receipt)));
        return receipt;
    }

    private AppointmentView view(AppointmentAggregate appointment, boolean canManage, boolean canCancel) {
        java.util.ArrayList<String> actions = new java.util.ArrayList<>();
        if (canManage && "PROPOSED".equals(appointment.status())) actions.add("CONFIRM");
        if (canManage && "CONFIRMED".equals(appointment.status())) {
            actions.add("RESCHEDULE");
            if (!appointment.currentRevision().window().end().isAfter(clock.instant())) actions.add("MARK_NO_SHOW");
        }
        if (canCancel && ("PROPOSED".equals(appointment.status()) || "CONFIRMED".equals(appointment.status()))) {
            actions.add("CANCEL");
        }
        return new AppointmentView(
                appointment.appointmentId(), appointment.projectId(), appointment.workOrderId(),
                appointment.taskId(), appointment.type(), appointment.status(),
                appointment.assignedNetworkId(), appointment.technicianId(),
                appointment.aggregateVersion(), appointment.currentRevisionNo(),
                appointment.createdAt(), appointment.createdBy(),
                repository.findRevisions(appointment.tenantId(), appointment.appointmentId()), List.copyOf(actions));
    }

    private void finish(
            CommandContext context, AuthorizationDecision auth, String operation,
            String auditAction, String eventType, AppointmentAggregate appointment,
            AppointmentCommandReceipt receipt, String requestDigest
    ) {
        repository.saveResult(context.tenantId(), operation, context.idempotencyKey(), receipt);
        String payload = eventPayload(eventType, appointment, receipt);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "appointment", eventType, 1,
                "Appointment", receipt.appointmentId().toString(), receipt.aggregateVersion(),
                context.tenantId(), context.correlationId(), context.idempotencyKey(),
                receipt.appointmentId().toString(), payload, Sha256.digest(payload), receipt.occurredAt()));
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(), auditAction,
                operation, "Appointment", receipt.appointmentId().toString(), "ALLOW",
                auth.matchedGrantIds(), auth.policyVersion(), "SUCCEEDED", null,
                requestDigest, context.correlationId(), receipt.occurredAt()));
        idempotency.complete(context, operation, receipt.appointmentId().toString(),
                Sha256.digest(serialize(receipt)));
    }

    private String eventPayload(
            String eventType, AppointmentAggregate appointment, AppointmentCommandReceipt receipt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appointmentId", receipt.appointmentId());
        payload.put("revisionId", receipt.revisionId());
        payload.put("revisionNo", receipt.revisionNo());
        payload.put("projectId", appointment.projectId());
        payload.put("workOrderId", appointment.workOrderId());
        payload.put("taskId", appointment.taskId());
        payload.put("appointmentType", appointment.type());
        payload.put("status", receipt.status());
        payload.put("aggregateVersion", receipt.aggregateVersion());
        payload.put("occurredAt", receipt.occurredAt());
        payload.put("eventType", eventType);
        return serialize(payload);
    }

    private String contactEventPayload(ContactAttemptView attempt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contactAttemptId", attempt.contactAttemptId());
        payload.put("projectId", attempt.projectId());
        payload.put("workOrderId", attempt.workOrderId());
        payload.put("taskId", attempt.taskId());
        payload.put("channel", attempt.channel());
        payload.put("contactedPartyRef", attempt.contactedPartyRef());
        payload.put("startedAt", attempt.startedAt());
        payload.put("endedAt", attempt.endedAt());
        payload.put("resultCode", attempt.resultCode());
        payload.put("nextContactAt", attempt.nextContactAt());
        payload.put("actorId", attempt.actorId());
        payload.put("occurredAt", attempt.createdAt());
        payload.put("eventType", "contact.attempt.recorded");
        return serialize(payload);
    }

    private String terminalEventPayload(
            String eventType, AppointmentAggregate appointment,
            AppointmentCommandReceipt receipt, AppointmentRevisionView revision
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appointmentId", receipt.appointmentId());
        payload.put("revisionId", receipt.revisionId());
        payload.put("revisionNo", receipt.revisionNo());
        payload.put("projectId", appointment.projectId());
        payload.put("workOrderId", appointment.workOrderId());
        payload.put("taskId", appointment.taskId());
        payload.put("appointmentType", appointment.type());
        payload.put("status", receipt.status());
        payload.put("reasonCode", revision.reasonCode());
        payload.put("noShowPartyType", revision.noShowPartyType());
        payload.put("noShowPartyRef", revision.noShowPartyRef());
        payload.put("evidenceRefs", revision.noShowEvidenceRefs());
        payload.put("aggregateVersion", receipt.aggregateVersion());
        payload.put("occurredAt", receipt.occurredAt());
        payload.put("eventType", eventType);
        return serialize(payload);
    }

    private AuthorizationDecision require(
            CurrentPrincipal principal, String capability, UUID projectId, String networkId,
            String resourceType, String resourceId, String correlationId
    ) {
        return authorization.require(principal, request(capability, principal.tenantId(), projectId,
                networkId, resourceType, resourceId), correlationId);
    }

    private boolean can(
            CurrentPrincipal principal, String capability, UUID projectId, String networkId,
            String resourceType, String resourceId, String correlationId
    ) {
        return authorization.authorize(principal, request(capability, principal.tenantId(), projectId,
                networkId, resourceType, resourceId), correlationId)
                .effect() == AuthorizationDecision.Effect.ALLOW;
    }

    private static AuthorizationRequest request(
            String capability, String tenantId, UUID projectId, String networkId,
            String resourceType, String resourceId
    ) {
        return new AuthorizationRequest(capability, tenantId, resourceType, resourceId,
                projectId == null ? null : projectId.toString(), null, null, networkId);
    }

    private TaskFulfillmentContext task(String tenantId, UUID taskId) {
        return tasks.find(tenantId, taskId).orElseThrow(() ->
                new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
    }

    private AppointmentAggregate appointment(String tenantId, UUID appointmentId) {
        return repository.findById(tenantId, appointmentId).orElseThrow(() ->
                new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Appointment does not exist"));
    }

    private ActiveServiceResponsibility responsibility(String tenantId, UUID taskId) {
        return responsibilities.find(tenantId, taskId)
                .orElse(new ActiveServiceResponsibility(taskId, null, null));
    }

    private static String alignedTechnician(
            TaskFulfillmentContext task, ActiveServiceResponsibility responsibility
    ) {
        String taskResponsible = task.responsiblePrincipalId();
        String serviceResponsible = responsibility.technicianId();
        if (taskResponsible != null && serviceResponsible != null
                && !taskResponsible.equals(serviceResponsible)) {
            throw new BusinessProblem(ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "Task and service responsibility are not aligned");
        }
        return serviceResponsible != null ? serviceResponsible : taskResponsible;
    }

    private static void validateTask(TaskFulfillmentContext task) {
        if (task.projectId() == null || task.workOrderId() == null || !"HUMAN".equals(task.taskKind())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Only a workflow HUMAN Task can be appointed");
        }
        if (!APPOINTABLE_TASK_STATES.contains(task.status())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Task state does not allow appointment");
        }
    }

    private static void conflict(AppointmentAggregate current, long expectedVersion) {
        String detail = current.aggregateVersion() != expectedVersion
                ? "Appointment version changed" : "Appointment state does not allow the command";
        throw new BusinessProblem(ProblemCode.APPOINTMENT_VERSION_CONFLICT, detail);
    }

    private static CommandContext context(CurrentPrincipal principal, CommandMetadata metadata) {
        return new CommandContext(principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Appointment serialization failed", exception);
        }
    }
}
