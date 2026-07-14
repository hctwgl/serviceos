package com.serviceos.fieldwork.application;

import com.serviceos.fieldwork.api.VisitCommandReceipt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Visit 持久化端口；所有读写都必须显式携带 tenant scope。 */
public interface VisitRepository {
    Optional<VisitAggregate> findById(String tenantId, UUID visitId);

    List<VisitAggregate> findByWorkOrder(String tenantId, UUID workOrderId);

    Optional<GeofencePolicy> findGeofencePolicy(String tenantId, UUID projectId);

    int nextSequence(String tenantId, UUID taskId);

    void create(VisitAggregate visit);

    boolean terminate(
            String tenantId, UUID visitId, long expectedVersion, String newStatus,
            Instant capturedAt, Instant receivedAt, String resultCode, String exceptionCode,
            String note, List<String> operationRefs, List<String> evidenceRefs);

    void appendFact(
            String tenantId, UUID visitId, long aggregateVersion, String factType,
            Instant capturedAt, Instant receivedAt, Double latitude, Double longitude,
            Double accuracyMeters, String geofenceResult, String resultCode,
            String exceptionCode, String note, List<String> references, String actorId,
            String deviceId, boolean offline);

    void saveResult(String tenantId, String operationType, String idempotencyKey, VisitCommandReceipt receipt);

    VisitCommandReceipt findResult(String tenantId, String operationType, String idempotencyKey);
}
