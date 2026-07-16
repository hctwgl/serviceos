package com.serviceos.task;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.AssignTaskCandidatesCommand;
import com.serviceos.task.api.AssignmentSourceType;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.StartHumanTaskCommand;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.task.api.TaskAssignmentService;
import com.serviceos.task.api.TaskFulfillmentContextService;
import com.serviceos.task.api.WorkflowTaskKind;
import org.flywaydb.core.Flyway;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M20：用真实 PostgreSQL 验证人工工作流任务的授权、幂等、乐观锁和事务事件闭环。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HumanTaskCommandPostgresIT {
    private static final String TENANT = "tenant-human-task-it";

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

    @Autowired HumanTaskCommandService commands;
    @Autowired TaskAssignmentService assignments;
    @Autowired TaskSchedulingService tasks;
    @Autowired TaskFulfillmentContextService fulfillmentContexts;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                DROP TRIGGER IF EXISTS trg_test_fail_task_completed ON rel_outbox_event;
                DROP FUNCTION IF EXISTS test_fail_task_completed();
                TRUNCATE TABLE tsk_task_reassignment_command_result,
                    tsk_task_execution_guard, tsk_task_assignment, tsk_task_assignment_batch,
                    tsk_human_task_command_result, tsk_task_execution_attempt, tsk_task,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record, auth_role_field_policy,
                    auth_role_grant, auth_role_capability, auth_role CASCADE;
                """).update();
        seedGrant("actor-a");
        seedGrant("actor-b");
    }

    @Test
    void claimStartCompleteAndReplaysReturnFrozenReceipts() {
        UUID taskId = workflowHumanTask();
        CurrentPrincipal actor = principal("actor-a");
        CommandMetadata claimMetadata = metadata("claim-001");

        var claimed = commands.claim(actor, claimMetadata, new ClaimHumanTaskCommand(taskId, 2));
        var claimReplayBeforeStart = commands.claim(
                actor, claimMetadata, new ClaimHumanTaskCommand(taskId, 2));
        var started = commands.start(
                actor, metadata("start-001"), new StartHumanTaskCommand(taskId, 3));
        var claimReplayAfterStart = commands.claim(
                actor, claimMetadata, new ClaimHumanTaskCommand(taskId, 2));
        var completed = commands.complete(
                actor, metadata("complete-001"),
                new CompleteHumanTaskCommand(
                        taskId, 4, "form-submission://SUB-001/3", "d".repeat(64)));
        var completeReplay = commands.complete(
                actor, metadata("complete-001"),
                new CompleteHumanTaskCommand(
                        taskId, 4, "form-submission://SUB-001/3", "d".repeat(64)));

        assertThat(claimReplayBeforeStart).isEqualTo(claimed);
        assertThat(claimReplayAfterStart).isEqualTo(claimed);
        assertThat(completeReplay).isEqualTo(completed);
        assertThat(started.status()).isEqualTo("RUNNING");
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.version()).isEqualTo(5);
        assertThat(jdbc.sql("""
                SELECT status || ':' || claimed_by || ':' || version FROM tsk_task WHERE task_id = :taskId
                """).param("taskId", taskId).query(String.class).single())
                .isEqualTo("COMPLETED:actor-a:5");
        assertThat(jdbc.sql("SELECT result_digest FROM tsk_task WHERE task_id = :taskId")
                .param("taskId", taskId).query(String.class).single()).isEqualTo("d".repeat(64));
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event ORDER BY aggregate_version")
                .query(String.class).list())
                .containsExactly("task.created", "task.assigned", "task.claimed", "task.started", "task.completed");
        assertThat(count("aud_audit_record")).isEqualTo(4);
        assertThat(count("rel_idempotency_record")).isEqualTo(4);
        assertThat(count("tsk_human_task_command_result")).isEqualTo(3);
        assertThat(count("tsk_task_execution_attempt")).isZero();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM tsk_task_assignment
                 WHERE task_id = :taskId AND status = 'ACTIVE'
                """).param("taskId", taskId).query(Long.class).single()).isZero();
    }

    @Test
    void staleVersionWrongOwnerAndIdempotencyMutationFailClosed() {
        UUID taskId = workflowHumanTask();
        CurrentPrincipal actorA = principal("actor-a");
        commands.claim(actorA, metadata("claim-002"), new ClaimHumanTaskCommand(taskId, 2));

        assertThatThrownBy(() -> commands.claim(
                actorA, metadata("claim-stale"), new ClaimHumanTaskCommand(taskId, 2)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VERSION_CONFLICT));
        assertThatThrownBy(() -> commands.start(
                principal("actor-b"), metadata("start-wrong-owner"),
                new StartHumanTaskCommand(taskId, 3)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_STATE_CONFLICT));
        assertThatThrownBy(() -> commands.claim(
                actorA, metadata("claim-002"), new ClaimHumanTaskCommand(taskId, 3)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.IDEMPOTENCY_KEY_REUSED));

        assertThat(jdbc.sql("SELECT status FROM tsk_task WHERE task_id = :taskId")
                .param("taskId", taskId).query(String.class).single()).isEqualTo("CLAIMED");
        assertThat(count("aud_audit_record")).isEqualTo(2);
        assertThat(count("rel_idempotency_record")).isEqualTo(2);
    }

    @Test
    void completionOutboxFailureRollsBackTaskAuditIdempotencyAndFrozenReceipt() {
        UUID taskId = workflowHumanTask();
        CurrentPrincipal actor = principal("actor-a");
        commands.claim(actor, metadata("claim-003"), new ClaimHumanTaskCommand(taskId, 2));
        commands.start(actor, metadata("start-003"), new StartHumanTaskCommand(taskId, 3));
        jdbc.sql("""
                CREATE FUNCTION test_fail_task_completed() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'task.completed' THEN
                        RAISE EXCEPTION 'injected task.completed outbox failure';
                    END IF;
                    RETURN NEW;
                END;
                $$;
                CREATE TRIGGER trg_test_fail_task_completed
                    BEFORE INSERT ON rel_outbox_event
                    FOR EACH ROW EXECUTE FUNCTION test_fail_task_completed();
                """).update();

        assertThatThrownBy(() -> commands.complete(
                actor, metadata("complete-rollback"),
                new CompleteHumanTaskCommand(taskId, 4, "result://rollback", "e".repeat(64))))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbc.sql("SELECT status || ':' || version FROM tsk_task WHERE task_id = :taskId")
                .param("taskId", taskId).query(String.class).single()).isEqualTo("RUNNING:4");
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type = 'task.completed'")
                .query(Long.class).single()).isZero();
        assertThat(count("aud_audit_record")).isEqualTo(3);
        assertThat(count("rel_idempotency_record")).isEqualTo(3);
        assertThat(count("tsk_human_task_command_result")).isEqualTo(2);
    }

    @Test
    void migrationSetIsCurrentAndRepeatable() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("079");
        assertThat(flyway.info().applied()).hasSize(81);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void freezesFormReferenceInTaskAndExposesItThroughThePublicContext() {
        UUID taskId = tasks.createWorkflowTask(
                workflowTaskCommand(UUID.randomUUID(), "survey.form")).taskId();

        assertThat(fulfillmentContexts.find(TENANT, taskId)).get().satisfies(context -> {
            assertThat(context.formRef()).isEqualTo("survey.form");
            assertThat(context.configurationBundleId()).isNotNull();
            assertThat(context.configurationBundleDigest()).isEqualTo("c".repeat(64));
        });
        assertThat(jdbc.sql("SELECT form_ref FROM tsk_task WHERE task_id = :taskId")
                .param("taskId", taskId).query(String.class).single()).isEqualTo("survey.form");
    }

    @Test
    void replayingTheSameWorkflowNodeWithADifferentFormReferenceFailsClosed() {
        UUID nodeInstanceId = UUID.randomUUID();
        CreateWorkflowTaskCommand original = workflowTaskCommand(nodeInstanceId, "survey.form");
        tasks.createWorkflowTask(original);

        CreateWorkflowTaskCommand drifted = workflowTaskCommand(nodeInstanceId, "installation.form");
        assertThatThrownBy(() -> tasks.createWorkflowTask(drifted))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.TASK_SCHEDULE_CONFLICT));
        assertThat(jdbc.sql("SELECT form_ref FROM tsk_task WHERE workflow_node_instance_id = :nodeId")
                .param("nodeId", nodeInstanceId).query(String.class).single()).isEqualTo("survey.form");
    }

    private UUID workflowHumanTask() {
        UUID taskId = tasks.createWorkflowTask(workflowTaskCommand(UUID.randomUUID(), null)).taskId();
        assignments.assignCandidates(
                principal("actor-a"), metadata("assign-" + taskId),
                new AssignTaskCandidatesCommand(
                        taskId, 1, java.util.List.of("actor-a", "actor-b"),
                        AssignmentSourceType.ASSIGNEE_POLICY, "policy://m21/test"));
        return taskId;
    }

    private CreateWorkflowTaskCommand workflowTaskCommand(UUID nodeInstanceId, String formRef) {
        return new CreateWorkflowTaskCommand(
                TENANT, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                nodeInstanceId, "SITE_SURVEY", UUID.randomUUID(), "a".repeat(64),
                UUID.randomUUID(), "c".repeat(64),
                "SURVEY", "SITE_SURVEY", WorkflowTaskKind.HUMAN, formRef, null,
                "work-order:test", "b".repeat(64),
                500, Instant.now(), 1, "corr-task-create", "cause-task-create");
    }

    private void seedGrant(String actorId) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, :roleCode, '人工任务执行人', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT)
                .param("roleCode", "human-worker-" + actorId).update();
        for (String capability : Set.of(
                "task.assign", "task.claim", "task.start", "task.complete", "task.release")) {
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
                    now() - interval '1 day', 'TEST_FIXTURE', 'M20-TEST', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("actorId", actorId).param("roleId", roleId).update();
    }

    private static CurrentPrincipal principal(String actorId) {
        return new CurrentPrincipal(
                actorId, TENANT, CurrentPrincipal.PrincipalType.USER, "m20-it",
                Set.of("task.assign", "task.claim", "task.start", "task.complete", "task.release"));
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
