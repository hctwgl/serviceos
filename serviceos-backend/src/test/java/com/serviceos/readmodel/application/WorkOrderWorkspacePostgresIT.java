package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.WorkOrderWorkspace;
import com.serviceos.readmodel.api.WorkOrderWorkspaceQueryService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M85：工作区组合查询的授权、无 PII 与缺权降级证据。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkOrderWorkspacePostgresIT {
    private static final String TENANT = "tenant-workspace-it";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos")
            .withUsername("serviceos_test")
            .withPassword("serviceos_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WorkOrderWorkspaceQueryService workspaces;

    @Autowired
    private WorkOrderCommandService workOrders;

    @Autowired
    private ConfigurationService configurations;

    @Autowired
    private JdbcClient jdbc;

    private UUID projectId;
    private ConfigurationBundleReference bundle;
    private UUID workOrderId;
    private UUID taskId;

    @BeforeEach
    void seed() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_projection_dead_letter, rdm_projection_checkpoint,
                    rdm_work_order_timeline_entry, rel_inbox_record,
                    aud_audit_record, tsk_task_execution_attempt, tsk_task,
                    ops_task_failure_recovery, ops_exception_ack_result, ops_operational_exception,
                    rel_outbox_publish_attempt, rel_outbox_event,
                    wo_work_order, cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    auth_role_field_policy, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        jdbc.sql("""
                UPDATE rdm_projection_state
                   SET active_generation = 1, status = 'RUNNING',
                       last_rebuild_started_at = NULL, last_rebuild_completed_at = NULL,
                       updated_at = now()
                 WHERE projection_code = 'work-order-core-timeline.v1'
                """).update();
        projectId = project();
        bundle = bundle();
        workOrderId = receive("M85-ORDER-1");
        taskId = task(projectId, workOrderId, "SITE_SURVEY");
        seedReader("reader", projectId, "workOrder.read");
    }

    @Test
    void composesWorkspaceWithoutPiiAndDegradesMissingSecondaryCapabilities() {
        WorkOrderWorkspace workspace = workspaces.get(
                principal("reader"), "corr-workspace", workOrderId);

        assertThat(workspace.header().id()).isEqualTo(workOrderId);
        assertThat(workspace.header().externalOrderCode()).isEqualTo("M85-ORDER-1");
        assertThat(workspace.currentTaskSummary()).isNotNull();
        assertThat(workspace.currentTaskSummary().taskId()).isEqualTo(taskId);
        assertThat(workspace.currentTaskSummary().status()).isEqualTo("READY");
        assertThat(workspace.allowedActionLink())
                .isEqualTo("/api/v1/tasks/" + taskId + "/allowed-actions");
        assertThat(workspace.sectionAvailability().get("TASKS")).isEqualTo("AVAILABLE");
        assertThat(workspace.sectionAvailability().get("SLA")).isEqualTo("UNAVAILABLE");
        assertThat(workspace.sectionAvailability().get("EXCEPTIONS")).isEqualTo("UNAVAILABLE");
        assertThat(workspace.slaSummary()).isNull();
        assertThat(workspace.exceptionSummary()).isNull();
        assertThat(workspace.timelineFreshnessStatus()).isEqualTo("UNKNOWN");
        assertThat(workspace.meta().freshnessStatus()).isEqualTo("UNKNOWN");
        assertThat(workspace.meta().projectionCheckpoint()).startsWith("work-order-core-timeline.v1:gen:");
        assertThat(workspace.sourceVersions().workOrderVersion()).isEqualTo(1);

        String json = workspace.toString();
        assertThat(json).doesNotContain("customerName", "customerMobile", "serviceAddress", "vehicleVin");
    }

    @Test
    void deniesCrossTenantAndMissingGrant() {
        assertThatThrownBy(() -> workspaces.get(
                principal("missing"), "corr-deny", workOrderId))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThatThrownBy(() -> workspaces.get(
                new CurrentPrincipal(
                        "reader", "other-tenant", CurrentPrincipal.PrincipalType.USER, "m85", Set.of()),
                "corr-cross", workOrderId))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    private UUID project() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id,tenant_id,project_code,client_id,project_name,starts_on,
                    project_status,aggregate_version,created_at
                ) VALUES (
                    :id,:tenantId,'M85','BYD','M85 项目',current_date,'ACTIVE',1,now()
                )
                """).param("id", id).param("tenantId", TENANT).update();
        return id;
    }

    private ConfigurationBundleReference bundle() {
        String definition = "{\"workflowCode\":\"M85\"}";
        UUID assetId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "M85-WF", "1.0.0", "1.0.0",
                definition, Sha256.digest(definition))).versionId();
        return configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "M85-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(assetId)));
    }

    private UUID receive(String externalOrderCode) {
        return workOrders.receive(new ReceiveExternalWorkOrderCommand(
                TENANT, projectId, "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                externalOrderCode, "e".repeat(64), bundle.bundleId(), bundle.bundleCode(),
                bundle.bundleVersion(), bundle.manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000",
                "山东省济南市历下区测试路1号", "LGXCE6CD0RA123456",
                LocalDateTime.of(2026, 7, 16, 8, 0), "corr-receive", "cause-receive"
        )).workOrderId();
    }

    private UUID task(UUID project, UUID workOrder, String taskType) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id,tenant_id,task_type,task_kind,business_key,payload_digest,
                    priority,status,next_run_at,attempt_count,max_attempts,correlation_id,
                    version,created_at,updated_at,project_id,work_order_id,
                    workflow_instance_id,stage_instance_id,workflow_node_instance_id,
                    workflow_node_id,workflow_definition_version_id,workflow_definition_digest,
                    configuration_bundle_id,configuration_bundle_digest,stage_code
                ) VALUES (
                    :id,:tenantId,:taskType,'HUMAN',:businessKey,:digest,
                    500,'READY',:now,0,3,'corr-task',1,:now,:now,:projectId,:workOrderId,
                    :workflowId,:stageId,:nodeId,'SURVEY_NODE',:definitionId,:definitionDigest,
                    :bundleId,:bundleDigest,'SURVEY'
                )
                """)
                .param("id", id)
                .param("tenantId", TENANT)
                .param("taskType", taskType)
                .param("businessKey", "m85:" + id)
                .param("digest", "a".repeat(64))
                .param("now", java.sql.Timestamp.from(now))
                .param("projectId", project)
                .param("workOrderId", workOrder)
                .param("workflowId", UUID.randomUUID())
                .param("stageId", UUID.randomUUID())
                .param("nodeId", UUID.randomUUID())
                .param("definitionId", UUID.randomUUID())
                .param("definitionDigest", "b".repeat(64))
                .param("bundleId", bundle.bundleId())
                .param("bundleDigest", bundle.manifestDigest())
                .update();
        return id;
    }

    private void seedReader(String principalId, UUID project, String capability) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (
                    role_id,tenant_id,role_code,role_name,role_status,created_at
                ) VALUES (:id,:tenantId,'M85_READER','M85 Reader','ACTIVE',now())
                """).param("id", roleId).param("tenantId", TENANT).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id,capability_code,granted_at)
                VALUES (:id,:capability,now())
                """).param("id", roleId).param("capability", capability).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id,tenant_id,principal_id,role_id,scope_type,scope_ref,
                    valid_from,source_code,approval_ref,created_at
                ) VALUES (
                    :grantId,:tenantId,:principalId,:roleId,'PROJECT',:projectId,
                    now()-interval '1 day','TEST','m85',now()
                )
                """)
                .param("grantId", UUID.randomUUID())
                .param("tenantId", TENANT)
                .param("principalId", principalId)
                .param("roleId", roleId)
                .param("projectId", project.toString())
                .update();
    }

    private static CurrentPrincipal principal(String principalId) {
        return new CurrentPrincipal(
                principalId, TENANT, CurrentPrincipal.PrincipalType.USER, "m85", Set.of());
    }
}
