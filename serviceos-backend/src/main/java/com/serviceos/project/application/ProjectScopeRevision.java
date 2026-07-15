package com.serviceos.project.application;

import com.serviceos.project.api.ProjectScopeRelationRevisionView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** project 模块内部不可变关系修订事实。 */
public record ProjectScopeRevision(
        UUID revisionId,
        String tenantId,
        UUID projectId,
        long expectedVersion,
        long aggregateVersion,
        List<String> regionCodes,
        List<String> networkIds,
        List<String> addedRegionCodes,
        List<String> removedRegionCodes,
        List<String> addedNetworkIds,
        List<String> removedNetworkIds,
        String reason,
        String revisedBy,
        Instant revisedAt
) {
    public ProjectScopeRelationRevisionView toView() {
        return new ProjectScopeRelationRevisionView(
                revisionId, projectId, regionCodes, networkIds,
                addedRegionCodes, removedRegionCodes, addedNetworkIds, removedNetworkIds,
                reason, aggregateVersion, revisedAt);
    }
}
