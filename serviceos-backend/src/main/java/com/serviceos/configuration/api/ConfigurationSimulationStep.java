package com.serviceos.configuration.api;

public record ConfigurationSimulationStep(
        int index,
        String nodeId,
        String nodeType,
        String action,
        String detail
) {
}
