package com.serviceos.task;

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
 * M323：冻结 ASSIGNEE_POLICY 在 task.created 后自动写入 CANDIDATE 快照。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AssigneePolicyTaskAssignmentPostgresIT {
    private static final String TENANT = "tenant-m323-assignee";

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
        registry.add("serviceos.outbox.worker-id", () -> "m323-assignee-it");
    }

    @Autowired WorkOrderCommandService workOrders;
    @Autowired ConfigurationService configurations;
    @Autowired OutboxWorker outboxWorker;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    UUID policyVersionId;
    ConfigurationBundleReference bundle;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_work_order_timeline_entry, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_inbox_record, tsk_task_assignment, tsk_task_assignment_batch,
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
                    :projectId, :tenantId, 'M323-ASSIGN', 'BYD', 'M323 Assignee',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        seedRolePool("NETWORK_DISPATCHER", List.of("dispatcher-a", "dispatcher-b"));
        policyVersionId = publishBundleWithAssigneePolicy();
    }

    @Test
    void taskCreatedAppliesFrozenAssigneePolicyCandidates() {
        workOrders.receive(receiveCommand(
                "M323-ORD-1", "c".repeat(64), "VINM3230001", "corr-m323", "cause-m323"));
        drainOutbox(40);

        assertThat(jdbc.sql("SELECT assignee_policy_ref FROM tsk_task")
                .query(String.class).single()).isEqualTo("default-assign");
        assertThat(jdbc.sql("""
                SELECT source_type, source_id, candidate_count
                  FROM tsk_task_assignment_batch
                """).query().singleRow())
                .containsEntry("source_type", "ASSIGNEE_POLICY")
                .containsEntry("source_id", policyVersionId.toString())
                .containsEntry("candidate_count", 2);
        assertThat(jdbc.sql("""
                SELECT principal_id FROM tsk_task_assignment
                 WHERE status = 'ACTIVE' AND assignment_kind = 'CANDIDATE'
                 ORDER BY principal_id
                """).query(String.class).list())
                .containsExactly("dispatcher-a", "dispatcher-b");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_inbox_record
                 WHERE consumer_name = 'task.assignee-policy.created.v1'
                   AND status = 'SUCCEEDED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'TASK_ASSIGNEE_POLICY_APPLIED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void emptyRolePoolLeavesReadyTaskForManualAssignment() {
        jdbc.sql("DELETE FROM auth_role_grant").update();
        workOrders.receive(receiveCommand(
                "M323-ORD-2", "d".repeat(64), "VINM3230002", "corr-m323-empty", "cause-m323-empty"));
        drainOutbox(40);

        assertThat(jdbc.sql("SELECT status FROM tsk_task").query(String.class).single())
                .isEqualTo("READY");
        assertThat(jdbc.sql("SELECT count(*) FROM tsk_task_assignment_batch")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'TASK_ASSIGNEE_POLICY_MANUAL'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
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

    private UUID publishBundleWithAssigneePolicy() {
        String workflow = """
                {"workflowKey":"M323_ASSIGN","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"HUMAN","nodeType":"USER_TASK","name":"受理",
                    "stageCode":"INTAKE","taskType":"INTAKE_REVIEW",
                    "assigneePolicyRef":"default-assign"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"HUMAN"},
                   {"transitionId":"t2","from":"HUMAN","to":"END"}]}
                """.replaceAll("\\s+", "");
        String policy = "{\"policyKey\":\"default-assign\",\"version\":\"1.0.0\",\"strategies\":[{\"strategyKey\":\"brand-match\",\"candidateType\":\"ROLE\",\"priority\":10,\"when\":{\"language\":\"SERVICEOS_EXPR_V1\",\"source\":\"workOrder.brandCode == \\\"BYD_OCEAN\\\"\"},\"roleCode\":\"NETWORK_DISPATCHER\",\"maxCandidates\":10}],\"fallback\":{\"mode\":\"MANUAL_INTERVENTION\",\"roleCode\":\"OPS\"}}";
        var workflowAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "M323_ASSIGN",
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        var policyAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.ASSIGNEE_POLICY, "default-assign",
                "1.0.0", "1.0.0", policy, Sha256.digest(policy)));
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "M323-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowAsset.versionId(), policyAsset.versionId())));
        return policyAsset.versionId();
    }

    private void seedRolePool(String roleCode, List<String> principalIds) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, :roleCode, 'M323 角色', 'ACTIVE', now())
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
                        now() - interval '1 day', 'TEST_FIXTURE', 'M323', now(),
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
