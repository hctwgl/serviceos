package com.serviceos.task;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.AssignTaskCandidatesCommand;
import com.serviceos.task.api.AssignmentSourceType;
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.ReleaseHumanTaskCommand;
import com.serviceos.task.api.TaskAssignmentService;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.task.api.WorkflowTaskKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.boot.test.context.SpringBootTest;
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

/** M21：真实 PostgreSQL 验证候选快照、唯一责任人、释放重领和事务回滚。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TaskAssignmentPostgresIT {
    private static final String TENANT = "tenant-assignment-it";

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

    @Autowired TaskAssignmentService assignments;
    @Autowired HumanTaskCommandService humanTasks;
    @Autowired TaskSchedulingService tasks;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                DROP TRIGGER IF EXISTS trg_test_fail_task_assigned ON rel_outbox_event;
                DROP FUNCTION IF EXISTS test_fail_task_assigned();
                TRUNCATE TABLE tsk_task_reassignment_command_result,
                    tsk_task_execution_guard, tsk_task_assignment, tsk_task_assignment_batch,
                    tsk_human_task_command_result, tsk_task_execution_attempt, tsk_task,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record, auth_role_field_policy,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        seedGrant("assignment-admin", Set.of("task.assign"));
        for (String actor : List.of("actor-a", "actor-b", "actor-c")) {
            seedGrant(actor, Set.of("task.claim", "task.release"));
        }
    }

    @Test
    void candidateGateResponsibleExclusivityReleaseAndReclaimAreAtomic() {
        UUID taskId = humanTask();
        CommandMetadata assignmentMetadata = metadata("assign-001");
        AssignTaskCandidatesCommand assignment = new AssignTaskCandidatesCommand(
                taskId, 1, List.of("actor-b", "actor-a", "actor-a"),
                AssignmentSourceType.ASSIGNEE_POLICY, "policy://survey/technicians/v3");

        var assigned = assignments.assignCandidates(principal("assignment-admin", "task.assign"),
                assignmentMetadata, assignment);
        var assignmentReplay = assignments.assignCandidates(
                principal("assignment-admin", "task.assign"), assignmentMetadata, assignment);
        assertThat(assignmentReplay).isEqualTo(assigned);
        assertThat(assigned.candidateCount()).isEqualTo(2);
        assertThat(assigned.taskVersion()).isEqualTo(2);

        assertThatThrownBy(() -> humanTasks.claim(
                principal("actor-c", "task.claim"), metadata("claim-not-candidate"),
                new ClaimHumanTaskCommand(taskId, 2)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.TASK_ASSIGNMENT_CONFLICT));

        var claimedByA = humanTasks.claim(
                principal("actor-a", "task.claim"), metadata("claim-a"),
                new ClaimHumanTaskCommand(taskId, 2));
        var released = humanTasks.release(
                principal("actor-a", "task.release"), metadata("release-a"),
                new ReleaseHumanTaskCommand(taskId, 3, "CUSTOMER_RESCHEDULE"));
        var releaseReplay = humanTasks.release(
                principal("actor-a", "task.release"), metadata("release-a"),
                new ReleaseHumanTaskCommand(taskId, 3, "CUSTOMER_RESCHEDULE"));
        var claimedByB = humanTasks.claim(
                principal("actor-b", "task.claim"), metadata("claim-b"),
                new ClaimHumanTaskCommand(taskId, 4));

        assertThat(claimedByA.version()).isEqualTo(3);
        assertThat(released.status()).isEqualTo("READY");
        assertThat(releaseReplay).isEqualTo(released);
        assertThat(claimedByB.version()).isEqualTo(5);
        assertThat(jdbc.sql("""
                SELECT principal_id FROM tsk_task_assignment
                 WHERE task_id = :taskId AND assignment_kind = 'RESPONSIBLE' AND status = 'ACTIVE'
                """).param("taskId", taskId).query(String.class).single()).isEqualTo("actor-b");
        assertThat(jdbc.sql("""
                SELECT assignment_kind || ':' || status || ':' || count(*)
                  FROM tsk_task_assignment WHERE task_id = :taskId
                 GROUP BY assignment_kind, status ORDER BY assignment_kind, status
                """).param("taskId", taskId).query(String.class).list())
                .containsExactly("CANDIDATE:ACTIVE:2", "RESPONSIBLE:ACTIVE:1", "RESPONSIBLE:REVOKED:1");
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event ORDER BY aggregate_version")
                .query(String.class).list())
                .containsExactly("task.created", "task.assigned", "task.claimed", "task.released", "task.claimed");
        assertThat(count("aud_audit_record")).isEqualTo(4);
        assertThat(count("rel_idempotency_record")).isEqualTo(4);
    }

    @Test
    void replacementRevokesOldSnapshotAndIdempotencyMutationFailsClosed() {
        UUID taskId = humanTask();
        CurrentPrincipal admin = principal("assignment-admin", "task.assign");
        assignments.assignCandidates(admin, metadata("assign-initial"), new AssignTaskCandidatesCommand(
                taskId, 1, List.of("actor-a", "actor-b"),
                AssignmentSourceType.MANUAL, "manual://dispatch/1"));
        assignments.assignCandidates(admin, metadata("assign-replace"), new AssignTaskCandidatesCommand(
                taskId, 2, List.of("actor-c"),
                AssignmentSourceType.MANUAL, "manual://dispatch/2"));

        assertThatThrownBy(() -> assignments.assignCandidates(
                admin, metadata("assign-replace"), new AssignTaskCandidatesCommand(
                        taskId, 3, List.of("actor-a"),
                        AssignmentSourceType.MANUAL, "manual://dispatch/3")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.IDEMPOTENCY_KEY_REUSED));
        assertThatThrownBy(() -> humanTasks.claim(
                principal("actor-a", "task.claim"), metadata("claim-old-candidate"),
                new ClaimHumanTaskCommand(taskId, 3)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.TASK_ASSIGNMENT_CONFLICT));
        assertThat(jdbc.sql("""
                SELECT principal_id || ':' || status FROM tsk_task_assignment
                 WHERE task_id = :taskId AND assignment_kind = 'CANDIDATE'
                 ORDER BY created_at, principal_id
                """).param("taskId", taskId).query(String.class).list())
                .containsExactly("actor-a:REVOKED", "actor-b:REVOKED", "actor-c:ACTIVE");
    }

    @Test
    void assignmentOutboxFailureRollsBackTaskCandidatesAuditAndIdempotency() {
        UUID taskId = humanTask();
        jdbc.sql("""
                CREATE FUNCTION test_fail_task_assigned() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'task.assigned' THEN
                        RAISE EXCEPTION 'injected task.assigned outbox failure';
                    END IF;
                    RETURN NEW;
                END;
                $$;
                CREATE TRIGGER trg_test_fail_task_assigned
                    BEFORE INSERT ON rel_outbox_event
                    FOR EACH ROW EXECUTE FUNCTION test_fail_task_assigned();
                """).update();

        assertThatThrownBy(() -> assignments.assignCandidates(
                principal("assignment-admin", "task.assign"), metadata("assign-rollback"),
                new AssignTaskCandidatesCommand(
                        taskId, 1, List.of("actor-a"),
                        AssignmentSourceType.ASSIGNEE_POLICY, "policy://rollback")))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbc.sql("SELECT version FROM tsk_task WHERE task_id = :taskId")
                .param("taskId", taskId).query(Long.class).single()).isEqualTo(1);
        assertThat(count("tsk_task_assignment")).isZero();
        assertThat(count("tsk_task_assignment_batch")).isZero();
        assertThat(count("aud_audit_record")).isZero();
        assertThat(count("rel_idempotency_record")).isZero();
    }

    private UUID humanTask() {
        return tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                TENANT, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "SITE_SURVEY", UUID.randomUUID(), "a".repeat(64),
                UUID.randomUUID(), "c".repeat(64),
                "SURVEY", "SITE_SURVEY", WorkflowTaskKind.HUMAN, null, null, null, null, null,
                "work-order:assignment-test", "b".repeat(64),
                500, Instant.now(), 1, "corr-task-create", "cause-task-create")).taskId();
    }

    private void seedGrant(String actorId, Set<String> capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, :roleCode, 'M21 测试角色', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT)
                .param("roleCode", "m21-" + actorId).update();
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
                    now() - interval '1 day', 'TEST_FIXTURE', 'M21-TEST', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("actorId", actorId).param("roleId", roleId).update();
    }

    private static CurrentPrincipal principal(String actorId, String... capabilities) {
        return new CurrentPrincipal(
                actorId, TENANT, CurrentPrincipal.PrincipalType.USER,
                "m21-it", Set.of(capabilities));
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
