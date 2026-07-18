package com.serviceos.configuration.web;

import com.serviceos.configuration.api.AnalyzeConfigurationDependenciesCommand;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationDependencyAnalysisService;
import com.serviceos.configuration.api.ConfigurationDependencyReport;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 配置依赖分析协议适配。 */
@RestController
@RequestMapping("/api/v1/configuration")
final class ConfigurationDependencyController {
    private final ConfigurationDependencyAnalysisService dependencies;
    private final CurrentPrincipalProvider principals;

    ConfigurationDependencyController(
            ConfigurationDependencyAnalysisService dependencies,
            CurrentPrincipalProvider principals
    ) {
        this.dependencies = dependencies;
        this.principals = principals;
    }

    @PostMapping("/dependency-reports:analyze")
    ResponseEntity<DependencyReportHttpModels.DependencyReportResponse> analyze(
            @RequestBody AnalyzeRequest request,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ConfigurationDependencyReport report = dependencies.analyze(
                principals.current(),
                correlationId,
                new AnalyzeConfigurationDependenciesCommand(
                        ConfigurationAssetType.valueOf(request.assetType()),
                        request.assetKey(),
                        request.definitionJson(),
                        request.bundleId()));
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(new DependencyReportHttpModels.DependencyReportResponse(
                        report.assetType().name(),
                        report.assetKey(),
                        report.draftId(),
                        report.bundleId(),
                        report.complete(),
                        report.dependencies().stream()
                                .map(item -> new DependencyReportHttpModels.DependencyItemResponse(
                                        item.refField(), item.refValue(), item.sourceNodeId(),
                                        item.expectedAssetType().name(), item.status().name(),
                                        item.satisfiedVersionId(), item.detail()))
                                .toList()));
    }

    record AnalyzeRequest(
            String assetType,
            String assetKey,
            String definitionJson,
            UUID bundleId
    ) {
    }
}
