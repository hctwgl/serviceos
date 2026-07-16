package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.CorrectionCaseQueueItem;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.CorrectionResubmissionView;
import com.serviceos.evidence.application.CorrectionCaseRepository;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
final class MyBatisCorrectionCaseRepository implements CorrectionCaseRepository {
    private final CorrectionCaseMapper mapper;
    private final ObjectMapper objectMapper;

    MyBatisCorrectionCaseRepository(CorrectionCaseMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insertCase(String tenantId, CorrectionCaseView correctionCase) {
        Map<String, Object> values = new HashMap<>();
        values.put("correctionCaseId", correctionCase.correctionCaseId().toString());
        values.put("tenantId", tenantId);
        values.put("projectId", correctionCase.projectId().toString());
        values.put("taskId", correctionCase.taskId().toString());
        values.put("sourceReviewCaseId", correctionCase.sourceReviewCaseId().toString());
        values.put("sourceReviewDecisionId", correctionCase.sourceReviewDecisionId().toString());
        values.put("sourceEvidenceSetSnapshotId", correctionCase.sourceEvidenceSetSnapshotId().toString());
        values.put("sourceSnapshotContentDigest", correctionCase.sourceSnapshotContentDigest());
        values.put("reasonCodes", writeJson(correctionCase.reasonCodes()));
        values.put("status", correctionCase.status());
        values.put("createdBy", correctionCase.createdBy());
        values.put("createdAt", correctionCase.createdAt());
        values.put("latestResubmissionSnapshotId",
                correctionCase.latestResubmissionSnapshotId() == null
                        ? null : correctionCase.latestResubmissionSnapshotId().toString());
        values.put("closedBy", correctionCase.closedBy());
        values.put("closedAt", correctionCase.closedAt());
        values.put("correctionTaskId",
                correctionCase.correctionTaskId() == null
                        ? null : correctionCase.correctionTaskId().toString());
        mapper.insertCase(values);
    }

    @Override
    public int linkCorrectionTask(String tenantId, UUID correctionCaseId, UUID correctionTaskId) {
        return mapper.linkCorrectionTask(
                tenantId, correctionCaseId.toString(), correctionTaskId.toString());
    }

    @Override
    public int markInProgress(String tenantId, UUID correctionCaseId, String expectedStatus) {
        return mapper.markInProgress(tenantId, correctionCaseId.toString(), expectedStatus);
    }

    @Override
    public void insertResubmission(String tenantId, UUID projectId, CorrectionResubmissionView resubmission) {
        Map<String, Object> values = new HashMap<>();
        values.put("correctionResubmissionId", resubmission.correctionResubmissionId().toString());
        values.put("tenantId", tenantId);
        values.put("projectId", projectId.toString());
        values.put("correctionCaseId", resubmission.correctionCaseId().toString());
        values.put("resubmissionOrdinal", resubmission.resubmissionOrdinal());
        values.put("evidenceSetSnapshotId", resubmission.evidenceSetSnapshotId().toString());
        values.put("snapshotContentDigest", resubmission.snapshotContentDigest());
        values.put("submittedBy", resubmission.submittedBy());
        values.put("submittedAt", resubmission.submittedAt());
        mapper.insertResubmission(values);
    }

    @Override
    public int markResubmitted(
            String tenantId, UUID correctionCaseId, String expectedStatus, UUID latestSnapshotId, Instant updatedAt
    ) {
        return mapper.markResubmitted(
                tenantId, correctionCaseId.toString(), expectedStatus, latestSnapshotId.toString());
    }

    @Override
    public int markClosed(
            String tenantId, UUID correctionCaseId, String expectedStatus, String closedBy, Instant closedAt
    ) {
        return mapper.markClosed(
                tenantId, correctionCaseId.toString(), expectedStatus, closedBy, closedAt);
    }

    @Override
    public int markWaived(
            String tenantId,
            UUID correctionCaseId,
            String expectedStatus,
            String waivedBy,
            Instant waivedAt,
            String approvalRef,
            String note
    ) {
        return mapper.markWaived(
                tenantId, correctionCaseId.toString(), expectedStatus, waivedBy, waivedAt,
                approvalRef, note);
    }

    @Override
    public Optional<CorrectionCaseView> find(String tenantId, UUID correctionCaseId) {
        Map<String, Object> row = mapper.findCase(tenantId, correctionCaseId.toString());
        if (row == null) {
            return Optional.empty();
        }
        List<CorrectionResubmissionView> rounds = mapper.listResubmissions(tenantId, correctionCaseId.toString())
                .stream().map(this::resubmissionView).toList();
        return Optional.of(caseView(row, rounds));
    }

    @Override
    public List<CorrectionCaseView> listByTask(String tenantId, UUID taskId) {
        return mapper.listCasesByTask(tenantId, taskId.toString()).stream()
                .map(row -> {
                    UUID correctionCaseId = uuid(row, "correctionCaseId");
                    List<CorrectionResubmissionView> rounds = mapper.listResubmissions(
                                    tenantId, correctionCaseId.toString())
                            .stream().map(this::resubmissionView).toList();
                    return caseView(row, rounds);
                })
                .toList();
    }

    @Override
    public List<CorrectionCaseQueueItem> findQueuePage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            String status,
            UUID taskId,
            UUID sourceReviewCaseId,
            Instant cursorCreatedAt,
            UUID cursorId,
            int fetchSize
    ) {
        return mapper.findQueuePage(
                        tenantId,
                        tenantWide,
                        projectIds.stream().map(UUID::toString).toList(),
                        status,
                        taskId == null ? null : taskId.toString(),
                        sourceReviewCaseId == null ? null : sourceReviewCaseId.toString(),
                        cursorCreatedAt,
                        cursorId == null ? null : cursorId.toString(),
                        fetchSize)
                .stream()
                .map(this::queueItem)
                .toList();
    }

