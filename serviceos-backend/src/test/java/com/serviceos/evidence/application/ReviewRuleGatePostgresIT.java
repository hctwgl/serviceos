package com.serviceos.evidence.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.evidence.api.DecideReviewCaseCommand;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.evidence.api.ReviewTargetDecisionCommand;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
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
 * M325：冻结 RULE 在 INTERNAL ReviewCase.decide(APPROVED) 前失败关闭。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReviewRuleGatePostgresIT {
    private static final String TENANT = "tenant-m325-rule";
    private static final String REVIEWER = "reviewer-m325";

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

    @Autowired ReviewCaseService reviews;
    @Autowired EvidenceSetSnapshotService snapshots;
    @Autowired ConfigurationService configurations;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    UUID taskId;
    UUID reviewCaseId;
    ConfigurationBundleReference bundle;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE evd_review_decision, evd_review_case, evd_evidence_set_snapshot,
                    evd_task_evidence_resolution, tsk_task, wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    aud_audit_record, rel_idempotency_record,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        projectId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        reviewCaseId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'M325-RULE', 'BYD', 'M325 Rule',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        seedGrant();
        bundle = publishBundleWithBlockingRule();
        seedWorkOrderTaskAndOpenCase();
    }

    @Test
    void approvedBlockedByFrozenRuleLeaveCaseOpen() {
        assertThatThrownBy(() -> reviews.decide(
                reviewer(), metadata("m325-approve-block"),
                decideCommand(reviewCaseId, "APPROVED", List.of(), "ok")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.REVIEW_RULE_BLOCKED));

        assertThat(jdbc.sql("SELECT status FROM evd_review_case WHERE review_case_id = :id")
                .param("id", reviewCaseId).query(String.class).single()).isEqualTo("OPEN");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'REVIEW_RULE_BLOCKED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.sql("SELECT rule_ref FROM tsk_task WHERE task_id = :id")
                .param("id", taskId).query(String.class).single()).isEqualTo("evidence-review");
    }

    @Test
    void rejectedAllowedEvenWhenRuleWouldBlockApprove() {
        var decided = reviews.decide(
                reviewer(), metadata("m325-reject-ok"),
                decideCommand(reviewCaseId, "REJECTED", List.of("IMAGE.BLUR"), "reject for rework"));
        assertThat(decided.reviewCase().status()).isEqualTo("REJECTED");
        assertThat(decided.correctionCaseId()).isNotNull();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'REVIEW_RULE_PASSED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
    }

    private ConfigurationBundleReference publishBundleWithBlockingRule() {
        String workflow = """
                {"workflowKey":"M325_RULE","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[{"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"HUMAN","nodeType":"USER_TASK","name":"审核",
                    "stageCode":"REVIEW","taskType":"EVIDENCE_REVIEW","ruleRef":"evidence-review"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[{"transitionId":"t1","from":"START","to":"HUMAN"},
                   {"transitionId":"t2","from":"HUMAN","to":"END"}]}
                """.replaceAll("\\s+", "");
        String rule = "{\"ruleKey\":\"evidence-review\",\"version\":\"1.0.0\",\"subjectType\":\"EVIDENCE_REVIEW\",\"stage\":\"INTERNAL\",\"defaultAction\":\"PASS\",\"rules\":[{\"ruleCode\":\"WRONG_BRAND\",\"name\":\"品牌不符\",\"severity\":\"BLOCK\",\"when\":{\"language\":\"SERVICEOS_EXPR_V1\",\"source\":\"workOrder.brandCode == \\\"BYD_OCEAN\\\"\"},\"rejectReasonCode\":\"BRAND_MISMATCH\",\"message\":\"阻断\"}]}";
        var workflowAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "M325_RULE",
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        var ruleAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.RULE, "evidence-review",
                "1.0.0", "1.0.0", rule, Sha256.digest(rule)));
        return configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "M325-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowAsset.versionId(), ruleAsset.versionId())));
    }

    private void seedWorkOrderTaskAndOpenCase() {
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
                    'M325-BUNDLE', '1.0.0', :bundleDigest, '370000', '370100', '370102',
                    '测试用户', '13800000000', '测试地址', 'VINM325000000001',
                    :dispatchedAt, :receivedAt, 1)
                """)
                .param("id", workOrderId).param("tenantId", TENANT).param("projectId", projectId)
                .param("externalOrderCode", "M325-" + workOrderId)
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
                    stage_code, rule_ref)
                VALUES (
                    :task, :tenant, 'EVIDENCE_REVIEW', 'HUMAN', :businessKey, :digest,
                    100, 'READY', now(), 0, 1, 'corr-m325', 1, now(), now(),
                    :project, :workOrder, :workflow, :stage, :nodeInstance, 'HUMAN',
                    :definitionId, :digest, :bundle, :bundleDigest, 'REVIEW', 'evidence-review')
                """)
                .param("task", taskId).param("tenant", TENANT)
                .param("businessKey", taskId.toString())
                .param("digest", "d".repeat(64))
                .param("project", projectId).param("workOrder", workOrderId)
                .param("workflow", UUID.randomUUID()).param("stage", UUID.randomUUID())
                .param("nodeInstance", UUID.randomUUID()).param("definitionId", definitionId)
                .param("bundle", bundle.bundleId()).param("bundleDigest", bundle.manifestDigest())
                .update();

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

        UUID snapshotId = UUID.randomUUID();
        String snapshotDigest = Sha256.digest("m325-snapshot:" + snapshotId);
        jdbc.sql("""
                INSERT INTO evd_evidence_set_snapshot (
                    evidence_set_snapshot_id, tenant_id, project_id, task_id, resolution_id,
                    purpose, member_count, content_digest, eligibility_summary, created_by, created_at
                ) VALUES (
                    :id, :tenant, :project, :task, :resolution,
                    'TASK_SUBMISSION', 0, :digest, '{}'::jsonb, 'fixture', now())
                """)
                .param("id", snapshotId).param("tenant", TENANT).param("project", projectId)
                .param("task", taskId).param("resolution", resolutionId).param("digest", snapshotDigest)
                .update();

        // M364：INTERNAL Case 必须绑定独立 evidence.review handling Task（reviewTaskId），
        // 否则 REJECTED/APPROVED 决定路径在 completeReviewHandlingTask 失败关闭。
        UUID reviewTaskId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id, tenant_id, task_type, task_kind, business_key, payload_ref,
                    payload_digest, priority, status, next_run_at, attempt_count, max_attempts,
                    correlation_id, version, created_at, updated_at
                ) VALUES (
                    :task, :tenant, 'evidence.review', 'HUMAN', :businessKey,
                    :payloadRef, :digest, 700, 'READY', now(), 0, 1,
                    'corr-m325-review', 1, now(), now())
                """)
                .param("task", reviewTaskId).param("tenant", TENANT)
                .param("businessKey", reviewCaseId.toString())
                .param("payloadRef", "review-case:" + reviewCaseId)
                .param("digest", Sha256.digest(
                        reviewCaseId + "|" + snapshotId + "|" + snapshotDigest))
                .update();

        jdbc.sql("""
                INSERT INTO evd_review_case (
                    review_case_id, tenant_id, project_id, task_id, review_task_id,
                    evidence_set_snapshot_id, snapshot_content_digest, scope_type, origin,
                    policy_version, status, created_by, created_at, decided_at
                ) VALUES (
                    :id, :tenant, :project, :task, :reviewTask, :snapshot, :digest,
                    'EVIDENCE_SET_SNAPSHOT', 'INTERNAL', 'POLICY_V1', 'OPEN',
                    'fixture', now(), NULL)
                """)
                .param("id", reviewCaseId).param("tenant", TENANT).param("project", projectId)
                .param("task", taskId).param("reviewTask", reviewTaskId)
                .param("snapshot", snapshotId)
                .param("digest", snapshotDigest)
                .update();
    }

    private void seedGrant() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'M325_REVIEWER', 'M325 Reviewer', 'ACTIVE', now())
                """).param("id", roleId).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:id, 'evidence.review', now())
                """).param("id", roleId).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at, grant_status, grant_effect
                ) VALUES (
                    :grant, :tenant, :principal, :role, 'PROJECT', :project,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M325', now(), 'ACTIVE', 'ALLOW')
                """)
                .param("grant", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", REVIEWER).param("role", roleId)
                .param("project", projectId.toString())
                .update();
    }

    private static CurrentPrincipal reviewer() {
        return new CurrentPrincipal(
                REVIEWER, TENANT, CurrentPrincipal.PrincipalType.USER, "m325-test", Set.of());
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }

    private DecideReviewCaseCommand decideCommand(
            UUID reviewCaseId, String overall, List<String> reasonCodes, String note
    ) {
        long aggregateVersion = jdbc.sql("""
                SELECT aggregate_version FROM evd_review_case WHERE review_case_id = :id
                """).param("id", reviewCaseId).query(Long.class).single();
        // 空 Snapshot 夹具：targetDecisions 为空，由服务端按 note 派生整组结果（仅 RULE 门禁 IT）。
        return new DecideReviewCaseCommand(reviewCaseId, List.of(), note, aggregateVersion);
    }

}

