package com.serviceos.sla.infrastructure;

import com.serviceos.sla.application.SlaQueryRepository;
import com.serviceos.sla.application.SlaStoredInstance;
import com.serviceos.sla.application.SlaStoredMilestone;
import com.serviceos.sla.application.SlaStoredSegment;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
final class MyBatisSlaQueryRepository implements SlaQueryRepository {
    private final SlaQueryMapper mapper;

    MyBatisSlaQueryRepository(SlaQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SlaStoredInstance> findPage(
            String tenantId, boolean tenantWide, List<UUID> projectIds, UUID workOrderId, String status,
            Instant cursorDeadlineAt, UUID cursorId, int fetchSize
    ) {
        return mapper.findPage(tenantId, tenantWide,
                projectIds.stream().map(UUID::toString).toList(), workOrderId, status,
                postgresTime(cursorDeadlineAt), cursorId, fetchSize).stream().map(this::instance).toList();
    }

    @Override
    public Optional<SlaStoredInstance> findById(String tenantId, UUID slaInstanceId) {
        return Optional.ofNullable(mapper.findById(tenantId, slaInstanceId)).map(this::instance);
    }

    @Override
    public List<SlaStoredSegment> findSegments(String tenantId, UUID slaInstanceId) {
        return mapper.findSegments(tenantId, slaInstanceId).stream().map(row -> new SlaStoredSegment(
                uuid(row, "segmentId"), number(row, "segmentNo").intValue(), string(row, "segmentType"),
                instant(row, "startedAt"), instant(row, "endedAt"), nullableLong(row, "elapsedSeconds"),
                uuid(row, "startEventId"), uuid(row, "endEventId"))).toList();
    }

    @Override
    public List<SlaStoredMilestone> findMilestones(String tenantId, UUID slaInstanceId) {
        return mapper.findMilestones(tenantId, slaInstanceId).stream().map(row -> new SlaStoredMilestone(
                uuid(row, "milestoneId"), string(row, "milestoneType"), instant(row, "scheduledAt"),
                string(row, "status"), instant(row, "triggeredAt"), instant(row, "detectedAt"),
                uuid(row, "triggerEventId"))).toList();
    }

    private SlaStoredInstance instance(Map<String, Object> row) {
        return new SlaStoredInstance(
                uuid(row, "slaInstanceId"), uuid(row, "projectId"), uuid(row, "workOrderId"),
                uuid(row, "taskId"), string(row, "slaRef"), uuid(row, "policyVersionId"),
                string(row, "policySemanticVersion"), string(row, "policyContentDigest"),
                string(row, "clockMode"), number(row, "targetDurationSeconds").longValue(),
                instant(row, "startedAt"), instant(row, "deadlineAt"), string(row, "status"),
                instant(row, "breachedAt"), instant(row, "breachDetectedAt"),
                instant(row, "completedAt"), nullableLong(row, "elapsedSeconds"),
                number(row, "aggregateVersion").longValue());
    }

    private static String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    private static Long nullableLong(Map<String, Object> row, String key) {
        Number value = (Number) row.get(key);
        return value == null ? null : value.longValue();
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
