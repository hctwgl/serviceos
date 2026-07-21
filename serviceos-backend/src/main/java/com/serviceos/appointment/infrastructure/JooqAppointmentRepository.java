package com.serviceos.appointment.infrastructure;

import com.serviceos.appointment.api.AppointmentCommandReceipt;
import com.serviceos.appointment.api.AppointmentRevisionView;
import com.serviceos.appointment.api.AppointmentType;
import com.serviceos.appointment.api.AppointmentWindow;
import com.serviceos.appointment.api.ContactAttemptView;
import com.serviceos.appointment.api.ContactResultCode;
import com.serviceos.appointment.application.AppointmentAggregate;
import com.serviceos.appointment.application.AppointmentRepository;
import com.serviceos.jooq.generated.tables.AptAppointment;
import com.serviceos.jooq.generated.tables.AptAppointmentRevision;
import com.serviceos.jooq.generated.tables.AptContactAttempt;
import com.serviceos.jooq.generated.tables.AptContactAttemptCommandResult;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.AptAppointment.APT_APPOINTMENT;
import static com.serviceos.jooq.generated.tables.AptAppointmentCommandResult.APT_APPOINTMENT_COMMAND_RESULT;
import static com.serviceos.jooq.generated.tables.AptAppointmentRevision.APT_APPOINTMENT_REVISION;
import static com.serviceos.jooq.generated.tables.AptAppointmentStatusHistory.APT_APPOINTMENT_STATUS_HISTORY;
import static com.serviceos.jooq.generated.tables.AptContactAttempt.APT_CONTACT_ATTEMPT;
import static com.serviceos.jooq.generated.tables.AptContactAttemptCommandResult.APT_CONTACT_ATTEMPT_COMMAND_RESULT;

/**
 * 预约聚合的 jOOQ 持久化（ADR-091）。聚合读取固定 JOIN 当前 Revision；状态推进携带原状态与
 * 版本条件并校验影响行数；jsonb 证据引用由全局 JsonbStringConverter 直接绑定 JSON 文本。
 */
@Repository
final class JooqAppointmentRepository implements AppointmentRepository {
    private static final AptAppointment A = APT_APPOINTMENT;
    private static final AptAppointmentRevision R = APT_APPOINTMENT_REVISION;
    private static final AptContactAttempt C = APT_CONTACT_ATTEMPT;
    private static final AptContactAttemptCommandResult CR = APT_CONTACT_ATTEMPT_COMMAND_RESULT;
    private static final List<SelectField<?>> AGGREGATE_FIELDS = List.of(
            A.APPOINTMENT_ID, A.TENANT_ID, A.PROJECT_ID, A.WORK_ORDER_ID, A.TASK_ID,
            A.APPOINTMENT_TYPE, A.STATUS, A.ASSIGNED_NETWORK_ID, A.TECHNICIAN_ID,
            A.AGGREGATE_VERSION, A.CURRENT_REVISION_NO, A.CREATED_AT, A.CREATED_BY,
            R.REVISION_ID, R.REVISION_NO, R.REVISION_KIND, R.PREVIOUS_REVISION_ID,
            R.WINDOW_START, R.WINDOW_END, R.TIMEZONE, R.ESTIMATED_DURATION_MINUTES,
            R.ADDRESS_REF, R.ADDRESS_VERSION, R.CONFIRMED_PARTY_TYPE, R.CONFIRMED_PARTY_REF,
            R.CONFIRMATION_CHANNEL, R.CONFIRMED_AT, R.REASON_CODE, R.NOTE,
            R.NO_SHOW_PARTY_TYPE, R.NO_SHOW_PARTY_REF, R.NO_SHOW_EVIDENCE_REFS,
            R.CREATED_BY, R.CREATED_AT);
    private static final List<SelectField<?>> REVISION_FIELDS = List.of(
            R.REVISION_ID, R.REVISION_NO, R.REVISION_KIND, R.PREVIOUS_REVISION_ID,
            R.WINDOW_START, R.WINDOW_END, R.TIMEZONE, R.ESTIMATED_DURATION_MINUTES,
            R.ADDRESS_REF, R.ADDRESS_VERSION, R.CONFIRMED_PARTY_TYPE, R.CONFIRMED_PARTY_REF,
            R.CONFIRMATION_CHANNEL, R.CONFIRMED_AT, R.REASON_CODE, R.NOTE,
            R.NO_SHOW_PARTY_TYPE, R.NO_SHOW_PARTY_REF, R.NO_SHOW_EVIDENCE_REFS,
            R.CREATED_BY, R.CREATED_AT);
    private static final List<SelectField<?>> CONTACT_FIELDS = List.of(
            C.CONTACT_ATTEMPT_ID, C.PROJECT_ID, C.WORK_ORDER_ID, C.TASK_ID, C.CHANNEL,
            C.CONTACTED_PARTY_REF, C.STARTED_AT, C.ENDED_AT, C.RESULT_CODE, C.NOTE,
            C.NEXT_CONTACT_AT, C.RECORDING_REF, C.ACTOR_ID, C.CREATED_AT);

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    JooqAppointmentRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AppointmentAggregate> findById(String tenantId, UUID appointmentId) {
        return dsl.select(AGGREGATE_FIELDS)
                .from(A)
                .join(R).on(R.REVISION_ID.eq(A.CURRENT_REVISION_ID))
                .where(A.TENANT_ID.eq(tenantId))
                .and(A.APPOINTMENT_ID.eq(appointmentId))
                .fetchOptional()
                .map(this::appointment);
    }

