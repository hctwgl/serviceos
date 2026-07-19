package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.application.DefaultAssigneePolicyRuntime;

import com.serviceos.configuration.api.AssigneePolicyResolveCommand;
import com.serviceos.configuration.api.AssigneePolicyResolution;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAssigneePolicyRuntimeTest {

    @Test
    void selectsHighestPriorityMatchingStrategyAndResolvesUsers() {
        String definition = """
                {
                  "policyKey":"default-assign",
                  "version":"1.0.0",
                  "strategies":[
                    {"strategyKey":"network-first","candidateType":"ROLE","priority":10,
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "roleCode":"NETWORK_DISPATCHER","maxCandidates":2},
                    {"strategyKey":"always","candidateType":"ROLE","priority":100,
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "roleCode":"OPS","maxCandidates":5}
                  ],
                  "fallback":{"mode":"MANUAL_INTERVENTION","roleCode":"OPS"}
                }
                """;
        var runtime = runtimeWith(definition);
        AssigneePolicyResolution resolution = runtime.resolve(new AssigneePolicyResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "default-assign",
                context("BYD_OCEAN"),
                Map.of("NETWORK_DISPATCHER", List.of("u1", "u2", "u3"), "OPS", List.of("ops1"))));
        assertThat(resolution.matchedStrategies()).hasSize(1);
        assertThat(resolution.matchedStrategies().getFirst().strategyKey()).isEqualTo("network-first");
        assertThat(resolution.resolvedUserPrincipalIds()).containsExactly("u1", "u2");
        assertThat(resolution.requiresManualIntervention()).isFalse();
        assertThat(resolution.fallback().applied()).isFalse();
        assertThat(resolution.contentDigest()).hasSize(64);
    }

    @Test
    void appliesFallbackWhenNoStrategyMatches() {
        String definition = """
                {
                  "policyKey":"fb",
                  "version":"1.0.0",
                  "strategies":[
                    {"strategyKey":"never","candidateType":"ROLE","priority":1,
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"OTHER\\""},
                     "roleCode":"NETWORK_DISPATCHER","maxCandidates":2}
                  ],
                  "fallback":{"mode":"ROLE_POOL","roleCode":"OPS"}
                }
                """;
        var runtime = runtimeWith(definition);
        AssigneePolicyResolution resolution = runtime.resolve(new AssigneePolicyResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "fb",
                context("BYD_OCEAN"), Map.of("OPS", List.of("ops-a"))));
        assertThat(resolution.matchedStrategies()).isEmpty();
        assertThat(resolution.fallback().applied()).isTrue();
        assertThat(resolution.resolvedUserPrincipalIds()).containsExactly("ops-a");
    }

    @Test
    void failsClosedWhenPolicyMissing() {
        var runtime = runtimeWith("""
                {"policyKey":"exists","version":"1.0.0",
                 "strategies":[{"strategyKey":"s","candidateType":"ROLE","priority":1,
                   "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                   "roleCode":"OPS","maxCandidates":1}],
                 "fallback":{"mode":"MANUAL_INTERVENTION","roleCode":"OPS"}}
                """);
        assertThatThrownBy(() -> runtime.resolve(new AssigneePolicyResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "missing",
                context("BYD_OCEAN"), Map.of())))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    private static ExpressionContext context(String brandCode) {
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext("BYD", brandCode, "HOME_CHARGING_SURVEY_INSTALL"),
                new ExpressionContext.RegionContext("370000", "370100", "370102"),
                new ExpressionContext.TaskContext("SURVEY", "HUMAN"));
    }

    private static DefaultAssigneePolicyRuntime runtimeWith(String definitionJson) {
        UUID versionId = UUID.randomUUID();
        String digest = Sha256.digest(definitionJson);
        ConfigurationAssetDefinition asset = new ConfigurationAssetDefinition(
                versionId, ConfigurationAssetType.ASSIGNEE_POLICY, "default-assign",
                "1.0.0", "1.0.0", definitionJson, digest);
        ConfigurationService configurations = new ConfigurationService() {
            @Override
            public com.serviceos.configuration.api.ConfigurationAssetVersionReference publishAsset(
                    com.serviceos.configuration.api.PublishConfigurationAssetCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.serviceos.configuration.api.ConfigurationBundleReference publishBundle(
                    com.serviceos.configuration.api.PublishConfigurationBundleCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.serviceos.configuration.api.ConfigurationBundleReference resolve(
                    com.serviceos.configuration.api.ResolveConfigurationBundleQuery query) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ConfigurationAssetDefinition requireBundleAsset(
                    String tenantId, UUID bundleId, String expectedManifestDigest,
                    ConfigurationAssetType assetType) {
                return asset;
            }

            @Override
            public List<ConfigurationAssetDefinition> listBundleAssets(
                    String tenantId, UUID bundleId, String expectedManifestDigest,
                    ConfigurationAssetType assetType) {
                return List.of(asset);
            }

            @Override
            public ConfigurationAssetDefinition requireAssetVersion(
                    String tenantId, UUID versionIdArg, ConfigurationAssetType assetType,
                    String expectedContentDigest) {
                throw new UnsupportedOperationException();
            }
        };
        return new DefaultAssigneePolicyRuntime(
                configurations, new ServiceOsExprV1Evaluator(), JsonMapper.builder().build());
    }
}
