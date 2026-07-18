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
import com.serviceos.workorder.api.CancelWorkOrderCommand;
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

/** M280：取消时为已完成且声明补偿的节点创建补偿任务。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkflowCompensationOnCancelPostgresIT {
    private static final String TENANT = "tenant-workflow-m280-it";

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
        registry.add("serviceos.outbox.worker-id", () -> "workflow-m280-it");
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
                    tsk_task_execution_attempt, tsk_task, wfl_compensation_request,
                    wfl_multi_instance_slot, wfl_multi_instance, wfl_subprocess_link,
                    wfl_timer_subscription, wfl_wait_subscription, wfl_parallel_join_token,
                    wfl_parallel_join, wfl_node_instance, wfl_stage_instance, wfl_workflow_instance,
                    wo_work_order, cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project CASCADE
                """).update();
    }

    @Test
    void cancelCreatesCompensationTaskForCompletedCompensatableNode() {
        Scope scope = scope(compensatableWorkflow());
        var receipt = workOrders.receive(command(scope));
        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();

        assertThat(runTask("SURVEY")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishPending("task.completed");

        assertThat(jdbc.sql("""
                SELECT status FROM wfl_node_instance WHERE node_id='NODE_SURVEY'
                """).query(String.class).single()).isEqualTo("COMPLETED");

        long version = jdbc.sql("SELECT version FROM wo_work_order").query(Long.class).single();
        workOrders.cancel(new CancelWorkOrderCommand(
                TENANT, receipt.workOrderId(), version, "CUSTOMER_CANCELLED",
                "APR-COMP-1", "corr-comp", "cause-comp"));
        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();

        assertThat(jdbc.sql("""
                SELECT compensation_task_type || ':' || status
                  FROM wfl_compensation_request
                """).query(String.class).single()).isEqualTo("UNDO_SURVEY:REQUESTED");
        assertThat(jdbc.sql("""
                SELECT status FROM tsk_task WHERE task_type='UNDO_SURVEY'
                """).query(String.class).single()).isEqualTo("PENDING");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM wfl_compensation_request WHERE source_node_id='NODE_INSTALL'
                """).query(Long.class).single()).isZero();
    }

    private Scope scope(String workflowDefinition) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'BYD-WORKFLOW-M280', 'BYD', 'M280 补偿测试',
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
                TENANT, ConfigurationAssetType.WORKFLOW, "BYD-WORKFLOW-M280", "1.0.0", "1.0.0",
                normalized, Sha256.digest(normalized))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "BYD-WORKFLOW-M280-BUNDLE", "1.0.0", "BYD_OCEAN",
                        "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                        null, List.of(assetId)));
        return new Scope(projectId, bundle);
    }

    private ReceiveExternalWorkOrderCommand command(Scope scope) {
        return new ReceiveExternalWorkOrderCommand(
                TENANT, scope.projectId(), "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                "BYD-WORKFLOW-M280-" + UUID.randomUUID(), "f".repeat(64), scope.bundle().bundleId(),
                scope.bundle().bundleCode(), scope.bundle().bundleVersion(), scope.bundle().manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000", "济南测试地址",
                "LGXCE6CD0RA280001", LocalDateTime.of(2026, 7, 18, 18, 0),
                "corr-workflow-m280", "cause-workflow-m280");
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
                "m280-" + taskType + "-" + UUID.randomUUID(), Duration.ofSeconds(30)).runOnce();
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

    private static String compensatableWorkflow() {
        return """
                {"workflowKey":"compensate.m280","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"NODE_SURVEY","nodeType":"SERVICE_TASK","name":"勘测",
                    "stageCode":"SURVEY","taskType":"SURVEY",
                    "compensation":{"taskType":"UNDO_SURVEY","stageCode":"COMPENSATION"}},
                   {"nodeId":"NODE_INSTALL","nodeType":"SERVICE_TASK","name":"安装",
                    "stageCode":"INSTALL","taskType":"INSTALL"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"NODE_SURVEY"},
                   {"transitionId":"t2","from":"NODE_SURVEY","to":"NODE_INSTALL"},
                   {"transitionId":"t3","from":"NODE_INSTALL","to":"END"}]}
                """;
    }

    private record Scope(UUID projectId, ConfigurationBundleReference bundle) {
    }
}
