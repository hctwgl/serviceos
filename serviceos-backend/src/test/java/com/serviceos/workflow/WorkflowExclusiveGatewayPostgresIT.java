package com.serviceos.workflow;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.reliability.application.OutboxWorker;
import com.serviceos.shared.Sha256;
import com.serviceos.task.application.TaskExecutionQueue;
import com.serviceos.task.application.TaskExecutionWorker;
import com.serviceos.task.application.TaskHandlerRegistry;
import com.serviceos.task.spi.AutomatedTaskHandler;
import com.serviceos.task.spi.TaskExecutionContext;
import com.serviceos.task.spi.TaskExecutionResult;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** M269：真实 PostgreSQL 验证 EXCLUSIVE_GATEWAY 唯一命中推进。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkflowExclusiveGatewayPostgresIT {
    private static final String TENANT = "tenant-workflow-m269-it";

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
        registry.add("serviceos.outbox.worker-id", () -> "workflow-m269-it");
    }

    @Autowired WorkOrderCommandService workOrders;
    @Autowired ConfigurationService configurations;
    @Autowired TaskExecutionQueue taskQueue;
    @Autowired OutboxWorker outboxWorker;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_work_order_timeline_entry, rel_outbox_publish_attempt, rel_outbox_event, rel_inbox_record,
                    tsk_task_execution_attempt, tsk_task, wfl_node_instance,
                    wfl_stage_instance, wfl_workflow_instance, wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project CASCADE
                """).update();
    }

    @Test
    void exclusiveGatewayAdvancesOnlyTheMatchingBranch() {
        Scope scope = scope(gatewayWorkflow());
        workOrders.receive(command(scope, "BYD_OCEAN"));
        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();

        assertThat(taskWorker("SURVEY").runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");

        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance ORDER BY activated_at, node_id
                """).query(String.class).list())
                .containsExactly("SURVEY_TASK:COMPLETED", "INSTALL_TASK:ACTIVE");
    }

    private Scope scope(String workflowDefinition) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'BYD-WORKFLOW-M269', 'BYD', 'M269 网关推进测试',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        String normalizedDefinition = workflowDefinition.trim();
        UUID assetId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "BYD-WORKFLOW-M269", "1.0.0", "1.0.0",
                normalizedDefinition, Sha256.digest(normalizedDefinition))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "BYD-WORKFLOW-M269-BUNDLE", "1.0.0", "BYD_OCEAN",
                        "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                        null, List.of(assetId)));
        return new Scope(projectId, bundle);
    }

    private ReceiveExternalWorkOrderCommand command(Scope scope, String brandCode) {
        return new ReceiveExternalWorkOrderCommand(
                TENANT, scope.projectId(), "BYD", brandCode, "HOME_CHARGING_SURVEY_INSTALL",
                "BYD-WORKFLOW-M269-ORDER", "d".repeat(64), scope.bundle().bundleId(),
                scope.bundle().bundleCode(), scope.bundle().bundleVersion(), scope.bundle().manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000", "济南测试地址",
                "LGXCE6CD0RA269001", LocalDateTime.of(2026, 7, 18, 9, 0),
                "corr-workflow-m269", "cause-workflow-m269");
    }

    private TaskExecutionWorker taskWorker(String taskType) {
        AutomatedTaskHandler handler = new AutomatedTaskHandler() {
            @Override
            public String taskType() {
                return taskType;
            }

            @Override
            public TaskExecutionResult execute(TaskExecutionContext context) {
                return TaskExecutionResult.succeeded(taskType.toLowerCase() + "://" + context.taskId());
            }
        };
        return new TaskExecutionWorker(
                taskQueue, new TaskHandlerRegistry(List.of(handler)),
                "m269-task-worker", Duration.ofSeconds(30));
    }

    private void drainOutbox() {
        for (int index = 0; index < 20; index++) {
            if (outboxWorker.runOnce() == OutboxWorker.RunResult.EMPTY) {
                return;
            }
        }
        throw new AssertionError("outbox did not drain");
    }

    private void publishUntil(String eventType) {
        for (int index = 0; index < 20; index++) {
            String status = jdbc.sql("SELECT status FROM rel_outbox_event WHERE event_type = :eventType")
                    .param("eventType", eventType).query(String.class).single();
            if ("PUBLISHED".equals(status)) {
                return;
            }
            OutboxWorker.RunResult result = outboxWorker.runOnce();
            if (result == OutboxWorker.RunResult.EMPTY) {
                String diagnostic = jdbc.sql("""
                                SELECT status || ':' || COALESCE(last_error_code, 'none')
                                  FROM rel_outbox_event
                                 WHERE event_type = :eventType
                                """)
                        .param("eventType", eventType).query(String.class).single();
                throw new AssertionError(eventType + " cannot be published: " + diagnostic);
            }
        }
        throw new AssertionError(eventType + " was not published");
    }

    private static String gatewayWorkflow() {
        return ("""
                {"workflowKey":"gateway.m269","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"SURVEY_TASK","nodeType":"SERVICE_TASK","name":"勘测",
                    "stageCode":"SURVEY","taskType":"SURVEY"},
                   {"nodeId":"GW","nodeType":"EXCLUSIVE_GATEWAY","name":"分支"},
                   {"nodeId":"INSTALL_TASK","nodeType":"SERVICE_TASK","name":"安装",
                    "stageCode":"INSTALL","taskType":"INSTALL"},
                   {"nodeId":"SKIP_TASK","nodeType":"SERVICE_TASK","name":"跳过",
                    "stageCode":"CLOSE","taskType":"SKIP"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"SURVEY_TASK"},
                   {"transitionId":"t2","from":"SURVEY_TASK","to":"GW"},
                   {"transitionId":"t3","from":"GW","to":"INSTALL_TASK","priority":10,
                    "condition":{"language":"SERVICEOS_EXPR_V1","source":"%s"}},
                   {"transitionId":"t4","from":"GW","to":"SKIP_TASK","priority":20,
                    "condition":{"language":"SERVICEOS_EXPR_V1","source":"%s"}}]}
                """).formatted(
                "workOrder.brandCode == \\\"BYD_OCEAN\\\"",
                "workOrder.brandCode != \\\"BYD_OCEAN\\\"");
    }

    private record Scope(UUID projectId, ConfigurationBundleReference bundle) {
    }
}
