package com.serviceos.configuration.api;

import java.util.UUID;

public record AdjustCanaryTrafficCommand(
        UUID activationId,
        long expectedVersion,
        int trafficPercent,
        boolean autoPromoteWhenFull
) {
}
