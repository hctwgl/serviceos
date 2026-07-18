package com.serviceos.configuration.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** WORKFLOW 无副作用干跑模拟。 */
public interface ConfigurationWorkflowSimulationService {
    ConfigurationSimulationReport simulateDraft(
            CurrentPrincipal principal,
            String correlationId,
            UUID draftId,
            ExpressionContext context,
            Integer maxSteps);

    ConfigurationSimulationReport simulate(
            CurrentPrincipal principal,
            String correlationId,
            RunConfigurationSimulationCommand command);
}
