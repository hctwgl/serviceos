package com.serviceos.dispatch;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.dispatch.api.ManualAssignServiceAssignmentCommand;
import com.serviceos.dispatch.api.ManualReassignTechnicianCommand;
import com.serviceos.dispatch.api.ManualServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ManualServiceAssignmentService;
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
 * M367 / ADR-088 A1-B：Manual assign/reassign 对定向目标不兼容师傅硬拒绝。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ManualAssignClientKindRejectPostgresIT {
    private static final String TENANT = "tenant-m367-manual";
    private static final String MANAGER = "m367-manager";
    private static final String BUSINESS = "HOME_CHARGING_SURVEY_INSTALL";
    private static final UUID NETWORK = UUID.fromString("36700000-0000-4000-8000-0000000000a1");
    private static final UUID TECH_IOS = UUID.fromString("36700000-0000-4000-8000-0000000000b1");
    private static final UUID TECH_WEB = UUID.fromString("36700000-0000-4000-8000-0000000000b2");
    private static final UUID PRINCIPAL_IOS = UUID.fromString("36700000-0000-4000-8000-0000000000c1");
    private static final UUID PRINCIPAL_WEB = UUID.fromString("36700000-0000-4000-8000-0000000000c2");

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
        registry.add("serviceos.outbox.scheduling-enabled", () -> "false");
        registry.add("serviceos.task.scheduling-enabled", () -> "false");
    }

    @Autowired ManualServiceAssignmentService manualAssignments;
    @Autowired ConfigurationService configurations;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    ConfigurationBundleReference bundle;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE dsp_assignment_command_result, dsp_capacity_command_result,
                    dsp_service_assignment_activation_saga, dsp_capacity_reservation,
                    dsp_service_assignment, dsp_capacity_counter,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record, auth_role_field_policy,
                    auth_role_grant, auth_role_capability, auth_role, tsk_task,
                    cfg_configuration_asset_client_target, cfg_configuration_bundle_item,
                    cfg_configuration_bundle, cfg_configuration_asset_version,
                    net_technician_profile, prj_project CASCADE
                """).update();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'M367-MANUAL', 'BYD', 'M367 Manual',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        seedGrant(Set.of("dispatch.capacity.configure", "dispatch.assignment.manage"));
        publishDirectedEvidenceBundle(List.of("TECHNICIAN_WEB"));
        seedTechnician(TECH_IOS, PRINCIPAL_IOS, "IOS", List.of("TECHNICIAN_IOS"));
        seedTechnician(TECH_WEB, PRINCIPAL_WEB, "WEB", List.of("TECHNICIAN_WEB"));
    }

    @Test
    void manualAssignRejectsIncompatibleTechnicianWithDenyAudit() {
        UUID taskId = seedHumanTask(UUID.randomUUID());

        assertThatThrownBy(() -> manualAssignments.manualAssign(
                manager(), metadata("m367-reject"),
                new ManualAssignServiceAssignmentCommand(
                        taskId, NETWORK.toString(), TECH_IOS.toString(), BUSINESS)))
                .isInstanceOfSatisfying(BusinessProblem.class, problem -> {
                    assertThat(problem.code()).isEqualTo(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED);
                    assertThat(problem.getMessage()).contains("无交集");
                });

        assertThat(jdbc.sql("""
                SELECT count(*) FROM dsp_service_assignment
                 WHERE tenant_id = :tenant AND task_id = :taskId
                """)
                .param("tenant", TENANT).param("taskId", taskId)
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'SERVICE_DISPATCH_TECHNICIAN_CLIENT_KIND_REJECT'
                   AND decision_code = 'DENY'
                   AND error_code = 'CLIENT_KIND_INCOMPATIBLE'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void manualAssignAllowsCompatibleTechnician() {
        UUID taskId = seedHumanTask(UUID.randomUUID());

        ManualServiceAssignmentReceipt receipt = manualAssignments.manualAssign(
                manager(), metadata("m367-ok"),
                new ManualAssignServiceAssignmentCommand(
                        taskId, NETWORK.toString(), TECH_WEB.toString(), BUSINESS));

        assertThat(receipt.technicianAssigneeId()).isEqualTo(TECH_WEB.toString());
        assertThat(jdbc.sql("""
                SELECT status FROM dsp_service_assignment
                 WHERE responsibility_level = 'TECHNICIAN' AND task_id = :taskId
                """)
                .param("taskId", taskId).query(String.class).single())
                .isEqualTo("ACTIVE");
    }

    @Test
    void reassignRejectsIncompatibleTargetTechnician() {
        UUID taskId = seedHumanTask(UUID.randomUUID());
        manualAssignments.manualAssign(
                manager(), metadata("m367-seed"),
                new ManualAssignServiceAssignmentCommand(
                        taskId, NETWORK.toString(), TECH_WEB.toString(), BUSINESS));

        assertThatThrownBy(() -> manualAssignments.reassignTechnician(
                manager(), metadata("m367-reassign-bad"),
                new ManualReassignTechnicianCommand(
                        taskId, NETWORK.toString(), TECH_IOS.toString(), BUSINESS, "CLIENT_KIND")))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED);

        assertThat(jdbc.sql("""
                SELECT assignee_id FROM dsp_service_assignment
                 WHERE responsibility_level = 'TECHNICIAN' AND status = 'ACTIVE' AND task_id = :taskId
                """)
                .param("taskId", taskId).query(String.class).single())
                .isEqualTo(TECH_WEB.toString());
    }

    @Test
    void undirectedBundleAllowsUndeclaredTechnician() {
        jdbc.sql("TRUNCATE TABLE cfg_configuration_bundle_item, cfg_configuration_bundle, "
                + "cfg_configuration_asset_client_target, cfg_configuration_asset_version CASCADE")
                .update();
        publishDirectedEvidenceBundle(null);
        UUID undeclared = UUID.fromString("36700000-0000-4000-8000-0000000000b3");
        seedTechnician(undeclared, UUID.fromString("36700000-0000-4000-8000-0000000000c3"),
                "Undeclared", null);
        UUID taskId = seedHumanTask(UUID.randomUUID());

        ManualServiceAssignmentReceipt receipt = manualAssignments.manualAssign(
                manager(), metadata("m367-null"),
                new ManualAssignServiceAssignmentCommand(
                        taskId, NETWORK.toString(), undeclared.toString(), BUSINESS));
        assertThat(receipt.technicianAssigneeId()).isEqualTo(undeclared.toString());
    }

    private void publishDirectedEvidenceBundle(List<String> evidenceKinds) {
        String workflow = """
                {"workflowKey":"M367_MANUAL","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[{"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"HUMAN","nodeType":"USER_TASK","name":"派单",
                    "stageCode":"DISPATCH","taskType":"NETWORK_DISPATCH"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[{"transitionId":"t1","from":"START","to":"HUMAN"},
                   {"transitionId":"t2","from":"HUMAN","to":"END"}]}
                """.replaceAll("\\s+", "");
        String evidence = """
                {"templateKey":"m367.evidence","version":"1.0.0","title":"现场",
                 "stage":"SURVEY","items":[{"evidenceKey":"site.photo","name":"现场照",
                   "mediaType":"PHOTO","required":true,
                   "capture":{"allowCamera":true,"allowGallery":true,"minCount":1,"maxCount":3},
                   "reviewPolicy":{"reviewRequired":false,"allowItemLevelReject":false}}]}
                """.replaceAll("\\s+", "");
        var workflowAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "M367_MANUAL",
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        var evidenceAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, "m367.evidence",
                "1.0.0", "1.0.0", evidence, Sha256.digest(evidence)));
        if (evidenceKinds != null && !evidenceKinds.isEmpty()) {
            jdbc.sql("""
                    INSERT INTO cfg_configuration_asset_client_target (
                        version_id, tenant_id, supported_client_kinds
                    ) VALUES (:versionId, :tenant, CAST(:kinds AS jsonb))
                    """)
                    .param("versionId", evidenceAsset.versionId())
                    .param("tenant", TENANT)
                    .param("kinds", toJsonArray(evidenceKinds))
                    .update();
        }
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "M367-BUNDLE", "1.0.0", "BYD_OCEAN",
                BUSINESS, "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowAsset.versionId(), evidenceAsset.versionId())));
    }

    private UUID seedHumanTask(UUID workOrderId) {
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-20T00:00:00Z");
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id, tenant_id, task_type, task_kind, business_key, payload_digest,
                    priority, status, next_run_at, attempt_count, max_attempts, correlation_id,
                    version, created_at, updated_at, project_id, work_order_id,
                    workflow_instance_id, stage_instance_id, workflow_node_instance_id,
                    workflow_node_id, workflow_definition_version_id, workflow_definition_digest,
                    configuration_bundle_id, configuration_bundle_digest, stage_code
                ) VALUES (
                    :taskId, :tenantId, 'INSTALLATION', 'HUMAN', :businessKey, :digest,
                    500, 'READY', :now, 0, 3, 'corr-m367', 1, :now, :now, :projectId,
                    :workOrderId, :workflowInstanceId, :stageInstanceId, :workflowNodeInstanceId,
                    'INSTALL_NODE', :definitionId, :definitionDigest, :bundleId, :bundleDigest,
                    'INSTALL'
                )
                """)
                .param("taskId", taskId)
                .param("tenantId", TENANT)
                .param("businessKey", "m367:" + taskId)
                .param("digest", "a".repeat(64))
                .param("now", java.sql.Timestamp.from(now))
                .param("projectId", projectId)
                .param("workOrderId", workOrderId)
                .param("workflowInstanceId", UUID.randomUUID())
                .param("stageInstanceId", UUID.randomUUID())
                .param("workflowNodeInstanceId", UUID.randomUUID())
                .param("definitionId", UUID.randomUUID())
                .param("definitionDigest", "b".repeat(64))
                .param("bundleId", bundle.bundleId())
                .param("bundleDigest", bundle.manifestDigest())
                .update();
        return taskId;
    }

    private void seedTechnician(UUID profileId, UUID principalId, String name, List<String> kinds) {
        jdbc.sql("""
                INSERT INTO net_technician_profile (
                    technician_profile_id, tenant_id, principal_id, display_name, profile_status,
                    supported_client_kinds, aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :principal, :name, 'ACTIVE',
                    CAST(:kinds AS jsonb), 1, now(), now()
                )
                """)
                .param("id", profileId)
                .param("tenant", TENANT)
                .param("principal", principalId)
                .param("name", name)
                .param("kinds", kinds == null ? null : toJsonArray(kinds))
                .update();
    }

    private void seedGrant(Set<String> capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, 'm367-manager', 'M367 测试角色', 'ACTIVE', now())
                """)
                .param("roleId", roleId).param("tenantId", TENANT).update();
        for (String capability : capabilities) {
            jdbc.sql("""
                    INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                    VALUES (:roleId, :capability, now())
                    """)
                    .param("roleId", roleId).param("capability", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at
                ) VALUES (
                    :grantId, :tenantId, :actorId, :roleId, 'TENANT', :tenantId,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M367-TEST', now()
                )
                """)
                .param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("actorId", MANAGER).param("roleId", roleId).update();
    }

    private static String toJsonArray(List<String> kinds) {
        return kinds.stream()
                .map(kind -> "\"" + kind + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static CurrentPrincipal manager() {
        return new CurrentPrincipal(MANAGER, TENANT, CurrentPrincipal.PrincipalType.USER,
                "m367-it", Set.of("dispatch.capacity.configure", "dispatch.assignment.manage"));
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }
}
