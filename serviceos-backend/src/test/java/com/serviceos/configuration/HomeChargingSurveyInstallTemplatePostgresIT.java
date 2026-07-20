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
import com.serviceos.workorder.api.WorkOrderReceipt;
import com.serviceos.workflow.api.ReviewGateWait;
import com.serviceos.workflow.api.SignalWorkflowWaitCommand;
import com.serviceos.workflow.api.WorkflowWaitSignalService;
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

/** M271：平台中立家充勘安模板可发布，并完成网关/等待冒烟推进。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HomeChargingSurveyInstallTemplatePostgresIT {
    private static final String TENANT = "tenant-template-m271-it";

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
        registry.add("serviceos.outbox.worker-id", () -> "template-m271-it");
    }

    @Autowired ConfigurationService configurations;
    @Autowired WorkOrderCommandService workOrders;
    @Autowired TaskExecutionQueue taskQueue;
    @Autowired OutboxWorker outboxWorker;
    @Autowired WorkflowWaitSignalService waitSignals;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_work_order_timeline_entry, rel_outbox_publish_attempt, rel_outbox_event, rel_inbox_record,
                    tsk_task_execution_attempt, tsk_task, wfl_review_gate_early_signal,
                    wfl_wait_subscription, wfl_node_instance,
                    wfl_stage_instance, wfl_workflow_instance, wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project CASCADE
                """).update();
    }

    @Test
    void platformTemplatePublishesAndAdvancesThroughGatewayAndWaits() throws Exception {
        String workflowJson = classpath("configuration-templates/home-charging-survey-install/workflow.json");
        String slaJson = classpath("configuration-templates/home-charging-survey-install/sla.json");
        assertThat(workflowJson).doesNotContain("BYD_CPIM", "byd-cpim", "CPIM");
        assertThat(slaJson).doesNotContain("BYD_CPIM", "byd-cpim");

        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'PLATFORM-HC-M271', 'PLATFORM', '标准家充模板测试',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();

        UUID slaId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.SLA, "platform.home-charging.task-elapsed",
                "1.0.0", "1.0.0", slaJson, Sha256.digest(slaJson))).versionId();
        UUID workflowId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "platform.home-charging.survey-install",
                "1.1.0", "1.1.0", workflowJson, Sha256.digest(workflowJson))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "PLATFORM-HC-BUNDLE", "1.1.0", "PLATFORM_BRAND",
                        "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                        null, List.of(slaId, workflowId)));

        WorkOrderReceipt receipt = workOrders.receive(new ReceiveExternalWorkOrderCommand(
                TENANT, projectId, "PLATFORM", "PLATFORM_BRAND", "HOME_CHARGING_SURVEY_INSTALL",
                "PLATFORM-HC-ORDER-1", "f".repeat(64), bundle.bundleId(), bundle.bundleCode(),
                bundle.bundleVersion(), bundle.manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000", "济南测试地址",
                "VINM27100000000001", LocalDateTime.of(2026, 7, 18, 10, 0),
                "corr-m271", "cause-m271"));

        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();
        assertThat(runTask("ASSIGN_COORDINATORS")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");
        assertThat(runTask("FIELD_SURVEY")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");

        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance ORDER BY activated_at, node_id
                """).query(String.class).list())
                .contains(
                        "INTAKE_COORDINATE:COMPLETED",
                        "SURVEY_TASK:COMPLETED",
                        "WAIT_SURVEY_CONFIRM:WAITING");

        String key = "workOrder:" + receipt.workOrderId();
        waitSignals.signal(new SignalWorkflowWaitCommand(
                TENANT, "platform.survey.confirmed", key, "sig-survey-1", "corr-m271-w1"));
        assertThat(runTask("FIELD_INSTALL")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");

        // M365：INSTALL 之后进入 REVIEW_TASK 门闸（WAITING，无 HUMAN 工作流 Task）。
        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance
                 WHERE node_id = 'REVIEW_TASK'
                """).query(String.class).single()).isEqualTo("REVIEW_TASK:WAITING");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM tsk_task
                 WHERE task_type = 'evidence.review' AND workflow_node_instance_id IS NOT NULL
                """).query(Long.class).single()).isZero();

        waitSignals.signal(new SignalWorkflowWaitCommand(
                TENANT, ReviewGateWait.WAIT_EVENT_TYPE, key, "sig-review-1", "corr-m365-review"));
        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance
                 WHERE node_id = 'REVIEW_TASK'
                """).query(String.class).single()).isEqualTo("REVIEW_TASK:COMPLETED");
        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance
                 WHERE node_id = 'WAIT_OEM_ACK'
                """).query(String.class).single()).isEqualTo("WAIT_OEM_ACK:WAITING");

        waitSignals.signal(new SignalWorkflowWaitCommand(
                TENANT, "platform.oem.acknowledged", key, "sig-oem-1", "corr-m271-w2"));

        assertThat(jdbc.sql("SELECT status FROM wfl_workflow_instance")
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT status FROM wo_work_order")
                .query(String.class).single()).isEqualTo("FULFILLED");
    }

    @Test
    void earlyReviewApproveSignalIsConsumedWhenReviewGateActivates() throws Exception {
        String workflowJson = classpath("configuration-templates/home-charging-survey-install/workflow.json");
        String slaJson = classpath("configuration-templates/home-charging-survey-install/sla.json");

        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'PLATFORM-HC-M365', 'PLATFORM', '早期审核门闸测试',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();

        UUID slaId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.SLA, "platform.home-charging.task-elapsed",
                "1.0.0", "1.0.0", slaJson, Sha256.digest(slaJson))).versionId();
        UUID workflowId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "platform.home-charging.survey-install",
                "1.1.0", "1.1.0", workflowJson, Sha256.digest(workflowJson))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "PLATFORM-HC-BUNDLE-EARLY", "1.1.0", "PLATFORM_BRAND",
                        "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                        null, List.of(slaId, workflowId)));

        WorkOrderReceipt receipt = workOrders.receive(new ReceiveExternalWorkOrderCommand(
                TENANT, projectId, "PLATFORM", "PLATFORM_BRAND", "HOME_CHARGING_SURVEY_INSTALL",
                "PLATFORM-HC-ORDER-EARLY", "e".repeat(64), bundle.bundleId(), bundle.bundleCode(),
                bundle.bundleVersion(), bundle.manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000001", "济南测试地址",
                "VINM36500000000001", LocalDateTime.of(2026, 7, 18, 10, 0),
                "corr-m365", "cause-m365"));

        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();
        assertThat(runTask("ASSIGN_COORDINATORS")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");
        assertThat(runTask("FIELD_SURVEY")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");

        String key = "workOrder:" + receipt.workOrderId();
        waitSignals.signal(new SignalWorkflowWaitCommand(
                TENANT, "platform.survey.confirmed", key, "sig-survey-early", "corr-m365-w1"));

        // 审核先于 INSTALL 完成：写入早期信号，门闸激活时自动消费。
        jdbc.sql("""
                INSERT INTO wfl_review_gate_early_signal (
                    tenant_id, work_order_id, review_case_id, review_decision_id,
                    decision, signal_id, correlation_id, created_at, consumed_at
                ) VALUES (
                    :tenantId, :workOrderId, :reviewCaseId, :decisionId,
                    'APPROVED', :signalId, 'corr-m365-early', :createdAt, NULL
                )
                """)
                .param("tenantId", TENANT)
                .param("workOrderId", receipt.workOrderId())
                .param("reviewCaseId", UUID.randomUUID())
                .param("decisionId", UUID.randomUUID())
                .param("signalId", "sig-review-early")
                .param("createdAt", OffsetDateTime.now())
                .update();

        assertThat(runTask("FIELD_INSTALL")).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        publishUntil("task.completed");
        drainOutbox();

        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance
                 WHERE node_id = 'REVIEW_TASK'
                """).query(String.class).single()).isEqualTo("REVIEW_TASK:COMPLETED");
        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance
                 WHERE node_id = 'WAIT_OEM_ACK'
                """).query(String.class).single()).isEqualTo("WAIT_OEM_ACK:WAITING");
        assertThat(jdbc.sql("""
                SELECT consumed_at IS NOT NULL FROM wfl_review_gate_early_signal
                 WHERE work_order_id = :workOrderId
                """).param("workOrderId", receipt.workOrderId()).query(Boolean.class).single()).isTrue();
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
                "m271-" + taskType, Duration.ofSeconds(30)).runOnce();
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
        for (int i = 0; i < 30; i++) {
            String status = jdbc.sql("""
                            SELECT status FROM rel_outbox_event
                             WHERE event_type = :eventType
                             ORDER BY occurred_at DESC LIMIT 1
                            """)
                    .param("eventType", eventType).query(String.class).single();
            if ("PUBLISHED".equals(status)) {
                return;
            }
            if (outboxWorker.runOnce() == OutboxWorker.RunResult.EMPTY) {
                throw new AssertionError(eventType + " stuck: " + status);
            }
        }
        throw new AssertionError(eventType + " was not published");
    }

    private static String classpath(String location) throws Exception {
        try (var in = new ClassPathResource(location).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }
}
