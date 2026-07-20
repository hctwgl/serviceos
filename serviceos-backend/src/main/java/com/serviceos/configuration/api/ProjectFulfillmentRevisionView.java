package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.UUID;

/** 已发布或草稿 Revision 视图。 */
public record ProjectFulfillmentRevisionView(
        UUID revisionId,
        UUID profileId,
        int versionNo,
        String revisionStatus,
        String documentJson,
        String manifestJson,
        String validationJson,
        String contentDigest,
        UUID sourceBundleId,
        UUID workflowAssetVersionId,
        Instant effectiveFrom,
        Instant effectiveTo,
        UUID supersedesRevisionId,
        String publishedBy,
        Instant publishedAt,
        Instant createdAt
) {
}
