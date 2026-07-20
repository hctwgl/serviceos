package com.serviceos.configuration.api;

import java.util.Objects;
import java.util.UUID;

/** 更新履约草稿概要与编排文档。 */
public record UpdateProjectFulfillmentDraftCommand(
        UUID profileId,
        long expectedVersion,
        String profileName,
        String description,
        String documentJson,
        UUID workflowAssetVersionId,
        UUID sourceBundleId
) {
    public UpdateProjectFulfillmentDraftCommand {
        profileId = Objects.requireNonNull(profileId, "profileId");
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("expectedVersion must be >= 1");
        }
        documentJson = Objects.requireNonNull(documentJson, "documentJson");
        if (documentJson.isBlank()) {
            throw new IllegalArgumentException("documentJson must not be blank");
        }
    }
}
