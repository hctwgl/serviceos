package com.serviceos.dispatch;

import com.serviceos.ServiceOsApplication;
import com.serviceos.dispatch.api.ManualAssignServiceAssignmentCommand;
import com.serviceos.dispatch.api.ManualServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ManualServiceAssignmentService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.BusinessProblem;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M144：人工初派编排经真实 PostgreSQL 落到双 ACTIVE 责任与 COMPLETED saga。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ManualServiceAssignmentPostgresIT {
    private static final String TENANT = "tenant-manual-assign-it";
    private static final String MANAGER = "manual-assign-manager";
    private static final String BUSINESS_TYPE = "INSTALLATION";
    private static final UUID TECHNICIAN_PROFILE =
            UUID.fromString("b6fd9adb-acde-44d1-ae58-2dc0f28326be");
    private static final UUID TECHNICIAN_PRINCIPAL =
            UUID.fromString("f4eb39d8-563b-4e58-b2fe-b79d3b2b4f88");

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
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE dsp_assignment_command_result, dsp_capacity_command_result,
                    dsp_service_assignment_activation_saga, dsp_capacity_reservation,
                    dsp_service_assignment, dsp_capacity_counter,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record, auth_role_field_policy,
                    auth_role_grant, auth_role_capability, auth_role, tsk_task,
                    net_technician_profile, idn_security_principal CASCADE
                """).update();
        DispatchTechnicianFixture.seed(
                jdbc, TENANT, TECHNICIAN_PROFILE, TECHNICIAN_PRINCIPAL, "试点安装师傅");
        seedGrant(Set.of("dispatch.capacity.configure", "dispatch.assignment.manage"));
    }

    @Test
    void manualAssignActivatesNetworkAndTechnicianAtomically() {
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = seedHumanTask(workOrderId);

        ManualServiceAssignmentReceipt receipt = manualAssignments.manualAssign(
                manager(), metadata("manual-initial"),
                new ManualAssignServiceAssignmentCommand(
                        taskId, "network-pilot-1", TECHNICIAN_PROFILE.toString(), BUSINESS_TYPE));

        assertThat(receipt.workOrderId()).isEqualTo(workOrderId);
        assertThat(receipt.networkAssigneeId()).isEqualTo("network-pilot-1");
        assertThat(receipt.technicianAssigneeId()).isEqualTo(TECHNICIAN_PROFILE.toString());
        assertThat(jdbc.sql("""
                        SELECT responsibility_level || ':' || assignee_id || ':' || status
                          FROM dsp_service_assignment
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                         ORDER BY responsibility_level
                        """)
                .param("tenantId", TENANT).param("taskId", taskId)
                .query(String.class).list())
                .containsExactly(
                        "NETWORK:network-pilot-1:ACTIVE",
                        "TECHNICIAN:" + TECHNICIAN_PROFILE + ":ACTIVE");
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM dsp_capacity_reservation
                         WHERE tenant_id = :tenantId AND status = 'CONFIRMED'
                        """)
                .param("tenantId", TENANT).query(Long.class).single())
                .isEqualTo(2L);
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM dsp_service_assignment_activation_saga
                         WHERE tenant_id = :tenantId AND task_id = :taskId AND stage = 'COMPLETED'
                        """)
                .param("tenantId", TENANT).param("taskId", taskId)
                .query(Long.class).single())
                .isEqualTo(2L);

        ManualServiceAssignmentReceipt replay = manualAssignments.manualAssign(
                manager(), metadata("manual-initial"),
                new ManualAssignServiceAssignmentCommand(
                        taskId, "network-pilot-1", TECHNICIAN_PROFILE.toString(), BUSINESS_TYPE));
        assertThat(replay.networkServiceAssignmentId())
                .isEqualTo(receipt.networkServiceAssignmentId());
        assertThat(replay.technicianServiceAssignmentId())
                .isEqualTo(receipt.technicianServiceAssignmentId());
    }

    @Test
    void manualAssignNetworkWithoutFrozenPolicyFailsClosedAndDoesNotCreateCapacity() {
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = seedHumanTask(workOrderId);

        assertThatThrownBy(() -> manualAssignments.manualAssignNetwork(
                manager(), metadata("manual-network-only"),
                taskId, "network-pilot-1", BUSINESS_TYPE))
                .isInstanceOf(BusinessProblem.class)
                .hasMessageContaining("缺少已冻结的派单配置");
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM dsp_capacity_counter
                         WHERE tenant_id = :tenantId AND responsibility_level = 'NETWORK'
                        """)
                .param("tenantId", TENANT)
                .query(Long.class).single())
                .isZero();
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM dsp_service_assignment
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                        """)
                .param("tenantId", TENANT).param("taskId", taskId)
                .query(Long.class).single()).isZero();
    }

    private UUID seedHumanTask(UUID workOrderId) {
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
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
                    500, 'READY', :now, 0, 3, 'corr-seed', 1, :now, :now, :projectId,
                    :workOrderId, :workflowInstanceId, :stageInstanceId, :workflowNodeInstanceId,
                    'INSTALL_NODE', :definitionId, :definitionDigest, :bundleId, :bundleDigest,
                    'INSTALL'
                )
                """)
                .param("taskId", taskId)
                .param("tenantId", TENANT)
                .param("businessKey", "manual-assign:" + taskId)
                .param("digest", "a".repeat(64))
                .param("now", java.sql.Timestamp.from(now))
                .param("projectId", UUID.randomUUID())
                .param("workOrderId", workOrderId)
                .param("workflowInstanceId", UUID.randomUUID())
                .param("stageInstanceId", UUID.randomUUID())
                .param("workflowNodeInstanceId", UUID.randomUUID())
                .param("definitionId", UUID.randomUUID())
                .param("definitionDigest", "b".repeat(64))
                .param("bundleId", UUID.randomUUID())
                .param("bundleDigest", "c".repeat(64))
                .update();
        return taskId;
    }

    private void seedGrant(Set<String> capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, 'manual-assign-manager', 'M144 测试角色', 'ACTIVE', now())
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
                    now() - interval '1 day', 'TEST_FIXTURE', 'M144-TEST', now()
                )
                """)
                .param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("actorId", MANAGER).param("roleId", roleId).update();
    }

    private static CurrentPrincipal manager() {
        return new CurrentPrincipal(MANAGER, TENANT, CurrentPrincipal.PrincipalType.USER,
                "m144-it", Set.of("dispatch.capacity.configure", "dispatch.assignment.manage"));
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }
}
