package com.serviceos.configuration.api;

import java.util.UUID;

public record AnalyzeConfigurationDependenciesCommand(
        ConfigurationAssetType assetType,
        String assetKey,
        String definitionJson,
        UUID bundleId
) {
}
