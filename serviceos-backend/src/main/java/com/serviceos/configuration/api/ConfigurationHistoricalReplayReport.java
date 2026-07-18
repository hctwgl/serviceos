package com.serviceos.configuration.api;

import java.util.List;
import java.util.UUID;

public record ConfigurationHistoricalReplayReport(
        UUID bundleId,
        String bundleCode,
        String bundleVersion,
        String manifestDigest,
        UUID workflowVersionId,
        String workflowAssetKey,
        String workflowSemanticVersion,
        ConfigurationSimulationOutcome outcome,
        String message,
        List<ConfigurationSimulationStep> steps
) {
}
