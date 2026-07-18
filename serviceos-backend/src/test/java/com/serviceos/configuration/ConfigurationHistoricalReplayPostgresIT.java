package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationHistoricalReplayReport;
import com.serviceos.configuration.api.ConfigurationHistoricalReplayService;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ConfigurationSimulationOutcome;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.configuration.api.RunConfigurationHistoricalReplayCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M296：冻结 Bundle WORKFLOW 历史回放。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConfigurationHistoricalReplayPostgresIT {
    private static final String TENANT = "tenant-cfg-m296-it";
    private static final String ACTOR = "designer-m296";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos")
            .withUsername("serviceos_test")
            .withPassword("serviceos_test");

    @org.springframework.test.context.DynamicPropertySource
    static void properties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("serviceos.outbox.worker-id", () -> "cfg-m296-it");
    }

    @Autowired ConfigurationService configurations;
    @Autowired ConfigurationHistoricalReplayService replays;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    ConfigurationBundleReference bundle;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE cfg_bundle_channel_activation, cfg_configuration_asset_draft,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, auth_role_grant, auth_role_capability,
                    auth_role, prj_project CASCADE
                """).update();
        seedGrants();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'PLATFORM-M296', 'PLATFORM', 'M296 回放测试',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();

        UUID workflowId = publishWorkflow("platform.m296.workflow", exclusiveWorkflow());
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "BUNDLE-M296", "1.0.0", "PLATFORM",
                "HOME_CHARGING_SURVEY_INSTALL", "370000",
                Instant.now().minusSeconds(60), null, List.of(workflowId)));
    }

    @Test
    void replayLockedWorkflowChoosesBranch() {
        ConfigurationHistoricalReplayReport report = replays.replay(
                principal(), "corr-replay",
                new RunConfigurationHistoricalReplayCommand(
                        bundle.bundleId(),
                        "platform.m296.workflow",
                        new ExpressionContext(
                                new ExpressionContext.WorkOrderContext("OEM_A", "PLATFORM", "INSTALL"),
                                new ExpressionContext.RegionContext("37", "3701", "370101"),
                                new ExpressionContext.TaskContext("STAGE_A", "DESIGNER_TASK"),
                                Map.of()),
                        64));

        assertThat(report.bundleId()).isEqualTo(bundle.bundleId());
        assertThat(report.manifestDigest()).isEqualTo(bundle.manifestDigest());
        assertThat(report.workflowAssetKey()).isEqualTo("platform.m296.workflow");
        assertThat(report.outcome()).isEqualTo(ConfigurationSimulationOutcome.COMPLETED);
        assertThat(report.steps()).anySatisfy(step -> assertThat(step.nodeId()).isEqualTo("TASK_A"));
        assertThat(report.steps()).noneSatisfy(step -> assertThat(step.nodeId()).isEqualTo("TASK_B"));
    }

    @Test
    void unknownBundleFailsClosed() {
        assertThatThrownBy(() -> replays.replay(
                principal(), "corr-missing",
                new RunConfigurationHistoricalReplayCommand(
                        UUID.randomUUID(), null, emptyContext(), 16)))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.RESOURCE_NOT_FOUND);
    }

    private UUID publishWorkflow(String key, String definition) {
        String trimmed = definition.trim();
        return configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, key, "1.0.0", "1.0.0",
                trimmed, Sha256.digest(trimmed))).versionId();
    }

    private void seedGrants() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'cfg-designer-m296', '配置设计器M296', 'ACTIVE', now())
                """).param("id", roleId).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:id, 'configuration.draft.write', now())
                """).param("id", roleId).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at
                ) VALUES (
                    :grant, :tenant, :principal, :role, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST', 'm296', now()
                )
                """)
                .param("grant", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("principal", ACTOR)
                .param("role", roleId)
                .update();
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(ACTOR, TENANT, CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("configuration.draft.write"));
    }

    private static ExpressionContext emptyContext() {
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext(null, null, null),
                new ExpressionContext.RegionContext(null, null, null),
                new ExpressionContext.TaskContext(null, null),
                Map.of());
    }

    private static String exclusiveWorkflow() {
        return """
                {"workflowKey":"platform.m296.workflow","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"GW","nodeType":"EXCLUSIVE_GATEWAY","name":"分流"},
                   {"nodeId":"TASK_A","nodeType":"SERVICE_TASK","name":"任务A",
                    "stageCode":"STAGE_A","taskType":"DESIGNER_TASK"},
                   {"nodeId":"TASK_B","nodeType":"SERVICE_TASK","name":"任务B",
                    "stageCode":"STAGE_B","taskType":"DESIGNER_TASK"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"GW"},
                   {"transitionId":"t2","from":"GW","to":"TASK_A","priority":10,
                    "condition":{"language":"SERVICEOS_EXPR_V1",
                      "source":"workOrder.clientCode == \\"OEM_A\\""}},
                   {"transitionId":"t3","from":"GW","to":"TASK_B","priority":20,
                    "condition":{"language":"SERVICEOS_EXPR_V1",
                      "source":"workOrder.clientCode == \\"OEM_B\\""}},
                   {"transitionId":"t4","from":"TASK_A","to":"END"},
                   {"transitionId":"t5","from":"TASK_B","to":"END"}]}
                """;
    }
}
