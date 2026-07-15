package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.application.EvidenceSlotRepository;
import com.serviceos.evidence.application.EvidenceTaskResolution;
import com.serviceos.evidence.application.EvidenceResolutionMember;
import com.serviceos.evidence.application.EvidenceResolutionMemberState;
import com.serviceos.evidence.application.EvidenceResolutionState;
import com.serviceos.evidence.application.EvidenceConditionDisposition;
import com.serviceos.evidence.application.PendingEvidenceConditionDisposition;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        values.put("conditionInputDigest", resolution.conditionInputDigest());
        values.put("resolutionExplanation", resolution.resolutionExplanationJson());
        values.put("generationNo", resolution.generationNo());
        values.put("conditionFactType", resolution.conditionFactType());
        values.put("conditionFactRef", resolution.conditionFactRef());
        values.put("conditionFactRevision", resolution.conditionFactRevision());
        values.put("previousResolutionId", resolution.previousResolutionId() == null
                ? null : resolution.previousResolutionId().toString());
        values.put("slotCount", Math.toIntExact(resolution.members().stream()
                .filter(EvidenceResolutionMember::conditionResult).count()));
        values.put("resolvedAt", resolution.resolvedAt());
        mapper.insertResolution(values);

        if (!resolution.slots().isEmpty()) {
            List<Map<String, Object>> slots = new ArrayList<>();
            for (EvidenceSlotView slot : resolution.slots()) {
                slots.add(slotValues(resolution.tenantId(), slot));
            }
            mapper.insertSlots(slots);
        }
        if (!resolution.members().isEmpty()) {
            mapper.insertMembers(resolution.members().stream()
                    .map(member -> memberValues(resolution.tenantId(), member)).toList());
        }
    }

    @Override
    public void lockResolutionStream(String tenantId, UUID taskId) {
        mapper.lockResolutionStream(tenantId + "|evidence-resolution|" + taskId);
    }

    @Override
    public Optional<EvidenceResolutionState> latestResolution(String tenantId, UUID taskId) {
        Map<String, Object> row = mapper.findLatestResolution(tenantId, taskId.toString());
        if (row == null) {
            return Optional.empty();
        }
        UUID resolutionId = uuid(row, "resolutionId");
        List<EvidenceResolutionMemberState> members = mapper.listResolutionMembers(
                        tenantId, resolutionId.toString()).stream()
                .map(this::memberState).toList();
        return Optional.of(new EvidenceResolutionState(
                resolutionId, number(row, "generationNo").intValue(),
                number(row, "conditionFactRevision").intValue(), members));
    }

    @Override
    public boolean resolutionExists(String tenantId, UUID taskId) {
        return mapper.countResolution(tenantId, taskId.toString()) > 0;
    }

    @Override
    public List<EvidenceSlotView> listSlots(String tenantId, UUID taskId) {
        return mapper.listSlots(tenantId, taskId.toString()).stream().map(this::view).toList();
    }

    @Override
    public List<EvidenceSlotView> listCurrentSlots(String tenantId, UUID taskId) {
        return mapper.listCurrentSlots(tenantId, taskId.toString()).stream().map(this::view).toList();
    }

    @Override
    public Optional<UUID> latestResolutionId(String tenantId, UUID taskId) {
        Map<String, Object> row = mapper.findLatestResolution(tenantId, taskId.toString());
        return row == null ? Optional.empty() : Optional.of(uuid(row, "resolutionId"));
    }

    @Override
    public boolean hasPendingDisposition(String tenantId, UUID taskId) {
        return mapper.countPendingDisposition(tenantId, taskId.toString()) > 0;
    }

    @Override
    public Optional<PendingEvidenceConditionDisposition> findPendingDisposition(
            String tenantId, UUID resolutionId, UUID slotId
    ) {
        Map<String, Object> row = mapper.findPendingDisposition(
                tenantId, resolutionId.toString(), slotId.toString());
        return row == null ? Optional.empty() : Optional.of(new PendingEvidenceConditionDisposition(
                uuid(row, "memberId"), uuid(row, "resolutionId"), uuid(row, "taskId"),
                uuid(row, "projectId"), uuid(row, "slotId")));
    }

    @Override
    public void insertDisposition(EvidenceConditionDisposition disposition) {
        Map<String, Object> values = new HashMap<>();
        values.put("dispositionId", disposition.dispositionId().toString());
        values.put("tenantId", disposition.tenantId());
        values.put("projectId", disposition.projectId().toString());
        values.put("taskId", disposition.taskId().toString());
        values.put("resolutionId", disposition.resolutionId().toString());
        values.put("memberId", disposition.memberId().toString());
        values.put("slotId", disposition.slotId().toString());
        values.put("decision", disposition.decision());
        values.put("reasonCode", disposition.reasonCode());
        values.put("reviewRef", disposition.reviewRef());
        values.put("decidedBy", disposition.decidedBy());
        values.put("decidedAt", disposition.decidedAt());
        values.put("requestDigest", disposition.requestDigest());
        mapper.insertDisposition(values);
    }

    @Override
    public Optional<EvidenceConditionDisposition> findDisposition(String tenantId, UUID memberId) {
        return disposition(mapper.findDisposition(tenantId, memberId.toString()));
    }

    @Override
    public Optional<EvidenceConditionDisposition> findDispositionById(
            String tenantId, UUID dispositionId
    ) {
        return disposition(mapper.findDispositionById(tenantId, dispositionId.toString()));
    }

    private Optional<EvidenceConditionDisposition> disposition(Map<String, Object> row) {
        return row == null ? Optional.empty() : Optional.of(new EvidenceConditionDisposition(
                uuid(row, "dispositionId"), text(row, "tenantId"), uuid(row, "projectId"),
                uuid(row, "taskId"), uuid(row, "resolutionId"), uuid(row, "memberId"),
                uuid(row, "slotId"), text(row, "decision"), text(row, "reasonCode"),
                text(row, "reviewRef"), text(row, "decidedBy"), instant(row.get("decidedAt")),
                text(row, "requestDigest")));
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
        values.put("slotGeneration", slot.slotGeneration());
        values.put("supersedesSlotId", slot.supersedesSlotId() == null
                ? null : slot.supersedesSlotId().toString());
        return values;
    }

    private static Map<String, Object> memberValues(String tenantId, EvidenceResolutionMember member) {
        Map<String, Object> values = new HashMap<>();
        values.put("memberId", member.memberId().toString());
        values.put("tenantId", tenantId);
        values.put("projectId", member.projectId().toString());
        values.put("taskId", member.taskId().toString());
        values.put("resolutionId", member.resolutionId().toString());
        values.put("templateVersionId", member.templateVersionId().toString());
        values.put("requirementCode", member.requirementCode());
        values.put("occurrenceKey", member.occurrenceKey());
        values.put("conditionResult", member.conditionResult());
        values.put("activeSlotId", member.activeSlotId() == null ? null : member.activeSlotId().toString());
        values.put("previousSlotId", member.previousSlotId() == null ? null : member.previousSlotId().toString());
        values.put("transition", member.transition());
        values.put("requiredDisposition", member.requiredDisposition());
        values.put("countingItemCount", member.countingItemCount());
        values.put("conditionInputDigest", member.conditionInputDigest());
        values.put("explanation", member.resolutionExplanationJson());
        values.put("createdAt", member.createdAt());
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
                instant(row.get("resolvedAt")), number(row, "slotGeneration").intValue(),
                nullableUuid(row, "supersedesSlotId"), uuid(row, "currentResolutionId"),
                number(row, "resolutionGeneration").intValue(),
                Boolean.TRUE.equals(row.get("active")), text(row, "transition"),
                text(row, "requiredDisposition"));
    }

    private EvidenceResolutionMemberState memberState(Map<String, Object> row) {
        return new EvidenceResolutionMemberState(
                uuid(row, "memberId"), uuid(row, "templateVersionId"),
                text(row, "requirementCode"), text(row, "occurrenceKey"),
                Boolean.TRUE.equals(row.get("conditionResult")), nullableUuid(row, "activeSlotId"),
                nullableUuid(row, "previousSlotId"), number(row, "slotGeneration").intValue(),
                text(row, "requiredDisposition"), text(row, "dispositionDecision"));
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static UUID nullableUuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value instanceof UUID id ? id : UUID.fromString(value.toString());
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
