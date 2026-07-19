package com.serviceos.task;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.AcquireTaskExecutionGuardCommand;
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.StartHumanTaskCommand;
import com.serviceos.task.api.TaskAllowedAction;
import com.serviceos.task.api.TaskAllowedActionQueryService;
import com.serviceos.task.api.TaskExecutionGuardService;
import org.flywaydb.core.Flyway;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M71：真实 PostgreSQL 证明动作投影与现有写命令使用同一状态、责任、guard 和实时授权事实。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TaskAllowedActionPostgresIT {
    private static final String TENANT = "tenant-task-action-it";
    private static final Set<String> HUMAN_CAPABILITIES = Set.of(
            "task.read", "task.claim", "task.start", "task.complete", "task.release");

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
    private TaskAllowedActionQueryService actionQueries;

    @Autowired
    private HumanTaskCommandService humanCommands;

    @Autowired
    private TaskExecutionGuardService guards;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE tsk_task_execution_guard, tsk_task_assignment,
                    tsk_task_assignment_batch, tsk_human_task_command_result,
                    tsk_task_execution_attempt, tsk_task, aud_audit_record,
                    rel_outbox_publish_attempt, rel_outbox_event, rel_idempotency_record,
                    auth_role_field_policy, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
    }

    @Test
    void followsClaimStartAndCompleteCommandPreconditions() {
        UUID taskId = humanTask("READY");
        assignment(taskId, "worker", "CANDIDATE");
        seedRole("worker", HUMAN_CAPABILITIES);
        CurrentPrincipal worker = principal("worker", TENANT);

        assertActionCodes(worker, taskId, "task.claim");
        var claimed = humanCommands.claim(
                worker, metadata("claim"), new ClaimHumanTaskCommand(taskId, 1));
        assertThat(claimed.version()).isEqualTo(2);
        var claimedActions = actionQueries.get(worker, "corr-claimed", taskId);
        assertThat(claimedActions.actions()).extracting(TaskAllowedAction::code)
                .containsExactly("task.start", "task.release");
        assertThat(claimedActions.actions().get(1).inputSchemaRef())
                .isEqualTo("#/components/schemas/ReleaseHumanTaskRequest");
        assertThat(claimedActions.actions().get(1).obligations())
                .containsExactly("REQUIRE_REASON");

        var started = humanCommands.start(
                worker, metadata("start"), new StartHumanTaskCommand(taskId, 2));
        assertThat(started.version()).isEqualTo(3);
        var runningActions = actionQueries.get(worker, "corr-running", taskId);
        assertThat(runningActions.resourceVersion()).isEqualTo(3);
        assertThat(runningActions.actions()).extracting(TaskAllowedAction::code)
                .containsExactly("task.complete");
        assertThat(runningActions.actions().getFirst().inputSchemaRef())
                .isEqualTo("#/components/schemas/CompleteHumanTaskRequest");
        assertThat(runningActions.actions().getFirst().obligations())
                .containsExactly("REQUIRE_RESULT");
    }

    @Test
    void capabilityRevocationAndExecutionGuardRemoveActionsImmediately() {
        UUID taskId = humanTask("READY");
        assignment(taskId, "worker", "CANDIDATE");
        UUID roleId = seedRole("worker", HUMAN_CAPABILITIES);
        CurrentPrincipal worker = principal("worker", TENANT);
        assertActionCodes(worker, taskId, "task.claim");

        jdbc.sql("""
                DELETE FROM auth_role_capability
                 WHERE role_id = :roleId AND capability_code = 'task.claim'
                """).param("roleId", roleId).update();
        assertActionCodes(worker, taskId);

        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, 'task.claim', now()), (:roleId, 'task.guard.manage', now())
                """).param("roleId", roleId).update();
        guards.acquire(
                worker,
                metadata("guard"),
                new AcquireTaskExecutionGuardCommand(taskId, 1, "saga://m71/guard", "REASSIGNMENT"));
        var guarded = actionQueries.get(worker, "corr-guarded", taskId);
        assertThat(guarded.resourceVersion()).isEqualTo(2);
        assertThat(guarded.actions()).isEmpty();
    }

    @Test
    void readBoundaryAndNonHumanOrUnassignedTasksFailClosed() {
        UUID humanTask = humanTask("READY");
        UUID automatedTask = task("AUTOMATED", "PENDING", UUID.randomUUID());
        seedRole("reader", Set.of("task.read"));
        seedRole("capable-but-not-candidate", HUMAN_CAPABILITIES);

        assertActionCodes(principal("reader", TENANT), humanTask);
        assertActionCodes(principal("reader", TENANT), automatedTask);
        assertActionCodes(principal("capable-but-not-candidate", TENANT), humanTask);

        assertThatThrownBy(() -> actionQueries.get(
                principal("no-read", TENANT), "corr-denied", humanTask))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThat(jdbc.sql("""
                SELECT decision_code FROM aud_audit_record
                 WHERE correlation_id = 'corr-denied'
                """).query(String.class).single()).isEqualTo("DENY");

        assertThatThrownBy(() -> actionQueries.get(
                principal("reader", "another-tenant"), "corr-cross", humanTask))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("118");
        assertThat(flyway.info().applied()).hasSize(120);
    }

    private void assertActionCodes(CurrentPrincipal principal, UUID taskId, String... codes) {
        assertThat(actionQueries.get(principal, "corr-actions", taskId).actions())
                .extracting(TaskAllowedAction::code)
                .containsExactly(codes);
    }

    private UUID humanTask(String status) {
        return task("HUMAN", status, UUID.randomUUID());
    }

    private UUID task(String taskKind, String status, UUID workflowNodeInstanceId) {
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id, tenant_id, task_type, task_kind, business_key, payload_digest,
                    priority, status, next_run_at, attempt_count, max_attempts, correlation_id,
                    version, created_at, updated_at, project_id, work_order_id,
                    workflow_instance_id, stage_instance_id, workflow_node_instance_id,
                    workflow_node_id, workflow_definition_version_id, workflow_definition_digest,
                    configuration_bundle_id, configuration_bundle_digest, stage_code
                ) VALUES (
                    :taskId, :tenantId, 'SITE_SURVEY', :taskKind, :businessKey, :digest,
                    500, :status, :now, 0, 3, 'corr-seed', 1, :now, :now, :projectId,
                    :workOrderId, :workflowInstanceId, :stageInstanceId, :workflowNodeInstanceId,
                    :workflowNodeId, :definitionId, :definitionDigest, :bundleId, :bundleDigest, 'SURVEY'
                )
                """)
                .param("taskId", taskId)
                .param("tenantId", TENANT)
                .param("taskKind", taskKind)
                .param("businessKey", "task-action:" + taskId)
                .param("digest", "a".repeat(64))
                .param("status", status)
                .param("now", java.sql.Timestamp.from(now))
                .param("projectId", UUID.randomUUID())
                .param("workOrderId", UUID.randomUUID())
                .param("workflowInstanceId", workflowNodeInstanceId == null ? null : UUID.randomUUID())
                .param("stageInstanceId", workflowNodeInstanceId == null ? null : UUID.randomUUID())
                .param("workflowNodeInstanceId", workflowNodeInstanceId)
                .param("workflowNodeId", workflowNodeInstanceId == null ? null : "SURVEY_NODE")
                .param("definitionId", workflowNodeInstanceId == null ? null : UUID.randomUUID())
                .param("definitionDigest", workflowNodeInstanceId == null ? null : "b".repeat(64))
                .param("bundleId", workflowNodeInstanceId == null ? null : UUID.randomUUID())
                .param("bundleDigest", workflowNodeInstanceId == null ? null : "c".repeat(64))
                .update();
        return taskId;
    }

    private void assignment(UUID taskId, String principalId, String kind) {
        jdbc.sql("""
                INSERT INTO tsk_task_assignment (
                    task_assignment_id, tenant_id, task_id, assignment_kind, principal_type,
                    principal_id, status, source_type, source_id, effective_from, created_by, created_at
                ) VALUES (
                    :id, :tenantId, :taskId, :kind, 'USER', :principalId, 'ACTIVE',
                    'ASSIGNEE_POLICY', 'policy://m71/test', now(), 'test', now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenantId", TENANT)
                .param("taskId", taskId)
                .param("kind", kind)
                .param("principalId", principalId)
                .update();
    }

    private UUID seedRole(String principalId, Set<String> capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, :roleCode, :roleCode, 'ACTIVE', now())
                """)
                .param("roleId", roleId)
                .param("tenantId", TENANT)
                .param("roleCode", "m71-" + principalId)
                .update();
        for (String capability : capabilities) {
            jdbc.sql("""
                    INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                    VALUES (:roleId, :capability, now())
                    """).param("roleId", roleId).param("capability", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at
                ) VALUES (
                    :grantId, :tenantId, :principalId, :roleId, 'TENANT', :tenantId,
                    now() - interval '1 day', 'TEST', 'm71', now()
                )
                """)
                .param("grantId", UUID.randomUUID())
                .param("tenantId", TENANT)
                .param("principalId", principalId)
                .param("roleId", roleId)
                .update();
        return roleId;
    }

    private static CurrentPrincipal principal(String principalId, String tenantId) {
        return new CurrentPrincipal(
                principalId, tenantId, CurrentPrincipal.PrincipalType.USER, "m71", Set.of());
    }

    private static CommandMetadata metadata(String suffix) {
        return new CommandMetadata("corr-" + suffix, "idem-" + suffix);
    }
}
