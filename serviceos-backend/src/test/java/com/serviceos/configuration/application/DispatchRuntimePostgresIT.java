package com.serviceos.configuration.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.DispatchCandidate;
import com.serviceos.configuration.api.DispatchResolution;
import com.serviceos.configuration.api.DispatchResolveCommand;
import com.serviceos.configuration.api.DispatchRuntime;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class)
class DispatchRuntimePostgresIT {
    private static final String TENANT = "tenant-dispatch-runtime";

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
    @Autowired DispatchRuntime dispatchRuntime;
    @Autowired JdbcClient jdbc;

    ConfigurationBundleReference bundle;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project CASCADE
                """).update();
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'DISPATCH-RT', 'BYD', 'Dispatch Runtime',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", java.time.OffsetDateTime.now())
                .update();
        String workflow = "{\"workflowCode\":\"LINEAR_V1\",\"schemaVersion\":\"1.0.0\",\"stages\":[{\"stageCode\":\"S1\",\"tasks\":[{\"taskCode\":\"T1\",\"taskType\":\"HUMAN\"}]}]}";
        String dispatch = "{\"policyKey\":\"default-dispatch\",\"version\":\"1.0.0\",\"scope\":{\"brandCodes\":[\"BYD_OCEAN\"],\"businessTypes\":[\"HOME_CHARGING_SURVEY_INSTALL\"],\"regionCodes\":[\"370000\"]},\"hardFilters\":[{\"filterKey\":\"ENABLED\",\"order\":1,\"expression\":{\"language\":\"SERVICEOS_EXPR_V1\",\"source\":\"workOrder.brandCode == \\\"BYD_OCEAN\\\"\"},\"failureCode\":\"DISABLED\"},{\"filterKey\":\"CAPACITY\",\"order\":2,\"expression\":{\"language\":\"SERVICEOS_EXPR_V1\",\"source\":\"workOrder.brandCode == \\\"BYD_OCEAN\\\"\"},\"failureCode\":\"NO_CAPACITY\"}],\"scoring\":[{\"factorKey\":\"REMAINING_CAPACITY\",\"weight\":1.0,\"expression\":{\"language\":\"SERVICEOS_EXPR_V1\",\"source\":\"workOrder.brandCode == \\\"BYD_OCEAN\\\"\"}}],\"capacity\":{\"reservationRequired\":true},\"fallback\":{\"onNoCandidate\":\"MANUAL_INTERVENTION\",\"manualRole\":\"OPS\",\"resolutionHours\":4}}";
        var workflowAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "LINEAR_V1",
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        var dispatchAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.DISPATCH, "default-dispatch",
                "1.0.0", "1.0.0", dispatch, Sha256.digest(dispatch)));
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "DISPATCH-RT-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowAsset.versionId(), dispatchAsset.versionId())));
    }

    @Test
    void ranksCandidatesFromFrozenDispatchPolicy() {
        ExpressionContext context = new ExpressionContext(
                new ExpressionContext.WorkOrderContext("BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL"),
                new ExpressionContext.RegionContext("370000", "370100", "370102"),
                new ExpressionContext.TaskContext("DISPATCH", "AUTO"));
        DispatchCandidate strong = new DispatchCandidate(
                "net-strong", true, false, true,
                Set.of("BYD_OCEAN"), Set.of("370000"), Set.of("HOME_CHARGING_SURVEY_INSTALL"),
                8, 0.95, 1.0, 0.0, 0.0);
        DispatchCandidate weak = new DispatchCandidate(
                "net-weak", true, false, true,
                Set.of("BYD_OCEAN"), Set.of("370000"), Set.of("HOME_CHARGING_SURVEY_INSTALL"),
                2, 0.70, 1.0, 0.0, 0.0);
        DispatchResolution resolution = dispatchRuntime.resolve(new DispatchResolveCommand(
                TENANT, bundle.bundleId(), bundle.manifestDigest(), "default-dispatch",
                context, List.of(weak, strong)));
        assertThat(resolution.rankedCandidates().getFirst().candidateId()).isEqualTo("net-strong");
        assertThat(resolution.rankedCandidates().getFirst().score()).isEqualTo(8.0);
        assertThat(resolution.assetVersionId()).isNotNull();
    }
}
