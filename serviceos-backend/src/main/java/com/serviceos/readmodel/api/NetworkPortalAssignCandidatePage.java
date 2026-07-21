package com.serviceos.readmodel.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Network Portal 分配候选页。
 *
 * <p>M412：始终返回 {@code rankingExplanation}；无 ACTIVE 师傅时给出 {@code emptyReason}。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NetworkPortalAssignCandidatePage(
        UUID networkId,
        UUID taskId,
        String businessType,
        String workOrderRegionSummary,
        List<NetworkPortalAssignCandidateItem> items,
        Instant asOf,
        String rankingExplanation,
        String emptyReason
) {
    public NetworkPortalAssignCandidatePage {
        Objects.requireNonNull(networkId, "networkId");
        Objects.requireNonNull(taskId, "taskId");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(rankingExplanation, "rankingExplanation");
    }
}
