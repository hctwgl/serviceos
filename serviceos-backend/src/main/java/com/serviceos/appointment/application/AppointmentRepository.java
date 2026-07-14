package com.serviceos.appointment.application;

import com.serviceos.appointment.api.AppointmentCommandReceipt;
import com.serviceos.appointment.api.AppointmentRevisionView;
import com.serviceos.appointment.api.ContactAttemptView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 预约持久化端口；所有实现必须显式携带 tenant scope。 */
public interface AppointmentRepository {
    Optional<AppointmentAggregate> findById(String tenantId, UUID appointmentId);

    List<AppointmentAggregate> findByTask(String tenantId, UUID taskId);

    List<AppointmentRevisionView> findRevisions(String tenantId, UUID appointmentId);

    List<ContactAttemptView> findContactAttempts(String tenantId, UUID taskId);

    void appendContactAttempt(String tenantId, ContactAttemptView attempt);

    void saveContactResult(String tenantId, String operationType, String idempotencyKey, UUID attemptId);

    ContactAttemptView findContactResult(String tenantId, String operationType, String idempotencyKey);

    void create(AppointmentAggregate appointment);

    boolean advance(
            String tenantId, UUID appointmentId, long expectedVersion, String expectedStatus,
            String newStatus, int newRevisionNo, UUID newRevisionId);

    boolean advanceStatus(
            String tenantId, UUID appointmentId, long expectedVersion,
            String expectedStatus, String newStatus);

    void appendRevision(String tenantId, UUID appointmentId, AppointmentRevisionView revision);

    void appendHistory(
            String tenantId, UUID appointmentId, long aggregateVersion, String fromStatus,
            String toStatus, String commandCode, String actorId, String reasonCode,
            UUID revisionId, Instant occurredAt);

    void saveResult(
            String tenantId, String operationType, String idempotencyKey,
            AppointmentCommandReceipt receipt);

    AppointmentCommandReceipt findResult(String tenantId, String operationType, String idempotencyKey);
}
