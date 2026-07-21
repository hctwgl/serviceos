package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record NetworkPortalAssignCandidatePage(
        UUID networkId,
        UUID taskId,
        String businessType,
        String workOrderRegionSummary,
        List<NetworkPortalAssignCandidateItem> items,
        Instant asOf
) {
    public NetworkPortalAssignCandidatePage {
        Objects.requireNonNull(networkId, "networkId");
        Objects.requireNonNull(taskId, "taskId");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(asOf, "asOf");
    }
}
