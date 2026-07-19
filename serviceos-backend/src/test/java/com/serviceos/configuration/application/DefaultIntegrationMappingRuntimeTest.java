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
                  "direction":"INBOUND","messageType":"CREATE_WORK_ORDER",
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
    void selectsInboundMappingByConnectorCodeAndMessageType() {
        String createDef = """
                {"mappingKey":"byd-create-v1","version":"1.0.0","connectorCode":"BYD_CPIM","direction":"INBOUND","messageType":"CREATE_WORK_ORDER",
                 "fieldMappings":[{"mappingId":"order","externalPath":"orderCode","internalPath":"externalOrderCode","required":true,"transform":"TRIM"}]}
                """;
        String updateDef = """
                {"mappingKey":"byd-update-v1","version":"1.0.0","connectorCode":"BYD_CPIM","direction":"INBOUND","messageType":"UPDATE_WORK_ORDER",
                 "fieldMappings":[{"mappingId":"order","externalPath":"orderCode","internalPath":"externalOrderCode","required":true,"transform":"UPPER"}]}
                """;
        var runtime = runtimeWith(createDef, updateDef);
        assertThat(runtime.hasInboundMappingForConnector(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "BYD_CPIM", "CREATE_WORK_ORDER")).isTrue();
        assertThat(runtime.hasInboundMappingForConnector(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "BYD_CPIM", "UPDATE_WORK_ORDER")).isTrue();
        assertThat(runtime.hasInboundMappingForConnector(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "BYD_CPIM", "CANCEL_WORK_ORDER")).isFalse();
        assertThat(runtime.hasInboundMappingForConnector(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "OTHER", "CREATE_WORK_ORDER")).isFalse();
        assertThat(runtime.applyInboundForConnectorIfPresent(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "BYD_CPIM", "CREATE_WORK_ORDER",
                Map.of("orderCode", "  X  ")).orElseThrow().internalFields().get("externalOrderCode"))
                .isEqualTo("X");
        assertThat(runtime.applyInboundForConnectorIfPresent(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "BYD_CPIM", "UPDATE_WORK_ORDER",
                Map.of("orderCode", "  y  ")).orElseThrow().internalFields().get("externalOrderCode"))
                .isEqualTo("Y");
    }

    @Test
    void appliesOutboundMappingInternalToExternalPaths() {
        String definition = """
                {"mappingKey":"byd-submit-v1","version":"1.0.0","connectorCode":"BYD_CPIM","direction":"OUTBOUND",
                 "fieldMappings":[
                   {"mappingId":"operator","internalPath":"operator","externalPath":"operatePerson","required":true,"transform":"TRIM"},
                   {"mappingId":"order","internalPath":"externalOrderCode","externalPath":"orderCode","required":true,"transform":"UPPER"},
                   {"mappingId":"commit","internalPath":"commitDate","externalPath":"commitDate","required":true,"transform":"NONE"}
                 ]}
                """;
        var runtime = runtimeWith(definition);
        assertThat(runtime.hasOutboundMappingForConnector(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "BYD_CPIM")).isTrue();
        assertThat(runtime.hasInboundMappingForConnector(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "BYD_CPIM", "CREATE_WORK_ORDER")).isFalse();
        IntegrationMappingResult result = runtime.applyOutboundForConnectorIfPresent(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "BYD_CPIM",
                Map.of(
                        "operator", "  reviewer-1  ",
                        "externalOrderCode", "ord-9",
                        "commitDate", "2026-07-19 10:00:00"))
                .orElseThrow();
        assertThat(result.direction()).isEqualTo("OUTBOUND");
        assertThat(result.externalFields())
                .containsEntry("operatePerson", "reviewer-1")
                .containsEntry("orderCode", "ORD-9")
                .containsEntry("commitDate", "2026-07-19 10:00:00");
        assertThat(result.internalFields()).isEmpty();
        assertThat(result.assetVersionId()).isNotNull();
        assertThat(result.contentDigest()).hasSize(64);
    }

    @Test
    void appliesConstantDefaultEnumAndConditionDsl() {
        String definition = """
                {
                  "mappingKey":"byd-dsl-v1",
                  "version":"1.0.0",
                  "connectorCode":"BYD_CPIM",
                  "direction":"INBOUND","messageType":"CREATE_WORK_ORDER",
                  "fieldMappings":[
                    {"mappingId":"order","externalPath":"orderCode","internalPath":"externalOrderCode","required":true,"transform":"TRIM"},
                    {"mappingId":"brand","internalPath":"brandCode","required":true,"constantValue":"BYD_OCEAN","transform":"NONE"},
                    {"mappingId":"product","internalPath":"serviceProductCode","required":true,"constantValue":"HOME_CHARGING_SURVEY_INSTALL","transform":"NONE"},
                    {"mappingId":"mobile","externalPath":"contactMobile","internalPath":"customerMobile","required":true,"defaultValue":"13800000000","transform":"NONE"},
                    {"mappingId":"type","externalPath":"carOwnerType","internalPath":"ownerType","required":true,"transform":"NONE",
                     "enumMap":{"1":"PERSONAL","2":"CORPORATE"}},
                    {"mappingId":"dealer","externalPath":"dealerName","internalPath":"dealer","required":false,"transform":"TRIM",
                     "condition":{"sourcePath":"channel","operator":"EQUALS","value":"CPIM"}},
                    {"mappingId":"skip","externalPath":"wallboxName","internalPath":"wallbox","required":true,"transform":"NONE",
                     "condition":{"sourcePath":"channel","operator":"EQUALS","value":"OTHER"}}
                  ]
                }
                """;
        var runtime = runtimeWith(definition);
        Map<String, Object> external = new LinkedHashMap<>();
        external.put("orderCode", "  ORD-DSL  ");
        external.put("carOwnerType", "1");
        external.put("channel", "CPIM");
        external.put("dealerName", "  济南店  ");
        IntegrationMappingResult result = runtime.applyInbound(new IntegrationMappingApplyCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "byd-dsl-v1", external));
        assertThat(result.internalFields())
                .containsEntry("externalOrderCode", "ORD-DSL")
                .containsEntry("brandCode", "BYD_OCEAN")
                .containsEntry("serviceProductCode", "HOME_CHARGING_SURVEY_INSTALL")
                .containsEntry("customerMobile", "13800000000")
                .containsEntry("ownerType", "PERSONAL")
                .containsEntry("dealer", "济南店")
                .doesNotContainKey("wallbox");
        assertThat(result.explanations()).anyMatch(text -> text.contains("skipped by condition"));
    }

    @Test
    void enumMapMissAndConditionPresentFailClosedOrSkip() {
        String enumMiss = """
                {"mappingKey":"m-enum","version":"1.0.0","connectorCode":"BYD_CPIM","direction":"INBOUND","messageType":"CREATE_WORK_ORDER",
                 "fieldMappings":[{"mappingId":"type","externalPath":"carOwnerType","internalPath":"ownerType","required":true,"transform":"NONE",
                  "enumMap":{"1":"PERSONAL"}}]}
                """;
        var runtime = runtimeWith(enumMiss);
        assertThatThrownBy(() -> runtime.applyInbound(new IntegrationMappingApplyCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "m-enum", Map.of("carOwnerType", "9"))))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        String present = """
                {"mappingKey":"m-present","version":"1.0.0","connectorCode":"BYD_CPIM","direction":"INBOUND","messageType":"CREATE_WORK_ORDER",
                 "fieldMappings":[{"mappingId":"note","externalPath":"remark","internalPath":"note","required":false,"transform":"TRIM",
                  "condition":{"sourcePath":"remark","operator":"PRESENT"}}]}
                """;
        var presentRuntime = runtimeWith(present);
        assertThat(presentRuntime.applyInbound(new IntegrationMappingApplyCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "m-present", Map.of()))
                .internalFields()).isEmpty();
    }

    @Test
    void failsClosedWhenRequiredMissingOrUnknownTransform() {
        String missingRequired = """
                {"mappingKey":"m1","version":"1.0.0","connectorCode":"BYD_CPIM","direction":"INBOUND","messageType":"CREATE_WORK_ORDER",
                 "fieldMappings":[{"mappingId":"order","externalPath":"orderCode","internalPath":"externalOrderCode","required":true,"transform":"NONE"}]}
                """;
        var runtime = runtimeWith(missingRequired);
        assertThatThrownBy(() -> runtime.applyInbound(new IntegrationMappingApplyCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "m1", Map.of())))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        String badTransform = """
                {"mappingKey":"m2","version":"1.0.0","connectorCode":"BYD_CPIM","direction":"INBOUND","messageType":"CREATE_WORK_ORDER",
                 "fieldMappings":[{"mappingId":"order","externalPath":"orderCode","internalPath":"externalOrderCode","required":true,"transform":"SCRIPT"}]}
                """;
        var badRuntime = runtimeWith(badTransform);
        assertThatThrownBy(() -> badRuntime.applyInbound(new IntegrationMappingApplyCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "m2", Map.of("orderCode", "X"))))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    private static DefaultIntegrationMappingRuntime runtimeWith(String... definitionJsons) {
        List<ConfigurationAssetDefinition> assets = new java.util.ArrayList<>();
        int index = 0;
        for (String definitionJson : definitionJsons) {
            UUID versionId = UUID.randomUUID();
            String digest = Sha256.digest(definitionJson);
            assets.add(new ConfigurationAssetDefinition(
                    versionId, ConfigurationAssetType.INTEGRATION, "integration-" + index,
                    "1.0.0", "1.0.0", definitionJson, digest));
            index++;
        }
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
                return assets.getFirst();
            }

            @Override
            public List<ConfigurationAssetDefinition> listBundleAssets(
                    String tenantId, UUID bundleId, String expectedManifestDigest,
                    ConfigurationAssetType assetType) {
                return assets;
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
