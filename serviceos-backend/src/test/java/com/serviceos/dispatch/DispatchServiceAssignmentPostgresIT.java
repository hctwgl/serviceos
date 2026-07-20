package com.serviceos.dispatch;

import com.serviceos.ServiceOsApplication;
import com.serviceos.dispatch.api.AbortServiceAssignmentActivationCommand;
import com.serviceos.dispatch.api.ActivateServiceAssignmentCommand;
import com.serviceos.dispatch.api.CapacityAuthorityService;
import com.serviceos.dispatch.api.CompleteServiceAssignmentActivationCommand;
import com.serviceos.dispatch.api.ConfigureCapacityCommand;
import com.serviceos.dispatch.api.ConfirmTaskAssignmentPreparedCommand;
import com.serviceos.dispatch.api.PrepareServiceAssignmentCommand;
import com.serviceos.dispatch.api.ResponsibilityLevel;
import com.serviceos.dispatch.api.ServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ServiceAssignmentService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M24：验证 ServiceAssignment、容量权威与跨模块激活 saga 的本地事务边界。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DispatchServiceAssignmentPostgresIT {
    private static final String TENANT = "tenant-dispatch-assignment-it";
    private static final String MANAGER = "dispatch-manager";
    private static final String BUSINESS_TYPE = "SITE_SURVEY";

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

    @Autowired CapacityAuthorityService capacities;
    @Autowired ServiceAssignmentService assignments;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                DROP TRIGGER IF EXISTS trg_test_fail_service_activation ON rel_outbox_event;
                DROP FUNCTION IF EXISTS test_fail_service_activation();
                TRUNCATE TABLE dsp_assignment_command_result, dsp_capacity_command_result,
                    dsp_service_assignment_activation_saga, dsp_capacity_reservation,
                    dsp_service_assignment, dsp_capacity_counter,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record, auth_role_field_policy,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        seedGrant(Set.of("dispatch.capacity.configure", "dispatch.assignment.manage"));
    }

    @Test
    void activationAndReassignmentSwitchResponsibilityAndCapacityAtomically() {
        configure("technician-a", "capacity-a");
        configure("technician-b", "capacity-b");
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        ActivatedAssignment initial = activateInitial(workOrderId, taskId, "technician-a", "initial");
        var initialCompleted = assignments.complete(manager(), metadata("initial-complete"),
                new CompleteServiceAssignmentActivationCommand(
                        initial.receipt().sagaId(), initial.receipt().serviceAssignmentId(),
                        initial.preparedTaskAssignmentId(), 3));
        assertThat(initialCompleted.sagaStage()).isEqualTo("COMPLETED");

        UUID sagaId = UUID.randomUUID();
        PrepareServiceAssignmentCommand prepareCommand = new PrepareServiceAssignmentCommand(
                sagaId, workOrderId, taskId, ResponsibilityLevel.TECHNICIAN,
                "technician-b", BUSINESS_TYPE, "decision://reassign-b",
                initial.receipt().serviceAssignmentId(), "MANUAL_REASSIGNMENT", 1);
        ServiceAssignmentReceipt pending = assignments.prepare(
                manager(), metadata("reassign-prepare"), prepareCommand);
        assertThat(assignments.prepare(manager(), metadata("reassign-prepare"), prepareCommand))
                .isEqualTo(pending);
        UUID guardId = UUID.randomUUID();
        UUID preparedId = UUID.randomUUID();
        assignments.confirmTaskPrepared(manager(), metadata("reassign-task-prepared"),
                new ConfirmTaskAssignmentPreparedCommand(
                        sagaId, pending.serviceAssignmentId(), taskId, guardId, preparedId, 1));
        ActivateServiceAssignmentCommand activateCommand = new ActivateServiceAssignmentCommand(
                sagaId, pending.serviceAssignmentId(), 2,
                "authority://technician-b", 7, "fence://decision-7", "policy-2026-07");
        ServiceAssignmentReceipt activated = assignments.activate(
                manager(), metadata("reassign-activate"), activateCommand);
        assertThat(assignments.activate(manager(), metadata("reassign-activate"), activateCommand))
                .isEqualTo(activated);

        assertThat(activated.assignmentStatus()).isEqualTo("ACTIVE");
        assertThat(activated.sagaStage()).isEqualTo("SERVICE_SWITCHED");
        assertThat(jdbc.sql("""
                SELECT assignee_id || ':' || status FROM dsp_service_assignment
                 WHERE task_id = :taskId ORDER BY created_at
                """).param("taskId", taskId).query(String.class).list())
                .containsExactly("technician-a:ENDED", "technician-b:ACTIVE");
        assertThat(jdbc.sql("""
                SELECT c.assignee_id || ':' || c.occupied_units || ':' || r.status
                  FROM dsp_capacity_counter c
                  JOIN dsp_capacity_reservation r ON r.capacity_counter_id = c.capacity_counter_id
                 ORDER BY c.assignee_id
                """).query(String.class).list())
                .containsExactly("technician-a:0:RELEASED", "technician-b:1:CONFIRMED");
        assertThat(jdbc.sql("""
                SELECT authority_assignment_id || ':' || authority_version || ':'
                    || fence_decision_id || ':' || fence_policy_version
                  FROM dsp_service_assignment WHERE service_assignment_id = :assignmentId
                """).param("assignmentId", pending.serviceAssignmentId()).query(String.class).single())
                .isEqualTo("authority://technician-b:7:fence://decision-7:policy-2026-07");

        ServiceAssignmentReceipt completed = assignments.complete(manager(), metadata("reassign-complete"),
                new CompleteServiceAssignmentActivationCommand(
                        sagaId, pending.serviceAssignmentId(), preparedId, 3));
        assertThat(completed.sagaStage()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event")
                .query(String.class).list())
                .contains("dispatch.capacity-configured",
                        "service.assignment.pending-activation",
                        "service.assignment.task-prepared",
                        "service.assignment.activated",
                        "service.assignment.activation-completed");
    }

    @Test
    void capacityLimitAndAbortPreserveExistingResponsibility() {
        configure("technician-a", "capacity-a");
        configure("technician-b", "capacity-b");
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ActivatedAssignment initial = activateInitial(workOrderId, taskId, "technician-a", "initial");

        ServiceAssignmentReceipt pending = assignments.prepare(manager(), metadata("abort-prepare"),
                new PrepareServiceAssignmentCommand(
                        UUID.randomUUID(), workOrderId, taskId, ResponsibilityLevel.TECHNICIAN,
                        "technician-b", BUSINESS_TYPE, "decision://abort",
                        initial.receipt().serviceAssignmentId(), "POLICY_REJECTED", 1));
        UUID preparedId = UUID.randomUUID();
        assignments.confirmTaskPrepared(manager(), metadata("abort-task-prepared"),
                new ConfirmTaskAssignmentPreparedCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), taskId,
                        UUID.randomUUID(), preparedId, 1));

        assertThatThrownBy(() -> assignments.prepare(manager(), metadata("capacity-exhausted"),
                new PrepareServiceAssignmentCommand(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        ResponsibilityLevel.TECHNICIAN, "technician-b", BUSINESS_TYPE,
                        "decision://capacity-exhausted", null, null, 2)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.DISPATCH_CAPACITY_CONFLICT));

        AbortServiceAssignmentActivationCommand abortCommand =
                new AbortServiceAssignmentActivationCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), 2, "POLICY_REJECTED");
        ServiceAssignmentReceipt aborted = assignments.abort(
                manager(), metadata("abort"), abortCommand);
        assertThat(assignments.abort(manager(), metadata("abort"), abortCommand)).isEqualTo(aborted);
        assertThat(aborted.assignmentStatus()).isEqualTo("FAILED_ACTIVATION");
        assertThat(jdbc.sql("""
                SELECT assignee_id || ':' || status FROM dsp_service_assignment
                 WHERE task_id = :taskId ORDER BY created_at
                """).param("taskId", taskId).query(String.class).list())
                .containsExactly("technician-a:ACTIVE", "technician-b:FAILED_ACTIVATION");
        assertThat(jdbc.sql("""
                SELECT occupied_units FROM dsp_capacity_counter
                 WHERE assignee_id = 'technician-b'
                """).query(Integer.class).single()).isZero();
    }

    @Test
    void activationOutboxFailureRollsBackAssignmentCapacityAndSagaForForwardRetry() {
        configure("technician-a", "capacity-a");
        configure("technician-b", "capacity-b");
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ActivatedAssignment initial = activateInitial(workOrderId, taskId, "technician-a", "initial");
        ServiceAssignmentReceipt pending = assignments.prepare(manager(), metadata("rollback-prepare"),
                new PrepareServiceAssignmentCommand(
                        UUID.randomUUID(), workOrderId, taskId, ResponsibilityLevel.TECHNICIAN,
                        "technician-b", BUSINESS_TYPE, "decision://rollback",
                        initial.receipt().serviceAssignmentId(), "MANUAL_REASSIGNMENT", 1));
        assignments.confirmTaskPrepared(manager(), metadata("rollback-task-prepared"),
                new ConfirmTaskAssignmentPreparedCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), taskId,
                        UUID.randomUUID(), UUID.randomUUID(), 1));
        jdbc.sql("""
                CREATE FUNCTION test_fail_service_activation() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'service.assignment.activated' THEN
                        RAISE EXCEPTION 'injected service activation outbox failure';
                    END IF;
                    RETURN NEW;
                END;
                $$;
                CREATE TRIGGER trg_test_fail_service_activation
                    BEFORE INSERT ON rel_outbox_event
                    FOR EACH ROW EXECUTE FUNCTION test_fail_service_activation();
                """).update();

        assertThatThrownBy(() -> assignments.activate(manager(), metadata("rollback-activate"),
                new ActivateServiceAssignmentCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), 2,
                        "authority://b", 1, "fence://b", "policy-1")))
                // ADR-091：jOOQ 路径下 PL/pgSQL RAISE（SQLState P0001）不在 Spring 错误码映射内，
                // 以 jOOQ 原生 DataAccessException 上浮并触发同事务回滚（等价原 UncategorizedSQLException）。
                .isInstanceOf(org.jooq.exception.DataAccessException.class);

        assertThat(jdbc.sql("""
                SELECT assignee_id || ':' || status FROM dsp_service_assignment
                 WHERE task_id = :taskId ORDER BY created_at
                """).param("taskId", taskId).query(String.class).list())
                .containsExactly("technician-a:ACTIVE", "technician-b:PENDING_ACTIVATION");
        assertThat(jdbc.sql("""
                SELECT c.assignee_id || ':' || c.occupied_units || ':' || r.status
                  FROM dsp_capacity_counter c
                  JOIN dsp_capacity_reservation r ON r.capacity_counter_id = c.capacity_counter_id
                 ORDER BY c.assignee_id
                """).query(String.class).list())
                .containsExactly("technician-a:1:CONFIRMED", "technician-b:1:HELD");
        assertThat(jdbc.sql("""
                SELECT stage || ':' || version FROM dsp_service_assignment_activation_saga
                 WHERE activation_saga_id = :sagaId
                """).param("sagaId", pending.sagaId()).query(String.class).single())
                .isEqualTo("TASK_PREPARED:2");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM dsp_assignment_command_result
                 WHERE operation_type = 'dispatch.assignment.activate'
                   AND idempotency_key = 'rollback-activate'
                """).query(Long.class).single()).isZero();
    }

    private ActivatedAssignment activateInitial(
            UUID workOrderId, UUID taskId, String assigneeId, String key) {
        ServiceAssignmentReceipt pending = assignments.prepare(manager(), metadata(key + "-prepare"),
                new PrepareServiceAssignmentCommand(
                        UUID.randomUUID(), workOrderId, taskId, ResponsibilityLevel.TECHNICIAN,
                        assigneeId, BUSINESS_TYPE, "decision://" + key, null, null, 1));
        UUID preparedId = UUID.randomUUID();
        assignments.confirmTaskPrepared(manager(), metadata(key + "-task-prepared"),
                new ConfirmTaskAssignmentPreparedCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), taskId,
                        UUID.randomUUID(), preparedId, 1));
        ServiceAssignmentReceipt activated = assignments.activate(manager(), metadata(key + "-activate"),
                new ActivateServiceAssignmentCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), 2,
                        "authority://" + assigneeId, 1, "fence://" + assigneeId, "policy-1"));
        return new ActivatedAssignment(activated, preparedId);
    }

    private void configure(String assigneeId, String key) {
        ConfigureCapacityCommand command = new ConfigureCapacityCommand(
                ResponsibilityLevel.TECHNICIAN, assigneeId, BUSINESS_TYPE, 1, 0);
        var configured = capacities.configure(manager(), metadata(key), command);
        assertThat(capacities.configure(manager(), metadata(key), command)).isEqualTo(configured);
    }

    private void seedGrant(Set<String> capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, 'm24-dispatch-manager', 'M24 测试角色', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT).update();
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
                    now() - interval '1 day', 'TEST_FIXTURE', 'M24-TEST', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("actorId", MANAGER).param("roleId", roleId).update();
    }

    private static CurrentPrincipal manager() {
        return new CurrentPrincipal(MANAGER, TENANT, CurrentPrincipal.PrincipalType.USER,
                "m24-it", Set.of("dispatch.capacity.configure", "dispatch.assignment.manage"));
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }

    private record ActivatedAssignment(
            ServiceAssignmentReceipt receipt,
            UUID preparedTaskAssignmentId
    ) {
    }
}
