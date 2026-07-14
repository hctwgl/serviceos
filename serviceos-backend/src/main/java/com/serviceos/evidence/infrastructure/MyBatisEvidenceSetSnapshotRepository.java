package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.EvidenceSetSnapshotMemberView;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.application.EvidenceSetSnapshotRepository;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
final class MyBatisEvidenceSetSnapshotRepository implements EvidenceSetSnapshotRepository {
    private final EvidenceSetSnapshotMapper mapper;

    MyBatisEvidenceSetSnapshotRepository(EvidenceSetSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(String tenantId, EvidenceSetSnapshotView snapshot) {
        Map<String, Object> values = new HashMap<>();
        values.put("snapshotId", snapshot.evidenceSetSnapshotId().toString());
        values.put("tenantId", tenantId);
        values.put("projectId", snapshot.projectId().toString());
        values.put("taskId", snapshot.taskId().toString());
        values.put("resolutionId", snapshot.resolutionId().toString());
        values.put("purpose", snapshot.purpose());
        values.put("memberCount", snapshot.memberCount());
        values.put("contentDigest", snapshot.contentDigest());
        values.put("eligibilitySummary", snapshot.eligibilitySummaryJson());
        values.put("createdBy", snapshot.createdBy());
        values.put("createdAt", snapshot.createdAt());
        mapper.insertSnapshot(values);
        for (EvidenceSetSnapshotMemberView member : snapshot.members()) {
            Map<String, Object> memberValues = new HashMap<>();
            memberValues.put("memberId", member.memberId().toString());
            memberValues.put("tenantId", tenantId);
            memberValues.put("snapshotId", snapshot.evidenceSetSnapshotId().toString());
            memberValues.put("projectId", snapshot.projectId().toString());
            memberValues.put("taskId", snapshot.taskId().toString());
            memberValues.put("slotId", member.evidenceSlotId().toString());
            memberValues.put("evidenceItemId", member.evidenceItemId().toString());
            memberValues.put("evidenceRevisionId", member.evidenceRevisionId().toString());
            memberValues.put("revisionNumber", member.revisionNumber());
            memberValues.put("revisionStatus", member.revisionStatus());
            memberValues.put("contentDigest", member.contentDigest());
            memberValues.put("validationDigest", member.validationDigest());
            memberValues.put("memberOrdinal", member.memberOrdinal());
            mapper.insertMember(memberValues);
        }
    }

    @Override
    public Optional<EvidenceSetSnapshotView> find(String tenantId, UUID snapshotId) {
        Map<String, Object> row = mapper.findSnapshot(tenantId, snapshotId.toString());
        if (row == null) {
            return Optional.empty();
        }
        List<EvidenceSetSnapshotMemberView> members = mapper.listMembers(
                        tenantId, snapshotId.toString()).stream()
                .map(this::memberView).toList();
        return Optional.of(new EvidenceSetSnapshotView(
                uuid(row, "snapshotId"), uuid(row, "taskId"), uuid(row, "projectId"),
                uuid(row, "resolutionId"), text(row, "purpose"),
                number(row, "memberCount").intValue(), text(row, "contentDigest"),
                text(row, "eligibilitySummary"), text(row, "createdBy"),
                instant(row.get("createdAt")), members));
    }

    @Override
    public Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey) {
        String value = mapper.findCommandResult(tenantId, operationType, idempotencyKey);
        return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
    }

    @Override
    public void saveCommandResult(String tenantId, String operationType, String idempotencyKey, UUID resultId) {
        Map<String, Object> values = new HashMap<>();
        values.put("tenantId", tenantId);
        values.put("operationType", operationType);
        values.put("idempotencyKey", idempotencyKey);
        values.put("resultId", resultId.toString());
        mapper.saveCommandResult(values);
    }

    private EvidenceSetSnapshotMemberView memberView(Map<String, Object> row) {
        return new EvidenceSetSnapshotMemberView(
                uuid(row, "memberId"), uuid(row, "slotId"), uuid(row, "evidenceItemId"),
                uuid(row, "evidenceRevisionId"), number(row, "revisionNumber").intValue(),
                text(row, "revisionStatus"), text(row, "contentDigest"),
                text(row, "validationDigest"), number(row, "memberOrdinal").intValue());
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalArgumentException("不支持的数据库时间类型: " + value);
    }
}
