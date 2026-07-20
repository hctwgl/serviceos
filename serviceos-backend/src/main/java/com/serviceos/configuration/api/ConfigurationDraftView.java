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
        String approvalRef,
        String approvedBy,
        Instant approvedAt,
        long aggregateVersion,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt,
        List<String> supportedClientKinds,
        ClientCompatibilityReport clientCompatibility
) {
    public ConfigurationDraftView {
        validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
        supportedClientKinds = supportedClientKinds == null
                ? null : List.copyOf(supportedClientKinds);
    }

    /** 附加或覆盖客户端兼容报告（派生视图，不落库）。 */
    public ConfigurationDraftView withClientCompatibility(ClientCompatibilityReport report) {
        return new ConfigurationDraftView(
                draftId, assetType, assetKey, intendedSemanticVersion, schemaVersion,
                definitionJson, contentDigest, status, baseVersionId, publishedVersionId,
                validationErrors, approvalRef, approvedBy, approvedAt, aggregateVersion,
                createdBy, updatedBy, createdAt, updatedAt, supportedClientKinds, report);
    }
}
