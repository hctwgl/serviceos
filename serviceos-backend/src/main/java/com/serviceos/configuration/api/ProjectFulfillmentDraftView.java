package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 履约配置草稿视图。
 *
 * <p>{@link #document()} 为产品主契约；{@link #documentJson()} 仅诊断用途。</p>
 */
public record ProjectFulfillmentDraftView(
        UUID profileId,
        UUID revisionId,
        String serviceProductCode,
        String profileName,
        String description,
        ProjectFulfillmentDocument document,
        String documentJson,
        UUID workflowAssetVersionId,
        UUID sourceBundleId,
        String validationJson,
        String simulationJson,
        String simulationDocumentDigest,
        Instant simulatedAt,
        long aggregateVersion,
        Instant updatedAt
) {
}
