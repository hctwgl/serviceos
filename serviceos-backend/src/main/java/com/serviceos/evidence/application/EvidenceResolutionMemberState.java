package com.serviceos.evidence.application;

import java.util.UUID;

/** 重解析比较前一 generation 使用的只读成员状态。 */
public record EvidenceResolutionMemberState(
        UUID memberId,
        UUID templateVersionId,
        String requirementCode,
        String occurrenceKey,
        boolean conditionResult,
        UUID activeSlotId,
        UUID previousSlotId,
        int slotGeneration,
        String requiredDisposition,
        String dispositionDecision
) {
    public UUID lineageSlotId() {
        return activeSlotId != null ? activeSlotId : previousSlotId;
    }
}
