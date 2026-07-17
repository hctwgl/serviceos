package com.serviceos.authorization.application;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface PageRegistryOverrideStore {
    Map<String, PageOverride> overridesForTenant(String tenantId);

    Set<String> enabledFeatureGates(String tenantId);

    record PageOverride(
            String pageId,
            boolean enabled,
            Optional<String> titleOverride,
            Optional<Integer> sortOrder,
            Optional<String> featureGate
    ) {
    }
}
