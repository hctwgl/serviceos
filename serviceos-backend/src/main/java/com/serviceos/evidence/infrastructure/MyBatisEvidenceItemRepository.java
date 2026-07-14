package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceValidationView;
import com.serviceos.evidence.application.EvidenceItemRepository;
import com.serviceos.evidence.application.EvidenceUploadBinding;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
final class MyBatisEvidenceItemRepository implements EvidenceItemRepository {
    private final EvidenceItemMapper mapper;

    MyBatisEvidenceItemRepository(EvidenceItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<EvidenceSlotView> findSlot(String tenantId, UUID taskId, UUID slotId) {
        return Optional.ofNullable(mapper.findSlot(tenantId, taskId.toString(), slotId.toString()))
                .map(this::slotView);
    }

    @Override
    public EvidenceSlotView lockSlot(String tenantId, UUID slotId) {
        Map<String, Object> row = mapper.lockSlot(tenantId, slotId.toString());
        if (row == null) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "EvidenceSlot does not exist");
        }
        return slotView(row);
    }

    @Override
    public void insertUploadBinding(EvidenceUploadBinding binding) {
        Map<String, Object> values = new HashMap<>();
        values.put("uploadSessionId", binding.uploadSessionId().toString());
        values.put("tenantId", binding.tenantId());
        values.put("projectId", binding.projectId().toString());
        values.put("taskId", binding.taskId().toString());
        values.put("slotId", binding.slotId().toString());
        values.put("fileId", binding.fileId().toString());
        values.put("evidenceItemId",
                binding.evidenceItemId() == null ? null : binding.evidenceItemId().toString());
        values.put("expectedSha256", binding.expectedSha256());
        values.put("declaredMimeType", binding.declaredMimeType());
        values.put("expectedSizeBytes", binding.expectedSizeBytes());
        values.put("originalFileName", binding.originalFileName());
        values.put("captureMetadata", binding.captureMetadataJson());
        values.put("status", binding.status());
        values.put("createdBy", binding.createdBy());
        values.put("createdAt", binding.createdAt());
        mapper.insertUploadBinding(values);
    }

    @Override
    public Optional<EvidenceUploadBinding> findUploadBinding(String tenantId, UUID uploadSessionId) {
        return Optional.ofNullable(mapper.findUploadBinding(tenantId, uploadSessionId.toString()))
                .map(this::binding);
    }

    @Override
    public Optional<EvidenceUploadBinding> findUploadBindingByFileId(String tenantId, UUID fileId) {
        return Optional.ofNullable(mapper.findUploadBindingByFileId(tenantId, fileId.toString()))
                .map(this::binding);
    }

    @Override
    public void markUploadFinalized(String tenantId, UUID uploadSessionId) {
        mapper.markUploadFinalized(tenantId, uploadSessionId.toString());
    }

    @Override
    public Optional<EvidenceItemView> findItem(String tenantId, UUID evidenceItemId) {
        Map<String, Object> row = mapper.findItem(tenantId, evidenceItemId.toString());
        if (row == null) {
            return Optional.empty();
        }
        Map<UUID, List<EvidenceValidationView>> validations = validationsByRevision(
                mapper.listValidationsForItem(tenantId, evidenceItemId.toString()));
        List<EvidenceRevisionView> revisions = mapper.listRevisionsForItem(
                        tenantId, evidenceItemId.toString()).stream()
                .map(revision -> revisionView(revision, validations)).toList();
        return Optional.of(itemView(row, revisions));
    }

    @Override
    public List<EvidenceItemView> listItems(String tenantId, UUID taskId) {
        List<Map<String, Object>> items = mapper.listItems(tenantId, taskId.toString());
        Map<UUID, List<EvidenceValidationView>> validations = validationsByRevision(
                mapper.listValidationsForTask(tenantId, taskId.toString()));
        Map<UUID, List<EvidenceRevisionView>> byItem = new LinkedHashMap<>();
        for (Map<String, Object> revision : mapper.listRevisionsForTask(tenantId, taskId.toString())) {
            EvidenceRevisionView view = revisionView(revision, validations);
            byItem.computeIfAbsent(view.evidenceItemId(), key -> new ArrayList<>()).add(view);
        }
        return items.stream()
                .map(row -> itemView(row, byItem.getOrDefault(uuid(row, "evidenceItemId"), List.of())))
                .toList();
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

    @Override
    public int nextItemOrdinal(String tenantId, UUID slotId) {
        Integer max = mapper.maxItemOrdinal(tenantId, slotId.toString());
        return (max == null ? 0 : max) + 1;
    }

    @Override
    public int countItems(String tenantId, UUID slotId) {
        return mapper.countItems(tenantId, slotId.toString());
    }

    @Override
    public int countCountingItems(String tenantId, UUID slotId) {
        return mapper.countCountingItems(tenantId, slotId.toString());
    }

    @Override
    public void insertItem(String tenantId, EvidenceItemView item) {
        Map<String, Object> values = new HashMap<>();
        values.put("evidenceItemId", item.evidenceItemId().toString());
        values.put("tenantId", tenantId);
        values.put("projectId", item.projectId().toString());
        values.put("taskId", item.taskId().toString());
        values.put("slotId", item.evidenceSlotId().toString());
        values.put("itemOrdinal", item.itemOrdinal());
        values.put("status", item.status());
        values.put("createdBy", item.createdBy());
        values.put("createdAt", item.createdAt());
        mapper.insertItem(values);
    }

    @Override
    public int nextRevisionNumber(String tenantId, UUID evidenceItemId) {
        Integer max = mapper.maxRevisionNumber(tenantId, evidenceItemId.toString());
        return (max == null ? 0 : max) + 1;
    }

    @Override
    public void insertRevision(String tenantId, EvidenceRevisionView revision) {
        Map<String, Object> values = new HashMap<>();
        values.put("evidenceRevisionId", revision.evidenceRevisionId().toString());
        values.put("tenantId", tenantId);
        values.put("projectId", revision.projectId().toString());
        values.put("taskId", revision.taskId().toString());
        values.put("slotId", revision.evidenceSlotId().toString());
        values.put("evidenceItemId", revision.evidenceItemId().toString());
        values.put("revisionNumber", revision.revisionNumber());
        values.put("fileObjectId", revision.fileObjectId().toString());
        values.put("contentDigest", revision.contentDigest());
        values.put("mimeType", revision.mimeType());
        values.put("sizeBytes", revision.sizeBytes());
        values.put("captureMetadata", revision.captureMetadataJson());
        values.put("status", revision.status());
        values.put("sourceUploadSessionId", revision.sourceUploadSessionId().toString());
        values.put("finalizeCommandId", revision.finalizeCommandId());
        values.put("createdBy", revision.createdBy());
        values.put("createdAt", revision.createdAt());
        mapper.insertRevision(values);
    }

    @Override
    public int updateRevisionStatus(String tenantId, UUID revisionId, String expectedStatus, String status) {
        return mapper.updateRevisionStatus(
                tenantId, revisionId.toString(), expectedStatus, status);
    }

    @Override
    public void updateSlotStatus(String tenantId, UUID slotId, String status) {
        mapper.updateSlotStatus(tenantId, slotId.toString(), status);
    }

    @Override
    public Optional<EvidenceRevisionView> findRevision(String tenantId, UUID revisionId) {
        return Optional.ofNullable(mapper.findRevision(tenantId, revisionId.toString()))
                .map(row -> revisionView(row, Map.of(
                        revisionId, listValidations(tenantId, revisionId))));
    }

    @Override
    public Optional<EvidenceRevisionView> findRevisionByFileObjectId(String tenantId, UUID fileObjectId) {
        return Optional.ofNullable(mapper.findRevisionByFileObjectId(tenantId, fileObjectId.toString()))
                .map(row -> {
                    UUID revisionId = uuid(row, "evidenceRevisionId");
                    return revisionView(row, Map.of(revisionId, listValidations(tenantId, revisionId)));
                });
    }

    @Override
    public Optional<EvidenceRevisionView> findRevisionByUploadSession(String tenantId, UUID uploadSessionId) {
        return Optional.ofNullable(
                        mapper.findRevisionByUploadSession(tenantId, uploadSessionId.toString()))
                .map(row -> {
                    UUID revisionId = uuid(row, "evidenceRevisionId");
                    return revisionView(row, Map.of(revisionId, listValidations(tenantId, revisionId)));
                });
    }

    @Override
    public List<EvidenceRevisionView> findRevisionsByIds(
            String tenantId, UUID taskId, List<UUID> revisionIds
    ) {
        if (revisionIds == null || revisionIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = revisionIds.stream().map(UUID::toString).toList();
        return mapper.findRevisionsByIds(tenantId, taskId.toString(), ids).stream()
                .map(row -> {
                    UUID revisionId = uuid(row, "evidenceRevisionId");
                    return revisionView(row, Map.of(revisionId, List.of()));
                })
                .toList();
    }

    @Override
    public boolean existsOtherCountingDigest(
            String tenantId, UUID projectId, String contentDigest, UUID excludeRevisionId
    ) {
        return mapper.countOtherCountingDigest(
                tenantId, projectId.toString(), contentDigest, excludeRevisionId.toString()) > 0;
    }

    @Override
    public List<EvidenceValidationView> listValidations(String tenantId, UUID revisionId) {
        return mapper.listValidations(tenantId, revisionId.toString()).stream()
                .map(this::validationView).toList();
    }

    @Override
    public void insertValidation(
            String tenantId, UUID projectId, UUID taskId, UUID slotId,
            UUID evidenceItemId, EvidenceValidationView validation
    ) {
        Map<String, Object> values = new HashMap<>();
        values.put("validationId", validation.validationId().toString());
        values.put("tenantId", tenantId);
        values.put("projectId", projectId.toString());
        values.put("taskId", taskId.toString());
        values.put("slotId", slotId.toString());
        values.put("evidenceItemId", evidenceItemId.toString());
        values.put("evidenceRevisionId", validation.evidenceRevisionId().toString());
        values.put("checkType", validation.checkType());
        values.put("severity", validation.severity());
        values.put("result", validation.result());
        values.put("reasonCode", validation.reasonCode());
        values.put("message", validation.message());
        values.put("details", validation.detailsJson());
        values.put("validatorName", validation.validatorName());
        values.put("validatorVersion", validation.validatorVersion());
        values.put("createdAt", validation.createdAt());
        mapper.insertValidation(values);
    }

    private EvidenceUploadBinding binding(Map<String, Object> row) {
        return new EvidenceUploadBinding(
                uuid(row, "uploadSessionId"), text(row, "tenantId"), uuid(row, "projectId"),
                uuid(row, "taskId"), uuid(row, "slotId"), uuid(row, "fileId"),
                row.get("evidenceItemId") == null ? null : uuid(row, "evidenceItemId"),
                text(row, "expectedSha256"), text(row, "declaredMimeType"),
                number(row, "expectedSizeBytes").longValue(), text(row, "originalFileName"),
                text(row, "captureMetadata"), text(row, "status"), text(row, "createdBy"),
                instant(row.get("createdAt")));
    }

    private EvidenceSlotView slotView(Map<String, Object> row) {
        return new EvidenceSlotView(
                uuid(row, "slotId"), uuid(row, "resolutionId"), uuid(row, "taskId"),
                uuid(row, "projectId"), uuid(row, "templateVersionId"), text(row, "templateKey"),
                text(row, "templateVersion"), text(row, "templateDigest"),
                text(row, "requirementCode"), text(row, "occurrenceKey"),
                text(row, "requirementName"), text(row, "mediaType"),
                Boolean.TRUE.equals(row.get("required")), number(row, "minCount").intValue(),
                row.get("maxCount") == null ? null : number(row, "maxCount").intValue(),
                text(row, "conditionInputDigest"), text(row, "explanation"),
                text(row, "definition"), text(row, "requirementDigest"), text(row, "status"),
                instant(row.get("resolvedAt")));
    }

    private EvidenceItemView itemView(Map<String, Object> row, List<EvidenceRevisionView> revisions) {
        return new EvidenceItemView(
                uuid(row, "evidenceItemId"), uuid(row, "taskId"), uuid(row, "projectId"),
                uuid(row, "slotId"), number(row, "itemOrdinal").intValue(), text(row, "status"),
                text(row, "createdBy"), instant(row.get("createdAt")), revisions);
    }

    private EvidenceRevisionView revisionView(
            Map<String, Object> row, Map<UUID, List<EvidenceValidationView>> validations
    ) {
        UUID revisionId = uuid(row, "evidenceRevisionId");
        return new EvidenceRevisionView(
                revisionId, uuid(row, "evidenceItemId"), uuid(row, "slotId"),
                uuid(row, "taskId"), uuid(row, "projectId"), number(row, "revisionNumber").intValue(),
                uuid(row, "fileObjectId"), text(row, "contentDigest"), text(row, "mimeType"),
                number(row, "sizeBytes").longValue(), text(row, "captureMetadata"), text(row, "status"),
                uuid(row, "sourceUploadSessionId"), text(row, "finalizeCommandId"),
                text(row, "createdBy"), instant(row.get("createdAt")),
                validations.getOrDefault(revisionId, List.of()));
    }

    private EvidenceValidationView validationView(Map<String, Object> row) {
        return new EvidenceValidationView(
                uuid(row, "validationId"), uuid(row, "evidenceRevisionId"),
                text(row, "checkType"), text(row, "severity"), text(row, "result"),
                text(row, "reasonCode"), text(row, "message"), text(row, "details"),
                text(row, "validatorName"), text(row, "validatorVersion"),
                instant(row.get("createdAt")));
    }

    private Map<UUID, List<EvidenceValidationView>> validationsByRevision(
            List<Map<String, Object>> rows
    ) {
        Map<UUID, List<EvidenceValidationView>> byRevision = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            EvidenceValidationView view = validationView(row);
            byRevision.computeIfAbsent(view.evidenceRevisionId(), key -> new ArrayList<>()).add(view);
        }
        return byRevision;
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
