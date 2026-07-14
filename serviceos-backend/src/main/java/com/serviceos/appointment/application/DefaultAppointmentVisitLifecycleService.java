package com.serviceos.appointment.application;

import com.serviceos.appointment.api.AppointmentVisitContext;
import com.serviceos.appointment.api.AppointmentVisitLifecycleService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Appointment 对 fieldwork 暴露的窄端口，不允许 fieldwork 直接访问预约表或 Mapper。 */
@Service
final class DefaultAppointmentVisitLifecycleService implements AppointmentVisitLifecycleService {
    private final AppointmentRepository repository;

    DefaultAppointmentVisitLifecycleService(AppointmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AppointmentVisitContext> find(String tenantId, UUID appointmentId) {
        return repository.findById(tenantId, appointmentId).map(appointment -> new AppointmentVisitContext(
                appointment.appointmentId(), appointment.projectId(), appointment.workOrderId(),
                appointment.taskId(), appointment.status(), appointment.assignedNetworkId(),
                appointment.technicianId(), appointment.aggregateVersion(),
                appointment.currentRevision().revisionId(), appointment.currentRevision().window().start(),
                appointment.currentRevision().window().end()));
    }

    @Override
    public long advance(
            String tenantId, UUID appointmentId, long expectedVersion,
            String expectedStatus, String newStatus, String commandCode,
            String actorId, String reasonCode, Instant occurredAt
    ) {
        AppointmentAggregate current = repository.findById(tenantId, appointmentId).orElseThrow(() ->
                new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Appointment does not exist"));
        if (!repository.advanceStatus(tenantId, appointmentId, expectedVersion, expectedStatus, newStatus)) {
            throw new BusinessProblem(ProblemCode.APPOINTMENT_VERSION_CONFLICT,
                    "Appointment version or state changed");
        }
        long newVersion = expectedVersion + 1;
        repository.appendHistory(tenantId, appointmentId, newVersion, expectedStatus, newStatus,
                commandCode, actorId, reasonCode, current.currentRevision().revisionId(), occurredAt);
        return newVersion;
    }
}
