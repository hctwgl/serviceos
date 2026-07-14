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
        Instant resolvedAt
) {
}
