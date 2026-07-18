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

/** M277：SUB_PROCESS 启动子流程并在子 END 后恢复父流程。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkflowSubProcessPostgresIT {
    private static final String TENANT = "tenant-workflow-m277-it";

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
        registry.add("serviceos.outbox.worker-id", () -> "workflow-m277-it");
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
                    tsk_task_execution_attempt, tsk_task, wfl_subprocess_link, wfl_timer_subscription,
                    wfl_wait_subscription, wfl_parallel_join_token, wfl_parallel_join, wfl_node_instance,
                    wfl_stage_instance, wfl_workflow_instance, wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project CASCADE
                """).update();
    }

    @Test
    void subProcessRunsChildThenResumesParentWithoutPrematureFulfillment() {
        Scope scope = scope();
        workOrders.receive(command(scope));
        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();

        assertThat(runTask("PARENT_START")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");

        assertThat(jdbc.sql("SELECT count(*) FROM wfl_workflow_instance").query(Long.class).single())
                .isEqualTo(2L);
        assertThat(jdbc.sql("""
                SELECT instance_role FROM wfl_workflow_instance ORDER BY instance_role
                """).query(String.class).list()).containsExactly("ROOT", "SUBPROCESS");
        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance
                 WHERE node_id IN ('CALL_CHILD', 'CHILD_TASK') ORDER BY node_id
                """).query(String.class).list())
                .containsExactly("CALL_CHILD:WAITING", "CHILD_TASK:ACTIVE");
        assertThat(jdbc.sql("SELECT status FROM wo_work_order").query(String.class).single())
                .isEqualTo("ACTIVE");

        assertThat(runTask("CHILD_WORK")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");

        assertThat(jdbc.sql("SELECT status FROM wfl_subprocess_link").query(String.class).single())
                .isEqualTo("COMPLETED");
        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance
                 WHERE node_id IN ('CALL_CHILD', 'PARENT_AFTER') ORDER BY node_id
                """).query(String.class).list())
                .containsExactly("CALL_CHILD:COMPLETED", "PARENT_AFTER:ACTIVE");
        assertThat(jdbc.sql("SELECT status FROM wo_work_order").query(String.class).single())
                .isEqualTo("ACTIVE");

        assertThat(runTask("PARENT_END")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");
        assertThat(jdbc.sql("""
                SELECT status FROM wfl_workflow_instance WHERE instance_role = 'ROOT'
                """).query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT status FROM wo_work_order").query(String.class).single())
                .isEqualTo("FULFILLED");
    }

    private Scope scope() {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'BYD-WORKFLOW-M277', 'BYD', 'M277 子流程测试',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        String child = """
                {"workflowKey":"platform.child.m277","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"CHILD_TASK","nodeType":"SERVICE_TASK","name":"子任务",
                    "stageCode":"CHILD","taskType":"CHILD_WORK"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"c1","from":"START","to":"CHILD_TASK"},
                   {"transitionId":"c2","from":"CHILD_TASK","to":"END"}]}
                """.trim();
        String parent = """
                {"workflowKey":"platform.parent.m277","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"PARENT_START","nodeType":"SERVICE_TASK","name":"父开始",
                    "stageCode":"INTAKE","taskType":"PARENT_START"},
                   {"nodeId":"CALL_CHILD","nodeType":"SUB_PROCESS","name":"调用子流程",
                    "stageCode":"INTAKE","subProcessRef":"platform.child.m277"},
                   {"nodeId":"PARENT_AFTER","nodeType":"SERVICE_TASK","name":"父后续",
                    "stageCode":"CLOSE","taskType":"PARENT_END"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"p1","from":"START","to":"PARENT_START"},
                   {"transitionId":"p2","from":"PARENT_START","to":"CALL_CHILD"},
                   {"transitionId":"p3","from":"CALL_CHILD","to":"PARENT_AFTER"},
                   {"transitionId":"p4","from":"PARENT_AFTER","to":"END"}]}
                """.trim();
        UUID childId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "platform.child.m277", "1.0.0", "1.0.0",
                child, Sha256.digest(child))).versionId();
        UUID parentId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "platform.parent.m277", "1.0.0", "1.0.0",
                parent, Sha256.digest(parent))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "BYD-WORKFLOW-M277-BUNDLE", "1.0.0", "BYD_OCEAN",
                        "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                        null, List.of(childId, parentId)));
        return new Scope(projectId, bundle);
    }

    private ReceiveExternalWorkOrderCommand command(Scope scope) {
        return new ReceiveExternalWorkOrderCommand(
                TENANT, scope.projectId(), "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                "BYD-WORKFLOW-M277-ORDER", "c".repeat(64), scope.bundle().bundleId(),
                scope.bundle().bundleCode(), scope.bundle().bundleVersion(), scope.bundle().manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000", "济南测试地址",
                "LGXCE6CD0RA277001", LocalDateTime.of(2026, 7, 18, 17, 0),
                "corr-workflow-m277", "cause-workflow-m277");
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
                "m277-" + taskType, Duration.ofSeconds(30)).runOnce();
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
            outboxWorker.runOnce();
        }
        throw new AssertionError(eventType + " was not published");
    }

    private record Scope(UUID projectId, ConfigurationBundleReference bundle) {
    }
}
