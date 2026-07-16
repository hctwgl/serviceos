package com.serviceos.workflow;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.forms.api.TaskFormQueryService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.application.OutboxWorker;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxPublisher;
import com.serviceos.shared.Sha256;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkflowBootstrapPostgresIT {
    private static final String TENANT = "tenant-workflow-it";

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
        registry.add("serviceos.outbox.worker-id", () -> "workflow-bootstrap-it");
    }

    @Autowired WorkOrderCommandService workOrders;
    @Autowired ConfigurationService configurations;
    @Autowired OutboxWorker worker;
    @Autowired OutboxPublisher publisher;
    @Autowired TaskFormQueryService taskForms;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_work_order_timeline_entry, rel_outbox_publish_attempt, rel_outbox_event, rel_inbox_record,
                    tsk_task_execution_attempt, tsk_task, wfl_node_instance,
                    wfl_stage_instance, wfl_workflow_instance,
                    wo_work_order, cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    auth_role_field_policy, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
    }

    @Test
    void receivedEventStartsExactWorkflowStageAndFirstTaskExactlyOnce() throws Exception {
        Scope scope = scope(validWorkflow());
        var receipt = workOrders.receive(command(scope, "a".repeat(64)));
        OutboxMessage received = receivedMessage();

        assertThat(receipt.status()).isEqualTo("RECEIVED");
        assertThat(count("wfl_workflow_instance")).isZero();
        assertThat(worker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);

        assertThat(jdbc.sql("SELECT status FROM wo_work_order WHERE id = :id")
                .param("id", receipt.workOrderId()).query(String.class).single()).isEqualTo("ACTIVE");
        assertThat(count("wfl_workflow_instance")).isEqualTo(1);
        assertThat(count("wfl_stage_instance")).isEqualTo(1);
        assertThat(count("wfl_node_instance")).isEqualTo(1);
        assertThat(count("tsk_task")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM tsk_task")
                .query(String.class).single()).isEqualTo("PENDING");
        assertThat(jdbc.sql("SELECT workflow_definition_digest FROM tsk_task")
                .query(String.class).single()).isEqualTo(scope.workflowDigest());
        assertThat(jdbc.sql("SELECT form_ref FROM tsk_task")
                .query(String.class).single()).isEqualTo("intake.form");
        UUID taskId = jdbc.sql("SELECT task_id FROM tsk_task").query(UUID.class).single();
        assertThatThrownBy(() -> taskForms.listForTask(principal(), "corr-form-denied", taskId))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        seedFormReadGrant(scope.projectId());
        assertThat(taskForms.listForTask(principal(), "corr-form-read", taskId)).singleElement()
                .satisfies(form -> {
                    assertThat(form.formKey()).isEqualTo("intake.form");
                    assertThat(form.semanticVersion()).isEqualTo("1.0.0");
                    assertThat(form.definitionJson()).contains("result.value");
                });
        assertThat(jdbc.sql("""
                SELECT status FROM rel_inbox_record
                 WHERE consumer_name = 'workflow.work-order-received.v1'
                """)
                .query(String.class).single()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event ORDER BY event_type")
                .query(String.class).list()).containsExactlyInAnyOrder(
                        "workorder.received", "workorder.activated", "workflow.started",
                        "stage.activated", "task.created");

        for (int index = 0; index < 4; index++) {
            assertThat(worker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        }
        // task.created 还会可靠解析权威空 EvidenceSlot 结果，并产生一条解析完成事件。
        assertThat(worker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        assertThat(worker.runOnce()).isEqualTo(OutboxWorker.RunResult.EMPTY);
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE status = 'PUBLISHED'")
                .query(Long.class).single()).isEqualTo(6);

        publisher.publish(received);
        assertThat(count("wfl_workflow_instance")).isEqualTo(1);
        assertThat(count("wfl_stage_instance")).isEqualTo(1);
        assertThat(count("wfl_node_instance")).isEqualTo(1);
        assertThat(count("tsk_task")).isEqualTo(1);
        assertThat(count("rel_outbox_event")).isEqualTo(6);
    }

    @Test
    void invalidFrozenWorkflowRollsBackConsumerFactsAndRemainsRetryable() {
        Scope scope = scope("{\"workflowKey\":\"broken\",\"semanticVersion\":\"1.0.0\"}");
        var receipt = workOrders.receive(command(scope, "b".repeat(64)));

        assertThat(worker.runOnce()).isEqualTo(OutboxWorker.RunResult.FAILED);
        assertThat(jdbc.sql("SELECT status FROM wo_work_order WHERE id = :id")
                .param("id", receipt.workOrderId()).query(String.class).single()).isEqualTo("RECEIVED");
        assertThat(count("wfl_workflow_instance")).isZero();
        assertThat(count("wfl_stage_instance")).isZero();
        assertThat(count("wfl_node_instance")).isZero();
        assertThat(count("tsk_task")).isZero();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_inbox_record
                 WHERE consumer_name = 'workflow.work-order-received.v1'
                """).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT status FROM rel_outbox_event")
                .query(String.class).single()).isEqualTo("FAILED");
    }

    private Scope scope(String workflowDefinition) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'BYD-WORKFLOW-IT', 'BYD', 'M17 工作流启动测试',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId).param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1)).param("createdAt", OffsetDateTime.now())
                .update();
        String normalizedDefinition = workflowDefinition.trim();
        String digest = Sha256.digest(normalizedDefinition);
        UUID assetId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "BYD-WORKFLOW", "1.0.0", "1.0.0",
                normalizedDefinition, digest)).versionId();
        String formDefinition = """
                {"formKey":"intake.form","version":"1.0.0","stage":"INTAKE",
                 "sections":[{"sectionKey":"base","title":"基础","fields":[{
                   "fieldKey":"result.value","label":"结果","dataType":"STRING",
                   "binding":"task.input.result.value"}]}]}
                """.trim();
        UUID formAssetId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.FORM, "intake.form", "1.0.0", "1.0.0",
                formDefinition, Sha256.digest(formDefinition))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "BYD-WORKFLOW-BUNDLE", "1.0.0", "BYD_OCEAN",
                        "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                        null, List.of(assetId, formAssetId)));
        return new Scope(projectId, bundle, digest);
    }

    private ReceiveExternalWorkOrderCommand command(Scope scope, String payloadDigest) {
        return new ReceiveExternalWorkOrderCommand(
                TENANT, scope.projectId(), "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                "BYD-WORKFLOW-ORDER-1", payloadDigest, scope.bundle().bundleId(),
                scope.bundle().bundleCode(), scope.bundle().bundleVersion(), scope.bundle().manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000", "济南测试地址",
                "LGXCE6CD0RA123456", LocalDateTime.of(2026, 7, 14, 9, 0),
                "corr-workflow-it", "cause-workflow-it");
    }

    private OutboxMessage receivedMessage() {
        return jdbc.sql("SELECT * FROM rel_outbox_event WHERE event_type = 'workorder.received'")
                .query(this::mapMessage).single();
    }

    private OutboxMessage mapMessage(ResultSet rs, int rowNumber) throws SQLException {
        return new OutboxMessage(
                rs.getObject("outbox_id", UUID.class), rs.getObject("event_id", UUID.class),
                rs.getString("module_name"), rs.getString("event_type"), rs.getInt("schema_version"),
                rs.getString("aggregate_type"), rs.getString("aggregate_id"), rs.getLong("aggregate_version"),
                rs.getString("tenant_id"), rs.getString("correlation_id"), rs.getString("causation_id"),
                rs.getString("partition_key"), rs.getString("payload"), rs.getString("payload_digest"),
                rs.getTimestamp("occurred_at").toInstant(), 1, rs.getString("trace_parent"), rs.getString("trace_state"));
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private void seedFormReadGrant(UUID projectId) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, 'workflow-form-reader', '任务表单读取人', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, 'form.read', now())
                """).param("roleId", roleId).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (:grantId, :tenantId, 'form-reader', :roleId, 'PROJECT', :projectId,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M33-TASK-FORM', now())
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("roleId", roleId).param("projectId", projectId.toString()).update();
    }

    private CurrentPrincipal principal() {
        return new CurrentPrincipal(
                "form-reader", TENANT, CurrentPrincipal.PrincipalType.USER, "workflow-it", Set.of());
    }

    private static String validWorkflow() {
        return """
                {"workflowKey":"byd.survey-install","semanticVersion":"1.0.0","startNodeId":"START",
                 "terminalNodeIds":["END"],
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"ASSIGN_COORDINATORS","nodeType":"SERVICE_TASK","name":"分配跟进人",
                    "stageCode":"INTAKE","taskType":"ASSIGN_COORDINATORS","formRef":"intake.form"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[{"transitionId":"t1","from":"START","to":"ASSIGN_COORDINATORS"}]}
                """;
    }

    private record Scope(UUID projectId, ConfigurationBundleReference bundle, String workflowDigest) {}
}
