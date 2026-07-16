package com.serviceos.operations.infrastructure;

import com.serviceos.operations.api.OperationalExceptionAcknowledgement;
import com.serviceos.operations.api.OperationalExceptionItem;
import com.serviceos.operations.application.OperationalExceptionWorkbenchRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
final class MyBatisOperationalExceptionWorkbenchRepository
        implements OperationalExceptionWorkbenchRepository {
    private final OperationalExceptionWorkbenchMapper mapper;

    MyBatisOperationalExceptionWorkbenchRepository(OperationalExceptionWorkbenchMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<OperationalExceptionItem> findPage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            UUID projectId,
            String status,
            String category,
            String severity,
            UUID workOrderId,
            UUID taskId,
            Instant cursorOpenedAt,
            UUID cursorId,
            int fetchSize
    ) {
        return mapper.findPage(
                        tenantId,
                        tenantWide,
                        projectIds.stream().map(UUID::toString).toList(),
                        projectId == null ? null : projectId.toString(),
                        status,
                        category,
                        severity,
                        workOrderId,
                        taskId,
                        postgresTime(cursorOpenedAt),
                        cursorId,
                        fetchSize)
                .stream()
                .map(this::item)
                .toList();
    }

    @Override
    public Optional<OperationalExceptionItem> findById(String tenantId, UUID exceptionId) {
        return Optional.ofNullable(mapper.findById(tenantId, exceptionId)).map(this::item);
    }

    @Override
    public boolean acknowledge(
            String tenantId, UUID exceptionId, long expectedVersion,
            String actorId, String note, Instant acknowledgedAt
    ) {
        return mapper.acknowledge(
                tenantId, exceptionId, expectedVersion, actorId, note, postgresTime(acknowledgedAt)) == 1;
    }

    @Override
    public void saveAcknowledgement(
            String tenantId, String idempotencyKey,
            OperationalExceptionAcknowledgement acknowledgement
    ) {
        mapper.insertAcknowledgement(
                tenantId, idempotencyKey, acknowledgement.exceptionId(),
                acknowledgement.aggregateVersion(), postgresTime(acknowledgement.acknowledgedAt()),
                acknowledgement.acknowledgedBy());
    }

    @Override
    public OperationalExceptionAcknowledgement findAcknowledgement(String tenantId, String idempotencyKey) {
        Map<String, Object> row = mapper.findAcknowledgement(tenantId, idempotencyKey);
        if (row == null) throw new IllegalStateException("Frozen acknowledgement result is missing");
        return new OperationalExceptionAcknowledgement(
                uuid(row, "exceptionId"), "ACKNOWLEDGED", number(row, "aggregateVersion"),
                instant(row, "acknowledgedAt"), string(row, "acknowledgedBy"));
    }

    private OperationalExceptionItem item(Map<String, Object> row) {
        String status = string(row, "status");
        return new OperationalExceptionItem(
                uuid(row, "exceptionId"), uuid(row, "projectId"),
                string(row, "sourceType"), string(row, "sourceId"),
                uuid(row, "sourceAttemptId"), string(row, "sourceTaskType"),
                string(row, "category"), string(row, "severity"), string(row, "errorCode"), status,
                uuid(row, "workOrderId"), uuid(row, "taskId"), uuid(row, "handlingTaskId"),
                number(row, "occurrenceCount"), number(row, "aggregateVersion"),
                instant(row, "openedAt"), instant(row, "lastDetectedAt"),
                instant(row, "acknowledgedAt"), string(row, "acknowledgedBy"),
                string(row, "acknowledgementNote"), instant(row, "resolvedAt"),
                string(row, "resolutionCode"), List.of());
    }

    private static String string(Map<String, Object> row, String key) {
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
