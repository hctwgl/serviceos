package com.serviceos.configuration.api;

import java.util.UUID;

public record CreateConfigurationDraftCommand(
        ConfigurationAssetType assetType,
        String assetKey,
        String intendedSemanticVersion,
        String schemaVersion,
        String definitionJson,
        UUID baseVersionId
) {
}
