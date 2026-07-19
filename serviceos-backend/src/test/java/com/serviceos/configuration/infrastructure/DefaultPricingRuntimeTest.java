package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.PricingResolution;
import com.serviceos.configuration.api.PricingResolveCommand;
import com.serviceos.configuration.application.DefaultPricingRuntime;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPricingRuntimeTest {

    @Test
    void matchesLinesAndSumsAmountMinor() {
        String definition = """
                {
                  "pricingKey":"home-install",
                  "version":"1.0.0",
                  "currency":"CNY",
                  "lines":[
                    {"lineKey":"base","chargeCode":"BASE_INSTALL","amountMinor":150000,
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "billableTo":"OEM"},
                    {"lineKey":"extra","chargeCode":"EXTRA_CABLE","amountMinor":30000,
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "billableTo":"CUSTOMER"},
                    {"lineKey":"skip","chargeCode":"OTHER","amountMinor":999,
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"OTHER\\""},
                     "billableTo":"PLATFORM"}
                  ]
                }
                """;
        PricingResolution resolution = runtimeWith(definition).resolve(command("home-install"));
        assertThat(resolution.matchedLines()).hasSize(2);
        assertThat(resolution.totalAmountMinor()).isEqualTo(180000L);
        assertThat(resolution.currency()).isEqualTo("CNY");
        assertThat(resolution.matchedLines()).extracting(PricingResolution.MatchedLine::lineKey)
                .containsExactly("base", "extra");
        assertThat(resolution.contentDigest()).hasSize(64);
    }

    @Test
    void returnsZeroWhenNoLineMatches() {
        String definition = """
                {
                  "pricingKey":"home-install",
                  "version":"1.0.0",
                  "currency":"CNY",
                  "lines":[
                    {"lineKey":"skip","chargeCode":"OTHER","amountMinor":100,
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"OTHER\\""}}
                  ]
                }
                """;
        PricingResolution resolution = runtimeWith(definition).resolve(command("home-install"));
        assertThat(resolution.matchedLines()).isEmpty();
        assertThat(resolution.totalAmountMinor()).isZero();
    }

    @Test
    void failsClosedWhenPricingKeyMissing() {
        String definition = """
                {
                  "pricingKey":"exists",
                  "version":"1.0.0",
                  "currency":"CNY",
                  "lines":[
                    {"lineKey":"base","chargeCode":"BASE","amountMinor":1,
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""}}
                  ]
                }
                """;
        assertThatThrownBy(() -> runtimeWith(definition).resolve(command("missing")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    private static PricingResolveCommand command(String pricingKey) {
        return new PricingResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), pricingKey, context());
    }

    private static ExpressionContext context() {
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext("BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL"),
                new ExpressionContext.RegionContext("370000", "370100", "370102"),
                new ExpressionContext.TaskContext("SETTLE", "AUTO"));
    }

    private static DefaultPricingRuntime runtimeWith(String definitionJson) {
        String pricingKey = definitionJson.contains("\"pricingKey\":\"exists\"")
                ? "exists" : "home-install";
        UUID versionId = UUID.randomUUID();
        String digest = Sha256.digest(definitionJson);
        ConfigurationAssetDefinition asset = new ConfigurationAssetDefinition(
                versionId, ConfigurationAssetType.PRICING, pricingKey,
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
        return new DefaultPricingRuntime(
                configurations, new ServiceOsExprV1Evaluator(), JsonMapper.builder().build());
    }
}