    @Override
    public Optional<UUID> findBySourceDecision(String tenantId, UUID reviewDecisionId) {
        String id = mapper.findCaseIdBySourceDecision(tenantId, reviewDecisionId.toString());
        return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
    }

    @Override
    public Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey) {
        String id = mapper.findCommandResult(tenantId, operationType, idempotencyKey);
        return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
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

    @Override
    public int nextResubmissionOrdinal(String tenantId, UUID correctionCaseId) {
        Integer max = mapper.maxResubmissionOrdinal(tenantId, correctionCaseId.toString());
        return (max == null ? 0 : max) + 1;
    }

    private CorrectionCaseView caseView(Map<String, Object> row, List<CorrectionResubmissionView> rounds) {
        return new CorrectionCaseView(
                uuid(row, "correctionCaseId"), uuid(row, "projectId"), uuid(row, "taskId"),
                uuid(row, "sourceReviewCaseId"), uuid(row, "sourceReviewDecisionId"),
                uuid(row, "sourceEvidenceSetSnapshotId"), text(row, "sourceSnapshotContentDigest"),
                readCodes(text(row, "reasonCodes")),
                nullableUuid(row.get("correctionTaskId")), text(row, "status"), text(row, "createdBy"),
                instant(row.get("createdAt")),
                nullableUuid(row.get("latestResubmissionSnapshotId")),
                nullableText(row.get("closedBy")), nullableInstant(row.get("closedAt")),
                nullableText(row.get("waivedBy")), nullableInstant(row.get("waivedAt")),
                nullableText(row.get("waiveApprovalRef")), nullableText(row.get("waiveNote")),
                rounds);
    }

    private CorrectionResubmissionView resubmissionView(Map<String, Object> row) {
        return new CorrectionResubmissionView(
                uuid(row, "correctionResubmissionId"), uuid(row, "correctionCaseId"),
                ((Number) row.get("resubmissionOrdinal")).intValue(),
                uuid(row, "evidenceSetSnapshotId"), text(row, "snapshotContentDigest"),
                text(row, "submittedBy"), instant(row.get("submittedAt")));
    }

    private CorrectionCaseQueueItem queueItem(Map<String, Object> row) {
        return new CorrectionCaseQueueItem(
                uuid(row, "correctionCaseId"),
                uuid(row, "projectId"),
                uuid(row, "taskId"),
                uuid(row, "sourceReviewCaseId"),
                uuid(row, "sourceReviewDecisionId"),
                readCodes(text(row, "reasonCodes")),
                nullableUuid(row.get("correctionTaskId")),
                text(row, "status"),
                instant(row.get("createdAt")),
                nullableUuid(row.get("latestResubmissionSnapshotId")),
                nullableInstant(row.get("closedAt")),
                nullableInstant(row.get("waivedAt")),
                ((Number) row.get("resubmissionCount")).intValue());
    }

    private List<String> readCodes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JacksonException exception) {
            throw new IllegalStateException("reasonCodes deserialization failed", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("JSON serialization failed", exception);
        }
    }

    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static UUID nullableUuid(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private static String nullableText(Object value) {
        return value == null ? null : value.toString();
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instantValue) {
            return instantValue;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalArgumentException("unsupported time type: " + value.getClass().getName());
    }

    private static Instant nullableInstant(Object value) {
        return value == null ? null : instant(value);
    }
}
