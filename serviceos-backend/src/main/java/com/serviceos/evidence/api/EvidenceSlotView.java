package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.UUID;

/** Task 创建时从冻结 EvidenceTemplate 解析出的不可变资料要求。 */
public record EvidenceSlotView(
        UUID slotId,
        UUID resolutionId,
        UUID taskId,
        UUID projectId,
        UUID templateVersionId,
        String templateKey,
        String templateVersion,
        String templateDigest,
        String requirementCode,
        String occurrenceKey,
        String requirementName,
        String mediaType,
        boolean required,
        int minCount,
        Integer maxCount,
        String conditionInputDigest,
        String resolutionExplanationJson,
        String requirementDefinitionJson,
        String requirementDigest,
        String status,
        Instant resolvedAt,
        int slotGeneration,
        UUID supersedesSlotId,
        UUID currentResolutionId,
        int resolutionGeneration,
        boolean active,
        String transition,
        String requiredDisposition
) {
    /** 单元测试和不关心重解析投影的内部调用使用的完整 generation-1 构造。 */
    public EvidenceSlotView(
            UUID slotId, UUID resolutionId, UUID taskId, UUID projectId,
            UUID templateVersionId, String templateKey, String templateVersion,
            String templateDigest, String requirementCode, String occurrenceKey,
            String requirementName, String mediaType, boolean required, int minCount,
            Integer maxCount, String conditionInputDigest, String resolutionExplanationJson,
            String requirementDefinitionJson, String requirementDigest, String status, Instant resolvedAt
    ) {
        this(slotId, resolutionId, taskId, projectId, templateVersionId, templateKey,
                templateVersion, templateDigest, requirementCode, occurrenceKey, requirementName,
                mediaType, required, minCount, maxCount, conditionInputDigest,
                resolutionExplanationJson, requirementDefinitionJson, requirementDigest, status,
                resolvedAt, 1, null, resolutionId, 1, true, "ACTIVATED", "NONE");
    }
}
