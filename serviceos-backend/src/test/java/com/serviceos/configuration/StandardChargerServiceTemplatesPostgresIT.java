package com.serviceos.configuration;

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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** M281：维修/移机/巡检标准模板可发布且冒烟启动首任务。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StandardChargerServiceTemplatesPostgresIT {
    private static final String TENANT = "tenant-template-m281-it";

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
        registry.add("serviceos.outbox.worker-id", () -> "template-m281-it");
    }

    @Autowired ConfigurationService configurations;
    @Autowired WorkOrderCommandService workOrders;
    @Autowired TaskExecutionQueue taskQueue;
    @Autowired OutboxWorker outboxWorker;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_work_order_timeline_entry, rel_outbox_publish_attempt, rel_outbox_event, rel_inbox_record,
                    tsk_task_execution_attempt, tsk_task, wfl_compensation_request, wfl_wait_subscription,
                    wfl_node_instance, wfl_stage_instance, wfl_workflow_instance, wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project CASCADE
                """).update();
    }

    @Test
    void maintenanceRelocateAndInspectionTemplatesPublishAndStart() throws Exception {
        publishAndStart(
                "charger-maintenance", "CHARGER_MAINTENANCE", "MAINT_INTAKE",
                "platform.charger.maintenance", "platform.charger.maintenance.task-elapsed");
        clean();
        publishAndStart(
                "charger-relocate", "CHARGER_RELOCATE", "RELOCATE_INTAKE",
                "platform.charger.relocate", "platform.charger.relocate.task-elapsed");
        clean();
        publishAndStart(
                "charger-inspection", "CHARGER_INSPECTION", "INSPECT_SCHEDULE",
                "platform.charger.inspection", "platform.charger.inspection.task-elapsed");
    }

    private void publishAndStart(
            String templateDir,
            String serviceProductCode,
            String firstTaskType,
            String workflowAssetCode,
            String slaAssetCode
    ) throws Exception {
        // PublishConfigurationAssetCommand 会对 definitionJson trim；摘要必须对 trim 后内容计算。
        String workflowJson = classpath("configuration-templates/" + templateDir + "/workflow.json").trim();
        String slaJson = classpath("configuration-templates/" + templateDir + "/sla.json").trim();
        assertThat(workflowJson).doesNotContain("BYD_CPIM", "byd-cpim", "CPIM");
        assertThat(slaJson).doesNotContain("BYD_CPIM", "byd-cpim");

        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, :code, 'PLATFORM', :name,
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("code", "PLATFORM-" + serviceProductCode)
                .param("name", "标准模板 " + serviceProductCode)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();

        UUID workflowVersionId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, workflowAssetCode, "1.0.0", "1.0.0",
                workflowJson, Sha256.digest(workflowJson))).versionId();
        UUID slaVersionId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.SLA, slaAssetCode, "1.0.0", "1.0.0",
                slaJson, Sha256.digest(slaJson))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, workflowAssetCode + "-bundle", "1.0.0", "PLATFORM",
                        serviceProductCode, "370000", Instant.now().minusSeconds(60),
                        null, List.of(workflowVersionId, slaVersionId)));

        workOrders.receive(new ReceiveExternalWorkOrderCommand(
                TENANT, projectId, "PLATFORM", "PLATFORM", serviceProductCode,
                "EXT-" + serviceProductCode + "-" + UUID.randomUUID(), "a".repeat(64),
                bundle.bundleId(), bundle.bundleCode(), bundle.bundleVersion(), bundle.manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000", "济南测试地址",
                "LGXCE6CD0RA281001", LocalDateTime.of(2026, 7, 18, 18, 0),
                "corr-" + serviceProductCode, "cause-" + serviceProductCode));
        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();

        assertThat(jdbc.sql("""
                SELECT status FROM tsk_task WHERE task_type = :taskType
                """).param("taskType", firstTaskType).query(String.class).single())
                .isEqualTo("PENDING");
        assertThat(runTask(firstTaskType)).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
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
                "m281-" + taskType + "-" + UUID.randomUUID(), Duration.ofSeconds(30)).runOnce();
    }

    private void drainOutbox() {
        for (int i = 0; i < 30; i++) {
            if (outboxWorker.runOnce() == OutboxWorker.RunResult.EMPTY) {
                return;
            }
        }
        throw new AssertionError("outbox did not drain");
    }

    private static String classpath(String path) throws Exception {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
