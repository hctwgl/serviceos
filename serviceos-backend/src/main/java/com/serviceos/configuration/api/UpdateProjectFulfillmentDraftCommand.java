package com.serviceos.configuration.api;

import java.util.Objects;
import java.util.UUID;

/** 更新履约草稿概要与结构化编排文档。 */
public record UpdateProjectFulfillmentDraftCommand(
        UUID profileId,
        long expectedVersion,
        String profileName,
        String description,
        ProjectFulfillmentDocument document,
        UUID workflowAssetVersionId,
        UUID sourceBundleId
) {
    public UpdateProjectFulfillmentDraftCommand {
        profileId = Objects.requireNonNull(profileId, "profileId");
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("expectedVersion must be >= 1");
        }
        document = Objects.requireNonNull(document, "document");
    }
}
