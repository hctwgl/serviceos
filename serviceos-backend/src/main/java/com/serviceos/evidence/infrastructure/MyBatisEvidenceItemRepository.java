package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;
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
        List<EvidenceRevisionView> revisions = mapper.listRevisionsForItem(
                        tenantId, evidenceItemId.toString()).stream()
                .map(this::revisionView).toList();
        return Optional.of(itemView(row, revisions));
    }

    @Override
    public List<EvidenceItemView> listItems(String tenantId, UUID taskId) {
        List<Map<String, Object>> items = mapper.listItems(tenantId, taskId.toString());
        Map<UUID, List<EvidenceRevisionView>> byItem = new LinkedHashMap<>();
        for (Map<String, Object> revision : mapper.listRevisionsForTask(tenantId, taskId.toString())) {
            EvidenceRevisionView view = revisionView(revision);
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
    public void updateRevisionStatus(String tenantId, UUID revisionId, String status) {
        mapper.updateRevisionStatus(tenantId, revisionId.toString(), status);
    }

    @Override
    public void updateSlotStatus(String tenantId, UUID slotId, String status) {
        mapper.updateSlotStatus(tenantId, slotId.toString(), status);
    }

    @Override
    public Optional<EvidenceRevisionView> findRevisionByFileObjectId(String tenantId, UUID fileObjectId) {
        return Optional.ofNullable(mapper.findRevisionByFileObjectId(tenantId, fileObjectId.toString()))
                .map(this::revisionView);
    }

    @Override
    public Optional<EvidenceRevisionView> findRevisionByUploadSession(String tenantId, UUID uploadSessionId) {
        return Optional.ofNullable(
                        mapper.findRevisionByUploadSession(tenantId, uploadSessionId.toString()))
                .map(this::revisionView);
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

    private EvidenceRevisionView revisionView(Map<String, Object> row) {
        return new EvidenceRevisionView(
                uuid(row, "evidenceRevisionId"), uuid(row, "evidenceItemId"), uuid(row, "slotId"),
                uuid(row, "taskId"), uuid(row, "projectId"), number(row, "revisionNumber").intValue(),
                uuid(row, "fileObjectId"), text(row, "contentDigest"), text(row, "mimeType"),
                number(row, "sizeBytes").longValue(), text(row, "captureMetadata"), text(row, "status"),
                uuid(row, "sourceUploadSessionId"), text(row, "finalizeCommandId"),
                text(row, "createdBy"), instant(row.get("createdAt")));
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
