package com.serviceos.configuration.api;

import java.util.List;
import java.util.UUID;

public record ConfigurationSimulationReport(
        ConfigurationAssetType assetType,
        String assetKey,
        UUID draftId,
        ConfigurationSimulationOutcome outcome,
        String message,
        List<ConfigurationSimulationStep> steps
) {
}
