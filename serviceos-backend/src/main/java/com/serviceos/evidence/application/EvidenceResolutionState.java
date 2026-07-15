package com.serviceos.evidence.application;

import java.util.List;
import java.util.UUID;

/** 一个 Task 当前最新 resolution generation 的串行化比较快照。 */
public record EvidenceResolutionState(
        UUID resolutionId,
        int generationNo,
        int conditionFactRevision,
        List<EvidenceResolutionMemberState> members
) {
    public EvidenceResolutionState {
        members = List.copyOf(members);
    }
}
