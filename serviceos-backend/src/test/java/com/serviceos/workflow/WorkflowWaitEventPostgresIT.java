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
import com.serviceos.workflow.api.SignalWorkflowWaitCommand;
import com.serviceos.workflow.api.WorkflowWaitSignalService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M270：WAIT_EVENT 挂起与幂等唤醒。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkflowWaitEventPostgresIT {
    private static final String TENANT = "tenant-workflow-m270-it";

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
        registry.add("serviceos.outbox.worker-id", () -> "workflow-m270-it");
    }

    @Autowired WorkOrderCommandService workOrders;
    @Autowired ConfigurationService configurations;
    @Autowired TaskExecutionQueue taskQueue;
    @Autowired OutboxWorker outboxWorker;
    @Autowired WorkflowWaitSignalService waitSignals;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_work_order_timeline_entry, rel_outbox_publish_attempt, rel_outbox_event, rel_inbox_record,
                    tsk_task_execution_attempt, tsk_task, wfl_wait_subscription, wfl_node_instance,
                    wfl_stage_instance, wfl_workflow_instance, wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project CASCADE
                """).update();
    }

    @Test
    void waitEventSuspendsThenWakesIdempotently() {
        Scope scope = scope(waitWorkflow());
        var receipt = workOrders.receive(command(scope));
        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();

        assertThat(taskWorker("SURVEY").runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");

        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance ORDER BY activated_at, node_id
                """).query(String.class).list())
                .containsExactly("SURVEY_TASK:COMPLETED", "WAIT_ACK:WAITING");
        assertThat(jdbc.sql("SELECT status FROM wfl_wait_subscription")
                .query(String.class).single()).isEqualTo("WAITING");

        String correlationKey = "workOrder:" + receipt.workOrderId();
        var first = waitSignals.signal(new SignalWorkflowWaitCommand(
                TENANT, "demo.client-ack", correlationKey, "signal-1", "corr-m270"));
        assertThat(first.replay()).isFalse();
        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance ORDER BY activated_at, node_id
                """).query(String.class).list())
                .containsExactly(
                        "SURVEY_TASK:COMPLETED", "WAIT_ACK:COMPLETED", "INSTALL_TASK:ACTIVE");

        var replay = waitSignals.signal(new SignalWorkflowWaitCommand(
                TENANT, "demo.client-ack", correlationKey, "signal-2", "corr-m270-replay"));
        assertThat(replay.replay()).isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM wfl_node_instance WHERE node_id = 'INSTALL_TASK'")
                .query(Long.class).single()).isEqualTo(1L);

        assertThatThrownBy(() -> waitSignals.signal(new SignalWorkflowWaitCommand(
                TENANT, "demo.client-ack", "workOrder:" + UUID.randomUUID(), "signal-x", "corr-miss")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    private Scope scope(String workflowDefinition) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'BYD-WORKFLOW-M270', 'BYD', 'M270 等待事件测试',
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
                TENANT, ConfigurationAssetType.WORKFLOW, "BYD-WORKFLOW-M270", "1.0.0", "1.0.0",
                normalizedDefinition, Sha256.digest(normalizedDefinition))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "BYD-WORKFLOW-M270-BUNDLE", "1.0.0", "BYD_OCEAN",
                        "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                        null, List.of(assetId)));
        return new Scope(projectId, bundle);
    }

    private ReceiveExternalWorkOrderCommand command(Scope scope) {
        return new ReceiveExternalWorkOrderCommand(
                TENANT, scope.projectId(), "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                "BYD-WORKFLOW-M270-ORDER", "e".repeat(64), scope.bundle().bundleId(),
                scope.bundle().bundleCode(), scope.bundle().bundleVersion(), scope.bundle().manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000", "济南测试地址",
                "LGXCE6CD0RA270001", LocalDateTime.of(2026, 7, 18, 9, 0),
                "corr-workflow-m270", "cause-workflow-m270");
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
                "m270-task-worker", Duration.ofSeconds(30));
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
                throw new AssertionError(eventType + " cannot be published");
            }
        }
        throw new AssertionError(eventType + " was not published");
    }

    private static String waitWorkflow() {
        return """
                {"workflowKey":"wait.m270","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"SURVEY_TASK","nodeType":"SERVICE_TASK","name":"勘测",
                    "stageCode":"SURVEY","taskType":"SURVEY"},
                   {"nodeId":"WAIT_ACK","nodeType":"WAIT_EVENT","name":"等待确认",
                    "stageCode":"SURVEY","waitEventType":"demo.client-ack",
                    "correlationKeyTemplate":"workOrder:{workOrderId}"},
                   {"nodeId":"INSTALL_TASK","nodeType":"SERVICE_TASK","name":"安装",
                    "stageCode":"INSTALL","taskType":"INSTALL"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"SURVEY_TASK"},
                   {"transitionId":"t2","from":"SURVEY_TASK","to":"WAIT_ACK"},
                   {"transitionId":"t3","from":"WAIT_ACK","to":"INSTALL_TASK"}]}
                """;
    }

    private record Scope(UUID projectId, ConfigurationBundleReference bundle) {
    }
}
