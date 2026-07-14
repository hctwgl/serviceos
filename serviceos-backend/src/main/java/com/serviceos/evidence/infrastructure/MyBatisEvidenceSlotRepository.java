package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.application.EvidenceSlotRepository;
import com.serviceos.evidence.application.EvidenceTaskResolution;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
final class MyBatisEvidenceSlotRepository implements EvidenceSlotRepository {
    private final EvidenceSlotMapper mapper;

    MyBatisEvidenceSlotRepository(EvidenceSlotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(EvidenceTaskResolution resolution) {
        Map<String, Object> values = new HashMap<>();
        values.put("resolutionId", resolution.resolutionId());
        values.put("tenantId", resolution.tenantId());
        values.put("projectId", resolution.projectId());
        values.put("taskId", resolution.taskId());
        values.put("bundleId", resolution.configurationBundleId());
        values.put("bundleDigest", resolution.configurationBundleDigest());
        values.put("stageCode", resolution.stageCode());
        values.put("sourceEventId", resolution.sourceEventId());
        values.put("sourceEventDigest", resolution.sourceEventDigest());
        values.put("resolverVersion", resolution.resolverVersion());
        values.put("slotCount", resolution.slots().size());
        values.put("resolvedAt", resolution.resolvedAt());
        mapper.insertResolution(values);

        if (!resolution.slots().isEmpty()) {
            List<Map<String, Object>> slots = new ArrayList<>();
            for (EvidenceSlotView slot : resolution.slots()) {
                slots.add(slotValues(resolution.tenantId(), slot));
            }
            mapper.insertSlots(slots);
        }
    }

    @Override
    public boolean resolutionExists(String tenantId, UUID taskId) {
        return mapper.countResolution(tenantId, taskId.toString()) == 1;
    }

    @Override
    public List<EvidenceSlotView> listSlots(String tenantId, UUID taskId) {
        return mapper.listSlots(tenantId, taskId.toString()).stream().map(this::view).toList();
    }

    private static Map<String, Object> slotValues(String tenantId, EvidenceSlotView slot) {
        Map<String, Object> values = new HashMap<>();
        // 动态批量 SQL 无法从 Map 属性稳定推断 UUID TypeHandler，统一传字符串并由 SQL 显式转换。
        values.put("slotId", slot.slotId().toString());
        values.put("tenantId", tenantId);
        values.put("projectId", slot.projectId().toString());
        values.put("taskId", slot.taskId().toString());
        values.put("resolutionId", slot.resolutionId().toString());
        values.put("templateVersionId", slot.templateVersionId().toString());
        values.put("templateKey", slot.templateKey());
        values.put("templateVersion", slot.templateVersion());
        values.put("templateDigest", slot.templateDigest());
        values.put("requirementCode", slot.requirementCode());
        values.put("occurrenceKey", slot.occurrenceKey());
        values.put("requirementName", slot.requirementName());
        values.put("mediaType", slot.mediaType());
        values.put("required", slot.required());
        values.put("minCount", slot.minCount());
        values.put("maxCount", slot.maxCount());
        values.put("conditionInputDigest", slot.conditionInputDigest());
        values.put("explanation", slot.resolutionExplanationJson());
        values.put("definition", slot.requirementDefinitionJson());
        values.put("requirementDigest", slot.requirementDigest());
        values.put("status", slot.status());
        values.put("resolvedAt", slot.resolvedAt());
        return values;
    }

    private EvidenceSlotView view(Map<String, Object> row) {
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
