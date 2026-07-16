package com.serviceos.fieldwork.application;

import com.serviceos.appointment.api.AppointmentVisitContext;
import com.serviceos.appointment.api.AppointmentVisitLifecycleService;
import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.ActiveServiceResponsibility;
import com.serviceos.dispatch.api.ActiveServiceResponsibilityService;
import com.serviceos.fieldwork.api.CheckInVisitCommand;
import com.serviceos.fieldwork.api.CheckOutVisitCommand;
import com.serviceos.fieldwork.api.InterruptVisitCommand;
import com.serviceos.fieldwork.api.VisitCommandReceipt;
import com.serviceos.fieldwork.api.VisitLocation;
import com.serviceos.fieldwork.api.VisitService;
import com.serviceos.fieldwork.api.VisitView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.PostgresInstants;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Visit 状态机应用服务。Visit、Appointment、审计、幂等结果与 Outbox 共享一个事务，
 * 因而不会出现“已签到但预约未推进”或“业务成功但可靠事件丢失”的半完成状态。
 */
@Service
final class DefaultVisitService implements VisitService {
    private static final String READ = "visit.read";
    private static final String CHECK_IN = "visit.checkIn";
    private static final String CHECK_OUT = "visit.checkOut";
    private static final String INTERRUPT = "visit.interrupt";
    private static final String OP_CHECK_IN = "visit.check-in";
    private static final String OP_CHECK_OUT = "visit.check-out";
    private static final String OP_INTERRUPT = "visit.interrupt";
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final VisitRepository repository;
    private final AppointmentVisitLifecycleService appointments;
    private final TaskFulfillmentContextService tasks;
    private final ActiveServiceResponsibilityService responsibilities;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultVisitService(
            VisitRepository repository,
            AppointmentVisitLifecycleService appointments,
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
        this.appointments = appointments;
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
    public List<VisitView> listByWorkOrder(
            CurrentPrincipal principal, String correlationId, UUID workOrderId
    ) {
        List<VisitAggregate> visits = repository.findByWorkOrder(principal.tenantId(), workOrderId);
        if (visits.isEmpty()) return List.of();
        VisitAggregate scope = visits.getFirst();
        require(principal, READ, scope.projectId(), scope.networkId(), "WorkOrder",
                workOrderId.toString(), correlationId);
        return visits.stream().map(visit -> view(principal, correlationId, visit)).toList();
    }

    @Override
    @Transactional
    public VisitCommandReceipt checkIn(
            CurrentPrincipal principal, CommandMetadata metadata, CheckInVisitCommand command
    ) {
        AppointmentVisitContext appointment = appointment(principal.tenantId(), command.appointmentId());
        TaskFulfillmentContext task = alignedTask(principal.tenantId(), appointment);
        ActiveServiceResponsibility responsibility = currentResponsibility(principal.tenantId(), task.taskId());
        validateCurrentTechnician(principal, appointment, task, responsibility);
        AuthorizationDecision auth = require(principal, CHECK_IN, appointment.projectId(),
                responsibility.networkId(), "Appointment", appointment.appointmentId().toString(),
                metadata.correlationId());
        if (!metadata.idempotencyKey().equals(command.deviceCommandId())) {
            throw new IllegalArgumentException("Idempotency-Key must equal deviceCommandId for check-in");
        }

        String digest = Sha256.digest(command.toString());
        CommandContext context = context(principal, metadata);
        IdempotencyDecision decision = idempotency.begin(context, OP_CHECK_IN, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return repository.findResult(context.tenantId(), OP_CHECK_IN, context.idempotencyKey());
        }
        if (!"CONFIRMED".equals(appointment.status())) {
            throw new BusinessProblem(ProblemCode.APPOINTMENT_VERSION_CONFLICT,
                    "Appointment state does not allow check-in");
        }

        Instant receivedAt = clock.instant();
        Instant capturedAt = PostgresInstants.truncate(command.capturedAt());
        validateCapturedAt(capturedAt, null, receivedAt);
        GeofenceDecision geofence = geofence(context.tenantId(), appointment.projectId(), command.location());
        UUID visitId = UUID.randomUUID();
        VisitAggregate visit = new VisitAggregate(
                visitId, context.tenantId(), appointment.projectId(), appointment.workOrderId(),
                appointment.taskId(), appointment.appointmentId(),
                repository.nextSequence(context.tenantId(), appointment.taskId()),
                principal.principalId(), responsibility.networkId(), "IN_PROGRESS",
                capturedAt, receivedAt, command.location(), geofence.result(), geofence.distanceMeters(),
                geofence.policyVersion(), geofence.policyDecision(), command.deviceId(),
                command.deviceCommandId(), command.offline(), null, null, null, null, null,
                List.of(), List.of(), 1, context.actorId(), receivedAt, receivedAt);

        // Appointment 与 Visit 在同一数据库事务推进；任一写入失败都会整体回滚。
        appointments.advance(context.tenantId(), appointment.appointmentId(), appointment.aggregateVersion(),
                "CONFIRMED", "IN_PROGRESS", "CHECK_IN_VISIT", context.actorId(), null, receivedAt);
        repository.create(visit);
        repository.appendFact(context.tenantId(), visitId, 1, "CHECK_IN", capturedAt, receivedAt,
                command.location().latitude(), command.location().longitude(), command.location().accuracyMeters(),
                geofence.result(), null, null, null, List.of(), context.actorId(), command.deviceId(),
                command.offline());

        VisitCommandReceipt receipt = new VisitCommandReceipt(
                visitId, "IN_PROGRESS", 1, geofence.result(), geofence.policyDecision(), receivedAt);
        finish(context, auth, OP_CHECK_IN, "VISIT_CHECK_IN", "visit.checked-in", visit, receipt, digest);
        return receipt;
    }

    @Override
    @Transactional
    public VisitCommandReceipt checkOut(
            CurrentPrincipal principal, CommandMetadata metadata, CheckOutVisitCommand command
    ) {
        return terminate(principal, metadata, command.visitId(), command.expectedVersion(),
                PostgresInstants.truncate(command.capturedAt()), "COMPLETED", command.resultCode(), null, null,
                command.operationRefs(), List.of(), CHECK_OUT, OP_CHECK_OUT,
                "VISIT_CHECK_OUT", "visit.checked-out");
    }

    @Override
    @Transactional
    public VisitCommandReceipt interrupt(
            CurrentPrincipal principal, CommandMetadata metadata, InterruptVisitCommand command
    ) {
        return terminate(principal, metadata, command.visitId(), command.expectedVersion(),
                PostgresInstants.truncate(command.capturedAt()), "INTERRUPTED", null, command.exceptionCode(), command.note(),
                List.of(), command.evidenceRefs(), INTERRUPT, OP_INTERRUPT,
                "VISIT_INTERRUPT", "visit.interrupted");
    }

    private VisitCommandReceipt terminate(
            CurrentPrincipal principal, CommandMetadata metadata, UUID visitId, long expectedVersion,
            Instant capturedAt, String newStatus, String resultCode, String exceptionCode, String note,
            List<String> operationRefs, List<String> evidenceRefs, String capability,
            String operation, String auditAction, String eventType
    ) {
        VisitAggregate visit = visit(principal.tenantId(), visitId);
        TaskFulfillmentContext task = task(principal.tenantId(), visit.taskId());
        ActiveServiceResponsibility responsibility = currentResponsibility(principal.tenantId(), visit.taskId());
        validateCurrentTechnician(principal, visit, task, responsibility);
        AuthorizationDecision auth = require(principal, capability, visit.projectId(), visit.networkId(),
                "Visit", visitId.toString(), metadata.correlationId());
        String digest = Sha256.digest(newStatus + '|' + expectedVersion + '|' + capturedAt + '|'
                + resultCode + '|' + exceptionCode + '|' + note + '|' + operationRefs + '|' + evidenceRefs);
        CommandContext context = context(principal, metadata);
        IdempotencyDecision decision = idempotency.begin(context, operation, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return repository.findResult(context.tenantId(), operation, context.idempotencyKey());
        }
        if (visit.aggregateVersion() != expectedVersion || !"IN_PROGRESS".equals(visit.status())) {
            throw new BusinessProblem(ProblemCode.VISIT_VERSION_CONFLICT,
                    "Visit version or state changed");
        }
        Instant receivedAt = clock.instant();
        validateCapturedAt(capturedAt, visit.checkInCapturedAt(), receivedAt);
        if (!repository.terminate(context.tenantId(), visitId, expectedVersion, newStatus,
                capturedAt, receivedAt, resultCode, exceptionCode, note, operationRefs, evidenceRefs)) {
            throw new BusinessProblem(ProblemCode.VISIT_VERSION_CONFLICT,
                    "Visit version or state changed");
        }
        long newVersion = expectedVersion + 1;
        List<String> references = "COMPLETED".equals(newStatus) ? operationRefs : evidenceRefs;
        repository.appendFact(context.tenantId(), visitId, newVersion,
                "COMPLETED".equals(newStatus) ? "CHECK_OUT" : "INTERRUPT",
                capturedAt, receivedAt, null, null, null, null, resultCode, exceptionCode,
                note, references, context.actorId(), null, false);
        AppointmentVisitContext appointment = appointment(context.tenantId(), visit.appointmentId());
        appointments.advance(context.tenantId(), visit.appointmentId(), appointment.aggregateVersion(),
                "IN_PROGRESS", newStatus, "COMPLETED".equals(newStatus)
                        ? "CHECK_OUT_VISIT" : "INTERRUPT_VISIT",
                context.actorId(), exceptionCode, receivedAt);

        VisitAggregate terminal = new VisitAggregate(
                visit.visitId(), visit.tenantId(), visit.projectId(), visit.workOrderId(), visit.taskId(),
                visit.appointmentId(), visit.visitSequence(), visit.technicianId(), visit.networkId(), newStatus,
                visit.checkInCapturedAt(), visit.checkInReceivedAt(), visit.checkInLocation(),
                visit.geofenceResult(), visit.geofenceDistanceMeters(), visit.geofencePolicyVersion(),
                visit.policyDecision(), visit.deviceId(), visit.deviceCommandId(), visit.offline(),
                capturedAt, receivedAt, resultCode, exceptionCode, note, operationRefs, evidenceRefs,
                newVersion, visit.createdBy(), visit.createdAt(), receivedAt);
        VisitCommandReceipt receipt = new VisitCommandReceipt(
                visitId, newStatus, newVersion, visit.geofenceResult(), visit.policyDecision(), receivedAt);
        finish(context, auth, operation, auditAction, eventType, terminal, receipt, digest);
        return receipt;
    }

    private GeofenceDecision geofence(String tenantId, UUID projectId, VisitLocation location) {
        GeofencePolicy policy = repository.findGeofencePolicy(tenantId, projectId).orElse(null);
        if (policy == null) {
            return new GeofenceDecision("LOCATION_UNAVAILABLE", null, null, "WARNING");
        }
        String result;
        Double distance = null;
        if (location.accuracyMeters() > policy.maxAccuracyMeters()) {
            result = "LOW_ACCURACY";
        } else {
            distance = distanceMeters(location.latitude(), location.longitude(),
                    policy.targetLatitude(), policy.targetLongitude());
            result = distance <= policy.radiusMeters() ? "WITHIN_GEOFENCE" : "OUTSIDE_GEOFENCE";
        }
        if (!"WITHIN_GEOFENCE".equals(result) && "BLOCK".equals(policy.exceptionAction())) {
            throw new BusinessProblem(ProblemCode.VISIT_GEOFENCE_REJECTED,
                    "Project geofence policy blocks this check-in");
        }
        return new GeofenceDecision(result, distance, policy.policyVersion(),
                "WITHIN_GEOFENCE".equals(result) ? "ACCEPTED" : "WARNING");
    }

    private VisitView view(CurrentPrincipal principal, String correlationId, VisitAggregate visit) {
        boolean current = principal.principalId().equals(visit.technicianId())
                && responsibilities.find(principal.tenantId(), visit.taskId())
                .map(value -> principal.principalId().equals(value.technicianId())).orElse(false);
        List<String> actions = !current || !"IN_PROGRESS".equals(visit.status()) ? List.of()
                : List.of(CHECK_OUT, INTERRUPT).stream()
                .filter(capability -> can(principal, capability, visit.projectId(), visit.networkId(),
                        "Visit", visit.visitId().toString(), correlationId))
                .toList();
        return new VisitView(
                visit.visitId(), visit.projectId(), visit.workOrderId(), visit.taskId(), visit.appointmentId(),
                visit.visitSequence(), visit.technicianId(), visit.networkId(), visit.status(),
                visit.checkInCapturedAt(), visit.checkInReceivedAt(), visit.checkInLocation(),
                visit.geofenceResult(), visit.geofenceDistanceMeters(), visit.geofencePolicyVersion(),
                visit.policyDecision(), visit.deviceId(), visit.deviceCommandId(), visit.offline(),
                visit.checkOutCapturedAt(), visit.checkOutReceivedAt(), visit.resultCode(),
                visit.exceptionCode(), visit.note(), visit.operationRefs(), visit.evidenceRefs(),
                visit.aggregateVersion(), actions);
    }

    private void finish(
            CommandContext context, AuthorizationDecision auth, String operation, String auditAction,
            String eventType, VisitAggregate visit, VisitCommandReceipt receipt, String requestDigest
    ) {
        repository.saveResult(context.tenantId(), operation, context.idempotencyKey(), receipt);
        String payload = eventPayload(eventType, visit, receipt);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "fieldwork", eventType, 1,
                "Visit", visit.visitId().toString(), receipt.aggregateVersion(), context.tenantId(),
                context.correlationId(), context.idempotencyKey(), visit.workOrderId().toString(),
                payload, Sha256.digest(payload), receipt.occurredAt()));
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(), auditAction, operation,
                "Visit", visit.visitId().toString(), "ALLOW", auth.matchedGrantIds(), auth.policyVersion(),
                "SUCCEEDED", null, requestDigest, context.correlationId(), receipt.occurredAt()));
        idempotency.complete(context, operation, visit.visitId().toString(), Sha256.digest(serialize(receipt)));
    }

    private String eventPayload(String eventType, VisitAggregate visit, VisitCommandReceipt receipt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("visitId", visit.visitId());
        payload.put("appointmentId", visit.appointmentId());
        payload.put("projectId", visit.projectId());
        payload.put("workOrderId", visit.workOrderId());
        payload.put("taskId", visit.taskId());
        payload.put("visitSequence", visit.visitSequence());
        payload.put("technicianId", visit.technicianId());
        payload.put("networkId", visit.networkId());
        payload.put("status", receipt.status());
        payload.put("capturedAt", "IN_PROGRESS".equals(receipt.status())
                ? visit.checkInCapturedAt() : visit.checkOutCapturedAt());
        payload.put("receivedAt", receipt.occurredAt());
        payload.put("geofenceResult", visit.geofenceResult());
        payload.put("policyDecision", visit.policyDecision());
        payload.put("resultCode", visit.resultCode());
        payload.put("exceptionCode", visit.exceptionCode());
        payload.put("operationRefs", visit.operationRefs());
        payload.put("evidenceRefs", visit.evidenceRefs());
        payload.put("aggregateVersion", receipt.aggregateVersion());
        payload.put("occurredAt", receipt.occurredAt());
        payload.put("eventType", eventType);
        return serialize(payload);
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double latitude = Math.toRadians(lat2 - lat1);
        double longitude = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latitude / 2) * Math.sin(latitude / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(longitude / 2) * Math.sin(longitude / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static void validateCapturedAt(Instant capturedAt, Instant checkInAt, Instant receivedAt) {
        if (capturedAt.isAfter(receivedAt.plus(MAX_CLOCK_SKEW))) {
            throw new IllegalArgumentException("capturedAt is too far in the future");
        }
        if (checkInAt != null && capturedAt.isBefore(checkInAt)) {
            throw new IllegalArgumentException("terminal capturedAt precedes check-in");
        }
    }

    private static void validateCurrentTechnician(
            CurrentPrincipal principal, AppointmentVisitContext appointment,
            TaskFulfillmentContext task, ActiveServiceResponsibility responsibility
    ) {
        if (!principal.principalId().equals(appointment.technicianId())
                || !principal.principalId().equals(task.responsiblePrincipalId())
                || !principal.principalId().equals(responsibility.technicianId())
                || !Objects.equals(appointment.assignedNetworkId(), responsibility.networkId())) {
            throw new BusinessProblem(ProblemCode.TECHNICIAN_ASSIGNMENT_CHANGED,
                    "Appointment, Task and active service responsibility no longer belong to this technician");
        }
    }

    private static void validateCurrentTechnician(
            CurrentPrincipal principal, VisitAggregate visit,
            TaskFulfillmentContext task, ActiveServiceResponsibility responsibility
    ) {
        if (!principal.principalId().equals(visit.technicianId())
                || !principal.principalId().equals(task.responsiblePrincipalId())
                || !principal.principalId().equals(responsibility.technicianId())
                || !Objects.equals(visit.networkId(), responsibility.networkId())) {
            throw new BusinessProblem(ProblemCode.TECHNICIAN_ASSIGNMENT_CHANGED,
                    "Visit and active service responsibility no longer belong to this technician");
        }
    }

    private TaskFulfillmentContext alignedTask(String tenantId, AppointmentVisitContext appointment) {
        TaskFulfillmentContext task = task(tenantId, appointment.taskId());
        if (!appointment.projectId().equals(task.projectId())
                || !appointment.workOrderId().equals(task.workOrderId())
                || !"HUMAN".equals(task.taskKind())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Appointment is not aligned with an executable HUMAN Task");
        }
        return task;
    }

    private AppointmentVisitContext appointment(String tenantId, UUID appointmentId) {
        return appointments.find(tenantId, appointmentId).orElseThrow(() ->
                new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Appointment does not exist"));
    }

    private TaskFulfillmentContext task(String tenantId, UUID taskId) {
        return tasks.find(tenantId, taskId).orElseThrow(() ->
                new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
    }

    private VisitAggregate visit(String tenantId, UUID visitId) {
        return repository.findById(tenantId, visitId).orElseThrow(() ->
                new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Visit does not exist"));
    }

    private ActiveServiceResponsibility currentResponsibility(String tenantId, UUID taskId) {
        return responsibilities.find(tenantId, taskId).orElseThrow(() ->
                new BusinessProblem(ProblemCode.TECHNICIAN_ASSIGNMENT_CHANGED,
                        "Task has no active service responsibility"));
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
                networkId, resourceType, resourceId), correlationId).effect()
                == AuthorizationDecision.Effect.ALLOW;
    }

    private static AuthorizationRequest request(
            String capability, String tenantId, UUID projectId, String networkId,
            String resourceType, String resourceId
    ) {
        return new AuthorizationRequest(capability, tenantId, resourceType, resourceId,
                projectId.toString(), null, null, networkId);
    }

    private static CommandContext context(CurrentPrincipal principal, CommandMetadata metadata) {
        return new CommandContext(principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
    }

    private String serialize(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Visit serialization failed", exception); }
    }

    private record GeofenceDecision(
            String result, Double distanceMeters, String policyVersion, String policyDecision
    ) {
    }
}
