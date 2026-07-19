package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.evidence.api.ReviewCaseQueueItem;
import com.serviceos.evidence.api.ReviewDecisionView;
import com.serviceos.evidence.application.ReviewCaseRepository;
import com.serviceos.evidence.application.ReviewCaseTimelineIdentity;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
final class MyBatisReviewCaseRepository implements ReviewCaseRepository {
    private final ReviewCaseMapper mapper;
    private final ObjectMapper objectMapper;

    MyBatisReviewCaseRepository(ReviewCaseMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insertCase(String tenantId, ReviewCaseView reviewCase) {
        Map<String, Object> values = new HashMap<>();
        values.put("reviewCaseId", reviewCase.reviewCaseId().toString());
        values.put("tenantId", tenantId);
        values.put("projectId", reviewCase.projectId().toString());
        values.put("taskId", reviewCase.taskId().toString());
        values.put("evidenceSetSnapshotId", reviewCase.evidenceSetSnapshotId().toString());
        values.put("snapshotContentDigest", reviewCase.snapshotContentDigest());
        values.put("scopeType", reviewCase.scopeType());
        values.put("origin", reviewCase.origin());
        values.put("policyVersion", reviewCase.policyVersion());
        values.put("status", reviewCase.status());
        values.put("createdBy", reviewCase.createdBy());
        values.put("createdAt", reviewCase.createdAt());
        values.put("decidedAt", reviewCase.decidedAt());
        values.put("sourceReviewCaseId", reviewCase.sourceReviewCaseId() == null
                ? null : reviewCase.sourceReviewCaseId().toString());
        values.put("externalSubmissionRef", reviewCase.externalSubmissionRef());
        values.put("callbackBatchRef", reviewCase.callbackBatchRef());
        values.put("mappingVersionId", reviewCase.mappingVersionId());
        values.put("reopenedFromReviewCaseId", reviewCase.reopenedFromReviewCaseId() == null
                ? null : reviewCase.reopenedFromReviewCaseId().toString());
        values.put("reopenTriggerRef", reviewCase.reopenTriggerRef());
        values.put("aggregateVersion", reviewCase.aggregateVersion() <= 0 ? 1L : reviewCase.aggregateVersion());
        mapper.insertCase(values);
    }

    @Override
    public int markDecided(
            String tenantId,
            UUID reviewCaseId,
            String expectedStatus,
            long expectedAggregateVersion,
            String status,
            Instant decidedAt
    ) {
        return mapper.markDecided(
                tenantId, reviewCaseId.toString(), expectedStatus, expectedAggregateVersion,
                status, decidedAt);
    }

    @Override
    public int markReopened(String tenantId, UUID reviewCaseId, String expectedStatus) {
        return mapper.markReopened(tenantId, reviewCaseId.toString(), expectedStatus);
    }

    @Override
    public void insertDecision(String tenantId, UUID projectId, ReviewDecisionView decision) {
        Map<String, Object> values = new HashMap<>();
        values.put("reviewDecisionId", decision.reviewDecisionId().toString());
        values.put("tenantId", tenantId);
        values.put("projectId", projectId.toString());
        values.put("reviewCaseId", decision.reviewCaseId().toString());
        values.put("decisionOrdinal", decision.decisionOrdinal());
        values.put("decision", decision.decision());
        values.put("decisionSource", decision.decisionSource());
        values.put("reasonCodes", writeJson(decision.reasonCodes()));
        values.put("note", decision.note());
        values.put("approvalRef", decision.approvalRef());
        values.put("decidedBy", decision.decidedBy());
        values.put("decidedAt", decision.decidedAt());
        mapper.insertDecision(values);
    }

    @Override
    public void insertTargetDecision(
            String tenantId,
            UUID projectId,
            UUID reviewCaseId,
            UUID reviewDecisionId,
            String targetType,
            UUID targetId,
            int targetVersion,
            String decision,
            List<String> reasonCodes,
            String note,
            Instant createdAt
    ) {
        Map<String, Object> values = new HashMap<>();
        values.put("reviewTargetDecisionId", UUID.randomUUID().toString());
        values.put("tenantId", tenantId);
        values.put("projectId", projectId.toString());
        values.put("reviewCaseId", reviewCaseId.toString());
        values.put("reviewDecisionId", reviewDecisionId.toString());
        values.put("targetType", targetType);
        values.put("targetId", targetId.toString());
        values.put("targetVersion", targetVersion);
        values.put("decision", decision);
        values.put("reasonCodes", writeJson(reasonCodes == null ? List.of() : reasonCodes));
        values.put("note", note);
        values.put("createdAt", createdAt);
        mapper.insertTargetDecision(values);
    }

    @Override
    public Optional<ReviewCaseView> find(String tenantId, UUID reviewCaseId) {
        Map<String, Object> row = mapper.findCase(tenantId, reviewCaseId.toString());
        if (row == null) {
            return Optional.empty();
        }
        List<ReviewDecisionView> decisions = mapper.listDecisions(tenantId, reviewCaseId.toString())
                .stream().map(this::decisionView).toList();
        return Optional.of(caseView(row, decisions));
    }

    @Override
    public List<ReviewCaseView> listByTask(String tenantId, UUID taskId) {
        return mapper.listCasesByTask(tenantId, taskId.toString()).stream()
                .map(row -> {
                    UUID reviewCaseId = uuid(row, "reviewCaseId");
                    List<ReviewDecisionView> decisions = mapper.listDecisions(
                                    tenantId, reviewCaseId.toString())
                            .stream().map(this::decisionView).toList();
                    return caseView(row, decisions);
                })
                .toList();
    }

    @Override
    public List<ReviewCaseQueueItem> findQueuePage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            String status,
            String origin,
            UUID taskId,
            Instant cursorCreatedAt,
            UUID cursorId,
            int fetchSize
    ) {
        return mapper.findQueuePage(
                        tenantId,
                        tenantWide,
                        projectIds.stream().map(UUID::toString).toList(),
                        status,
                        origin,
                        taskId == null ? null : taskId.toString(),
                        cursorCreatedAt,
                        cursorId == null ? null : cursorId.toString(),
                        fetchSize)
                .stream()
                .map(this::queueItem)
                .toList();
    }

