package com.serviceos.configuration.api;

import java.util.Objects;
import java.util.UUID;

/** 建单冻结结果。 */
public record ProjectFulfillmentResolveResult(
        UUID profileId,
        UUID revisionId,
        String fulfillmentVersion,
        UUID configurationBundleId,
        String configurationBundleCode,
        String configurationBundleVersion,
        String configurationBundleDigest,
        String manifestJson,
        String contentDigest
) {
    public ProjectFulfillmentResolveResult {
        profileId = Objects.requireNonNull(profileId, "profileId");
        revisionId = Objects.requireNonNull(revisionId, "revisionId");
        fulfillmentVersion = Objects.requireNonNull(fulfillmentVersion, "fulfillmentVersion");
        configurationBundleId = Objects.requireNonNull(configurationBundleId, "configurationBundleId");
        configurationBundleCode = Objects.requireNonNull(configurationBundleCode, "configurationBundleCode");
        configurationBundleVersion = Objects.requireNonNull(
                configurationBundleVersion, "configurationBundleVersion");
        configurationBundleDigest = Objects.requireNonNull(
                configurationBundleDigest, "configurationBundleDigest");
        manifestJson = Objects.requireNonNull(manifestJson, "manifestJson");
        contentDigest = Objects.requireNonNull(contentDigest, "contentDigest");
    }
}