    @Override
    public List<AppointmentAggregate> findByTask(String tenantId, UUID taskId) {
        return dsl.select(AGGREGATE_FIELDS)
                .from(A)
                .join(R).on(R.REVISION_ID.eq(A.CURRENT_REVISION_ID))
                .where(A.TENANT_ID.eq(tenantId))
                .and(A.TASK_ID.eq(taskId))
                .orderBy(A.CREATED_AT, A.APPOINTMENT_ID)
                .fetch(this::appointment);
    }

    @Override
    public List<AppointmentRevisionView> findRevisions(String tenantId, UUID appointmentId) {
        return dsl.select(REVISION_FIELDS)
                .from(R)
                .where(R.TENANT_ID.eq(tenantId))
                .and(R.APPOINTMENT_ID.eq(appointmentId))
                .orderBy(R.REVISION_NO)
                .fetch(this::revision);
    }

    @Override
    public List<ContactAttemptView> findContactAttempts(String tenantId, UUID taskId) {
        return dsl.select(CONTACT_FIELDS)
                .from(C)
                .where(C.TENANT_ID.eq(tenantId))
                .and(C.TASK_ID.eq(taskId))
                .orderBy(C.STARTED_AT, C.CONTACT_ATTEMPT_ID)
                .fetch(this::contactAttempt);
    }

    @Override
    public Optional<ContactAttemptView> findContactAttemptById(String tenantId, UUID contactAttemptId) {
        return dsl.select(CONTACT_FIELDS)
                .from(C)
                .where(C.TENANT_ID.eq(tenantId))
                .and(C.CONTACT_ATTEMPT_ID.eq(contactAttemptId))
                .fetchOptional()
                .map(this::contactAttempt);
    }

    @Override
    public void appendContactAttempt(String tenantId, ContactAttemptView attempt) {
        dsl.insertInto(C)
                .set(C.CONTACT_ATTEMPT_ID, attempt.contactAttemptId())
                .set(C.TENANT_ID, tenantId)
                .set(C.PROJECT_ID, attempt.projectId())
                .set(C.WORK_ORDER_ID, attempt.workOrderId())
                .set(C.TASK_ID, attempt.taskId())
                .set(C.CHANNEL, attempt.channel())
                .set(C.CONTACTED_PARTY_REF, attempt.contactedPartyRef())
                .set(C.STARTED_AT, attempt.startedAt())
                .set(C.ENDED_AT, attempt.endedAt())
                .set(C.RESULT_CODE, attempt.resultCode().name())
                .set(C.NOTE, attempt.note())
                .set(C.NEXT_CONTACT_AT, attempt.nextContactAt())
                .set(C.RECORDING_REF, attempt.recordingRef())
                .set(C.ACTOR_ID, attempt.actorId())
                .set(C.CREATED_AT, attempt.createdAt())
                .execute();
    }

    @Override
    public void saveContactResult(String tenantId, String operationType, String idempotencyKey, UUID attemptId) {
        dsl.insertInto(CR)
                .set(CR.TENANT_ID, tenantId)
                .set(CR.OPERATION_TYPE, operationType)
                .set(CR.IDEMPOTENCY_KEY, idempotencyKey)
                .set(CR.CONTACT_ATTEMPT_ID, attemptId)
                .execute();
    }

