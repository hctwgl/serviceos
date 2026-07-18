package com.serviceos.configuration.web;

import com.serviceos.configuration.api.ConfigurationHistoricalReplayReport;
import com.serviceos.configuration.api.ConfigurationHistoricalReplayService;
import com.serviceos.configuration.api.RunConfigurationHistoricalReplayCommand;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 配置历史回放协议适配。 */
@RestController
@RequestMapping("/api/v1/configuration")
final class ConfigurationHistoricalReplayController {
    private final ConfigurationHistoricalReplayService replays;
    private final CurrentPrincipalProvider principals;

    ConfigurationHistoricalReplayController(
            ConfigurationHistoricalReplayService replays,
            CurrentPrincipalProvider principals
    ) {
        this.replays = replays;
        this.principals = principals;
    }

    @PostMapping("/replays:run")
    ResponseEntity<ReplayResponse> run(
            @RequestBody ReplayRequest request,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ConfigurationHistoricalReplayReport report = replays.replay(
                principals.current(),
                correlationId,
                new RunConfigurationHistoricalReplayCommand(
                        request.bundleId(),
                        request.workflowAssetKey(),
                        SimulationHttpModels.toContext(request.context()),
                        request.maxSteps()));
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(new ReplayResponse(
                        report.bundleId(),
                        report.bundleCode(),
                        report.bundleVersion(),
                        report.manifestDigest(),
                        report.workflowVersionId(),
                        report.workflowAssetKey(),
                        report.workflowSemanticVersion(),
                        report.outcome().name(),
                        report.message(),
                        report.steps().stream()
                                .map(step -> new SimulationHttpModels.SimulationStepResponse(
                                        step.index(), step.nodeId(), step.nodeType(),
                                        step.action(), step.detail()))
                                .toList()));
    }

    record ReplayRequest(
            UUID bundleId,
            String workflowAssetKey,
            SimulationHttpModels.SimulationContextRequest context,
            Integer maxSteps
    ) {
    }

    record ReplayResponse(
            UUID bundleId,
            String bundleCode,
            String bundleVersion,
            String manifestDigest,
            UUID workflowVersionId,
            String workflowAssetKey,
            String workflowSemanticVersion,
            String outcome,
            String message,
            java.util.List<SimulationHttpModels.SimulationStepResponse> steps
    ) {
    }
}
