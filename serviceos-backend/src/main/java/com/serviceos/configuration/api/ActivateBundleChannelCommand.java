package com.serviceos.configuration.api;

import java.util.UUID;

public record ActivateBundleChannelCommand(
        UUID projectId,
        BundleChannel channel,
        UUID bundleId,
        String approvalRef
) {
}
