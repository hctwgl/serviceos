package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.UUID;

/** 工单履约配置快照（冻结事实 + Manifest）。 */
public record WorkOrderFulfillmentSnapshot(
        UUID workOrderId,
        UUID projectId,
        String serviceProductCode,
        String configKind,
        UUID profileId,
        String profileName,
        UUID revisionId,
        String fulfillmentVersion,
        UUID configurationBundleId,
        String configurationBundleVersion,
        String configurationBundleDigest,
        String manifestJson,
        String contentDigest,
        Instant frozenAt,
        String legacyExplanation
) {
}
