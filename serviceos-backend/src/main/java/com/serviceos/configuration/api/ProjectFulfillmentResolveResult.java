package com.serviceos.configuration.api;

import java.util.Objects;
import java.util.UUID;

/** 建单冻结结果。 */
public record ProjectFulfillmentResolveResult(
        UUID profileId,
        String profileCode,
        String profileName,
        UUID revisionId,
        String fulfillmentVersion,
        int matchPriority,
        int matchSpecificity,
        java.util.List<String> matchExplanation,
        UUID configurationBundleId,
        String configurationBundleCode,
        String configurationBundleVersion,
        String configurationBundleDigest,
        String manifestJson,
        String contentDigest
) {
    public ProjectFulfillmentResolveResult {
        profileId = Objects.requireNonNull(profileId, "profileId");
        profileCode = Objects.requireNonNull(profileCode, "profileCode");
        profileName = Objects.requireNonNull(profileName, "profileName");
        revisionId = Objects.requireNonNull(revisionId, "revisionId");
        fulfillmentVersion = Objects.requireNonNull(fulfillmentVersion, "fulfillmentVersion");
        matchExplanation = java.util.List.copyOf(
                Objects.requireNonNull(matchExplanation, "matchExplanation"));
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
