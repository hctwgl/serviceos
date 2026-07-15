package com.serviceos.task;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.AbortPreparedTaskAssignmentCommand;
import com.serviceos.task.api.ActivatePreparedTaskAssignmentCommand;
import com.serviceos.task.api.AssignTaskCandidatesCommand;
import com.serviceos.task.api.AssignmentSourceType;
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.PrepareTaskReassignmentCommand;
import com.serviceos.task.api.ReleaseTaskExecutionGuardCommand;
import com.serviceos.task.api.StartHumanTaskCommand;
import com.serviceos.task.api.TaskAssignmentService;
import com.serviceos.task.api.TaskExecutionGuardService;
import com.serviceos.task.api.TaskReassignmentService;
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

/** M23：验证 PREPARED 责任的可靠激活、切换前放弃和失败后向前重试条件。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TaskReassignmentPostgresIT {
    private static final String TENANT = "tenant-task-reassignment-it";
    private static final String SERVICE_ASSIGNMENT = "service-assignment://technician/sa-002";

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

    @Autowired TaskReassignmentService reassignments;
    @Autowired TaskAssignmentService assignments;
    @Autowired TaskExecutionGuardService guards;
    @Autowired HumanTaskCommandService humanTasks;
    @Autowired TaskSchedulingService tasks;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                DROP TRIGGER IF EXISTS trg_test_fail_assignment_activation ON rel_outbox_event;
                DROP FUNCTION IF EXISTS test_fail_assignment_activation();
                TRUNCATE TABLE tsk_task_reassignment_command_result,
                    tsk_task_execution_guard, tsk_task_assignment, tsk_task_assignment_batch,
                    tsk_human_task_command_result, tsk_task_execution_attempt, tsk_task,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record, auth_role_field_policy,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        seedGrant("reassignment-manager", Set.of(
                "task.assign", "task.reassignment.manage", "task.guard.manage"));
        seedGrant("actor-a", Set.of("task.claim", "task.start"));
        seedGrant("actor-b", Set.of("task.start"));
    }

    @Test
    void prepareAndActivateSwitchResponsibilityAndReleaseGuardAtomically() {
        UUID taskId = claimedTask();
        PrepareTaskReassignmentCommand prepareCommand = new PrepareTaskReassignmentCommand(
                taskId, 3, "saga://technician-reassignment/001", "actor-b",
                SERVICE_ASSIGNMENT, "TECHNICIAN_REASSIGNMENT");
        var prepared = reassignments.prepare(
                manager(), metadata("prepare-001"), prepareCommand);
        var prepareReplay = reassignments.prepare(
                manager(), metadata("prepare-001"), prepareCommand);

        assertThat(prepareReplay).isEqualTo(prepared);
        assertThat(prepared.status()).isEqualTo("PREPARED");
        assertThat(prepared.taskVersion()).isEqualTo(4);
        assertThatThrownBy(() -> reassignments.prepare(
                manager(), metadata("prepare-concurrent"),
                new PrepareTaskReassignmentCommand(
                        taskId, 4, "saga://technician-reassignment/002", "actor-c",
                        "service-assignment://technician/sa-003", "TECHNICIAN_REASSIGNMENT")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_EXECUTION_GUARDED));
        assertThatThrownBy(() -> humanTasks.start(
                principal("actor-a", "task.start"), metadata("start-during-switch"),
                new StartHumanTaskCommand(taskId, 4)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_EXECUTION_GUARDED));
        assertThatThrownBy(() -> guards.release(
                principal("reassignment-manager", "task.guard.manage"), metadata("low-level-release"),
                new ReleaseTaskExecutionGuardCommand(
                        taskId, prepared.guardId(), 4, "MANUAL_RELEASE")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_ASSIGNMENT_CONFLICT));
        assertThatThrownBy(() -> reassignments.activate(
                manager(), metadata("activate-wrong-source"),
                new ActivatePreparedTaskAssignmentCommand(
                        taskId, prepared.guardId(), prepared.preparedTaskAssignmentId(), 4,
                        "service-assignment://technician/wrong")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_ASSIGNMENT_CONFLICT));

        ActivatePreparedTaskAssignmentCommand activateCommand = new ActivatePreparedTaskAssignmentCommand(
                taskId, prepared.guardId(), prepared.preparedTaskAssignmentId(), 4, SERVICE_ASSIGNMENT);
        var activated = reassignments.activate(
                manager(), metadata("activate-001"), activateCommand);
        var activationReplay = reassignments.activate(
                manager(), metadata("activate-001"), activateCommand);

        assertThat(activationReplay).isEqualTo(activated);
        assertThat(activated.status()).isEqualTo("ACTIVE");
        assertThat(activated.taskVersion()).isEqualTo(5);
        assertThat(jdbc.sql("""
                SELECT status || ':' || claimed_by || ':' || version
                  FROM tsk_task WHERE task_id = :taskId
                """).param("taskId", taskId).query(String.class).single())
                .isEqualTo("CLAIMED:actor-b:5");
        assertThat(jdbc.sql("""
                SELECT assignment_kind || ':' || principal_id || ':' || status
                  FROM tsk_task_assignment WHERE task_id = :taskId
                 ORDER BY created_at, assignment_kind, principal_id
                """).param("taskId", taskId).query(String.class).list())
                .containsExactly(
                        "CANDIDATE:actor-a:REVOKED",
                        "RESPONSIBLE:actor-a:REVOKED",
                        "RESPONSIBLE:actor-b:ACTIVE",
                        "CANDIDATE:actor-b:ACTIVE");
        assertThat(jdbc.sql("""
                SELECT status || ':' || release_reason_code
                  FROM tsk_task_execution_guard WHERE task_execution_guard_id = :guardId
                """).param("guardId", prepared.guardId()).query(String.class).single())
                .isEqualTo("RELEASED:TASK_ASSIGNMENT_ACTIVATED");
        assertThatThrownBy(() -> humanTasks.start(
                principal("actor-a", "task.start"), metadata("old-actor-start"),
                new StartHumanTaskCommand(taskId, 5)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_STATE_CONFLICT));
        var started = humanTasks.start(
                principal("actor-b", "task.start"), metadata("new-actor-start"),
                new StartHumanTaskCommand(taskId, 5));
        assertThat(started.version()).isEqualTo(6);
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event")
                .query(String.class).list())
                .contains("task.assignment-prepared", "task.assignment-activated",
                        "task.execution-guard.activated", "task.execution-guard.released");
    }

    @Test
    void abortBeforeServiceSwitchKeepsOldResponsibilityAndUnblocksTask() {
        UUID taskId = claimedTask();
        var prepared = prepare(taskId, "abort");
        AbortPreparedTaskAssignmentCommand abortCommand = new AbortPreparedTaskAssignmentCommand(
                taskId, prepared.guardId(), prepared.preparedTaskAssignmentId(), 4,
                "CAPACITY_RESERVATION_FAILED");

        var aborted = reassignments.abort(manager(), metadata("abort-001"), abortCommand);
        var abortReplay = reassignments.abort(manager(), metadata("abort-001"), abortCommand);
        var started = humanTasks.start(
                principal("actor-a", "task.start"), metadata("start-after-abort"),
                new StartHumanTaskCommand(taskId, 5));

        assertThat(abortReplay).isEqualTo(aborted);
        assertThat(aborted.status()).isEqualTo("ABORTED");
        assertThat(started.version()).isEqualTo(6);
        assertThat(jdbc.sql("""
                SELECT principal_id || ':' || status FROM tsk_task_assignment
                 WHERE task_id = :taskId AND assignment_kind = 'RESPONSIBLE'
                 ORDER BY created_at
                """).param("taskId", taskId).query(String.class).list())
                .containsExactly("actor-a:ACTIVE", "actor-b:ABORTED");
        assertThat(jdbc.sql("SELECT status FROM tsk_task_execution_guard WHERE task_id = :taskId")
                .param("taskId", taskId).query(String.class).single()).isEqualTo("RELEASED");
    }

    @Test
    void activationOutboxFailureKeepsGuardAndPreparedResponsibilityForForwardRetry() {
        UUID taskId = claimedTask();
        var prepared = prepare(taskId, "rollback");
        jdbc.sql("""
                CREATE FUNCTION test_fail_assignment_activation() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'task.assignment-activated' THEN
                        RAISE EXCEPTION 'injected assignment activation outbox failure';
                    END IF;
                    RETURN NEW;
                END;
                $$;
                CREATE TRIGGER trg_test_fail_assignment_activation
                    BEFORE INSERT ON rel_outbox_event
                    FOR EACH ROW EXECUTE FUNCTION test_fail_assignment_activation();
                """).update();

        assertThatThrownBy(() -> reassignments.activate(
                manager(), metadata("activation-rollback"),
                new ActivatePreparedTaskAssignmentCommand(
                        taskId, prepared.guardId(), prepared.preparedTaskAssignmentId(), 4,
                        SERVICE_ASSIGNMENT)))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbc.sql("SELECT claimed_by || ':' || version FROM tsk_task WHERE task_id = :taskId")
                .param("taskId", taskId).query(String.class).single()).isEqualTo("actor-a:4");
        assertThat(jdbc.sql("SELECT status FROM tsk_task_execution_guard WHERE task_id = :taskId")
                .param("taskId", taskId).query(String.class).single()).isEqualTo("ACTIVE");
        assertThat(jdbc.sql("""
                SELECT principal_id || ':' || status FROM tsk_task_assignment
                 WHERE task_id = :taskId AND assignment_kind = 'RESPONSIBLE'
                 ORDER BY created_at
                """).param("taskId", taskId).query(String.class).list())
                .containsExactly("actor-a:ACTIVE", "actor-b:PREPARED");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM tsk_task_reassignment_command_result
                 WHERE operation_type = 'task.reassignment.activate'
                """).query(Long.class).single()).isZero();
        assertThat(count("aud_audit_record")).isEqualTo(3);
        assertThat(count("rel_idempotency_record")).isEqualTo(3);
        assertThat(count("rel_outbox_event")).isEqualTo(5);
    }

    private UUID claimedTask() {
        UUID taskId = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                TENANT, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "SITE_SURVEY", UUID.randomUUID(), "a".repeat(64),
                UUID.randomUUID(), "c".repeat(64),
                "SURVEY", "SITE_SURVEY", WorkflowTaskKind.HUMAN, null, null,
                "work-order:reassignment-test", "b".repeat(64),
                500, Instant.now(), 1, "corr-task-create", "cause-task-create")).taskId();
        assignments.assignCandidates(
                principal("reassignment-manager", "task.assign"), metadata("assign-" + taskId),
                new AssignTaskCandidatesCommand(taskId, 1, List.of("actor-a"),
                        AssignmentSourceType.MANUAL, "manual://initial-technician"));
        humanTasks.claim(principal("actor-a", "task.claim"), metadata("claim-" + taskId),
                new ClaimHumanTaskCommand(taskId, 2));
        return taskId;
    }

    private com.serviceos.task.api.TaskReassignmentReceipt prepare(UUID taskId, String suffix) {
        return reassignments.prepare(manager(), metadata("prepare-" + suffix),
                new PrepareTaskReassignmentCommand(
                        taskId, 3, "saga://technician-reassignment/" + suffix, "actor-b",
                        SERVICE_ASSIGNMENT, "TECHNICIAN_REASSIGNMENT"));
    }

    private void seedGrant(String actorId, Set<String> capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, :roleCode, 'M23 测试角色', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT)
                .param("roleCode", "m23-" + actorId).update();
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
                    now() - interval '1 day', 'TEST_FIXTURE', 'M23-TEST', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("actorId", actorId).param("roleId", roleId).update();
    }

    private static CurrentPrincipal manager() {
        return principal("reassignment-manager", "task.reassignment.manage");
    }

    private static CurrentPrincipal principal(String actorId, String... capabilities) {
        return new CurrentPrincipal(actorId, TENANT, CurrentPrincipal.PrincipalType.USER,
                "m23-it", Set.of(capabilities));
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
