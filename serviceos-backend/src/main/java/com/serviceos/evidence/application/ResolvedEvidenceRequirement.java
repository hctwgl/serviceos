package com.serviceos.evidence.application;

import java.util.UUID;

/** 已证明不依赖条件表达式的固定资料要求；尚未生成运行时 slotId。 */
public record ResolvedEvidenceRequirement(
        UUID templateVersionId,
        String templateKey,
        String templateVersion,
        String templateDigest,
        String requirementCode,
        String requirementName,
        String mediaType,
        boolean required,
        int minCount,
        Integer maxCount,
        String conditionInputDigest,
        String resolutionExplanationJson,
        String requirementDefinitionJson,
        String requirementDigest
) {
}