    @Override
    public Optional<ReviewCaseTimelineIdentity> findTimelineIdentity(String tenantId, UUID reviewCaseId) {
        Map<String, Object> row = mapper.findTimelineIdentity(tenantId, reviewCaseId.toString());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new ReviewCaseTimelineIdentity(
                uuid(row, "reviewCaseId"), uuid(row, "projectId"), uuid(row, "taskId")));
    }

    @Override
    public Optional<UUID> findActiveBySnapshot(String tenantId, UUID snapshotId, String origin) {
        String id = mapper.findActiveCaseIdBySnapshot(tenantId, snapshotId.toString(), origin);
        return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
    }

    @Override
    public Optional<UUID> findClientByExternalSubmissionRef(String tenantId, String externalSubmissionRef) {
        String id = mapper.findClientCaseIdByExternalSubmissionRef(tenantId, externalSubmissionRef);
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
    public int nextDecisionOrdinal(String tenantId, UUID reviewCaseId) {
        Integer max = mapper.maxDecisionOrdinal(tenantId, reviewCaseId.toString());
        return (max == null ? 0 : max) + 1;
    }

    private ReviewCaseView caseView(Map<String, Object> row, List<ReviewDecisionView> decisions) {
        return new ReviewCaseView(
                uuid(row, "reviewCaseId"), uuid(row, "projectId"), uuid(row, "taskId"),
                uuid(row, "evidenceSetSnapshotId"), text(row, "snapshotContentDigest"),
                text(row, "scopeType"), text(row, "origin"), text(row, "policyVersion"), text(row, "status"),
                text(row, "createdBy"), instant(row.get("createdAt")),
                row.get("decidedAt") == null ? null : instant(row.get("decidedAt")),
                nullableUuid(row, "sourceReviewCaseId"),
                nullableText(row, "externalSubmissionRef"),
                nullableText(row, "callbackBatchRef"),
                nullableText(row, "mappingVersionId"),
                row.get("reopenedFromReviewCaseId") == null ? null : uuid(row, "reopenedFromReviewCaseId"),
                row.get("reopenTriggerRef") == null ? null : text(row, "reopenTriggerRef"),
                decisions,
                row.get("aggregateVersion") == null
                        ? 1L : ((Number) row.get("aggregateVersion")).longValue());
    }

    private ReviewDecisionView decisionView(Map<String, Object> row) {
        return new ReviewDecisionView(
                uuid(row, "reviewDecisionId"), uuid(row, "reviewCaseId"),
                ((Number) row.get("decisionOrdinal")).intValue(), text(row, "decision"),
                text(row, "decisionSource"),
                readCodes(text(row, "reasonCodes")),
                row.get("note") == null ? null : text(row, "note"),
                row.get("approvalRef") == null ? null : text(row, "approvalRef"),
                text(row, "decidedBy"), instant(row.get("decidedAt")));
    }

    private ReviewCaseQueueItem queueItem(Map<String, Object> row) {
        return new ReviewCaseQueueItem(
                uuid(row, "reviewCaseId"), uuid(row, "projectId"), uuid(row, "taskId"),
                uuid(row, "evidenceSetSnapshotId"), text(row, "scopeType"), text(row, "origin"),
                text(row, "policyVersion"), text(row, "status"), instant(row.get("createdAt")),
                row.get("decidedAt") == null ? null : instant(row.get("decidedAt")),
                nullableUuid(row, "sourceReviewCaseId"), nullableText(row, "externalSubmissionRef"),
                nullableText(row, "callbackBatchRef"), nullableText(row, "mappingVersionId"),
                nullableUuid(row, "reopenedFromReviewCaseId"), nullableText(row, "reopenTriggerRef"),
                nullableUuid(row, "latestDecisionId"), nullableText(row, "latestDecision"),
                nullableText(row, "latestDecisionSource"),
                row.get("latestReasonCodes") == null
                        ? List.of() : readCodes(text(row, "latestReasonCodes")),
                row.get("latestDecisionAt") == null ? null : instant(row.get("latestDecisionAt")));
    }

    private List<String> readCodes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException exception) {
            throw new IllegalStateException("reasonCodes are invalid", exception);
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

    private static UUID nullableUuid(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : uuid(row, key);
    }

    private static String nullableText(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : text(row, key);
    }

    private static String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalArgumentException("unsupported time type: " + value.getClass().getName());
    }
}
