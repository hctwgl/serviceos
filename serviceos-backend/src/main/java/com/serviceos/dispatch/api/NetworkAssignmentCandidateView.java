package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin 工单工作区可直接展示的责任网点候选读模型。 */
public record NetworkAssignmentCandidateView(
        UUID taskId,
        UUID workOrderId,
        String businessType,
        Instant generatedAt,
        String rankingExplanation,
        String emptyReason,
        List<Candidate> candidates
) {
    public NetworkAssignmentCandidateView {
        candidates = List.copyOf(candidates);
    }

    /**
     * 网点标识只用于后续命令提交；普通界面必须展示 networkName，不得以标识充当名称。
     */
    public record Candidate(
            UUID networkId,
            String networkName,
            int rank,
            String coverageSummary,
            int remainingCapacity,
            String recommendationSummary
    ) {
    }
}
