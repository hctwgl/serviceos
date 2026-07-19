package com.serviceos.configuration.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.reliability.application.OutboxWorker;
import com.serviceos.shared.Sha256;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M326：task.created → NotificationRuntime → Intent/Delivery/Attempt 持久化与本地 ACK。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationReliableDeliveryPostgresIT {
    private static final String TENANT = "tenant-m326-notify";

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
        registry.add("serviceos.outbox.worker-id", () -> "m326-notify-it");
    }

    @Autowired WorkOrderCommandService workOrders;
    @Autowired ConfigurationService configurations;
    @Autowired OutboxWorker outboxWorker;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    ConfigurationBundleReference bundle;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_work_order_timeline_entry, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_inbox_record, cfg_notification_attempt, cfg_notification_delivery,
                    cfg_notification_intent, tsk_task_assignment, tsk_task_assignment_batch,
                    tsk_task_execution_attempt, tsk_task, wfl_node_instance, wfl_stage_instance,
                    wfl_workflow_instance, wo_work_order, cfg_configuration_bundle_item,
                    cfg_configuration_bundle, cfg_configuration_asset_version, prj_project,
                    aud_audit_record, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'M326-NOTIFY', 'BYD', 'M326 Notification',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        seedRolePool("NETWORK_DISPATCHER", List.of("dispatcher-notify-a"));
        publishBundleWithNotification();
    }

    @Test
    void taskCreatedDispatchesFrozenNotificationAndPersistsAckedDelivery() {
        workOrders.receive(receiveCommand(
                "M326-ORD-1", "a".repeat(64), "VINM3260001", "corr-m326", "cause-m326"));
        drainOutbox(40);

        assertThat(jdbc.sql("""
                SELECT policy_key, status, requires_manual_intervention
                  FROM cfg_notification_intent
                """).query().singleRow())
                .containsEntry("policy_key", "default-notify")
                .containsEntry("status", "COMPLETED")
                .containsEntry("requires_manual_intervention", false);
        assertThat(jdbc.sql("""
                SELECT status, recipient_principal_id, channel, acknowledged_at IS NOT NULL AS acked
                  FROM cfg_notification_delivery
                """).query().singleRow())
                .containsEntry("status", "SENT")
                .containsEntry("recipient_principal_id", "dispatcher-notify-a")
                .containsEntry("channel", "IN_APP")
                .containsEntry("acked", true);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM cfg_notification_attempt WHERE outcome = 'SENT'
                """).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_inbox_record
                 WHERE consumer_name = 'configuration.notification.task-event.v1'
                   AND status = 'SUCCEEDED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'NOTIFICATION_RUNTIME_DISPATCHED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void emptyRolePoolMarksPartialManualWithoutDelivery() {
        jdbc.sql("DELETE FROM auth_role_grant").update();
        workOrders.receive(receiveCommand(
                "M326-ORD-2", "b".repeat(64), "VINM3260002", "corr-m326-empty", "cause-m326-empty"));
        drainOutbox(40);

        assertThat(jdbc.sql("""
                SELECT status, requires_manual_intervention FROM cfg_notification_intent
                """).query().singleRow())
                .containsEntry("status", "PARTIAL")
                .containsEntry("requires_manual_intervention", true);
        assertThat(jdbc.sql("SELECT count(*) FROM cfg_notification_delivery")
                .query(Long.class).single()).isZero();
    }

    private ReceiveExternalWorkOrderCommand receiveCommand(
            String externalOrderCode,
            String payloadDigest,
            String vehicleVin,
            String correlationId,
            String causationId
    ) {
        return new ReceiveExternalWorkOrderCommand(
                TENANT, projectId, "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                externalOrderCode, payloadDigest,
                bundle.bundleId(), bundle.bundleCode(), bundle.bundleVersion(), bundle.manifestDigest(),
                "370000", "370100", "370102", "客户", "13800000000", "地址", vehicleVin,
                LocalDateTime.of(2026, 7, 19, 10, 0), correlationId, causationId);
    }

    private void publishBundleWithNotification() {
        String workflow = """
                {"workflowKey":"M326_NOTIFY","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"HUMAN","nodeType":"USER_TASK","name":"受理",
                    "stageCode":"INTAKE","taskType":"INTAKE_REVIEW"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"HUMAN"},
                   {"transitionId":"t2","from":"HUMAN","to":"END"}]}
                """.replaceAll("\\s+", "");
        String notification = """
                {"policyKey":"default-notify","version":"1.0.0","defaultChannel":"IN_APP",
                 "triggers":[{"triggerKey":"task-created","eventType":"task.created",
                   "templateKey":"TASK_CREATED","channel":"IN_APP",
                   "when":{"language":"SERVICEOS_EXPR_V1",
                     "source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                   "recipientRole":"NETWORK_DISPATCHER"}]}
                """.replaceAll("\\s+", "");
        var workflowAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "M326_NOTIFY",
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        var notifyAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.NOTIFICATION, "default-notify",
                "1.0.0", "1.0.0", notification, Sha256.digest(notification)));
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "M326-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowAsset.versionId(), notifyAsset.versionId())));
    }

    private void seedRolePool(String roleCode, List<String> principalIds) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, :roleCode, 'M326 角色', 'ACTIVE', now())
                """)
                .param("roleId", roleId).param("tenantId", TENANT).param("roleCode", roleCode)
                .update();
        for (String principalId : principalIds) {
            jdbc.sql("""
                    INSERT INTO auth_role_grant (
                        grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                        valid_from, source_code, approval_ref, created_at,
                        grant_status, grant_effect
                    ) VALUES (
                        :grantId, :tenantId, :principalId, :roleId, 'PROJECT', :projectId,
                        now() - interval '1 day', 'TEST_FIXTURE', 'M326', now(),
                        'ACTIVE', 'ALLOW'
                    )
                    """)
                    .param("grantId", UUID.randomUUID())
                    .param("tenantId", TENANT)
                    .param("principalId", principalId)
                    .param("roleId", roleId)
                    .param("projectId", projectId.toString())
                    .update();
        }
    }

    private void drainOutbox(int maxRounds) {
        for (int index = 0; index < maxRounds; index++) {
            OutboxWorker.RunResult result = outboxWorker.runOnce();
            if (result == OutboxWorker.RunResult.EMPTY) {
                return;
            }
        }
    }
}
