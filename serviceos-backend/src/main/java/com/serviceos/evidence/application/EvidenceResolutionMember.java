package com.serviceos.evidence.application;

import java.time.Instant;
import java.util.UUID;

/** 一个 resolution generation 中某资料 requirement 的不可变活动决策。 */
public record EvidenceResolutionMember(
        UUID memberId,
        UUID resolutionId,
        UUID taskId,
        UUID projectId,
        UUID templateVersionId,
        String requirementCode,
        String occurrenceKey,
        boolean conditionResult,
        UUID activeSlotId,
        UUID previousSlotId,
        String transition,
        String requiredDisposition,
        int countingItemCount,
        String conditionInputDigest,
        String resolutionExplanationJson,
        Instant createdAt
) {
}
