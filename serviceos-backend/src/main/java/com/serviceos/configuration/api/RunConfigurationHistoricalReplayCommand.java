package com.serviceos.configuration.api;

import java.util.UUID;

public record RunConfigurationHistoricalReplayCommand(
        UUID bundleId,
        String workflowAssetKey,
        ExpressionContext context,
        Integer maxSteps
) {
}
