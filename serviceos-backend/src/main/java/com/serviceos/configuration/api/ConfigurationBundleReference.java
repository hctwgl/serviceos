package com.serviceos.configuration.api;

import java.util.UUID;

public record ConfigurationBundleReference(
        UUID bundleId,
        UUID projectId,
        String bundleCode,
        String bundleVersion,
        String manifestDigest
) {
}
