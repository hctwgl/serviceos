package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationDraftService;
import com.serviceos.configuration.api.ConfigurationDraftView;
import com.serviceos.configuration.api.ConfigurationSimulationOutcome;
import com.serviceos.configuration.api.ConfigurationSimulationReport;
import com.serviceos.configuration.api.ConfigurationWorkflowSimulationService;
import com.serviceos.configuration.api.CreateConfigurationDraftCommand;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.RunConfigurationSimulationCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** M292：WORKFLOW 干跑模拟。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConfigurationWorkflowSimulationPostgresIT {
    private static final String TENANT = "tenant-cfg-m292-it";
    private static final String ACTOR = "designer-m292";

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
        registry.add("serviceos.outbox.worker-id", () -> "cfg-m292-it");
    }

    @Autowired ConfigurationDraftService drafts;
    @Autowired ConfigurationWorkflowSimulationService simulations;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE cfg_configuration_asset_draft, cfg_configuration_bundle_item,
                    cfg_configuration_bundle, cfg_configuration_asset_version,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        seedGrants();
    }

    @Test
    void linearWorkflowCompletes() {
        ConfigurationDraftView draft = drafts.create(principal(), meta("linear"),
                new CreateConfigurationDraftCommand(
                        ConfigurationAssetType.WORKFLOW, "sim.linear.m292", "1.0.0", "1.0.0",
                        linearWorkflow(), null));
        ConfigurationSimulationReport report = simulations.simulateDraft(
                principal(), "corr-linear", draft.draftId(), emptyContext(), 32);
        assertThat(report.outcome()).isEqualTo(ConfigurationSimulationOutcome.COMPLETED);
        assertThat(report.steps()).extracting(step -> step.action())
                .contains("ENTER", "ACTIVATE", "COMPLETE", "END");
    }

    @Test
    void exclusiveGatewayChoosesMatchingBranch() {
        ConfigurationSimulationReport report = simulations.simulate(
                principal(), "corr-gw",
                new RunConfigurationSimulationCommand(
                        ConfigurationAssetType.WORKFLOW, "sim.gw.m292", exclusiveWorkflow(),
                        new ExpressionContext(
                                new ExpressionContext.WorkOrderContext("OEM_A", "BRAND", "INSTALL"),
                                new ExpressionContext.RegionContext("11", "1101", "110101"),
                                new ExpressionContext.TaskContext("STAGE_A", "DESIGNER_TASK"),
                                Map.of()),
                        64));
        assertThat(report.outcome()).isEqualTo(ConfigurationSimulationOutcome.COMPLETED);
        assertThat(report.steps()).anySatisfy(step ->
                assertThat(step.nodeId()).isEqualTo("TASK_A"));
        assertThat(report.steps()).noneSatisfy(step ->
                assertThat(step.nodeId()).isEqualTo("TASK_B"));
    }

    @Test
    void exclusiveGatewayZeroHitFailsClosed() {
        ConfigurationSimulationReport report = simulations.simulate(
                principal(), "corr-zero",
                new RunConfigurationSimulationCommand(
                        ConfigurationAssetType.WORKFLOW, "sim.zero.m292", exclusiveWorkflow(),
                        new ExpressionContext(
                                new ExpressionContext.WorkOrderContext("OTHER", "BRAND", "INSTALL"),
                                new ExpressionContext.RegionContext("11", "1101", "110101"),
                                new ExpressionContext.TaskContext("STAGE_A", "DESIGNER_TASK"),
                                Map.of()),
                        64));
        assertThat(report.outcome()).isEqualTo(ConfigurationSimulationOutcome.FAIL_CLOSED);
        assertThat(report.message()).contains("零命中");
    }

    @Test
    void waitEventPausesWithoutForgingSignal() {
        ConfigurationSimulationReport report = simulations.simulate(
                principal(), "corr-wait",
                new RunConfigurationSimulationCommand(
                        ConfigurationAssetType.WORKFLOW, "sim.wait.m292", waitWorkflow(),
                        emptyContext(), 32));
        assertThat(report.outcome()).isEqualTo(ConfigurationSimulationOutcome.WAITING);
        assertThat(report.message()).contains("WAIT_EVENT");
    }

    private void seedGrants() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'cfg-designer-m292', '配置设计器M292', 'ACTIVE', now())
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
                    now() - interval '1 day', 'TEST', 'm292', now()
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

    private static CommandMetadata meta(String key) {
        return new CommandMetadata("corr-" + key, "idem-" + key);
    }

    private static ExpressionContext emptyContext() {
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext(null, null, null),
                new ExpressionContext.RegionContext(null, null, null),
                new ExpressionContext.TaskContext(null, null),
                Map.of());
    }

    private static String linearWorkflow() {
        return """
                {"workflowKey":"sim.linear.m292","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"TASK_A","nodeType":"SERVICE_TASK","name":"任务A",
                    "stageCode":"STAGE_A","taskType":"DESIGNER_TASK"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"TASK_A"},
                   {"transitionId":"t2","from":"TASK_A","to":"END"}]}
                """;
    }

    private static String exclusiveWorkflow() {
        return """
                {"workflowKey":"sim.gw.m292","semanticVersion":"1.0.0","startNodeId":"START",
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

    private static String waitWorkflow() {
        return """
                {"workflowKey":"sim.wait.m292","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"WAIT","nodeType":"WAIT_EVENT","name":"等待",
                    "eventType":"oem.callback","correlationKeyTemplate":"{tenantId}:{externalOrderCode}"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"WAIT"},
                   {"transitionId":"t2","from":"WAIT","to":"END"}]}
                """;
    }
}
