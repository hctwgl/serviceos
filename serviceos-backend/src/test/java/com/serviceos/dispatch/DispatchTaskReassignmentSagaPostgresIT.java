package com.serviceos.dispatch;

import com.serviceos.ServiceOsApplication;
import com.serviceos.dispatch.api.ActivateServiceAssignmentCommand;
import com.serviceos.dispatch.api.AbortServiceAssignmentActivationCommand;
import com.serviceos.dispatch.api.CapacityAuthorityService;
import com.serviceos.dispatch.api.CompleteServiceAssignmentActivationCommand;
import com.serviceos.dispatch.api.ConfigureCapacityCommand;
import com.serviceos.dispatch.api.ConfirmTaskAssignmentPreparedCommand;
import com.serviceos.dispatch.api.PrepareServiceAssignmentCommand;
import com.serviceos.dispatch.api.ResponsibilityLevel;
import com.serviceos.dispatch.api.ServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ServiceAssignmentService;
import com.serviceos.dispatch.api.ServiceAssignmentTimeoutScanner;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.application.OutboxWorker;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.task.api.AssignTaskCandidatesCommand;
import com.serviceos.task.api.AssignmentSourceType;
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.TaskAssignmentService;
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

/** M25/M26：验证师傅改派的正常推进、持久检查点与切换前可靠终止。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DispatchTaskReassignmentSagaPostgresIT {
    private static final String TENANT = "tenant-dispatch-task-saga-it";
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
    @Autowired ServiceAssignmentService serviceAssignments;
    @Autowired TaskSchedulingService tasks;
    @Autowired TaskAssignmentService taskAssignments;
    @Autowired HumanTaskCommandService humanTasks;
    @Autowired OutboxWorker outboxWorker;
    @Autowired ServiceAssignmentTimeoutScanner timeoutScanner;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                DROP TRIGGER IF EXISTS trg_test_fail_m25_activation ON rel_outbox_event;
                DROP FUNCTION IF EXISTS test_fail_m25_activation();
                DROP TRIGGER IF EXISTS trg_test_fail_m26_task_abort ON rel_outbox_event;
                DROP FUNCTION IF EXISTS test_fail_m26_task_abort();
                DROP TRIGGER IF EXISTS trg_test_fail_m27_timeout ON rel_outbox_event;
                DROP FUNCTION IF EXISTS test_fail_m27_timeout();
                TRUNCATE TABLE dsp_assignment_command_result, dsp_capacity_command_result,
                    dsp_service_assignment_saga_timeout, dsp_service_assignment_activation_saga,
                    dsp_capacity_reservation,
                    dsp_service_assignment, dsp_capacity_counter,
                    ops_operational_exception,
                    tsk_task_reassignment_command_result, tsk_task_execution_guard,
                    tsk_task_assignment, tsk_task_assignment_batch,
                    tsk_human_task_command_result, tsk_task_execution_attempt, tsk_task,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_inbox_record, rel_idempotency_record,
                    auth_role_field_policy, auth_role_grant,
                    auth_role_capability, auth_role CASCADE
                """).update();
        seedGrant(MANAGER, Set.of(
                "dispatch.capacity.configure", "dispatch.assignment.manage",
                "task.assign", "task.reassignment.manage"));
        seedGrant("technician-a", Set.of("task.claim"));
    }

    @Test
    void reliableInboxChainCompletesReassignmentAndAlignsBothAuthorities() {
        Baseline baseline = baseline("normal");
        ServiceAssignmentReceipt pending = prepareReliableReassignment(baseline, "normal");

        drainOutbox();

        assertCompletedAlignment(baseline, pending);
        assertThat(jdbc.sql("""
                SELECT consumer_name || ':' || status FROM rel_inbox_record
                 WHERE consumer_name LIKE 'task.service-assignment-%'
                    OR consumer_name LIKE 'dispatch.service-assignment-%'
                    OR consumer_name LIKE 'dispatch.task-assignment-%'
                 ORDER BY consumer_name
                """).query(String.class).list())
                .containsExactly(
                        "dispatch.service-assignment-task-prepared.v2:SUCCEEDED",
                        "dispatch.task-assignment-activated.v1:SUCCEEDED",
                        "dispatch.task-assignment-prepared.v1:SUCCEEDED",
                        "task.service-assignment-activated.v2:SUCCEEDED",
                        "task.service-assignment-pending.v2:SUCCEEDED");
    }

    @Test
    void dispatchActivationFailureRollsBackInboxThenRetriesForwardToCompletion() {
        Baseline baseline = baseline("retry");
        ServiceAssignmentReceipt pending = prepareReliableReassignment(baseline, "retry");
        jdbc.sql("""
                CREATE FUNCTION test_fail_m25_activation() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'service.assignment.activated' AND NEW.schema_version = 2 THEN
                        RAISE EXCEPTION 'injected M25 activation failure';
                    END IF;
                    RETURN NEW;
                END;
                $$;
                CREATE TRIGGER trg_test_fail_m25_activation
                    BEFORE INSERT ON rel_outbox_event
                    FOR EACH ROW EXECUTE FUNCTION test_fail_m25_activation();
                """).update();

        drainUntilFailure();

        assertThat(jdbc.sql("""
                SELECT stage || ':' || version FROM dsp_service_assignment_activation_saga
                 WHERE activation_saga_id = :sagaId
                """).param("sagaId", pending.sagaId()).query(String.class).single())
                .isEqualTo("TASK_PREPARED:2");
        assertThat(jdbc.sql("""
                SELECT status FROM dsp_service_assignment
                 WHERE service_assignment_id = :assignmentId
                """).param("assignmentId", pending.serviceAssignmentId()).query(String.class).single())
                .isEqualTo("PENDING_ACTIVATION");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_inbox_record
                 WHERE consumer_name = 'dispatch.task-assignment-prepared.v1'
                """).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_inbox_record
                 WHERE consumer_name = 'dispatch.service-assignment-task-prepared.v2'
                """).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                SELECT status FROM tsk_task_assignment
                 WHERE source_id = :assignmentId AND assignment_kind = 'RESPONSIBLE'
                """).param("assignmentId", pending.serviceAssignmentId().toString())
                .query(String.class).single()).isEqualTo("PREPARED");

        jdbc.sql("""
                DROP TRIGGER trg_test_fail_m25_activation ON rel_outbox_event;
                DROP FUNCTION test_fail_m25_activation();
                UPDATE rel_outbox_event
                 SET status = 'PENDING', available_at = now() - interval '1 second',
                       claim_owner = NULL, claim_until = NULL
                 WHERE event_type = 'service.assignment.task-prepared' AND status = 'FAILED'
                """).update();
        drainOutbox();

        assertCompletedAlignment(baseline, pending);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_outbox_publish_attempt
                 WHERE result_code = 'FAILED'
                """).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void revokedInitiatorAuthorizationFailsClosedBeforeTaskPreparation() {
        Baseline baseline = baseline("revoked");
        ServiceAssignmentReceipt pending = prepareReliableReassignment(baseline, "revoked");
        jdbc.sql("""
                UPDATE auth_role_grant SET valid_to = now() - interval '1 second'
                 WHERE tenant_id = :tenantId AND principal_id = :principalId
                """).param("tenantId", TENANT).param("principalId", MANAGER).update();

        drainUntilFailure();

        assertThat(jdbc.sql("""
                SELECT stage || ':' || version FROM dsp_service_assignment_activation_saga
                 WHERE activation_saga_id = :sagaId
                """).param("sagaId", pending.sagaId()).query(String.class).single())
                .isEqualTo("PENDING:1");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM tsk_task_execution_guard
                 WHERE guard_key = :sagaId
                """).param("sagaId", pending.sagaId().toString()).query(Long.class).single())
                .isZero();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_inbox_record
                 WHERE consumer_name = 'task.service-assignment-pending.v2'
                """).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                SELECT status FROM dsp_capacity_reservation
                 WHERE service_assignment_id = :assignmentId
                """).param("assignmentId", pending.serviceAssignmentId())
                .query(String.class).single()).isEqualTo("HELD");
    }

    @Test
    void preSwitchAbortWithdrawsPreparedTaskAndCompletesSaga() {
        Baseline baseline = baseline("abort");
        ServiceAssignmentReceipt pending = prepareReliableReassignment(baseline, "abort");
        advanceToTaskPrepared(pending);

        serviceAssignments.abort(
                manager(), metadata("abort-b"),
                new AbortServiceAssignmentActivationCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), 2, "ACTIVATION_TIMEOUT"));
        drainOutbox();

        assertAbortedAlignment(baseline, pending);
        assertThat(jdbc.sql("""
                SELECT consumer_name || ':' || status FROM rel_inbox_record
                 WHERE consumer_name IN (
                    'dispatch.service-assignment-task-prepared.v2',
                    'task.service-assignment-aborted.v2',
                    'dispatch.task-assignment-aborted.v1')
                 ORDER BY consumer_name
                """).query(String.class).list())
                .containsExactly(
                        "dispatch.service-assignment-task-prepared.v2:SUCCEEDED",
                        "dispatch.task-assignment-aborted.v1:SUCCEEDED",
                        "task.service-assignment-aborted.v2:SUCCEEDED");
    }

    @Test
    void taskAbortOutboxFailureRollsBackThenRetriesToCompletion() {
        Baseline baseline = baseline("abort-retry");
        ServiceAssignmentReceipt pending = prepareReliableReassignment(baseline, "abort-retry");
        advanceToTaskPrepared(pending);
        serviceAssignments.abort(
                manager(), metadata("abort-retry-b"),
                new AbortServiceAssignmentActivationCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), 2, "ACTIVATION_TIMEOUT"));
        jdbc.sql("""
                CREATE FUNCTION test_fail_m26_task_abort() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'task.assignment-aborted' THEN
                        RAISE EXCEPTION 'injected M26 Task abort Outbox failure';
                    END IF;
                    RETURN NEW;
                END;
                $$;
                CREATE TRIGGER trg_test_fail_m26_task_abort
                    BEFORE INSERT ON rel_outbox_event
                    FOR EACH ROW EXECUTE FUNCTION test_fail_m26_task_abort();
                """).update();

        drainUntilFailure();

        assertThat(jdbc.sql("""
                SELECT stage || ':' || version FROM dsp_service_assignment_activation_saga
                 WHERE activation_saga_id = :sagaId
                """).param("sagaId", pending.sagaId()).query(String.class).single())
                .isEqualTo("ABORTING:3");
        assertThat(jdbc.sql("""
                SELECT status FROM tsk_task_assignment
                 WHERE source_id = :assignmentId AND assignment_kind = 'RESPONSIBLE'
                """).param("assignmentId", pending.serviceAssignmentId().toString())
                .query(String.class).single()).isEqualTo("PREPARED");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_inbox_record
                 WHERE consumer_name = 'task.service-assignment-aborted.v2'
                """).query(Long.class).single()).isZero();

        jdbc.sql("""
                DROP TRIGGER trg_test_fail_m26_task_abort ON rel_outbox_event;
                DROP FUNCTION test_fail_m26_task_abort();
                """).update();
        int reset = jdbc.sql("""
                UPDATE rel_outbox_event
                   SET status = 'PENDING', available_at = now() - interval '1 second',
                       claim_owner = NULL, claim_until = NULL
                 WHERE event_type = 'service.assignment.activation-aborted' AND status = 'FAILED'
                """).update();
        assertThat(reset).isEqualTo(1);
        drainOutbox();

        assertThat(jdbc.sql("""
                SELECT consumer_name || ':' || status FROM rel_inbox_record
                 WHERE consumer_name IN (
                    'task.service-assignment-aborted.v2',
                    'dispatch.task-assignment-aborted.v1')
                 ORDER BY consumer_name
                """).query(String.class).list())
                .containsExactly(
                        "dispatch.task-assignment-aborted.v1:SUCCEEDED",
                        "task.service-assignment-aborted.v2:SUCCEEDED");

        assertAbortedAlignment(baseline, pending);
    }

    @Test
    void revokedInitiatorAuthorizationBlocksTaskAbortAndKeepsGuard() {
        Baseline baseline = baseline("abort-revoked");
        ServiceAssignmentReceipt pending = prepareReliableReassignment(baseline, "abort-revoked");
        advanceToTaskPrepared(pending);
        serviceAssignments.abort(
                manager(), metadata("abort-revoked-b"),
                new AbortServiceAssignmentActivationCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), 2, "ACTIVATION_TIMEOUT"));
        jdbc.sql("""
                UPDATE auth_role_grant SET valid_to = now() - interval '1 second'
                 WHERE tenant_id = :tenantId AND principal_id = :principalId
                """).param("tenantId", TENANT).param("principalId", MANAGER).update();

        drainUntilFailure();

        assertThat(jdbc.sql("""
                SELECT stage || ':' || version FROM dsp_service_assignment_activation_saga
                 WHERE activation_saga_id = :sagaId
                """).param("sagaId", pending.sagaId()).query(String.class).single())
                .isEqualTo("ABORTING:3");
        assertThat(jdbc.sql("""
                SELECT status FROM tsk_task_assignment
                 WHERE source_id = :assignmentId AND assignment_kind = 'RESPONSIBLE'
                """).param("assignmentId", pending.serviceAssignmentId().toString())
                .query(String.class).single()).isEqualTo("PREPARED");
        assertThat(jdbc.sql("""
                SELECT status FROM tsk_task_execution_guard WHERE guard_key = :sagaId
                """).param("sagaId", pending.sagaId().toString())
                .query(String.class).single()).isEqualTo("ACTIVE");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_inbox_record
                 WHERE consumer_name = 'task.service-assignment-aborted.v2'
                """).query(Long.class).single()).isZero();
    }

    @Test
    void expiredPreparedStageOpensOneOperationalExceptionAndKeepsTaskGuarded() {
        Baseline baseline = baseline("timeout");
        ServiceAssignmentReceipt pending = prepareReliableReassignment(baseline, "timeout");
        advanceToTaskPrepared(pending);
        stallPreparedCheckpointAndExpire(pending);

        assertThat(timeoutScanner.detectNextTimeout()).isTrue();
        assertThat(timeoutScanner.detectNextTimeout()).isFalse();

        assertThat(jdbc.sql("""
                SELECT stage || ':' || version || ':' || last_error_code
                  FROM dsp_service_assignment_activation_saga
                 WHERE activation_saga_id = :sagaId
                """).param("sagaId", pending.sagaId()).query(String.class).single())
                .isEqualTo("TASK_PREPARED:2:ACTIVATION_SAGA_TIMEOUT");
        assertThat(jdbc.sql("SELECT count(*) FROM dsp_service_assignment_saga_timeout")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT status FROM tsk_task_execution_guard WHERE guard_key = :sagaId
                """).param("sagaId", pending.sagaId().toString())
                .query(String.class).single()).isEqualTo("ACTIVE");
        assertThat(jdbc.sql("""
                SELECT status FROM dsp_capacity_reservation
                 WHERE service_assignment_id = :assignmentId
                """).param("assignmentId", pending.serviceAssignmentId())
                .query(String.class).single()).isEqualTo("HELD");

        publishUntilOperationalException();
        assertThat(jdbc.sql("""
                SELECT category_code || ':' || severity_code || ':' || status || ':' || occurrence_count
                  FROM ops_operational_exception
                 WHERE source_id = :sagaId
                """).param("sagaId", pending.sagaId().toString())
                .query(String.class).single()).isEqualTo("DISPATCH:P1:OPEN:1");
        assertThat(jdbc.sql("""
                SELECT task_kind || ':' || status || ':' || task_type
                  FROM tsk_task
                 WHERE task_type = 'operations.resolve-dispatch-timeout'
                """).query(String.class).single())
                .isEqualTo("HUMAN:READY:operations.resolve-dispatch-timeout");
        assertThat(jdbc.sql("""
                SELECT consumer_name || ':' || status FROM rel_inbox_record
                 WHERE consumer_name = 'operations.service-assignment-timeout.v1'
                """).query(String.class).single())
                .isEqualTo("operations.service-assignment-timeout.v1:SUCCEEDED");
    }

    @Test
    void timeoutOutboxFailureRollsBackOccurrenceAndCanBeRetried() {
        Baseline baseline = baseline("timeout-rollback");
        ServiceAssignmentReceipt pending = prepareReliableReassignment(baseline, "timeout-rollback");
        advanceToTaskPrepared(pending);
        stallPreparedCheckpointAndExpire(pending);
        jdbc.sql("""
                CREATE FUNCTION test_fail_m27_timeout() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'service.assignment.activation-timed-out' THEN
                        RAISE EXCEPTION 'injected M27 timeout Outbox failure';
                    END IF;
                    RETURN NEW;
                END;
                $$;
                CREATE TRIGGER trg_test_fail_m27_timeout
                    BEFORE INSERT ON rel_outbox_event
                    FOR EACH ROW EXECUTE FUNCTION test_fail_m27_timeout();
                """).update();

        assertThatThrownBy(timeoutScanner::detectNextTimeout)
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM dsp_service_assignment_saga_timeout")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                SELECT last_error_code FROM dsp_service_assignment_activation_saga
                 WHERE activation_saga_id = :sagaId
                """).param("sagaId", pending.sagaId()).query(String.class).optional())
                .isEmpty();

        jdbc.sql("""
                DROP TRIGGER trg_test_fail_m27_timeout ON rel_outbox_event;
                DROP FUNCTION test_fail_m27_timeout();
                """).update();
        assertThat(timeoutScanner.detectNextTimeout()).isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM dsp_service_assignment_saga_timeout")
                .query(Long.class).single()).isEqualTo(1);
    }

    private Baseline baseline(String key) {
        configure("technician-a", "capacity-a-" + key);
        configure("technician-b", "capacity-b-" + key);
        EvidenceContext evidence = seedEvidenceContext(key);
        UUID workOrderId = UUID.randomUUID();
        seedWorkOrder(evidence, workOrderId, key);
        UUID taskId = tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                TENANT, evidence.projectId(), workOrderId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "SITE_SURVEY", UUID.randomUUID(), "a".repeat(64),
                evidence.bundleId(), "c".repeat(64),
                "SURVEY", BUSINESS_TYPE, WorkflowTaskKind.HUMAN, null, null, null,
                "work-order:m25-" + key, "b".repeat(64),
                500, Instant.now(), 1, "corr-task-" + key, "cause-task-" + key)).taskId();
        taskAssignments.assignCandidates(
                manager(), metadata("assign-a-" + key),
                new AssignTaskCandidatesCommand(taskId, 1, List.of("technician-a"),
                        AssignmentSourceType.MANUAL, "manual://m25-initial"));
        humanTasks.claim(
                principal("technician-a", "task.claim"), metadata("claim-a-" + key),
                new ClaimHumanTaskCommand(taskId, 2));

        ServiceAssignmentReceipt pending = serviceAssignments.prepare(
                manager(), metadata("service-a-prepare-" + key),
                new PrepareServiceAssignmentCommand(
                        UUID.randomUUID(), workOrderId, taskId, ResponsibilityLevel.TECHNICIAN,
                        "technician-a", BUSINESS_TYPE, "decision://initial-a-" + key,
                        null, null, 1));
        UUID preparedId = UUID.randomUUID();
        serviceAssignments.confirmTaskPrepared(
                manager(), metadata("service-a-confirm-" + key),
                new ConfirmTaskAssignmentPreparedCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), taskId,
                        UUID.randomUUID(), preparedId, 1));
        serviceAssignments.activate(
                manager(), metadata("service-a-activate-" + key),
                new ActivateServiceAssignmentCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), 2,
                        "authority://a", 1, "fence://a", "policy-1"));
        serviceAssignments.complete(
                manager(), metadata("service-a-complete-" + key),
                new CompleteServiceAssignmentActivationCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), preparedId, 3));
        drainOutbox();
        return new Baseline(workOrderId, taskId, pending.serviceAssignmentId());
    }

    private EvidenceContext seedEvidenceContext(String key) {
        UUID projectId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at)
                VALUES (:projectId, :tenantId, :projectCode, 'client-m25', 'M25 测试项目',
                    current_date, 'ACTIVE', 1, now())
                """).param("projectId", projectId).param("tenantId", TENANT)
                .param("projectCode", "M25-" + key + "-" + projectId).update();
        jdbc.sql("""
                INSERT INTO cfg_configuration_bundle (
                    bundle_id, tenant_id, project_id, bundle_code, bundle_version,
                    brand_code, service_product_code, effective_from,
                    manifest_digest, status, published_at)
                VALUES (:bundleId, :tenantId, :projectId, :bundleCode, '1.0.0',
                    'TEST', 'SITE_SURVEY', now() - interval '1 day',
                    :digest, 'PUBLISHED', now())
                """).param("bundleId", bundleId).param("tenantId", TENANT)
                .param("projectId", projectId).param("bundleCode", "M25-" + key + "-" + bundleId)
                .param("digest", "c".repeat(64)).update();
        return new EvidenceContext(projectId, bundleId);
    }

    /**
     * 时间线消费者会校验 Task 引用的权威工单范围。测试任务属于真实工作流任务，夹具必须同时建立工单，
     * 不能依赖一个悬空 workOrderId 来绕过跨聚合身份不变量。
     */
    private void seedWorkOrder(EvidenceContext evidence, UUID workOrderId, String key) {
        jdbc.sql("""
                INSERT INTO wo_work_order (
                    id, tenant_id, project_id, client_code, brand_code, service_product_code,
                    external_order_code, payload_digest, status, configuration_bundle_id,
                    configuration_bundle_code, configuration_bundle_version,
                    configuration_bundle_digest, province_code, city_code, district_code,
                    customer_name, customer_mobile, service_address, vehicle_vin,
                    external_dispatched_at, received_at, activated_at, version)
                VALUES (
                    :id, :tenantId, :projectId, 'TEST', 'TEST', 'SITE_SURVEY',
                    :externalOrderCode, :payloadDigest, 'ACTIVE', :bundleId,
                    :bundleCode, '1.0.0', :bundleDigest, '370000', '370100', '370102',
                    'M25 测试用户', '13800000000', 'M25 测试地址', 'LSLM25TEST12345678',
                    now(), now(), now(), 1)
                """)
                .param("id", workOrderId).param("tenantId", TENANT)
                .param("projectId", evidence.projectId()).param("bundleId", evidence.bundleId())
                .param("externalOrderCode", "M25-" + key + "-" + workOrderId)
                .param("payloadDigest", "b".repeat(64))
                .param("bundleCode", "M25-" + key + "-" + evidence.bundleId())
                .param("bundleDigest", "c".repeat(64)).update();
    }

    private ServiceAssignmentReceipt prepareReliableReassignment(Baseline baseline, String key) {
        return serviceAssignments.prepare(
                manager(), metadata("service-b-prepare-" + key),
                new PrepareServiceAssignmentCommand(
                        UUID.randomUUID(), baseline.workOrderId(), baseline.taskId(),
                        ResponsibilityLevel.TECHNICIAN, "technician-b", BUSINESS_TYPE,
                        "decision://reassign-b-" + key, baseline.serviceAssignmentId(),
                        "MANUAL_REASSIGNMENT", 1,
                        "authority://b-" + key, 7,
                        "fence://b-" + key, "policy-2026-07"));
    }

    private void advanceToTaskPrepared(ServiceAssignmentReceipt pending) {
        for (int iteration = 0; iteration < 20; iteration++) {
            String state = jdbc.sql("""
                    SELECT stage || ':' || version FROM dsp_service_assignment_activation_saga
                     WHERE activation_saga_id = :sagaId
                    """).param("sagaId", pending.sagaId()).query(String.class).single();
            if ("TASK_PREPARED:2".equals(state)) return;
            assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        }
        throw new AssertionError("M26 saga did not reach TASK_PREPARED checkpoint");
    }

    private void stallPreparedCheckpointAndExpire(ServiceAssignmentReceipt pending) {
        int stalled = jdbc.sql("""
                UPDATE rel_outbox_event SET status = 'PUBLISHED', published_at = now()
                 WHERE tenant_id = :tenantId
                   AND event_type = 'service.assignment.task-prepared'
                   AND aggregate_id = :assignmentId AND status = 'PENDING'
                """).param("tenantId", TENANT)
                .param("assignmentId", pending.serviceAssignmentId().toString()).update();
        assertThat(stalled).isEqualTo(1);
        jdbc.sql("""
                UPDATE dsp_service_assignment_activation_saga
                   SET deadline_at = now() - interval '1 second'
                 WHERE activation_saga_id = :sagaId
                """).param("sagaId", pending.sagaId()).update();
    }

    private void publishUntilOperationalException() {
        for (int iteration = 0; iteration < 20; iteration++) {
            if (jdbc.sql("SELECT count(*) FROM ops_operational_exception")
                    .query(Long.class).single() == 1) return;
            assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        }
        throw new AssertionError("M27 timeout event did not open an OperationalException");
    }

    private void assertCompletedAlignment(Baseline baseline, ServiceAssignmentReceipt pending) {
        assertThat(jdbc.sql("""
                SELECT stage || ':' || version FROM dsp_service_assignment_activation_saga
                 WHERE activation_saga_id = :sagaId
                """).param("sagaId", pending.sagaId()).query(String.class).single())
                .isEqualTo("COMPLETED:4");
        assertThat(jdbc.sql("""
                SELECT assignee_id || ':' || status FROM dsp_service_assignment
                 WHERE task_id = :taskId ORDER BY created_at
                """).param("taskId", baseline.taskId()).query(String.class).list())
                .containsExactly("technician-a:ENDED", "technician-b:ACTIVE");
        assertThat(jdbc.sql("SELECT claimed_by FROM tsk_task WHERE task_id = :taskId")
                .param("taskId", baseline.taskId()).query(String.class).single())
                .isEqualTo("technician-b");
        assertThat(jdbc.sql("""
                SELECT principal_id || ':' || status FROM tsk_task_assignment
                 WHERE task_id = :taskId AND assignment_kind = 'RESPONSIBLE'
                 ORDER BY created_at
                """).param("taskId", baseline.taskId()).query(String.class).list())
                .containsExactly("technician-a:REVOKED", "technician-b:ACTIVE");
        assertThat(jdbc.sql("""
                SELECT status FROM tsk_task_execution_guard
                 WHERE guard_key = :sagaId
                """).param("sagaId", pending.sagaId().toString()).query(String.class).single())
                .isEqualTo("RELEASED");
    }

    private void assertAbortedAlignment(Baseline baseline, ServiceAssignmentReceipt pending) {
        assertThat(jdbc.sql("""
                SELECT stage || ':' || version || ':' || (completed_at IS NOT NULL)
                  FROM dsp_service_assignment_activation_saga
                 WHERE activation_saga_id = :sagaId
                """).param("sagaId", pending.sagaId()).query(String.class).single())
                .isEqualTo("ABORTED:4:true");
        assertThat(jdbc.sql("""
                SELECT assignee_id || ':' || status FROM dsp_service_assignment
                 WHERE task_id = :taskId ORDER BY created_at
                """).param("taskId", baseline.taskId()).query(String.class).list())
                .containsExactly("technician-a:ACTIVE", "technician-b:FAILED_ACTIVATION");
        assertThat(jdbc.sql("SELECT claimed_by FROM tsk_task WHERE task_id = :taskId")
                .param("taskId", baseline.taskId()).query(String.class).single())
                .isEqualTo("technician-a");
        assertThat(jdbc.sql("""
                SELECT principal_id || ':' || status FROM tsk_task_assignment
                 WHERE task_id = :taskId AND assignment_kind = 'RESPONSIBLE'
                 ORDER BY created_at
                """).param("taskId", baseline.taskId()).query(String.class).list())
                .containsExactly("technician-a:ACTIVE", "technician-b:ABORTED");
        assertThat(jdbc.sql("""
                SELECT status FROM tsk_task_execution_guard
                 WHERE guard_key = :sagaId
                """).param("sagaId", pending.sagaId().toString()).query(String.class).single())
                .isEqualTo("RELEASED");
        assertThat(jdbc.sql("""
                SELECT status FROM dsp_capacity_reservation
                 WHERE service_assignment_id = :assignmentId
                """).param("assignmentId", pending.serviceAssignmentId())
                .query(String.class).single()).isEqualTo("RELEASED");
    }

    private void drainOutbox() {
        for (int iteration = 0; iteration < 100; iteration++) {
            OutboxWorker.RunResult result = outboxWorker.runOnce();
            if (result == OutboxWorker.RunResult.EMPTY) return;
            assertThat(result).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        }
        throw new AssertionError("M25 outbox chain did not drain");
    }

    private void drainUntilFailure() {
        for (int iteration = 0; iteration < 100; iteration++) {
            OutboxWorker.RunResult result = outboxWorker.runOnce();
            if (result == OutboxWorker.RunResult.FAILED) return;
            assertThat(result).isNotEqualTo(OutboxWorker.RunResult.EMPTY);
        }
        throw new AssertionError("injected M25 failure was not reached");
    }

    private void configure(String assigneeId, String key) {
        capacities.configure(manager(), metadata(key), new ConfigureCapacityCommand(
                ResponsibilityLevel.TECHNICIAN, assigneeId, BUSINESS_TYPE, 1, 0));
    }

    private void seedGrant(String actorId, Set<String> capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, :roleCode, 'M25 测试角色', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT)
                .param("roleCode", "m25-" + actorId).update();
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
                    now() - interval '1 day', 'TEST_FIXTURE', 'M25-TEST', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("actorId", actorId).param("roleId", roleId).update();
    }

    private static CurrentPrincipal manager() {
        return principal(MANAGER,
                "dispatch.capacity.configure", "dispatch.assignment.manage",
                "task.assign", "task.reassignment.manage");
    }

    private static CurrentPrincipal principal(String actorId, String... capabilities) {
        return new CurrentPrincipal(actorId, TENANT, CurrentPrincipal.PrincipalType.USER,
                "m25-it", Set.of(capabilities));
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }

    private record Baseline(UUID workOrderId, UUID taskId, UUID serviceAssignmentId) {
    }

    private record EvidenceContext(UUID projectId, UUID bundleId) {
    }
}
