package com.serviceos.configuration.web;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationAssetVersionReference;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.shared.Sha256;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * 本地产品场景所需的最小配置基线编排。
 *
 * <p>该适配器只在 local profile 注册。它不直接写配置表，而是调用正式
 * {@link ConfigurationService}，因此仍执行资产 Schema、项目状态、配置包范围和不可变版本校验。
 * 生产环境不会暴露此入口。</p>
 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/product-development/projects/{projectId}")
final class ProductDevelopmentConfigurationController {
    private static final String WORKFLOW_KEY = "platform.home-charging.survey-install";
    private static final String SLA_KEY = "platform.home-charging.task-elapsed";
    private static final String INTEGRATION_KEY = "platform.byd-cpim.create-work-order";
    private static final String DISPATCH_KEY = "product.byd-ocean.network-dispatch";

    private final ConfigurationService configurations;
    private final AuthorizationService authorization;
    private final CurrentPrincipalProvider principals;
    private final Clock clock;

    ProductDevelopmentConfigurationController(
            ConfigurationService configurations,
            AuthorizationService authorization,
            CurrentPrincipalProvider principals,
            Clock clock
    ) {
        this.configurations = configurations;
        this.authorization = authorization;
        this.principals = principals;
        this.clock = clock;
    }

    @PostMapping("/configuration-foundation")
    ResponseEntity<FoundationResponse> publish(
            @PathVariable UUID projectId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) throws IOException {
        CurrentPrincipal principal = principals.current();
        authorization.require(principal, AuthorizationRequest.projectCapability(
                "configuration.publish", principal.tenantId(),
                "Project", projectId.toString(), projectId.toString()), correlationId);

        ConfigurationAssetVersionReference sla = publishAsset(
                principal.tenantId(), ConfigurationAssetType.SLA, SLA_KEY,
                "1.0.0", "configuration-templates/home-charging-survey-install/sla.json");
        ConfigurationAssetVersionReference workflow = publishAsset(
                principal.tenantId(), ConfigurationAssetType.WORKFLOW, WORKFLOW_KEY,
                "1.0.0", "configuration-templates/product-development/byd-ocean/workflow.json");
        ConfigurationAssetVersionReference integration = publishAsset(
                principal.tenantId(), ConfigurationAssetType.INTEGRATION, INTEGRATION_KEY,
                "1.0.0", "configuration-templates/home-charging-survey-install/byd-cpim-create-work-order.json");
        ConfigurationAssetVersionReference dispatch = publishAsset(
                principal.tenantId(), ConfigurationAssetType.DISPATCH, DISPATCH_KEY,
                "1.0.0", "configuration-templates/product-development/byd-ocean/dispatch.json");
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        principal.tenantId(), projectId,
                        "PRODUCT-HOME-CHARGING", "1.0.0", "BYD_OCEAN",
                        "HOME_CHARGING_SURVEY_INSTALL", "370000",
                        clock.instant().minusSeconds(60), null,
                        List.of(sla.versionId(), workflow.versionId(), integration.versionId(), dispatch.versionId())));
        return ResponseEntity.ok(new FoundationResponse(workflow.versionId(), bundle.bundleId()));
    }

    private ConfigurationAssetVersionReference publishAsset(
            String tenantId,
            ConfigurationAssetType type,
            String key,
            String semanticVersion,
            String classpath
    ) throws IOException {
        String definition = new ClassPathResource(classpath)
                .getContentAsString(StandardCharsets.UTF_8)
                .trim();
        return configurations.publishAsset(new PublishConfigurationAssetCommand(
                tenantId, type, key, semanticVersion, semanticVersion,
                definition, Sha256.digest(definition)));
    }

    record FoundationResponse(UUID workflowAssetVersionId, UUID sourceBundleId) {
    }
}
