package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.DispatchCandidate;
import com.serviceos.configuration.api.DispatchResolution;
import com.serviceos.configuration.api.DispatchResolveCommand;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.application.DefaultDispatchRuntime;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultDispatchRuntimeTest {

    @Test
    void filtersScoresAndBreaksTiesDeterministically() {
        String definition = """
                {
                  "policyKey":"default-dispatch",
                  "version":"1.0.0",
                  "scope":{"brandCodes":["BYD_OCEAN"],"businessTypes":["HOME_CHARGING_SURVEY_INSTALL"],"regionCodes":["370000"]},
                  "hardFilters":[
                    {"filterKey":"ENABLED","order":1,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "failureCode":"DISABLED"},
                    {"filterKey":"CAPACITY","order":2,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "failureCode":"NO_CAPACITY"},
                    {"filterKey":"BRAND_SCOPE","order":3,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "failureCode":"BRAND_OUT_OF_SCOPE"}
                  ],
                  "scoring":[
                    {"factorKey":"REMAINING_CAPACITY","weight":1.0,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""}},
                    {"factorKey":"NETWORK_SCORE","weight":10.0,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""}}
                  ],
                  "capacity":{"reservationRequired":true},
                  "fallback":{"onNoCandidate":"MANUAL_INTERVENTION","manualRole":"OPS","resolutionHours":4}
                }
                """;
        var runtime = runtimeWith(definition);
        DispatchCandidate a = candidate("net-b", true, false, true, 5, 0.9, 2.0);
        DispatchCandidate b = candidate("net-a", true, false, true, 5, 0.9, 2.0);
        DispatchCandidate disabled = candidate("net-z", false, false, true, 9, 1.0, 9.0);
        DispatchResolution resolution = runtime.resolve(new DispatchResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "default-dispatch",
                context(), List.of(a, b, disabled)));
        assertThat(resolution.rankedCandidates()).hasSize(2);
        // same score -> candidateId ascending: net-a then net-b
        assertThat(resolution.rankedCandidates().get(0).candidateId()).isEqualTo("net-a");
        assertThat(resolution.rankedCandidates().get(0).rank()).isEqualTo(1);
        assertThat(resolution.rankedCandidates().get(1).candidateId()).isEqualTo("net-b");
        assertThat(resolution.rejectedCandidates()).extracting(DispatchResolution.RejectedCandidate::candidateId)
                .contains("net-z");
        assertThat(resolution.requiresManualIntervention()).isFalse();
    }

    @Test
    void appliesFallbackWhenNoCandidateSurvives() {
        String definition = """
                {
                  "policyKey":"empty",
                  "version":"1.0.0",
                  "scope":{"brandCodes":["BYD_OCEAN"],"businessTypes":["HOME_CHARGING_SURVEY_INSTALL"],"regionCodes":["370000"]},
                  "hardFilters":[
                    {"filterKey":"CAPACITY","order":1,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "failureCode":"NO_CAPACITY"}
                  ],
                  "scoring":[],
                  "capacity":{"reservationRequired":true},
                  "fallback":{"onNoCandidate":"MANUAL_INTERVENTION","manualRole":"OPS","resolutionHours":2}
                }
                """;
        var runtime = runtimeWith(definition);
        DispatchResolution resolution = runtime.resolve(new DispatchResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "empty",
                context(), List.of(candidate("net-x", true, false, true, 0, 1.0, 1.0))));
        assertThat(resolution.rankedCandidates()).isEmpty();
        assertThat(resolution.fallback().applied()).isTrue();
        assertThat(resolution.requiresManualIntervention()).isTrue();
    }

    @Test
    void policyScopeMismatchFailsClosed() {
        String definition = """
                {
                  "policyKey":"scope-miss",
                  "version":"1.0.0",
                  "scope":{"brandCodes":["OTHER_BRAND"],"businessTypes":["HOME_CHARGING_SURVEY_INSTALL"],"regionCodes":["370000"]},
                  "hardFilters":[
                    {"filterKey":"ENABLED","order":1,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "failureCode":"DISABLED"}
                  ],
                  "scoring":[],
                  "capacity":{"reservationRequired":false},
                  "fallback":{"onNoCandidate":"MANUAL_INTERVENTION","manualRole":"OPS","resolutionHours":2}
                }
                """;
        var runtime = runtimeWith(definition);
        DispatchResolution resolution = runtime.resolve(new DispatchResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "scope-miss",
                context(), List.of(candidate("net-x", true, false, true, 5, 1.0, 1.0))));
        assertThat(resolution.rankedCandidates()).isEmpty();
        assertThat(resolution.requiresManualIntervention()).isTrue();
        assertThat(resolution.rejectedCandidates())
                .extracting(DispatchResolution.RejectedCandidate::failureCode)
                .containsOnly("POLICY_SCOPE_MISMATCH");
    }

    @Test
    void regionScopeMatchesCityOrDistrictCodes() {
        String definition = """
                {
                  "policyKey":"region-city",
                  "version":"1.0.0",
                  "scope":{"brandCodes":["BYD_OCEAN"],"businessTypes":["HOME_CHARGING_SURVEY_INSTALL"],"regionCodes":["370000"]},
                  "hardFilters":[
                    {"filterKey":"REGION_SCOPE","order":1,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "failureCode":"REGION_MISMATCH"}
                  ],
                  "scoring":[{"factorKey":"REMAINING_CAPACITY","weight":1.0,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""}}],
                  "capacity":{"reservationRequired":false},
                  "fallback":{"onNoCandidate":"MANUAL_INTERVENTION","manualRole":"OPS","resolutionHours":2}
                }
                """;
        var runtime = runtimeWith(definition);
        DispatchCandidate cityMatch = new DispatchCandidate(
                "net-city", true, false, true,
                Set.of("BYD_OCEAN"), Set.of("370100"), Set.of("HOME_CHARGING_SURVEY_INSTALL"),
                4, 0.0, 0.0, 0.0, 0.0);
        DispatchCandidate miss = new DispatchCandidate(
                "net-miss", true, false, true,
                Set.of("BYD_OCEAN"), Set.of("110000"), Set.of("HOME_CHARGING_SURVEY_INSTALL"),
                9, 0.0, 0.0, 0.0, 0.0);
        DispatchResolution resolution = runtime.resolve(new DispatchResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "region-city",
                context(), List.of(cityMatch, miss)));
        assertThat(resolution.rankedCandidates()).extracting(DispatchResolution.RankedCandidate::candidateId)
                .containsExactly("net-city");
        assertThat(resolution.rejectedCandidates()).extracting(DispatchResolution.RejectedCandidate::candidateId)
                .contains("net-miss");
    }

    @Test
    void allocationRatioGapPrefersUnderAllocatedCandidate() {
        String definition = """
                {
                  "policyKey":"ratio",
                  "version":"1.0.0",
                  "scope":{"brandCodes":["BYD_OCEAN"],"businessTypes":["HOME_CHARGING_SURVEY_INSTALL"],"regionCodes":["370000"]},
                  "hardFilters":[
                    {"filterKey":"ENABLED","order":1,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "failureCode":"DISABLED"}
                  ],
                  "scoring":[
                    {"factorKey":"ALLOCATION_RATIO_GAP","weight":10.0,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""}},
                    {"factorKey":"REMAINING_CAPACITY","weight":0.01,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""}}
                  ],
                  "allocationRatio":{"enabled":true,"period":"MONTH","measure":"ORDER_COUNT"},
                  "capacity":{"reservationRequired":false},
                  "fallback":{"onNoCandidate":"MANUAL_INTERVENTION","manualRole":"OPS","resolutionHours":2}
                }
                """;
        var runtime = runtimeWith(definition);
        DispatchCandidate strongCapacity = new DispatchCandidate(
                "net-strong", true, false, true,
                Set.of("BYD_OCEAN"), Set.of("370000"), Set.of("HOME_CHARGING_SURVEY_INSTALL"),
                10, 0.0, 0.0, 0.0, 0.1);
        DispatchCandidate weakCapacity = new DispatchCandidate(
                "net-weak", true, false, true,
                Set.of("BYD_OCEAN"), Set.of("370000"), Set.of("HOME_CHARGING_SURVEY_INSTALL"),
                2, 0.0, 0.0, 0.0, 0.8);
        DispatchResolution resolution = runtime.resolve(new DispatchResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "ratio",
                context(), List.of(strongCapacity, weakCapacity)));
        assertThat(resolution.rankedCandidates().getFirst().candidateId()).isEqualTo("net-weak");
    }

    @Test
    void allocationRatioDisabledZerosGapFactor() {
        String definition = """
                {
                  "policyKey":"ratio-off",
                  "version":"1.0.0",
                  "scope":{"brandCodes":["BYD_OCEAN"],"businessTypes":["HOME_CHARGING_SURVEY_INSTALL"],"regionCodes":["370000"]},
                  "hardFilters":[
                    {"filterKey":"ENABLED","order":1,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "failureCode":"DISABLED"}
                  ],
                  "scoring":[
                    {"factorKey":"ALLOCATION_RATIO_GAP","weight":100.0,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""}},
                    {"factorKey":"REMAINING_CAPACITY","weight":1.0,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""}}
                  ],
                  "allocationRatio":{"enabled":false,"period":"MONTH","measure":"ORDER_COUNT"},
                  "capacity":{"reservationRequired":false},
                  "fallback":{"onNoCandidate":"MANUAL_INTERVENTION","manualRole":"OPS","resolutionHours":2}
                }
                """;
        var runtime = runtimeWith(definition);
        DispatchCandidate strongCapacity = new DispatchCandidate(
                "net-strong", true, false, true,
                Set.of("BYD_OCEAN"), Set.of("370000"), Set.of("HOME_CHARGING_SURVEY_INSTALL"),
                10, 0.0, 0.0, 0.0, 0.1);
        DispatchCandidate weakCapacity = new DispatchCandidate(
                "net-weak", true, false, true,
                Set.of("BYD_OCEAN"), Set.of("370000"), Set.of("HOME_CHARGING_SURVEY_INSTALL"),
                2, 0.0, 0.0, 0.0, 0.9);
        DispatchResolution resolution = runtime.resolve(new DispatchResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "ratio-off",
                context(), List.of(strongCapacity, weakCapacity)));
        assertThat(resolution.rankedCandidates().getFirst().candidateId()).isEqualTo("net-strong");
    }

    @Test
    void failsClosedWhenPolicyMissing() {
        var runtime = runtimeWith("""
                {"policyKey":"exists","version":"1.0.0",
                 "scope":{"brandCodes":["BYD_OCEAN"],"businessTypes":["X"],"regionCodes":["370000"]},
                 "hardFilters":[{"filterKey":"ENABLED","order":1,
                   "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                   "failureCode":"DISABLED"}],
                 "scoring":[],
                 "fallback":{"onNoCandidate":"MANUAL_INTERVENTION","manualRole":"OPS","resolutionHours":1}}
                """);
        assertThatThrownBy(() -> runtime.resolve(new DispatchResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "missing", context(), List.of())))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    private static ExpressionContext context() {
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext("BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL"),
                new ExpressionContext.RegionContext("370000", "370100", "370102"),
                new ExpressionContext.TaskContext("DISPATCH", "AUTO"));
    }

    private static DispatchCandidate candidate(
            String id, boolean enabled, boolean blacklisted, boolean qualified,
            int capacity, double fulfillment, double networkScore
    ) {
        return new DispatchCandidate(
                id, enabled, blacklisted, qualified,
                Set.of("BYD_OCEAN"), Set.of("370000"), Set.of("HOME_CHARGING_SURVEY_INSTALL"),
                capacity, fulfillment, networkScore, 0.0, 0.0);
    }

    private static DefaultDispatchRuntime runtimeWith(String definitionJson) {
        UUID versionId = UUID.randomUUID();
        String digest = Sha256.digest(definitionJson);
        ConfigurationAssetDefinition asset = new ConfigurationAssetDefinition(
                versionId, ConfigurationAssetType.DISPATCH, "default-dispatch",
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
        return new DefaultDispatchRuntime(
                configurations, new ServiceOsExprV1Evaluator(), JsonMapper.builder().build());
    }
}
