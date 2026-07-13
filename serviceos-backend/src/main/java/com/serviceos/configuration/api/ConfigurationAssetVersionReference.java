package com.serviceos.configuration.api;

import java.util.UUID;

public record ConfigurationAssetVersionReference(
        UUID versionId,
        ConfigurationAssetType assetType,
        String assetKey,
        String semanticVersion,
        String contentDigest
) {
}
