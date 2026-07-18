package com.serviceos.configuration.web;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationSimulationReport;
import com.serviceos.configuration.api.ConfigurationWorkflowSimulationService;
import com.serviceos.configuration.api.RunConfigurationSimulationCommand;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** WORKFLOW 干跑模拟协议适配。 */
@RestController
@RequestMapping("/api/v1/configuration")
final class ConfigurationSimulationController {
    private final ConfigurationWorkflowSimulationService simulations;
    private final CurrentPrincipalProvider principals;

    ConfigurationSimulationController(
            ConfigurationWorkflowSimulationService simulations,
            CurrentPrincipalProvider principals
    ) {
        this.simulations = simulations;
        this.principals = principals;
    }

    @PostMapping("/simulations:run")
    ResponseEntity<SimulationHttpModels.SimulationReportResponse> run(
            @RequestBody RunRequest request,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ConfigurationSimulationReport report = simulations.simulate(
                principals.current(),
                correlationId,
                new RunConfigurationSimulationCommand(
                        ConfigurationAssetType.valueOf(request.assetType()),
                        request.assetKey(),
                        request.definitionJson(),
                        SimulationHttpModels.toContext(request.context()),
                        request.maxSteps()));
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(SimulationHttpModels.toResponse(report));
    }

    record RunRequest(
            String assetType,
            String assetKey,
            String definitionJson,
            SimulationHttpModels.SimulationContextRequest context,
            Integer maxSteps
    ) {
    }
}
