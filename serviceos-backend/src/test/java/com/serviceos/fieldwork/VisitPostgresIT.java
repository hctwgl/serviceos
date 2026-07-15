package com.serviceos.fieldwork;

import com.serviceos.ServiceOsApplication;
import com.serviceos.appointment.api.AppointmentService;
import com.serviceos.appointment.api.AppointmentType;
import com.serviceos.appointment.api.AppointmentWindow;
import com.serviceos.appointment.api.ConfirmAppointmentCommand;
import com.serviceos.appointment.api.ProposeAppointmentCommand;
import com.serviceos.fieldwork.api.CheckInVisitCommand;
import com.serviceos.fieldwork.api.CheckOutVisitCommand;
import com.serviceos.fieldwork.api.InterruptVisitCommand;
import com.serviceos.fieldwork.api.VisitLocation;
import com.serviceos.fieldwork.api.VisitService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M32：真实 PostgreSQL 验证 Visit 生命周期、围栏策略、重复上门与改派冲突。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VisitPostgresIT {
    private static final String TENANT = "tenant-visit-it";
    private static final UUID PROJECT = UUID.fromString("10000000-0000-0000-0000-000000000032");
    private static final UUID WORK_ORDER = UUID.fromString("20000000-0000-0000-0000-000000000032");
    private static final String TECHNICIAN = "technician-032";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos").withUsername("serviceos_test").withPassword("serviceos_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AppointmentService appointments;
    @Autowired VisitService visits;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE fld_visit_command_result, fld_visit_fact, fld_visit, fld_geofence_policy,
                    apt_contact_attempt_command_result, apt_contact_attempt,
                    apt_appointment_command_result, apt_appointment_status_history,
                    apt_appointment_revision, apt_appointment, dsp_service_assignment,
                    tsk_task_assignment, tsk_task_assignment_batch, tsk_task,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        seedGrant(TECHNICIAN);
    }

    @Test
    void currentTechnicianChecksInAndOutWithFrozenFactsAndReplay() {
        UUID taskId = seedTask(TECHNICIAN);
        seedResponsibility(taskId, "network-a", TECHNICIAN);
        seedGeofence("WARN", 100, 50);
        UUID appointmentId = confirmedAppointment(taskId, "normal");
        Instant captured = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(30);
        CheckInVisitCommand checkIn = new CheckInVisitCommand(
                appointmentId, captured, "device-command-normal", "device-032",
                new VisitLocation(31.230400, 121.473700, 8), false);

        var first = visits.checkIn(principal(), metadata("device-command-normal"), checkIn);
        var replay = visits.checkIn(principal(), metadata("device-command-normal"), checkIn);
        assertThat(replay).isEqualTo(first);
        assertThat(first.status()).isEqualTo("IN_PROGRESS");
        assertThat(first.geofenceResult()).isEqualTo("WITHIN_GEOFENCE");
        assertThat(jdbc.sql("SELECT status FROM apt_appointment WHERE appointment_id = :id")
                .param("id", appointmentId).query(String.class).single()).isEqualTo("IN_PROGRESS");

        var completed = visits.checkOut(principal(), metadata("checkout-normal"),
                new CheckOutVisitCommand(first.visitId(), 1, captured.plusSeconds(120),
                        "SERVICE_COMPLETED", List.of("operation://survey/1")));
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.aggregateVersion()).isEqualTo(2);
        var history = visits.listByWorkOrder(principal(), "corr-history", WORK_ORDER);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().operationRefs()).containsExactly("operation://survey/1");
        assertThat(history.getFirst().checkInCapturedAt()).isEqualTo(captured);
        assertThat(history.getFirst().checkInReceivedAt()).isAfter(captured);
        assertThat(jdbc.sql("SELECT count(*) FROM fld_visit_fact WHERE visit_id = :id")
                .param("id", first.visitId()).query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE module_name = 'fieldwork'")
                .query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT status FROM apt_appointment WHERE appointment_id = :id")
                .param("id", appointmentId).query(String.class).single()).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> visits.interrupt(principal(), metadata("stale-terminal"),
                new InterruptVisitCommand(first.visitId(), 1, captured.plusSeconds(180),
                        "SITE_UNSAFE", null, List.of())))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VISIT_VERSION_CONFLICT));
        assertThatThrownBy(() -> visits.listByWorkOrder(
                principal("intruder"), "corr-intruder", WORK_ORDER))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThatThrownBy(() -> jdbc.sql("UPDATE fld_visit_fact SET note = 'tampered'").update())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void outsideFenceWarnsOrBlocksAccordingToFrozenProjectPolicy() {
        UUID warningTask = seedTask(TECHNICIAN);
        seedResponsibility(warningTask, "network-a", TECHNICIAN);
        seedGeofence("WARN", 20, 50);
        UUID warningAppointment = confirmedAppointment(warningTask, "warn");

        var warning = checkIn(warningAppointment, "device-command-warn", 31.240400, 121.473700);
        assertThat(warning.geofenceResult()).isEqualTo("OUTSIDE_GEOFENCE");
        assertThat(warning.policyDecision()).isEqualTo("WARNING");

        jdbc.sql("UPDATE fld_geofence_policy SET exception_action = 'BLOCK', policy_version = 'geo-v2'")
                .update();
        UUID blockedTask = seedTask(TECHNICIAN);
        seedResponsibility(blockedTask, "network-a", TECHNICIAN);
        UUID blockedAppointment = confirmedAppointment(blockedTask, "block");
        assertThatThrownBy(() -> checkIn(blockedAppointment, "device-command-block", 31.240400, 121.473700))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VISIT_GEOFENCE_REJECTED));
        assertThat(jdbc.sql("SELECT count(*) FROM fld_visit WHERE appointment_id = :id")
                .param("id", blockedAppointment).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT status FROM apt_appointment WHERE appointment_id = :id")
                .param("id", blockedAppointment).query(String.class).single()).isEqualTo("CONFIRMED");
    }

    @Test
    void repeatVisitUsesNewAppointmentAndMonotonicTaskSequence() {
        UUID taskId = seedTask(TECHNICIAN);
        seedResponsibility(taskId, "network-a", TECHNICIAN);
        seedGeofence("WARN", 100, 50);
        UUID firstAppointment = confirmedAppointment(taskId, "repeat-1");
        var first = checkIn(firstAppointment, "device-command-repeat-1", 31.230400, 121.473700);
        visits.interrupt(principal(), metadata("interrupt-repeat-1"),
                new InterruptVisitCommand(first.visitId(), 1, Instant.now(),
                        "MATERIAL_MISSING", "需要二次上门", List.of("file://evidence/1")));

        UUID secondAppointment = confirmedAppointment(taskId, "repeat-2");
        var second = checkIn(secondAppointment, "device-command-repeat-2", 31.230400, 121.473700);
        var history = visits.listByWorkOrder(principal(), "corr-repeat", WORK_ORDER);

        assertThat(second.visitId()).isNotEqualTo(first.visitId());
        assertThat(history).extracting(item -> item.visitSequence()).containsExactly(1, 2);
        assertThat(history).extracting(item -> item.status()).containsExactly("INTERRUPTED", "IN_PROGRESS");
        assertThat(history.getFirst().evidenceRefs()).containsExactly("file://evidence/1");
    }

    @Test
    void reassignedOldTechnicianOfflineCheckInFailsWithoutVisitPollution() {
        UUID taskId = seedTask(TECHNICIAN);
        seedResponsibility(taskId, "network-a", TECHNICIAN);
        seedGeofence("WARN", 100, 50);
        UUID appointmentId = confirmedAppointment(taskId, "reassign");
        reassign(taskId, "technician-new");

        assertThatThrownBy(() -> checkIn(
                appointmentId, "device-command-stale-offline", 31.230400, 121.473700, true))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.TECHNICIAN_ASSIGNMENT_CHANGED));
        assertThat(jdbc.sql("SELECT count(*) FROM fld_visit").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM fld_visit_fact").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM rel_idempotency_record WHERE operation_type = 'visit.check-in'")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT status FROM apt_appointment WHERE appointment_id = :id")
                .param("id", appointmentId).query(String.class).single()).isEqualTo("CONFIRMED");
    }

    private com.serviceos.fieldwork.api.VisitCommandReceipt checkIn(
            UUID appointmentId, String commandId, double latitude, double longitude
    ) {
        return checkIn(appointmentId, commandId, latitude, longitude, false);
    }

    private com.serviceos.fieldwork.api.VisitCommandReceipt checkIn(
            UUID appointmentId, String commandId, double latitude, double longitude, boolean offline
    ) {
        return visits.checkIn(principal(), metadata(commandId), new CheckInVisitCommand(
                appointmentId, Instant.now().minusSeconds(10), commandId, "device-032",
                new VisitLocation(latitude, longitude, 8), offline));
    }

    private UUID confirmedAppointment(UUID taskId, String key) {
        Instant start = Instant.now().minusSeconds(3600);
        var proposed = appointments.propose(principal(), metadata("propose-" + key),
                new ProposeAppointmentCommand(taskId, AppointmentType.SURVEY,
                        new AppointmentWindow(start, start.plusSeconds(10800), "Asia/Shanghai", 120),
                        "address-ref", "address-v1"));
        return appointments.confirm(principal(), metadata("confirm-" + key),
                new ConfirmAppointmentCommand(proposed.appointmentId(), 1,
                        "CUSTOMER", "customer-ref", "PHONE")).appointmentId();
    }

    private UUID seedTask(String responsible) {
        UUID taskId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id, tenant_id, task_type, task_kind, business_key, payload_digest,
                    priority, status, next_run_at, attempt_count, max_attempts,
                    correlation_id, version, created_at, updated_at,
                    project_id, work_order_id, workflow_instance_id, stage_instance_id,
                    workflow_node_instance_id, workflow_node_id,
                    workflow_definition_version_id, workflow_definition_digest,
                    configuration_bundle_id, configuration_bundle_digest, stage_code
                ) VALUES (
                    :taskId, :tenant, 'SURVEY_TASK', 'HUMAN', :businessKey, :digest,
                    100, 'READY', now(), 0, 1, 'corr-fixture', 1, now(), now(),
                    :projectId, :workOrderId, :workflowId, :stageId, :nodeInstanceId,
                    'SURVEY_TASK', :definitionId, :digest, :bundleId, :digest, 'SURVEY')
                """).param("taskId", taskId).param("tenant", TENANT)
                .param("businessKey", taskId.toString()).param("digest", "a".repeat(64))
                .param("projectId", PROJECT).param("workOrderId", WORK_ORDER)
                .param("workflowId", UUID.randomUUID()).param("stageId", UUID.randomUUID())
                .param("nodeInstanceId", UUID.randomUUID()).param("definitionId", UUID.randomUUID())
                .param("bundleId", UUID.randomUUID()).update();
        insertTaskAssignment(taskId, responsible, "ACTIVE");
        return taskId;
    }

    private void insertTaskAssignment(UUID taskId, String technician, String status) {
        jdbc.sql("""
                INSERT INTO tsk_task_assignment (
                    task_assignment_id, tenant_id, task_id, assignment_kind,
                    principal_type, principal_id, status, source_type, source_id,
                    effective_from, created_by, created_at)
                VALUES (:id, :tenant, :taskId, 'RESPONSIBLE', 'USER', :technician,
                    :status, 'MANUAL', 'M32-FIXTURE', now(), 'fixture', now())
                """).param("id", UUID.randomUUID()).param("tenant", TENANT).param("taskId", taskId)
                .param("technician", technician).param("status", status).update();
    }

    private void seedResponsibility(UUID taskId, String networkId, String technicianId) {
        insertServiceAssignment(taskId, "NETWORK", networkId, "ACTIVE");
        insertServiceAssignment(taskId, "TECHNICIAN", technicianId, "ACTIVE");
    }

    private void insertServiceAssignment(UUID taskId, String level, String assigneeId, String status) {
        jdbc.sql("""
                INSERT INTO dsp_service_assignment (
                    service_assignment_id, tenant_id, work_order_id, task_id,
                    responsibility_level, assignee_id, business_type, source_decision_id,
                    status, activation_saga_id, effective_from, authority_assignment_id,
                    authority_version, fence_decision_id, fence_policy_version,
                    created_by, created_at)
                VALUES (:id, :tenant, :workOrderId, :taskId, :level, :assigneeId,
                    'SURVEY', :decision, :status, :sagaId, now(), :authorityId,
                    1, :fenceId, 'fence-v1', 'fixture', now())
                """).param("id", UUID.randomUUID()).param("tenant", TENANT)
                .param("workOrderId", WORK_ORDER).param("taskId", taskId).param("level", level)
                .param("assigneeId", assigneeId).param("decision", UUID.randomUUID().toString())
                .param("status", status).param("sagaId", UUID.randomUUID())
                .param("authorityId", UUID.randomUUID().toString())
                .param("fenceId", UUID.randomUUID().toString()).update();
    }

    private void seedGeofence(String action, double radius, double maxAccuracy) {
        jdbc.sql("""
                INSERT INTO fld_geofence_policy (
                    tenant_id, project_id, target_latitude, target_longitude,
                    radius_meters, max_accuracy_meters, exception_action, policy_version, created_at)
                VALUES (:tenant, :project, 31.230400, 121.473700,
                    :radius, :accuracy, :action, 'geo-v1', now())
                """).param("tenant", TENANT).param("project", PROJECT).param("radius", radius)
                .param("accuracy", maxAccuracy).param("action", action).update();
    }

    private void reassign(UUID taskId, String technician) {
        jdbc.sql("""
                UPDATE tsk_task_assignment
                   SET status = 'REVOKED', effective_to = now(),
                       revoked_by = 'fixture', revoke_reason_code = 'MANUAL_REASSIGNMENT'
                 WHERE task_id = :id
                """)
                .param("id", taskId).update();
        insertTaskAssignment(taskId, technician, "ACTIVE");
        jdbc.sql("""
                UPDATE dsp_service_assignment
                   SET status = 'ENDED', effective_to = now(),
                       ended_by = 'fixture', end_reason_code = 'MANUAL_REASSIGNMENT'
                 WHERE task_id = :id
                """)
                .param("id", taskId).update();
        seedResponsibility(taskId, "network-a", technician);
    }

    private void seedGrant(String actor) {
        UUID role = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:role, :tenant, 'field-tech', '现场技师', 'ACTIVE', now())
                """).param("role", role).param("tenant", TENANT).update();
        for (String capability : Set.of("appointment.read", "appointment.propose", "appointment.manage",
                "appointment.recordContact", "appointment.cancel", "visit.read", "visit.checkIn",
                "visit.checkOut", "visit.interrupt")) {
            jdbc.sql("INSERT INTO auth_role_capability (role_id, capability_code, granted_at) VALUES (:role, :capability, now())")
                    .param("role", role).param("capability", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (:grant, :tenant, :actor, :role, 'PROJECT', :project,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M32-TEST', now())
                """).param("grant", UUID.randomUUID()).param("tenant", TENANT).param("actor", actor)
                .param("role", role).param("project", PROJECT.toString()).update();
    }

    private CurrentPrincipal principal() {
        return principal(TECHNICIAN);
    }

    private CurrentPrincipal principal(String actor) {
        return new CurrentPrincipal(actor, TENANT, CurrentPrincipal.PrincipalType.USER,
                "visit-it", Set.of());
    }

    private CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }
}
