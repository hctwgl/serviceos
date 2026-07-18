package com.serviceos.configuration.api;

import java.util.List;
import java.util.UUID;

public record ConfigurationDependencyReport(
        ConfigurationAssetType assetType,
        String assetKey,
        UUID draftId,
        UUID bundleId,
        boolean complete,
        List<ConfigurationDependencyItem> dependencies
) {
}
