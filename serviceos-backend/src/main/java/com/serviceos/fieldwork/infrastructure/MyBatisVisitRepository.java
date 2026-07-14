package com.serviceos.fieldwork.infrastructure;

import com.serviceos.fieldwork.api.VisitCommandReceipt;
import com.serviceos.fieldwork.api.VisitLocation;
import com.serviceos.fieldwork.application.GeofencePolicy;
import com.serviceos.fieldwork.application.VisitAggregate;
import com.serviceos.fieldwork.application.VisitRepository;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
final class MyBatisVisitRepository implements VisitRepository {
    private final VisitMapper mapper;
    private final ObjectMapper objectMapper;

    MyBatisVisitRepository(VisitMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<VisitAggregate> findById(String tenantId, UUID visitId) {
        return Optional.ofNullable(mapper.findById(tenantId, visitId)).map(this::visit);
    }

    @Override
    public List<VisitAggregate> findByWorkOrder(String tenantId, UUID workOrderId) {
        return mapper.findByWorkOrder(tenantId, workOrderId).stream().map(this::visit).toList();
    }

    @Override
    public Optional<GeofencePolicy> findGeofencePolicy(String tenantId, UUID projectId) {
        return Optional.ofNullable(mapper.findGeofencePolicy(tenantId, projectId)).map(row -> new GeofencePolicy(
                uuid(row, "projectId"), decimal(row, "targetLatitude"), decimal(row, "targetLongitude"),
                decimal(row, "radiusMeters"), decimal(row, "maxAccuracyMeters"),
                text(row, "exceptionAction"), text(row, "policyVersion")));
    }

    @Override
    public int nextSequence(String tenantId, UUID taskId) {
        // 同一 Task 的并发二次上门先锁定 Task 权威行，避免 MAX+1 竞争产生重复序号。
        if (mapper.lockTask(tenantId, taskId) == null) {
            throw new IllegalStateException("Visit Task disappeared while allocating sequence");
        }
        return mapper.nextSequence(tenantId, taskId);
    }

    @Override
    public void create(VisitAggregate visit) {
        Map<String, Object> values = values(visit);
        mapper.insertVisit(values);
    }

    @Override
    public boolean terminate(
            String tenantId, UUID visitId, long expectedVersion, String newStatus,
            Instant capturedAt, Instant receivedAt, String resultCode, String exceptionCode,
            String note, List<String> operationRefs, List<String> evidenceRefs
    ) {
        Map<String, Object> values = new HashMap<>();
        values.put("tenantId", tenantId);
        values.put("visitId", visitId);
        values.put("expectedVersion", expectedVersion);
        values.put("newStatus", newStatus);
        values.put("capturedAt", time(capturedAt));
        values.put("receivedAt", time(receivedAt));
        values.put("resultCode", resultCode);
        values.put("exceptionCode", exceptionCode);
        values.put("note", note);
        values.put("operationRefs", json(operationRefs));
        values.put("evidenceRefs", json(evidenceRefs));
        return mapper.terminate(values) == 1;
    }

    @Override
    public void appendFact(
            String tenantId, UUID visitId, long aggregateVersion, String factType,
            Instant capturedAt, Instant receivedAt, Double latitude, Double longitude,
            Double accuracyMeters, String geofenceResult, String resultCode, String exceptionCode,
            String note, List<String> references, String actorId, String deviceId, boolean offline
    ) {
        Map<String, Object> values = new HashMap<>();
        values.put("visitFactId", UUID.randomUUID());
        values.put("tenantId", tenantId);
        values.put("visitId", visitId);
        values.put("aggregateVersion", aggregateVersion);
        values.put("factType", factType);
        values.put("capturedAt", time(capturedAt));
        values.put("receivedAt", time(receivedAt));
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("accuracyMeters", accuracyMeters);
        values.put("geofenceResult", geofenceResult);
        values.put("resultCode", resultCode);
        values.put("exceptionCode", exceptionCode);
        values.put("note", note);
        values.put("referenceList", json(references));
        values.put("actorId", actorId);
        values.put("deviceId", deviceId);
        values.put("offline", offline);
        mapper.insertFact(values);
    }

    @Override
    public void saveResult(String tenantId, String operationType, String idempotencyKey, VisitCommandReceipt receipt) {
        Map<String, Object> values = new HashMap<>();
        values.put("tenantId", tenantId);
        values.put("operationType", operationType);
        values.put("idempotencyKey", idempotencyKey);
        values.put("visitId", receipt.visitId());
        values.put("status", receipt.status());
        values.put("aggregateVersion", receipt.aggregateVersion());
        values.put("geofenceResult", receipt.geofenceResult());
        values.put("policyDecision", receipt.policyDecision());
        values.put("occurredAt", time(receipt.occurredAt()));
        mapper.insertResult(values);
    }

    @Override
    public VisitCommandReceipt findResult(String tenantId, String operationType, String idempotencyKey) {
        Map<String, Object> row = mapper.findResult(tenantId, operationType, idempotencyKey);
        if (row == null) throw new IllegalStateException("Frozen Visit result is missing");
        return new VisitCommandReceipt(uuid(row, "visitId"), text(row, "status"),
                number(row, "aggregateVersion"), text(row, "geofenceResult"),
                text(row, "policyDecision"), instant(row, "occurredAt"));
    }

    private Map<String, Object> values(VisitAggregate visit) {
        Map<String, Object> values = new HashMap<>();
        values.put("visitId", visit.visitId());
        values.put("tenantId", visit.tenantId());
        values.put("projectId", visit.projectId());
        values.put("workOrderId", visit.workOrderId());
        values.put("taskId", visit.taskId());
        values.put("appointmentId", visit.appointmentId());
        values.put("visitSequence", visit.visitSequence());
        values.put("technicianId", visit.technicianId());
        values.put("networkId", visit.networkId());
        values.put("status", visit.status());
        values.put("checkInCapturedAt", time(visit.checkInCapturedAt()));
        values.put("checkInReceivedAt", time(visit.checkInReceivedAt()));
        values.put("latitude", visit.checkInLocation().latitude());
        values.put("longitude", visit.checkInLocation().longitude());
        values.put("accuracyMeters", visit.checkInLocation().accuracyMeters());
        values.put("geofenceResult", visit.geofenceResult());
        values.put("geofenceDistanceMeters", visit.geofenceDistanceMeters());
        values.put("geofencePolicyVersion", visit.geofencePolicyVersion());
        values.put("policyDecision", visit.policyDecision());
        values.put("deviceId", visit.deviceId());
        values.put("deviceCommandId", visit.deviceCommandId());
        values.put("offline", visit.offline());
        values.put("aggregateVersion", visit.aggregateVersion());
        values.put("createdBy", visit.createdBy());
        values.put("createdAt", time(visit.createdAt()));
        values.put("updatedAt", time(visit.updatedAt()));
        return values;
    }

    private VisitAggregate visit(Map<String, Object> row) {
        return new VisitAggregate(
                uuid(row, "visitId"), text(row, "tenantId"), uuid(row, "projectId"),
                uuid(row, "workOrderId"), uuid(row, "taskId"), uuid(row, "appointmentId"),
                integer(row, "visitSequence"), text(row, "technicianId"), text(row, "networkId"),
                text(row, "status"), instant(row, "checkInCapturedAt"), instant(row, "checkInReceivedAt"),
                new VisitLocation(decimal(row, "checkInLatitude"), decimal(row, "checkInLongitude"),
                        decimal(row, "checkInAccuracyMeters")),
                text(row, "geofenceResult"), nullableDecimal(row, "geofenceDistanceMeters"),
                text(row, "geofencePolicyVersion"), text(row, "policyDecision"), text(row, "deviceId"),
                text(row, "deviceCommandId"), bool(row, "offlineFlag"), instant(row, "checkOutCapturedAt"),
                instant(row, "checkOutReceivedAt"), text(row, "resultCode"), text(row, "exceptionCode"),
                text(row, "note"), strings(row, "operationRefs"), strings(row, "evidenceRefs"),
                number(row, "aggregateVersion"), text(row, "createdBy"), instant(row, "createdAt"),
                instant(row, "updatedAt"));
    }

    private String json(List<String> values) {
        try { return objectMapper.writeValueAsString(values == null ? List.of() : values); }
        catch (JacksonException exception) { throw new IllegalStateException("Visit references serialization failed", exception); }
    }

    private List<String> strings(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return List.of();
        try {
            JsonNode node = objectMapper.readTree(value.toString());
            ArrayList<String> result = new ArrayList<>();
            node.forEach(item -> result.add(item.asText()));
            return List.copyOf(result);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored Visit references are invalid", exception);
        }
    }

    private static String text(Map<String, Object> row, String key) { Object value = row.get(key); return value == null ? null : value.toString(); }
    private static UUID uuid(Map<String, Object> row, String key) { Object value = row.get(key); return value == null ? null : value instanceof UUID id ? id : UUID.fromString(value.toString()); }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static int integer(Map<String, Object> row, String key) { return ((Number) row.get(key)).intValue(); }
    private static double decimal(Map<String, Object> row, String key) { return ((Number) row.get(key)).doubleValue(); }
    private static Double nullableDecimal(Map<String, Object> row, String key) { Object value = row.get(key); return value == null ? null : ((Number) value).doubleValue(); }
    private static boolean bool(Map<String, Object> row, String key) { return Boolean.TRUE.equals(row.get(key)); }
    private static Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key); if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime time) return time.toInstant();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        return Instant.parse(value.toString());
    }
    private static OffsetDateTime time(Instant value) { return value == null ? null : value.atOffset(ZoneOffset.UTC); }
}
