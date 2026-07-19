package com.serviceos.configuration.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.IntegrationMappingApplyCommand;
import com.serviceos.configuration.api.IntegrationMappingResult;
import com.serviceos.configuration.api.IntegrationMappingRuntime;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class)
class IntegrationMappingRuntimePostgresIT {
    private static final String TENANT = "tenant-integration-runtime";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos")
            .withUsername("serviceos_test")
            .withPassword("serviceos_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired ConfigurationService configurations;
    @Autowired IntegrationMappingRuntime mappingRuntime;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    ConfigurationBundleReference bundle;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project CASCADE
                """).update();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'INT-RUNTIME', 'BYD', 'Mapping Runtime',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", java.time.OffsetDateTime.now())
                .update();

        String workflow = "{\"workflowCode\":\"LINEAR_V1\",\"schemaVersion\":\"1.0.0\",\"stages\":[{\"stageCode\":\"S1\",\"tasks\":[{\"taskCode\":\"T1\",\"taskType\":\"HUMAN\"}]}]}";
        // 使用与发布方一致的紧凑 JSON，避免 text-block 空白导致 digest 不一致。
        String integration = "{\"mappingKey\":\"byd-create-runtime\",\"version\":\"1.0.0\",\"connectorCode\":\"BYD_CPIM\",\"direction\":\"INBOUND\",\"fieldMappings\":[{\"mappingId\":\"order\",\"externalPath\":\"orderCode\",\"internalPath\":\"externalOrderCode\",\"required\":true,\"transform\":\"TRIM\"},{\"mappingId\":\"mobile\",\"externalPath\":\"contactMobile\",\"internalPath\":\"customerMobile\",\"required\":true,\"transform\":\"NONE\"}]}";
        var workflowAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "LINEAR_V1",
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        var integrationAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.INTEGRATION, "byd-create-runtime",
                "1.0.0", "1.0.0", integration, Sha256.digest(integration)));
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "INT-RUNTIME-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, java.util.List.of(workflowAsset.versionId(), integrationAsset.versionId())));
    }

    @Test
    void appliesFrozenBundleMappingAndRejectsWrongDigest() {
        Map<String, Object> external = new LinkedHashMap<>();
        external.put("orderCode", "  ORD-9  ");
        external.put("contactMobile", "13800000000");
        IntegrationMappingResult result = mappingRuntime.applyInbound(new IntegrationMappingApplyCommand(
                TENANT, bundle.bundleId(), bundle.manifestDigest(), "byd-create-runtime", external));
        assertThat(result.internalFields().get("externalOrderCode")).isEqualTo("ORD-9");
        assertThat(result.internalFields().get("customerMobile")).isEqualTo("13800000000");
        assertThat(result.connectorCode()).isEqualTo("BYD_CPIM");

        assertThatThrownBy(() -> mappingRuntime.applyInbound(new IntegrationMappingApplyCommand(
                TENANT, bundle.bundleId(), "b".repeat(64), "byd-create-runtime", external)))
                .isInstanceOf(BusinessProblem.class);
    }

    @Test
    void failsClosedWhenMappingKeyMissingInBundle() {
        assertThatThrownBy(() -> mappingRuntime.applyInbound(new IntegrationMappingApplyCommand(
                TENANT, bundle.bundleId(), bundle.manifestDigest(), "missing-key",
                Map.of("orderCode", "X", "contactMobile", "1"))))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void appliesFrozenOutboundMappingToExternalFields() {
        String outbound = "{\"mappingKey\":\"byd-submit-runtime\",\"version\":\"1.0.0\",\"connectorCode\":\"BYD_CPIM\",\"direction\":\"OUTBOUND\",\"fieldMappings\":[{\"mappingId\":\"operator\",\"internalPath\":\"operator\",\"externalPath\":\"operatePerson\",\"required\":true,\"transform\":\"TRIM\"},{\"mappingId\":\"order\",\"internalPath\":\"externalOrderCode\",\"externalPath\":\"orderCode\",\"required\":true,\"transform\":\"NONE\"},{\"mappingId\":\"commit\",\"internalPath\":\"commitDate\",\"externalPath\":\"commitDate\",\"required\":true,\"transform\":\"NONE\"}]}";
        var outboundAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.INTEGRATION, "byd-submit-runtime",
                "1.0.0", "1.0.0", outbound, Sha256.digest(outbound)));
        String workflow = "{\"workflowCode\":\"LINEAR_V1\",\"schemaVersion\":\"1.0.0\",\"stages\":[{\"stageCode\":\"S1\",\"tasks\":[{\"taskCode\":\"T1\",\"taskType\":\"HUMAN\"}]}]}";
        var workflowAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "LINEAR_V1_OUT",
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        // 使用不同 province 避免与 setUp 中 INBOUND Bundle 解析范围重叠。
        ConfigurationBundleReference outboundBundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "INT-OUTBOUND-BUNDLE", "1.0.0", "BYD_OCEAN",
                        "HOME_CHARGING_SURVEY_INSTALL", "110000", Instant.now().minusSeconds(30),
                        null, java.util.List.of(workflowAsset.versionId(), outboundAsset.versionId())));

        assertThat(mappingRuntime.hasOutboundMappingForConnector(
                TENANT, outboundBundle.bundleId(), outboundBundle.manifestDigest(), "BYD_CPIM"))
                .isTrue();
        IntegrationMappingResult result = mappingRuntime.applyOutboundForConnectorIfPresent(
                TENANT, outboundBundle.bundleId(), outboundBundle.manifestDigest(), "BYD_CPIM",
                Map.of(
                        "operator", "  op-1  ",
                        "externalOrderCode", "ORD-OUT-1",
                        "commitDate", "2026-07-19 12:00:00"))
                .orElseThrow();
        assertThat(result.externalFields())
                .containsEntry("operatePerson", "op-1")
                .containsEntry("orderCode", "ORD-OUT-1")
                .containsEntry("commitDate", "2026-07-19 12:00:00");
        assertThat(result.assetVersionId()).isEqualTo(outboundAsset.versionId());
        assertThat(result.contentDigest()).isEqualTo(Sha256.digest(outbound));
    }
}
