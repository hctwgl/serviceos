package com.serviceos.project.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 首次修订结果的不可变收据；幂等重放不能用 Project 后续当前状态替代。 */
public record ProjectScopeRelationRevisionView(
        UUID revisionId,
        UUID projectId,
        List<String> regionCodes,
        List<String> networkIds,
        List<String> addedRegionCodes,
        List<String> removedRegionCodes,
        List<String> addedNetworkIds,
        List<String> removedNetworkIds,
        String reason,
        long aggregateVersion,
        Instant revisedAt
) {
}
