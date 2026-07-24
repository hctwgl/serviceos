package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 履约 Profile 详情（含草稿与当前发布摘要）。 */
public record ProjectFulfillmentProfileDetail(
        UUID profileId,
        UUID projectId,
        String profileCode,
        String serviceProductCode,
        String profileName,
        String description,
        int matchPriority,
        String status,
        UUID draftRevisionId,
        UUID activeRevisionId,
        String activeVersion,
        Instant activeEffectiveFrom,
        List<String> allowedActions,
        long aggregateVersion,
        Instant createdAt,
        Instant updatedAt,
        Instant asOf
) {
    public ProjectFulfillmentProfileDetail {
        allowedActions = List.copyOf(allowedActions == null ? List.of() : allowedActions);
    }
}
