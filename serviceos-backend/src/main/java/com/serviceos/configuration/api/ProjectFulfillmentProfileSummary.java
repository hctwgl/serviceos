package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.UUID;

/** 项目履约配置列表项。 */
public record ProjectFulfillmentProfileSummary(
        UUID profileId,
        UUID projectId,
        String profileCode,
        String serviceProductCode,
        String profileName,
        int matchPriority,
        String status,
        Integer stageCount,
        Integer formCount,
        Integer evidenceCount,
        String activeVersion,
        Instant effectiveFrom,
        String workflowSummary,
        String slaSummary,
        long aggregateVersion,
        Instant updatedAt
) {
}
