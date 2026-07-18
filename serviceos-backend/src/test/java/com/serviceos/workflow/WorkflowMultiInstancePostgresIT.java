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

/** M278：多实例任务全部完成后才推进。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkflowMultiInstancePostgresIT {
    private static final String TENANT = "tenant-workflow-m278-it";

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
        registry.add("serviceos.outbox.worker-id", () -> "workflow-m278-it");
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
                    tsk_task_execution_attempt, tsk_task, wfl_multi_instance_slot, wfl_multi_instance,
                    wfl_subprocess_link, wfl_timer_subscription, wfl_wait_subscription,
                    wfl_parallel_join_token, wfl_parallel_join, wfl_node_instance, wfl_stage_instance,
                    wfl_workflow_instance, wo_work_order, cfg_configuration_bundle_item,
                    cfg_configuration_bundle, cfg_configuration_asset_version, prj_project CASCADE
                """).update();
    }

    @Test
    void multiInstanceWaitsForAllSlotsBeforeAdvancing() {
        Scope scope = scope(multiWorkflow());
        workOrders.receive(command(scope));
        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();

        assertThat(runTask("ASSIGN_COORDINATORS")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishPending("task.completed");

        assertThat(jdbc.sql("""
                SELECT count(*) FROM wfl_node_instance
                 WHERE node_id = 'MULTI_TASK' AND status = 'ACTIVE'
                """).query(Long.class).single()).isEqualTo(3L);
        assertThat(jdbc.sql("SELECT expected_instances FROM wfl_multi_instance WHERE status = 'OPEN'")
                .query(Integer.class).single()).isEqualTo(3);

        assertThat(runTask("MULTI_WORK")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishPending("task.completed");
        assertThat(jdbc.sql("SELECT completed_instances FROM wfl_multi_instance WHERE status = 'OPEN'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM wfl_node_instance WHERE node_id = 'AFTER_MULTI'")
                .query(Long.class).single()).isZero();

        assertThat(runTask("MULTI_WORK")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishPending("task.completed");
        assertThat(runTask("MULTI_WORK")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishPending("task.completed");

        assertThat(jdbc.sql("SELECT status FROM wfl_multi_instance").query(String.class).single())
                .isEqualTo("COMPLETED");
        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance WHERE node_id = 'AFTER_MULTI'
                """).query(String.class).single()).isEqualTo("AFTER_MULTI:ACTIVE");
    }

    private Scope scope(String workflowDefinition) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'BYD-WORKFLOW-M278', 'BYD', 'M278 多实例测试',
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
                TENANT, ConfigurationAssetType.WORKFLOW, "BYD-WORKFLOW-M278", "1.0.0", "1.0.0",
                normalized, Sha256.digest(normalized))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "BYD-WORKFLOW-M278-BUNDLE", "1.0.0", "BYD_OCEAN",
                        "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                        null, List.of(assetId)));
        return new Scope(projectId, bundle);
    }

    private ReceiveExternalWorkOrderCommand command(Scope scope) {
        return new ReceiveExternalWorkOrderCommand(
                TENANT, scope.projectId(), "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                "BYD-WORKFLOW-M278-ORDER", "d".repeat(64), scope.bundle().bundleId(),
                scope.bundle().bundleCode(), scope.bundle().bundleVersion(), scope.bundle().manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000", "济南测试地址",
                "LGXCE6CD0RA278001", LocalDateTime.of(2026, 7, 18, 18, 0),
                "corr-workflow-m278", "cause-workflow-m278");
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
                "m278-" + taskType + "-" + UUID.randomUUID(), Duration.ofSeconds(30)).runOnce();
    }

    private void drainOutbox() {
        for (int i = 0; i < 30; i++) {
            if (outboxWorker.runOnce() == OutboxWorker.RunResult.EMPTY) {
                return;
            }
        }
        throw new AssertionError("outbox did not drain");
    }

    private void publishPending(String eventType) {
        for (int i = 0; i < 40; i++) {
            Long pending = jdbc.sql("""
                            SELECT count(*) FROM rel_outbox_event
                             WHERE event_type = :eventType AND status <> 'PUBLISHED'
                            """)
                    .param("eventType", eventType).query(Long.class).single();
            if (pending == 0) {
                return;
            }
            outboxWorker.runOnce();
        }
        throw new AssertionError(eventType + " pending not drained");
    }

    private static String multiWorkflow() {
        return """
                {"workflowKey":"multi.m278","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"INTAKE","nodeType":"SERVICE_TASK","name":"受理",
                    "stageCode":"INTAKE","taskType":"ASSIGN_COORDINATORS"},
                   {"nodeId":"MULTI_TASK","nodeType":"SERVICE_TASK","name":"多实例",
                    "stageCode":"WORK","taskType":"MULTI_WORK",
                    "multiInstance":{"cardinality":3}},
                   {"nodeId":"AFTER_MULTI","nodeType":"SERVICE_TASK","name":"汇合后",
                    "stageCode":"CLOSE","taskType":"AFTER_MULTI"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"INTAKE"},
                   {"transitionId":"t2","from":"INTAKE","to":"MULTI_TASK"},
                   {"transitionId":"t3","from":"MULTI_TASK","to":"AFTER_MULTI"}]}
                """;
    }

    private record Scope(UUID projectId, ConfigurationBundleReference bundle) {
    }
}
