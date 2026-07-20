package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.UUID;

/** 履约配置草稿视图。documentJson 为阶段编排文档。 */
public record ProjectFulfillmentDraftView(
        UUID profileId,
        UUID revisionId,
        String serviceProductCode,
        String profileName,
        String description,
        String documentJson,
        UUID workflowAssetVersionId,
        UUID sourceBundleId,
        String validationJson,
        long aggregateVersion,
        Instant updatedAt
) {
}
