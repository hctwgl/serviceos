package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 配置资产草稿当前事实。 */
public record ConfigurationDraftView(
        UUID draftId,
        ConfigurationAssetType assetType,
        String assetKey,
        String intendedSemanticVersion,
        String schemaVersion,
        String definitionJson,
        String contentDigest,
        String status,
        UUID baseVersionId,
        UUID publishedVersionId,
        List<String> validationErrors,
        long aggregateVersion,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
