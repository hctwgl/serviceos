package com.serviceos.task;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.AcquireTaskExecutionGuardCommand;
import com.serviceos.task.api.AssignTaskCandidatesCommand;
import com.serviceos.task.api.AssignmentSourceType;
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.ReleaseHumanTaskCommand;
import com.serviceos.task.api.ReleaseTaskExecutionGuardCommand;
import com.serviceos.task.api.StartHumanTaskCommand;
import com.serviceos.task.api.TaskAssignmentService;
import com.serviceos.task.api.TaskExecutionGuardService;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.task.api.WorkflowTaskKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
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

/** M22：真实 PostgreSQL 验证改派保护窗对人工命令失败关闭，并保持事务事实与幂等响应。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TaskExecutionGuardPostgresIT {
    private static final String TENANT = "tenant-task-guard-it";

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

    @Autowired TaskExecutionGuardService guards;
    @Autowired TaskAssignmentService assignments;
    @Autowired HumanTaskCommandService humanTasks;
    @Autowired TaskSchedulingService tasks;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                DROP TRIGGER IF EXISTS trg_test_fail_task_guard ON rel_outbox_event;
                DROP FUNCTION IF EXISTS test_fail_task_guard();
                TRUNCATE TABLE tsk_task_reassignment_command_result,
                    tsk_task_execution_guard, tsk_task_assignment, tsk_task_assignment_batch,
                    tsk_human_task_command_result, tsk_task_execution_attempt, tsk_task,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record, auth_role_field_policy,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        seedGrant("guard-manager", Set.of("task.guard.manage", "task.assign"));
        seedGrant("actor-a", Set.of("task.claim", "task.start", "task.release"));
    }

    @Test
    void activeGuardBlocksHumanCommandsUntilExactGuardIsReleased() {
        UUID taskId = humanTask();
        assignments.assignCandidates(
                principal("guard-manager", "task.assign"), metadata("assign"),
                new AssignTaskCandidatesCommand(taskId, 1, List.of("actor-a"),
                        AssignmentSourceType.MANUAL, "manual://guard-test"));
        humanTasks.claim(principal("actor-a", "task.claim"), metadata("claim"),
                new ClaimHumanTaskCommand(taskId, 2));

        AcquireTaskExecutionGuardCommand acquireCommand = new AcquireTaskExecutionGuardCommand(
                taskId, 3, "saga://reassignment/001", "TECHNICIAN_REASSIGNMENT");
        var acquired = guards.acquire(
                principal("guard-manager", "task.guard.manage"), metadata("guard-acquire"), acquireCommand);
        var acquireReplay = guards.acquire(
                principal("guard-manager", "task.guard.manage"), metadata("guard-acquire"), acquireCommand);

        assertThat(acquireReplay).isEqualTo(acquired);
        assertThat(acquired.status()).isEqualTo("ACTIVE");
        assertThat(acquired.taskVersion()).isEqualTo(4);
        assertThatThrownBy(() -> humanTasks.start(
                principal("actor-a", "task.start"), metadata("start-guarded"),
                new StartHumanTaskCommand(taskId, 4)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_EXECUTION_GUARDED));
        assertThatThrownBy(() -> humanTasks.release(
                principal("actor-a", "task.release"), metadata("release-guarded"),
                new ReleaseHumanTaskCommand(taskId, 4, "USER_REQUEST")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_EXECUTION_GUARDED));
        assertThatThrownBy(() -> guards.acquire(
                principal("guard-manager", "task.guard.manage"), metadata("second-guard"),
                new AcquireTaskExecutionGuardCommand(
                        taskId, 4, "saga://reassignment/002", "TECHNICIAN_REASSIGNMENT")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_EXECUTION_GUARDED));
        assertThatThrownBy(() -> guards.release(
                principal("guard-manager", "task.guard.manage"), metadata("wrong-guard-release"),
                new ReleaseTaskExecutionGuardCommand(
                        taskId, UUID.randomUUID(), 4, "SERVICE_ASSIGNMENT_ALIGNED")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_EXECUTION_GUARDED));

        ReleaseTaskExecutionGuardCommand releaseCommand = new ReleaseTaskExecutionGuardCommand(
                taskId, acquired.guardId(), 4, "SERVICE_ASSIGNMENT_ALIGNED");
        var released = guards.release(
                principal("guard-manager", "task.guard.manage"), metadata("guard-release"), releaseCommand);
        var releaseReplay = guards.release(
                principal("guard-manager", "task.guard.manage"), metadata("guard-release"), releaseCommand);
        var started = humanTasks.start(principal("actor-a", "task.start"), metadata("start-after-release"),
                new StartHumanTaskCommand(taskId, 5));

        assertThat(releaseReplay).isEqualTo(released);
        assertThat(released.status()).isEqualTo("RELEASED");
        assertThat(released.taskVersion()).isEqualTo(5);
        assertThat(started.version()).isEqualTo(6);
        assertThat(jdbc.sql("""
                SELECT status || ':' || activated_task_version || ':' || released_task_version
                  FROM tsk_task_execution_guard WHERE task_execution_guard_id = :guardId
                """).param("guardId", acquired.guardId()).query(String.class).single())
                .isEqualTo("RELEASED:4:5");
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event ORDER BY aggregate_version")
                .query(String.class).list())
                .containsExactly("task.created", "task.assigned", "task.claimed",
                        "task.execution-guard.activated", "task.execution-guard.released", "task.started");
    }

    @Test
    void readyGuardBlocksClaimAndCandidateSnapshotReplacement() {
        UUID taskId = humanTask();
        assignments.assignCandidates(
                principal("guard-manager", "task.assign"), metadata("assign-before-ready-guard"),
                new AssignTaskCandidatesCommand(taskId, 1, List.of("actor-a"),
                        AssignmentSourceType.MANUAL, "manual://ready-guard"));
        guards.acquire(
                principal("guard-manager", "task.guard.manage"), metadata("ready-guard"),
                new AcquireTaskExecutionGuardCommand(
                        taskId, 2, "saga://ready-reassignment", "TECHNICIAN_REASSIGNMENT"));

        assertThatThrownBy(() -> humanTasks.claim(
                principal("actor-a", "task.claim"), metadata("claim-ready-guarded"),
                new ClaimHumanTaskCommand(taskId, 3)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_EXECUTION_GUARDED));
        assertThatThrownBy(() -> assignments.assignCandidates(
                principal("guard-manager", "task.assign"), metadata("replace-ready-guarded"),
                new AssignTaskCandidatesCommand(taskId, 3, List.of("actor-a"),
                        AssignmentSourceType.MANUAL, "manual://replacement")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_EXECUTION_GUARDED));

        assertThat(jdbc.sql("""
                SELECT principal_id || ':' || status FROM tsk_task_assignment
                 WHERE task_id = :taskId AND assignment_kind = 'CANDIDATE'
                """).param("taskId", taskId).query(String.class).list())
                .containsExactly("actor-a:ACTIVE");
    }

    @Test
    void guardOutboxFailureRollsBackTaskVersionGuardAuditAndIdempotency() {
        UUID taskId = humanTask();
        jdbc.sql("""
                CREATE FUNCTION test_fail_task_guard() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'task.execution-guard.activated' THEN
                        RAISE EXCEPTION 'injected task guard outbox failure';
                    END IF;
                    RETURN NEW;
                END;
                $$;
                CREATE TRIGGER trg_test_fail_task_guard
                    BEFORE INSERT ON rel_outbox_event
                    FOR EACH ROW EXECUTE FUNCTION test_fail_task_guard();
                """).update();

        assertThatThrownBy(() -> guards.acquire(
                principal("guard-manager", "task.guard.manage"), metadata("guard-rollback"),
                new AcquireTaskExecutionGuardCommand(
                        taskId, 1, "saga://rollback", "TECHNICIAN_REASSIGNMENT")))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbc.sql("SELECT version FROM tsk_task WHERE task_id = :taskId")
                .param("taskId", taskId).query(Long.class).single()).isEqualTo(1);
        assertThat(count("tsk_task_execution_guard")).isZero();
        assertThat(count("aud_audit_record")).isZero();
        assertThat(count("rel_idempotency_record")).isZero();
    }

    private UUID humanTask() {
        return tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                TENANT, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "SITE_SURVEY", UUID.randomUUID(), "a".repeat(64),
                UUID.randomUUID(), "c".repeat(64),
                "SURVEY", "SITE_SURVEY", WorkflowTaskKind.HUMAN, null, null, null,
                "work-order:guard-test", "b".repeat(64),
                500, Instant.now(), 1, "corr-task-create", "cause-task-create")).taskId();
    }

    private void seedGrant(String actorId, Set<String> capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, :roleCode, 'M22 测试角色', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT)
                .param("roleCode", "m22-" + actorId).update();
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
                    :grantId, :tenantId, :actorId, :roleId, 'TENANT', :tenantId,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M22-TEST', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("actorId", actorId).param("roleId", roleId).update();
    }

    private static CurrentPrincipal principal(String actorId, String... capabilities) {
        return new CurrentPrincipal(actorId, TENANT, CurrentPrincipal.PrincipalType.USER,
                "m22-it", Set.of(capabilities));
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
