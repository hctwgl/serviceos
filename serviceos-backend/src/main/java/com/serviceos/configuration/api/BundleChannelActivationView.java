package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.UUID;

public record BundleChannelActivationView(
        UUID activationId,
        UUID projectId,
        BundleChannel channel,
        UUID bundleId,
        String bundleCode,
        String bundleVersion,
        UUID previousActivationId,
        String status,
        String approvalRef,
        int trafficPercent,
        String activatedBy,
        Instant activatedAt,
        Instant supersededAt,
        long aggregateVersion
) {
}
