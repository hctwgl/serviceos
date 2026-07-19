package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.IntegrationMappingApplyCommand;
import com.serviceos.configuration.api.IntegrationMappingResult;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultIntegrationMappingRuntimeTest {

    @Test
    void appliesWhitelistTransformsAndBuildsExplanation() {
        String definition = """
                {
                  "mappingKey":"byd-create-v1",
                  "version":"1.0.0",
                  "connectorCode":"BYD_CPIM",
                  "direction":"INBOUND",
                  "fieldMappings":[
                    {"mappingId":"order","externalPath":"orderCode","internalPath":"externalOrderCode","required":true,"transform":"TRIM"},
                    {"mappingId":"name","externalPath":"contactName","internalPath":"customer.name","required":true,"transform":"UPPER"},
                    {"mappingId":"when","externalPath":"dispatchDate","internalPath":"dispatchedOn","required":false,"transform":"DATE_ISO"}
                  ]
                }
                """;
        var runtime = runtimeWith(definition);
        Map<String, Object> external = new LinkedHashMap<>();
        external.put("orderCode", "  ORD-1  ");
        external.put("contactName", "alice");
        external.put("dispatchDate", "2026-07-19 10:00:00");
        IntegrationMappingResult result = runtime.applyInbound(new IntegrationMappingApplyCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "byd-create-v1", external));
        assertThat(result.internalFields().get("externalOrderCode")).isEqualTo("ORD-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> customer = (Map<String, Object>) result.internalFields().get("customer");
        assertThat(customer.get("name")).isEqualTo("ALICE");
        assertThat(result.internalFields().get("dispatchedOn")).isEqualTo("2026-07-19");
        assertThat(result.explanations()).isNotEmpty();
        assertThat(result.contentDigest()).hasSize(64);
    }

    @Test
    void failsClosedWhenRequiredMissingOrUnknownTransform() {
        String missingRequired = """
                {"mappingKey":"m1","version":"1.0.0","connectorCode":"BYD_CPIM","direction":"INBOUND",
                 "fieldMappings":[{"mappingId":"order","externalPath":"orderCode","internalPath":"externalOrderCode","required":true,"transform":"NONE"}]}
                """;
        var runtime = runtimeWith(missingRequired);
        assertThatThrownBy(() -> runtime.applyInbound(new IntegrationMappingApplyCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "m1", Map.of())))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        String badTransform = """
                {"mappingKey":"m2","version":"1.0.0","connectorCode":"BYD_CPIM","direction":"INBOUND",
                 "fieldMappings":[{"mappingId":"order","externalPath":"orderCode","internalPath":"externalOrderCode","required":true,"transform":"SCRIPT"}]}
                """;
        var badRuntime = runtimeWith(badTransform);
        assertThatThrownBy(() -> badRuntime.applyInbound(new IntegrationMappingApplyCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "m2", Map.of("orderCode", "X"))))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    private static DefaultIntegrationMappingRuntime runtimeWith(String definitionJson) {
        UUID versionId = UUID.randomUUID();
        String digest = Sha256.digest(definitionJson);
        ConfigurationAssetDefinition asset = new ConfigurationAssetDefinition(
                versionId, ConfigurationAssetType.INTEGRATION, "byd-create",
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
        return new DefaultIntegrationMappingRuntime(configurations, JsonMapper.builder().build());
    }
}
