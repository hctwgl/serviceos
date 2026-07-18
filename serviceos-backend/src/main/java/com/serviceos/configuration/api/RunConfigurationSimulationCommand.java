package com.serviceos.configuration.api;

import java.util.Map;

public record RunConfigurationSimulationCommand(
        ConfigurationAssetType assetType,
        String assetKey,
        String definitionJson,
        ExpressionContext context,
        Integer maxSteps
) {
    public RunConfigurationSimulationCommand {
        if (context == null) {
            context = new ExpressionContext(
                    new ExpressionContext.WorkOrderContext(null, null, null),
                    new ExpressionContext.RegionContext(null, null, null),
                    new ExpressionContext.TaskContext(null, null),
                    Map.of());
        }
    }
}
