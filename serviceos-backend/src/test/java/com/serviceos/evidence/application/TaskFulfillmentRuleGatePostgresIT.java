package com.serviceos.evidence.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.evidence.api.CreateEvidenceSetSnapshotCommand;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCommandService;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M330：冻结 RULE 在 EvidenceSetSnapshot 创建与 Task complete 前失败关闭。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TaskFulfillmentRuleGatePostgresIT {
    private static final String TENANT = "tenant-m330-fulfillment-rule";
    private static final String ACTOR = "actor-m330";

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
    }

    @Autowired EvidenceSetSnapshotService snapshots;
    @Autowired HumanTaskCommandService humanTasks;
    @Autowired ConfigurationService configurations;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    UUID taskId;
    ConfigurationBundleReference bundle;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE evd_evidence_set_member, evd_evidence_set_snapshot,
                    evd_evidence_command_result, evd_task_evidence_resolution,
                    tsk_human_task_command_result, tsk_task_assignment, tsk_task, wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        projectId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'M330-RULE', 'BYD', 'M330 Rule',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        seedGrants();
        bundle = publishBundleWithBlockingRule();
        seedRunningTaskWithResolution();
    }

    @Test
    void snapshotCreateBlockedByFrozenRule() {
        assertThatThrownBy(() -> snapshots.create(
                actor(), metadata("m330-snap-block"),
                new CreateEvidenceSetSnapshotCommand(taskId, "TASK_SUBMISSION", List.of())))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.REVIEW_RULE_BLOCKED));

        assertThat(jdbc.sql("SELECT count(*) FROM evd_evidence_set_snapshot")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'REVIEW_RULE_BLOCKED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void taskCompleteBlockedByFrozenRule() {
        assertThatThrownBy(() -> humanTasks.complete(
                actor(), metadata("m330-complete-block"),
                new CompleteHumanTaskCommand(
                        taskId, 1, "result://m330", "d".repeat(64))))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.REVIEW_RULE_BLOCKED));

        assertThat(jdbc.sql("SELECT status FROM tsk_task WHERE task_id = :id")
                .param("id", taskId).query(String.class).single()).isEqualTo("RUNNING");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'REVIEW_RULE_BLOCKED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
    }

    private ConfigurationBundleReference publishBundleWithBlockingRule() {
        String workflow = """
                {"workflowKey":"M330_RULE","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[{"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"HUMAN","nodeType":"USER_TASK","name":"现场",
                    "stageCode":"REVIEW","taskType":"EVIDENCE_REVIEW","ruleRef":"evidence-review"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[{"transitionId":"t1","from":"START","to":"HUMAN"},
                   {"transitionId":"t2","from":"HUMAN","to":"END"}]}
                """.replaceAll("\\s+", "");
        String rule = "{\"ruleKey\":\"evidence-review\",\"version\":\"1.0.0\",\"subjectType\":\"EVIDENCE_REVIEW\",\"stage\":\"INTERNAL\",\"defaultAction\":\"PASS\",\"rules\":[{\"ruleCode\":\"WRONG_BRAND\",\"name\":\"品牌不符\",\"severity\":\"BLOCK\",\"when\":{\"language\":\"SERVICEOS_EXPR_V1\",\"source\":\"workOrder.brandCode == \\\"BYD_OCEAN\\\"\"},\"rejectReasonCode\":\"BRAND_MISMATCH\",\"message\":\"阻断\"}]}";
        var workflowAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "M330_RULE",
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        var ruleAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.RULE, "evidence-review",
                "1.0.0", "1.0.0", rule, Sha256.digest(rule)));
        return configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "M330-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowAsset.versionId(), ruleAsset.versionId())));
    }

    private void seedRunningTaskWithResolution() {
        UUID workOrderId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO wo_work_order (
                    id, tenant_id, project_id, client_code, brand_code, service_product_code,
                    external_order_code, payload_digest, status, configuration_bundle_id,
                    configuration_bundle_code, configuration_bundle_version,
                    configuration_bundle_digest, province_code, city_code, district_code,
                    customer_name, customer_mobile, service_address, vehicle_vin,
                    external_dispatched_at, received_at, version)
                VALUES (
                    :id, :tenantId, :projectId, 'BYD', 'BYD_OCEAN', 'HOME_CHARGING_SURVEY_INSTALL',
                    :externalOrderCode, :payloadDigest, 'RECEIVED', :bundleId,
                    'M330-BUNDLE', '1.0.0', :bundleDigest, '370000', '370100', '370102',
                    '测试用户', '13800000000', '测试地址', 'VINM330000000001',
                    :dispatchedAt, :receivedAt, 1)
                """)
                .param("id", workOrderId).param("tenantId", TENANT).param("projectId", projectId)
                .param("externalOrderCode", "M330-" + workOrderId)
                .param("payloadDigest", Sha256.digest(workOrderId.toString()))
                .param("bundleId", bundle.bundleId()).param("bundleDigest", bundle.manifestDigest())
                .param("dispatchedAt", java.time.LocalDateTime.now().minusHours(1))
                .param("receivedAt", OffsetDateTime.now().minusHours(1))
                .update();

        UUID definitionId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id, tenant_id, task_type, task_kind, business_key, payload_digest,
                    priority, status, next_run_at, attempt_count, max_attempts,
                    correlation_id, version, created_at, updated_at,
                    project_id, work_order_id, workflow_instance_id, stage_instance_id,
                    workflow_node_instance_id, workflow_node_id, workflow_definition_version_id,
                    workflow_definition_digest, configuration_bundle_id, configuration_bundle_digest,
                    stage_code, rule_ref, claimed_by, claimed_at, started_at)
                VALUES (
                    :task, :tenant, 'EVIDENCE_REVIEW', 'HUMAN', :businessKey, :digest,
                    100, 'RUNNING', now(), 0, 1, 'corr-m330', 1, now(), now(),
                    :project, :workOrder, :workflow, :stage, :nodeInstance, 'HUMAN',
                    :definitionId, :digest, :bundle, :bundleDigest, 'REVIEW', 'evidence-review',
                    :actor, now(), now())
                """)
                .param("task", taskId).param("tenant", TENANT)
                .param("businessKey", taskId.toString())
                .param("digest", "d".repeat(64))
                .param("project", projectId).param("workOrder", workOrderId)
                .param("workflow", UUID.randomUUID()).param("stage", UUID.randomUUID())
                .param("nodeInstance", UUID.randomUUID()).param("definitionId", definitionId)
                .param("bundle", bundle.bundleId()).param("bundleDigest", bundle.manifestDigest())
                .param("actor", ACTOR)
                .update();

        jdbc.sql("""
                INSERT INTO tsk_task_assignment (
                    task_assignment_id, tenant_id, task_id, assignment_kind, principal_type,
                    principal_id, status, source_type, source_id, effective_from, created_by, created_at)
                VALUES (:id, :tenant, :task, 'RESPONSIBLE', 'USER', :actor, 'ACTIVE',
                    'MANUAL', 'M330-FIXTURE', now(), 'fixture', now())
                """).param("id", UUID.randomUUID()).param("tenant", TENANT).param("task", taskId)
                .param("actor", ACTOR).update();

        UUID resolutionId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO evd_task_evidence_resolution (
                    resolution_id, tenant_id, project_id, task_id, configuration_bundle_id,
                    configuration_bundle_digest, stage_code, source_event_id, source_event_digest,
                    resolver_version, condition_input_digest, resolution_explanation,
                    generation_no, condition_fact_type, condition_fact_ref, condition_fact_revision,
                    slot_count, resolved_at)
                VALUES (
                    :id, :tenant, :project, :task, :bundle, :digest, 'REVIEW', :event,
                    :eventDigest, 'FIXED_EVIDENCE_V1', :condDigest,
                    CAST('{"kind":"TEST_FIXED_CONTEXT"}' AS jsonb),
                    1, 'TASK_CREATED', CAST(:event AS varchar), 0, 0, now())
                """)
                .param("id", resolutionId).param("tenant", TENANT).param("project", projectId)
                .param("task", taskId).param("bundle", bundle.bundleId())
                .param("digest", bundle.manifestDigest())
                .param("event", UUID.randomUUID())
                .param("eventDigest", "e".repeat(64))
                .param("condDigest", "c".repeat(64))
                .update();
    }

    private void seedGrants() {
        UUID projectRoleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'M330_EVIDENCE', 'M330 Evidence', 'ACTIVE', now())
                """).param("id", projectRoleId).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:id, 'evidence.submit', now())
                """).param("id", projectRoleId).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at, grant_status, grant_effect
                ) VALUES (
                    :grant, :tenant, :principal, :role, 'PROJECT', :project,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M330', now(), 'ACTIVE', 'ALLOW')
                """)
                .param("grant", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", ACTOR).param("role", projectRoleId)
                .param("project", projectId.toString())
                .update();

        // task.complete 使用 TENANT 范围 capability，与 HumanTaskCommand 授权模型一致。
        UUID tenantRoleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'M330_TASK', 'M330 Task', 'ACTIVE', now())
                """).param("id", tenantRoleId).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:id, 'task.complete', now())
                """).param("id", tenantRoleId).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at, grant_status, grant_effect
                ) VALUES (
                    :grant, :tenant, :principal, :role, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M330', now(), 'ACTIVE', 'ALLOW')
                """)
                .param("grant", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", ACTOR).param("role", tenantRoleId)
                .update();
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(
                ACTOR, TENANT, CurrentPrincipal.PrincipalType.USER, "m330-test", Set.of());
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }
}
