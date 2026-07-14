package com.serviceos.appointment.infrastructure;

import com.serviceos.appointment.api.AppointmentCommandReceipt;
import com.serviceos.appointment.api.AppointmentRevisionView;
import com.serviceos.appointment.api.AppointmentType;
import com.serviceos.appointment.api.AppointmentWindow;
import com.serviceos.appointment.application.AppointmentAggregate;
import com.serviceos.appointment.application.AppointmentRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
final class MyBatisAppointmentRepository implements AppointmentRepository {
    private final AppointmentMapper mapper;

    MyBatisAppointmentRepository(AppointmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AppointmentAggregate> findById(String tenantId, UUID appointmentId) {
        return Optional.ofNullable(mapper.findById(tenantId, appointmentId)).map(this::appointment);
    }

    @Override
    public List<AppointmentAggregate> findByTask(String tenantId, UUID taskId) {
        return mapper.findByTask(tenantId, taskId).stream().map(this::appointment).toList();
    }

    @Override
    public List<AppointmentRevisionView> findRevisions(String tenantId, UUID appointmentId) {
        return mapper.findRevisions(tenantId, appointmentId).stream().map(this::revision).toList();
    }

    @Override
    public void create(AppointmentAggregate appointment) {
        Map<String, Object> values = appointmentValues(appointment);
        mapper.insertAppointment(values);
        mapper.insertRevision(revisionValues(
                appointment.tenantId(), appointment.appointmentId(), appointment.currentRevision()));
    }

    @Override
    public boolean advance(
            String tenantId, UUID appointmentId, long expectedVersion, String expectedStatus,
            String newStatus, int newRevisionNo, UUID newRevisionId
    ) {
        Map<String, Object> values = new HashMap<>();
        values.put("tenantId", tenantId);
        values.put("appointmentId", appointmentId);
        values.put("expectedVersion", expectedVersion);
        values.put("expectedStatus", expectedStatus);
        values.put("newStatus", newStatus);
        values.put("newRevisionNo", newRevisionNo);
        values.put("newRevisionId", newRevisionId);
        return mapper.advance(values) == 1;
    }

    @Override
    public void appendRevision(String tenantId, UUID appointmentId, AppointmentRevisionView revision) {
        mapper.insertRevision(revisionValues(tenantId, appointmentId, revision));
    }

    @Override
    public void appendHistory(
            String tenantId, UUID appointmentId, long aggregateVersion, String fromStatus,
            String toStatus, String commandCode, String actorId, String reasonCode,
            UUID revisionId, Instant occurredAt
    ) {
        Map<String, Object> values = new HashMap<>();
        values.put("historyId", UUID.randomUUID());
        values.put("tenantId", tenantId);
        values.put("appointmentId", appointmentId);
        values.put("aggregateVersion", aggregateVersion);
        values.put("fromStatus", fromStatus);
        values.put("toStatus", toStatus);
        values.put("commandCode", commandCode);
        values.put("actorId", actorId);
        values.put("reasonCode", reasonCode);
        values.put("revisionId", revisionId);
        values.put("occurredAt", postgresTime(occurredAt));
        mapper.insertHistory(values);
    }

    @Override
    public void saveResult(
            String tenantId, String operationType, String idempotencyKey,
            AppointmentCommandReceipt receipt
    ) {
        Map<String, Object> values = new HashMap<>();
        values.put("tenantId", tenantId);
        values.put("operationType", operationType);
        values.put("idempotencyKey", idempotencyKey);
        values.put("appointmentId", receipt.appointmentId());
        values.put("revisionId", receipt.revisionId());
        values.put("status", receipt.status());
        values.put("revisionNo", receipt.revisionNo());
        values.put("aggregateVersion", receipt.aggregateVersion());
        values.put("occurredAt", postgresTime(receipt.occurredAt()));
        mapper.insertResult(values);
    }

    @Override
    public AppointmentCommandReceipt findResult(
            String tenantId, String operationType, String idempotencyKey
    ) {
        Map<String, Object> row = mapper.findResult(tenantId, operationType, idempotencyKey);
        if (row == null) throw new IllegalStateException("Frozen appointment result is missing");
        return new AppointmentCommandReceipt(
                uuid(row, "appointmentId"), uuid(row, "revisionId"), text(row, "status"),
                integer(row, "revisionNo"), number(row, "aggregateVersion"), instant(row, "occurredAt"));
    }

    private AppointmentAggregate appointment(Map<String, Object> row) {
        return new AppointmentAggregate(
                uuid(row, "appointmentId"), text(row, "tenantId"), uuid(row, "projectId"),
                uuid(row, "workOrderId"), uuid(row, "taskId"),
                AppointmentType.valueOf(text(row, "appointmentType")), text(row, "status"),
                text(row, "assignedNetworkId"), text(row, "technicianId"),
                number(row, "aggregateVersion"), integer(row, "currentRevisionNo"),
                instant(row, "appointmentCreatedAt"), text(row, "appointmentCreatedBy"), revision(row));
    }

    private AppointmentRevisionView revision(Map<String, Object> row) {
        return new AppointmentRevisionView(
                uuid(row, "revisionId"), integer(row, "revisionNo"), uuid(row, "previousRevisionId"),
                new AppointmentWindow(
                        instant(row, "windowStart"), instant(row, "windowEnd"),
                        text(row, "timezone"), integer(row, "estimatedDurationMinutes")),
                text(row, "addressRef"), text(row, "addressVersion"),
                text(row, "confirmedPartyType"), text(row, "confirmedPartyRef"),
                text(row, "confirmationChannel"), instant(row, "confirmedAt"),
                text(row, "reasonCode"), text(row, "note"), text(row, "revisionCreatedBy"),
                instant(row, "revisionCreatedAt"));
    }

    private static Map<String, Object> appointmentValues(AppointmentAggregate appointment) {
        Map<String, Object> values = new HashMap<>();
        values.put("appointmentId", appointment.appointmentId());
        values.put("tenantId", appointment.tenantId());
        values.put("projectId", appointment.projectId());
        values.put("workOrderId", appointment.workOrderId());
        values.put("taskId", appointment.taskId());
        values.put("appointmentType", appointment.type().name());
        values.put("status", appointment.status());
        values.put("currentRevisionId", appointment.currentRevision().revisionId());
        values.put("currentRevisionNo", appointment.currentRevisionNo());
        values.put("assignedNetworkId", appointment.assignedNetworkId());
        values.put("technicianId", appointment.technicianId());
        values.put("aggregateVersion", appointment.aggregateVersion());
        values.put("createdBy", appointment.createdBy());
        values.put("createdAt", postgresTime(appointment.createdAt()));
        return values;
    }

    private static Map<String, Object> revisionValues(
            String tenantId, UUID appointmentId, AppointmentRevisionView revision
    ) {
        Map<String, Object> values = new HashMap<>();
        values.put("revisionId", revision.revisionId());
        values.put("tenantId", tenantId);
        values.put("appointmentId", appointmentId);
        values.put("revisionNo", revision.revisionNo());
        values.put("previousRevisionId", revision.previousRevisionId());
        values.put("windowStart", postgresTime(revision.window().start()));
        values.put("windowEnd", postgresTime(revision.window().end()));
        values.put("timezone", revision.window().timezone());
        values.put("estimatedDurationMinutes", revision.window().estimatedDurationMinutes());
        values.put("addressRef", revision.addressRef());
        values.put("addressVersion", revision.addressVersion());
        values.put("confirmedPartyType", revision.confirmedPartyType());
        values.put("confirmedPartyRef", revision.confirmedPartyRef());
        values.put("confirmationChannel", revision.confirmationChannel());
        values.put("confirmedAt", postgresTime(revision.confirmedAt()));
        values.put("reasonCode", revision.reasonCode());
        values.put("note", revision.note());
        values.put("createdBy", revision.createdBy());
        values.put("createdAt", postgresTime(revision.createdAt()));
        return values;
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static int integer(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }

    private static Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        return Instant.parse(value.toString());
    }

    private static OffsetDateTime postgresTime(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
