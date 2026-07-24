package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationAssetVersionReference;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleSuccessorCommand;
import com.serviceos.configuration.api.ResolveConfigurationBundleQuery;
import com.serviceos.shared.Sha256;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * 本地产品场景配置基线的应用编排。
 *
 * <p>该服务只在 local profile 注册，并始终通过正式配置用例发布资产和后继配置包。
 * projectCode 由调用方显式传入并参与已发布配置解析，避免把某个演示项目身份写死在适配层。</p>
 */
@Profile("local")
@Service
public final class ProductDevelopmentConfigurationFoundationService {
    private static final String WORKFLOW_KEY = "platform.home-charging.survey-install";
    private static final String SLA_KEY = "platform.home-charging.task-elapsed";
    private static final String INBOUND_INTEGRATION_KEY = "platform.byd-cpim.create-work-order";
    private static final String OUTBOUND_INTEGRATION_KEY = "product.byd-ocean.submit-review";
    private static final String DISPATCH_KEY = "product.byd-ocean.network-dispatch";
    private static final String SURVEY_FORM_KEY = "product.byd-ocean.survey-form";
    private static final String SURVEY_EVIDENCE_KEY = "product.byd-ocean.survey-evidence";
    private static final String INSTALL_EVIDENCE_KEY = "product.byd-ocean.install-evidence";

    private final ConfigurationService configurations;
    private final Clock clock;

    public ProductDevelopmentConfigurationFoundationService(
            ConfigurationService configurations,
            Clock clock
    ) {
        this.configurations = configurations;
        this.clock = clock;
    }

    public FoundationResult publish(String tenantId, UUID projectId, String projectCode) throws IOException {
        ConfigurationAssetVersionReference sla = publishAsset(
                tenantId, ConfigurationAssetType.SLA, SLA_KEY,
                "1.0.0", "configuration-templates/home-charging-survey-install/sla.json");
        ConfigurationAssetVersionReference workflow = publishAsset(
                tenantId, ConfigurationAssetType.WORKFLOW, WORKFLOW_KEY,
                "1.0.0", "configuration-templates/product-development/byd-ocean/workflow.json");
        ConfigurationAssetVersionReference inboundIntegration = publishAsset(
                tenantId, ConfigurationAssetType.INTEGRATION, INBOUND_INTEGRATION_KEY,
                "1.0.0", "configuration-templates/home-charging-survey-install/byd-cpim-create-work-order.json");
        ConfigurationAssetVersionReference outboundIntegration = publishAsset(
                tenantId, ConfigurationAssetType.INTEGRATION, OUTBOUND_INTEGRATION_KEY,
                "1.0.0", "configuration-templates/product-development/byd-ocean/submit-review.json");
        ConfigurationAssetVersionReference dispatch = publishAsset(
                tenantId, ConfigurationAssetType.DISPATCH, DISPATCH_KEY,
                "1.0.0", "configuration-templates/product-development/byd-ocean/dispatch.json");
        ConfigurationAssetVersionReference surveyForm = publishAsset(
                tenantId, ConfigurationAssetType.FORM, SURVEY_FORM_KEY,
                "1.0.0", "configuration-templates/product-development/byd-ocean/survey-form.json");
        ConfigurationAssetVersionReference surveyEvidence = publishAsset(
                tenantId, ConfigurationAssetType.EVIDENCE, SURVEY_EVIDENCE_KEY,
                "1.0.0", "configuration-templates/product-development/byd-ocean/survey-evidence.json");
        ConfigurationAssetVersionReference installEvidence = publishAsset(
                tenantId, ConfigurationAssetType.EVIDENCE, INSTALL_EVIDENCE_KEY,
                "1.0.0", "configuration-templates/product-development/byd-ocean/install-evidence.json");

        var successor = new PublishConfigurationBundleCommand(
                tenantId, projectId,
                "PRODUCT-HOME-CHARGING", "1.1.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000",
                clock.instant().minusSeconds(60), null,
                List.of(sla.versionId(), workflow.versionId(),
                        inboundIntegration.versionId(), outboundIntegration.versionId(),
                        dispatch.versionId(), surveyForm.versionId(), surveyEvidence.versionId(),
                        installEvidence.versionId()));
        ConfigurationBundleReference current = configurations.resolve(
                new ResolveConfigurationBundleQuery(
                        tenantId, requiredProjectCode(projectCode), "BYD_OCEAN",
                        "HOME_CHARGING_SURVEY_INSTALL", "370000", clock.instant()));
        ConfigurationBundleReference bundle = configurations.publishBundleSuccessor(
                new PublishConfigurationBundleSuccessorCommand(current.bundleId(), successor));
        return new FoundationResult(workflow.versionId(), bundle.bundleId());
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

    private static String requiredProjectCode(String projectCode) {
        if (projectCode == null || projectCode.isBlank()) {
            throw new IllegalArgumentException("projectCode must not be blank");
        }
        return projectCode.trim();
    }

    public record FoundationResult(UUID workflowAssetVersionId, UUID sourceBundleId) {
    }
}
