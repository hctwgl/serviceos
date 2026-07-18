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

/** M275：PARALLEL_GATEWAY 分叉与汇聚计数。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkflowParallelGatewayPostgresIT {
    private static final String TENANT = "tenant-workflow-m275-it";

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
        registry.add("serviceos.outbox.worker-id", () -> "workflow-m275-it");
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
                    tsk_task_execution_attempt, tsk_task, wfl_parallel_join_token, wfl_parallel_join,
                    wfl_wait_subscription, wfl_node_instance, wfl_stage_instance, wfl_workflow_instance,
                    wo_work_order, cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project CASCADE
                """).update();
    }

    @Test
    void parallelForkActivatesBothBranchesAndJoinWaitsForAllTokens() {
        Scope scope = scope(parallelWorkflow());
        workOrders.receive(command(scope));
        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();

        assertThat(runTask("ASSIGN_COORDINATORS")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");

        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance
                 WHERE status = 'ACTIVE' ORDER BY node_id
                """).query(String.class).list())
                .containsExactly("BRANCH_A:ACTIVE", "BRANCH_B:ACTIVE");

        assertThat(runTask("BRANCH_A")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");
        assertThat(jdbc.sql("SELECT arrived_tokens FROM wfl_parallel_join WHERE status = 'OPEN'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM wfl_node_instance WHERE node_id = 'MERGE_TASK'")
                .query(Long.class).single()).isZero();

        assertThat(runTask("BRANCH_B")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");
        assertThat(jdbc.sql("SELECT status FROM wfl_parallel_join")
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance
                 WHERE node_id = 'MERGE_TASK'
                """).query(String.class).single()).isEqualTo("MERGE_TASK:ACTIVE");
    }

    private Scope scope(String workflowDefinition) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'BYD-WORKFLOW-M275', 'BYD', 'M275 并行网关测试',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        String normalized = workflowDefinition.trim();
        UUID assetId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "BYD-WORKFLOW-M275", "1.0.0", "1.0.0",
                normalized, Sha256.digest(normalized))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "BYD-WORKFLOW-M275-BUNDLE", "1.0.0", "BYD_OCEAN",
                        "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                        null, List.of(assetId)));
        return new Scope(projectId, bundle);
    }

    private ReceiveExternalWorkOrderCommand command(Scope scope) {
        return new ReceiveExternalWorkOrderCommand(
                TENANT, scope.projectId(), "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                "BYD-WORKFLOW-M275-ORDER", "a".repeat(64), scope.bundle().bundleId(),
                scope.bundle().bundleCode(), scope.bundle().bundleVersion(), scope.bundle().manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000", "济南测试地址",
                "LGXCE6CD0RA275001", LocalDateTime.of(2026, 7, 18, 15, 0),
                "corr-workflow-m275", "cause-workflow-m275");
    }

    private TaskExecutionWorker.RunResult runTask(String taskType) {
        AutomatedTaskHandler handler = new AutomatedTaskHandler() {
            @Override
            public String taskType() {
                return taskType;
            }

            @Override
            public TaskExecutionResult execute(TaskExecutionContext context) {
                return TaskExecutionResult.succeeded(taskType + "://" + context.taskId());
            }
        };
        return new TaskExecutionWorker(
                taskQueue, new TaskHandlerRegistry(List.of(handler)),
                "m275-" + taskType, Duration.ofSeconds(30)).runOnce();
    }

    private void drainOutbox() {
        for (int i = 0; i < 30; i++) {
            if (outboxWorker.runOnce() == OutboxWorker.RunResult.EMPTY) {
                return;
            }
        }
        throw new AssertionError("outbox did not drain");
    }

    private void publishUntil(String eventType) {
        for (int i = 0; i < 40; i++) {
            Long pending = jdbc.sql("""
                            SELECT count(*) FROM rel_outbox_event
                             WHERE event_type = :eventType AND status <> 'PUBLISHED'
                            """)
                    .param("eventType", eventType).query(Long.class).single();
            Long published = jdbc.sql("""
                            SELECT count(*) FROM rel_outbox_event
                             WHERE event_type = :eventType AND status = 'PUBLISHED'
                            """)
                    .param("eventType", eventType).query(Long.class).single();
            if (pending == 0 && published > 0) {
                return;
            }
            if (outboxWorker.runOnce() == OutboxWorker.RunResult.EMPTY && pending > 0) {
                throw new AssertionError(eventType + " stuck unpublished");
            }
        }
        throw new AssertionError(eventType + " was not fully published");
    }

    private static String parallelWorkflow() {
        return """
                {"workflowKey":"parallel.m275","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"INTAKE_TASK","nodeType":"SERVICE_TASK","name":"受理",
                    "stageCode":"INTAKE","taskType":"ASSIGN_COORDINATORS"},
                   {"nodeId":"FORK","nodeType":"PARALLEL_GATEWAY","name":"分叉"},
                   {"nodeId":"BRANCH_A","nodeType":"SERVICE_TASK","name":"分支A",
                    "stageCode":"PARALLEL","taskType":"BRANCH_A"},
                   {"nodeId":"BRANCH_B","nodeType":"SERVICE_TASK","name":"分支B",
                    "stageCode":"PARALLEL","taskType":"BRANCH_B"},
                   {"nodeId":"JOIN","nodeType":"PARALLEL_GATEWAY","name":"汇聚"},
                   {"nodeId":"MERGE_TASK","nodeType":"SERVICE_TASK","name":"汇合后",
                    "stageCode":"CLOSE","taskType":"MERGE"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"INTAKE_TASK"},
                   {"transitionId":"t2","from":"INTAKE_TASK","to":"FORK"},
                   {"transitionId":"t3","from":"FORK","to":"BRANCH_A"},
                   {"transitionId":"t4","from":"FORK","to":"BRANCH_B"},
                   {"transitionId":"t5","from":"BRANCH_A","to":"JOIN"},
                   {"transitionId":"t6","from":"BRANCH_B","to":"JOIN"},
                   {"transitionId":"t7","from":"JOIN","to":"MERGE_TASK"}]}
                """;
    }

    private record Scope(UUID projectId, ConfigurationBundleReference bundle) {
    }
}
