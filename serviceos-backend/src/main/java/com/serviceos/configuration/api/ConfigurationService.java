package com.serviceos.configuration.api;

public interface ConfigurationService {
    ConfigurationAssetVersionReference publishAsset(PublishConfigurationAssetCommand command);

    ConfigurationBundleReference publishBundle(PublishConfigurationBundleCommand command);

    ConfigurationBundleReference resolve(ResolveConfigurationBundleQuery query);

    ConfigurationAssetDefinition requireBundleAsset(
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