    @Override
    public ContactAttemptView findContactResult(String tenantId, String operationType, String idempotencyKey) {
        return dsl.select(CONTACT_FIELDS)
                .from(CR)
                .join(C).on(C.CONTACT_ATTEMPT_ID.eq(CR.CONTACT_ATTEMPT_ID))
                .where(CR.TENANT_ID.eq(tenantId))
                .and(CR.OPERATION_TYPE.eq(operationType))
                .and(CR.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(this::contactAttempt)
                .orElseThrow(() -> new IllegalStateException("Frozen contact-attempt result is missing"));
    }

    @Override
    public void create(AppointmentAggregate appointment) {
        dsl.insertInto(A)
                .set(A.APPOINTMENT_ID, appointment.appointmentId())
                .set(A.TENANT_ID, appointment.tenantId())
                .set(A.PROJECT_ID, appointment.projectId())
                .set(A.WORK_ORDER_ID, appointment.workOrderId())
                .set(A.TASK_ID, appointment.taskId())
                .set(A.APPOINTMENT_TYPE, appointment.type().name())
                .set(A.STATUS, appointment.status())
                .set(A.CURRENT_REVISION_ID, appointment.currentRevision().revisionId())
                .set(A.CURRENT_REVISION_NO, appointment.currentRevisionNo())
                .set(A.ASSIGNED_NETWORK_ID, appointment.assignedNetworkId())
                .set(A.TECHNICIAN_ID, appointment.technicianId())
                .set(A.AGGREGATE_VERSION, appointment.aggregateVersion())
                .set(A.CREATED_BY, appointment.createdBy())
                .set(A.CREATED_AT, appointment.createdAt())
                .execute();
        insertRevision(appointment.tenantId(), appointment.appointmentId(), appointment.currentRevision());
    }

    @Override
    public boolean advance(
            String tenantId, UUID appointmentId, long expectedVersion, String expectedStatus,
            String newStatus, int newRevisionNo, UUID newRevisionId
    ) {
        return dsl.update(A)
                .set(A.STATUS, newStatus)
                .set(A.CURRENT_REVISION_ID, newRevisionId)
                .set(A.CURRENT_REVISION_NO, newRevisionNo)
                .set(A.AGGREGATE_VERSION, A.AGGREGATE_VERSION.plus(1))
                .where(A.TENANT_ID.eq(tenantId))
                .and(A.APPOINTMENT_ID.eq(appointmentId))
                .and(A.STATUS.eq(expectedStatus))
                .and(A.AGGREGATE_VERSION.eq(expectedVersion))
                .execute() == 1;
    }

    @Override
    public boolean advanceStatus(
            String tenantId, UUID appointmentId, long expectedVersion,
            String expectedStatus, String newStatus
    ) {
        return dsl.update(A)
                .set(A.STATUS, newStatus)
                .set(A.AGGREGATE_VERSION, A.AGGREGATE_VERSION.plus(1))
                .where(A.TENANT_ID.eq(tenantId))
                .and(A.APPOINTMENT_ID.eq(appointmentId))
                .and(A.STATUS.eq(expectedStatus))
                .and(A.AGGREGATE_VERSION.eq(expectedVersion))
                .execute() == 1;
    }

    @Override
    public void appendRevision(String tenantId, UUID appointmentId, AppointmentRevisionView revision) {
        insertRevision(tenantId, appointmentId, revision);
    }

    @Override
    public void appendHistory(
            String tenantId, UUID appointmentId, long aggregateVersion, String fromStatus,
            String toStatus, String commandCode, String actorId, String reasonCode,
            UUID revisionId, Instant occurredAt
    ) {
        dsl.insertInto(APT_APPOINTMENT_STATUS_HISTORY)
                .set(APT_APPOINTMENT_STATUS_HISTORY.HISTORY_ID, UUID.randomUUID())
                .set(APT_APPOINTMENT_STATUS_HISTORY.TENANT_ID, tenantId)
                .set(APT_APPOINTMENT_STATUS_HISTORY.APPOINTMENT_ID, appointmentId)
                .set(APT_APPOINTMENT_STATUS_HISTORY.AGGREGATE_VERSION, aggregateVersion)
                .set(APT_APPOINTMENT_STATUS_HISTORY.FROM_STATUS, fromStatus)
                .set(APT_APPOINTMENT_STATUS_HISTORY.TO_STATUS, toStatus)
                .set(APT_APPOINTMENT_STATUS_HISTORY.COMMAND_CODE, commandCode)
                .set(APT_APPOINTMENT_STATUS_HISTORY.ACTOR_ID, actorId)
                .set(APT_APPOINTMENT_STATUS_HISTORY.REASON_CODE, reasonCode)
                .set(APT_APPOINTMENT_STATUS_HISTORY.REVISION_ID, revisionId)
                .set(APT_APPOINTMENT_STATUS_HISTORY.OCCURRED_AT, occurredAt)
                .execute();
    }

    @Override
    public void saveResult(
            String tenantId, String operationType, String idempotencyKey,
            AppointmentCommandReceipt receipt
    ) {
        dsl.insertInto(APT_APPOINTMENT_COMMAND_RESULT)
                .set(APT_APPOINTMENT_COMMAND_RESULT.TENANT_ID, tenantId)
                .set(APT_APPOINTMENT_COMMAND_RESULT.OPERATION_TYPE, operationType)
                .set(APT_APPOINTMENT_COMMAND_RESULT.IDEMPOTENCY_KEY, idempotencyKey)
                .set(APT_APPOINTMENT_COMMAND_RESULT.APPOINTMENT_ID, receipt.appointmentId())
                .set(APT_APPOINTMENT_COMMAND_RESULT.REVISION_ID, receipt.revisionId())
                .set(APT_APPOINTMENT_COMMAND_RESULT.STATUS, receipt.status())
                .set(APT_APPOINTMENT_COMMAND_RESULT.REVISION_NO, receipt.revisionNo())
                .set(APT_APPOINTMENT_COMMAND_RESULT.AGGREGATE_VERSION, receipt.aggregateVersion())
                .set(APT_APPOINTMENT_COMMAND_RESULT.OCCURRED_AT, receipt.occurredAt())
                .execute();
    }

    @Override
    public AppointmentCommandReceipt findResult(
            String tenantId, String operationType, String idempotencyKey
    ) {
        return dsl.select(
                        APT_APPOINTMENT_COMMAND_RESULT.APPOINTMENT_ID,
                        APT_APPOINTMENT_COMMAND_RESULT.REVISION_ID,
                        APT_APPOINTMENT_COMMAND_RESULT.STATUS,
                        APT_APPOINTMENT_COMMAND_RESULT.REVISION_NO,
                        APT_APPOINTMENT_COMMAND_RESULT.AGGREGATE_VERSION,
                        APT_APPOINTMENT_COMMAND_RESULT.OCCURRED_AT)
                .from(APT_APPOINTMENT_COMMAND_RESULT)
                .where(APT_APPOINTMENT_COMMAND_RESULT.TENANT_ID.eq(tenantId))
                .and(APT_APPOINTMENT_COMMAND_RESULT.OPERATION_TYPE.eq(operationType))
                .and(APT_APPOINTMENT_COMMAND_RESULT.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(row -> new AppointmentCommandReceipt(
                        row.get(APT_APPOINTMENT_COMMAND_RESULT.APPOINTMENT_ID),
                        row.get(APT_APPOINTMENT_COMMAND_RESULT.REVISION_ID),
                        row.get(APT_APPOINTMENT_COMMAND_RESULT.STATUS),
                        row.get(APT_APPOINTMENT_COMMAND_RESULT.REVISION_NO),
                        row.get(APT_APPOINTMENT_COMMAND_RESULT.AGGREGATE_VERSION),
                        row.get(APT_APPOINTMENT_COMMAND_RESULT.OCCURRED_AT)))
                .orElseThrow(() -> new IllegalStateException("Frozen appointment result is missing"));
    }

    private void insertRevision(String tenantId, UUID appointmentId, AppointmentRevisionView revision) {
        dsl.insertInto(R)
                .set(R.REVISION_ID, revision.revisionId())
                .set(R.TENANT_ID, tenantId)
                .set(R.APPOINTMENT_ID, appointmentId)
                .set(R.REVISION_NO, revision.revisionNo())
                .set(R.PREVIOUS_REVISION_ID, revision.previousRevisionId())
                .set(R.REVISION_KIND, revision.revisionKind())
                .set(R.WINDOW_START, revision.window().start())
                .set(R.WINDOW_END, revision.window().end())
                .set(R.TIMEZONE, revision.window().timezone())
                .set(R.ESTIMATED_DURATION_MINUTES, revision.window().estimatedDurationMinutes())
                .set(R.ADDRESS_REF, revision.addressRef())
                .set(R.ADDRESS_VERSION, revision.addressVersion())
                .set(R.CONFIRMED_PARTY_TYPE, revision.confirmedPartyType())
                .set(R.CONFIRMED_PARTY_REF, revision.confirmedPartyRef())
                .set(R.CONFIRMATION_CHANNEL, revision.confirmationChannel())
                .set(R.CONFIRMED_AT, revision.confirmedAt())
                .set(R.REASON_CODE, revision.reasonCode())
                .set(R.NOTE, revision.note())
                .set(R.NO_SHOW_PARTY_TYPE, revision.noShowPartyType())
                .set(R.NO_SHOW_PARTY_REF, revision.noShowPartyRef())
                .set(R.NO_SHOW_EVIDENCE_REFS, toJson(revision.noShowEvidenceRefs()))
                .set(R.CREATED_BY, revision.createdBy())
                .set(R.CREATED_AT, revision.createdAt())
                .execute();
    }

    private AppointmentAggregate appointment(Record row) {
        return new AppointmentAggregate(
                row.get(A.APPOINTMENT_ID), row.get(A.TENANT_ID), row.get(A.PROJECT_ID),
                row.get(A.WORK_ORDER_ID), row.get(A.TASK_ID),
                AppointmentType.valueOf(row.get(A.APPOINTMENT_TYPE)), row.get(A.STATUS),
                row.get(A.ASSIGNED_NETWORK_ID), row.get(A.TECHNICIAN_ID),
                row.get(A.AGGREGATE_VERSION), row.get(A.CURRENT_REVISION_NO),
                row.get(A.CREATED_AT), row.get(A.CREATED_BY), revision(row));
    }

    private AppointmentRevisionView revision(Record row) {
        return new AppointmentRevisionView(
                row.get(R.REVISION_ID), row.get(R.REVISION_NO), row.get(R.PREVIOUS_REVISION_ID),
                row.get(R.REVISION_KIND),
                new AppointmentWindow(
                        row.get(R.WINDOW_START), row.get(R.WINDOW_END),
                        row.get(R.TIMEZONE), row.get(R.ESTIMATED_DURATION_MINUTES)),
                row.get(R.ADDRESS_REF), row.get(R.ADDRESS_VERSION),
                row.get(R.CONFIRMED_PARTY_TYPE), row.get(R.CONFIRMED_PARTY_REF),
                row.get(R.CONFIRMATION_CHANNEL), row.get(R.CONFIRMED_AT),
                row.get(R.REASON_CODE), row.get(R.NOTE), row.get(R.NO_SHOW_PARTY_TYPE),
                row.get(R.NO_SHOW_PARTY_REF), jsonStrings(row.get(R.NO_SHOW_EVIDENCE_REFS)),
                row.get(R.CREATED_BY), row.get(R.CREATED_AT));
    }

    private ContactAttemptView contactAttempt(Record row) {
        return new ContactAttemptView(
                row.get(C.CONTACT_ATTEMPT_ID), row.get(C.PROJECT_ID), row.get(C.WORK_ORDER_ID),
                row.get(C.TASK_ID), row.get(C.CHANNEL), row.get(C.CONTACTED_PARTY_REF),
                row.get(C.STARTED_AT), row.get(C.ENDED_AT),
                ContactResultCode.valueOf(row.get(C.RESULT_CODE)), row.get(C.NOTE),
                row.get(C.NEXT_CONTACT_AT), row.get(C.RECORDING_REF), row.get(C.ACTOR_ID),
                row.get(C.CREATED_AT));
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JacksonException exception) {
            throw new IllegalStateException("No-show evidence reference serialization failed", exception);
        }
    }

    private List<String> jsonStrings(String value) {
        if (value == null) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            java.util.ArrayList<String> result = new java.util.ArrayList<>();
            node.forEach(item -> result.add(item.asText()));
            return List.copyOf(result);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored no-show evidence references are invalid", exception);
        }
    }
}
