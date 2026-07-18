package com.serviceos.configuration.api;

import java.util.UUID;

public record ConfigurationDependencyItem(
        String refField,
        String refValue,
        String sourceNodeId,
        ConfigurationAssetType expectedAssetType,
        ConfigurationDependencyStatus status,
        UUID satisfiedVersionId,
        String detail
) {
}
