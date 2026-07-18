package com.serviceos.configuration.api;

import java.util.UUID;

public record ActivateBundleChannelCommand(
        UUID projectId,
        BundleChannel channel,
        UUID bundleId,
        String approvalRef,
        Integer trafficPercent,
        String slotCode,
        boolean autoPromoteWhenFull
) {
    public ActivateBundleChannelCommand(
            UUID projectId,
            BundleChannel channel,
            UUID bundleId,
            String approvalRef
    ) {
        this(projectId, channel, bundleId, approvalRef, null, null, false);
    }

    public ActivateBundleChannelCommand(
            UUID projectId,
            BundleChannel channel,
            UUID bundleId,
            String approvalRef,
            Integer trafficPercent
    ) {
        this(projectId, channel, bundleId, approvalRef, trafficPercent, null, false);
    }
}
