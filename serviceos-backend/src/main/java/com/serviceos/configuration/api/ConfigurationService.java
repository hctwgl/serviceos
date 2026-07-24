package com.serviceos.configuration.api;

public interface ConfigurationService {
    ConfigurationAssetVersionReference publishAsset(PublishConfigurationAssetCommand command);

    ConfigurationBundleReference publishBundle(PublishConfigurationBundleCommand command);

    default ConfigurationBundleReference publishBundleSuccessor(
            PublishConfigurationBundleSuccessorCommand command
    ) {
        throw new UnsupportedOperationException("bundle successor publication is not supported");
    }

    ConfigurationBundleReference resolve(ResolveConfigurationBundleQuery query);

    ConfigurationAssetDefinition requireBundleAsset(
            String tenantId,
            java.util.UUID bundleId,
            String expectedManifestDigest,
            ConfigurationAssetType assetType);

    java.util.List<ConfigurationAssetDefinition> listBundleAssets(
            String tenantId,
            java.util.UUID bundleId,
            String expectedManifestDigest,
            ConfigurationAssetType assetType);

    ConfigurationAssetDefinition requireAssetVersion(
            String tenantId,
            java.util.UUID versionId,
            ConfigurationAssetType assetType,
            String expectedContentDigest);
}
