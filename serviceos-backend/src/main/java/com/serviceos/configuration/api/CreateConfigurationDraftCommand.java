package com.serviceos.configuration.api;

import java.util.List;
import java.util.UUID;

public record CreateConfigurationDraftCommand(
        ConfigurationAssetType assetType,
        String assetKey,
        String intendedSemanticVersion,
        String schemaVersion,
        String definitionJson,
        UUID baseVersionId,
        List<String> supportedClientKinds
) {
    public CreateConfigurationDraftCommand {
        supportedClientKinds = supportedClientKinds == null
                ? null : List.copyOf(supportedClientKinds);
    }

    /** 未声明定向目标（默认全部生产师傅端）。 */
    public CreateConfigurationDraftCommand(
            ConfigurationAssetType assetType,
            String assetKey,
            String intendedSemanticVersion,
            String schemaVersion,
            String definitionJson,
            UUID baseVersionId
    ) {
        this(assetType, assetKey, intendedSemanticVersion, schemaVersion,
                definitionJson, baseVersionId, null);
    }
}
