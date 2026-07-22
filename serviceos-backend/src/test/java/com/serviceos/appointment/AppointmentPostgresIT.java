package com.serviceos.appointment;

import com.serviceos.ServiceOsApplication;
import com.serviceos.appointment.api.AppointmentService;
import com.serviceos.appointment.api.AppointmentType;
import com.serviceos.appointment.api.AppointmentWindow;
import com.serviceos.appointment.api.ConfirmAppointmentCommand;
import com.serviceos.appointment.api.CancelAppointmentCommand;
import com.serviceos.appointment.api.ContactResultCode;
import com.serviceos.appointment.api.MarkAppointmentNoShowCommand;
import com.serviceos.appointment.api.ProposeAppointmentCommand;
import com.serviceos.appointment.api.RescheduleAppointmentCommand;
import com.serviceos.appointment.api.RecordContactAttemptCommand;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M30-M31：真实 PostgreSQL 验证预约修订、联系事实、终态、幂等与权限隔离。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AppointmentPostgresIT {
    private static final String TENANT = "tenant-appointment-it";
    private static final UUID PROJECT = UUID.fromString("10000000-0000-0000-0000-000000000030");
    private static final UUID WORK_ORDER = UUID.fromString("20000000-0000-0000-0000-000000000030");

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
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE apt_contact_attempt_command_result, apt_contact_attempt,
                    apt_appointment_command_result, apt_appointment_status_history,
                    apt_appointment_revision, apt_appointment, dsp_service_assignment,
                    tsk_task_assignment, tsk_task_assignment_batch, tsk_task,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        seedGrant("scheduler");
    }

    @Test
    void surveyAndInstallationAppointmentsKeepIndependentRevisionChains() {
        UUID surveyTask = seedTask("SURVEY_TASK", "survey-tech");
        UUID installTask = seedTask("INSTALL_TASK", "install-tech");
        seedServiceResponsibility(surveyTask, "network-a", "survey-tech");
        seedServiceResponsibility(installTask, "network-a", "install-tech");

        var survey = propose(surveyTask, AppointmentType.SURVEY, "survey-propose");
        var installation = propose(installTask, AppointmentType.INSTALLATION, "install-propose");
        var confirmed = appointments.confirm(principal(), metadata("survey-confirm"),
                new ConfirmAppointmentCommand(
                        survey.appointmentId(), 1, "CUSTOMER", "customer-ref", "PHONE"));
        var rescheduled = appointments.reschedule(principal(), metadata("survey-reschedule"),
                new RescheduleAppointmentCommand(
                        survey.appointmentId(), confirmed.aggregateVersion(),
                        window("2026-08-03T01:00:00Z"), "CUSTOMER_REQUESTED_LATER", "客户改期"));

        var surveyView = appointments.get(principal(), "corr-survey", survey.appointmentId());
        var installView = appointments.get(principal(), "corr-install", installation.appointmentId());

        assertThat(rescheduled.status()).isEqualTo("PROPOSED");
        assertThat(surveyView.revisions()).hasSize(3);
        assertThat(surveyView.revisions()).extracting(revision -> revision.revisionNo())
                .containsExactly(1, 2, 3);
        assertThat(surveyView.revisions().get(1).confirmedPartyType()).isEqualTo("CUSTOMER");
        assertThat(surveyView.revisions().get(2).reasonCode()).isEqualTo("CUSTOMER_REQUESTED_LATER");
        assertThat(installView.status()).isEqualTo("PROPOSED");
        assertThat(installView.revisions()).hasSize(1);
        assertThat(installView.technicianId()).isEqualTo(principalId("install-tech"));
        assertThat(installView.assignedNetworkId()).isEqualTo("network-a");
    }

    @Test
    void staleConcurrentRescheduleLosesWithoutOverwritingWinner() {
        UUID taskId = seedTask("SURVEY_TASK", "tech-a");
        seedServiceResponsibility(taskId, "network-a", "tech-a");
        var proposed = propose(taskId, AppointmentType.SURVEY, "concurrent-propose");
        var confirmed = appointments.confirm(principal(), metadata("concurrent-confirm"),
                new ConfirmAppointmentCommand(
                        proposed.appointmentId(), 1, "CUSTOMER", "party-1", "PHONE"));
        var winnerWindow = window("2026-08-05T01:00:00Z");

        appointments.reschedule(principal(), metadata("reschedule-winner"),
                new RescheduleAppointmentCommand(
                        proposed.appointmentId(), confirmed.aggregateVersion(), winnerWindow,
                        "CUSTOMER_REQUESTED_LATER", null));

        assertThatThrownBy(() -> appointments.reschedule(principal(), metadata("reschedule-loser"),
                new RescheduleAppointmentCommand(
                        proposed.appointmentId(), confirmed.aggregateVersion(),
                        window("2026-08-06T01:00:00Z"), "NETWORK_CONFLICT", null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.APPOINTMENT_VERSION_CONFLICT));
        var current = appointments.get(principal(), "corr-current", proposed.appointmentId());
        assertThat(current.aggregateVersion()).isEqualTo(3);
        assertThat(current.revisions()).hasSize(3);
        assertThat(current.revisions().getLast().window()).isEqualTo(winnerWindow);
    }

    @Test
    void proposeReplayFreezesResponseAndCommitsAuditOutboxAtomically() {
        UUID taskId = seedTask("SURVEY_TASK", null);
        ProposeAppointmentCommand command = new ProposeAppointmentCommand(
                taskId, AppointmentType.SURVEY, window("2026-08-02T01:00:00Z"),
                "address-ref", "address-v1");
        CommandMetadata metadata = metadata("replay-propose");

        var first = appointments.propose(principal(), metadata, command);
        var replay = appointments.propose(principal(), metadata, command);

        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.sql("SELECT count(*) FROM apt_appointment").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM apt_appointment_revision").query(Long.class).single())
                .isEqualTo(1);
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event").query(String.class).single())
                .isEqualTo("appointment.proposed");
        assertThat(jdbc.sql("SELECT action_name FROM aud_audit_record").query(String.class).single())
                .isEqualTo("APPOINTMENT_PROPOSE");
        assertThat(jdbc.sql("SELECT count(*) FROM apt_appointment_command_result")
                .query(Long.class).single()).isEqualTo(1);

        assertThatThrownBy(() -> appointments.propose(principal(), metadata,
                new ProposeAppointmentCommand(
                        taskId, AppointmentType.INSTALLATION, command.window(),
                        "address-ref", "address-v1")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    void tenantBoundaryAndTaskResponsibilityMismatchFailClosed() {
        UUID taskId = seedTask("SURVEY_TASK", "task-tech");
        seedServiceResponsibility(taskId, "network-a", "different-tech");

        assertThatThrownBy(() -> propose(taskId, AppointmentType.SURVEY, "mismatch-propose"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.SERVICE_ASSIGNMENT_CONFLICT));

        UUID otherTask = seedTaskForTenant("other-tenant", "SURVEY_TASK", null);
        assertThatThrownBy(() -> appointments.listByTask(principal(), "corr-other", otherTask))
                                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void networkScopedGrantAuthorizesOnlyTheGrantedPrincipal() {
        UUID taskId = seedTask("SURVEY_TASK", "network-tech");
        seedServiceResponsibility(taskId, "network-a", "network-tech");
        jdbc.sql("""
                UPDATE auth_role_grant
                   SET principal_id = 'network-scheduler', scope_type = 'NETWORK', scope_ref = 'network-a'
                 WHERE tenant_id = :tenant
                """).param("tenant", TENANT).update();

        var proposed = appointments.propose(principal("network-scheduler"), metadata("network-propose"),
                new ProposeAppointmentCommand(
                        taskId, AppointmentType.SURVEY, window("2026-08-07T01:00:00Z"),
                        "address-ref", "address-v1"));

        assertThat(proposed.status()).isEqualTo("PROPOSED");
        assertThatThrownBy(() -> appointments.listByTask(
                principal("ungranted-user"), "corr-ungranted", taskId))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    @Test
    void databaseRejectsMutableRevisionAndInvalidLifecycleEvidence() {
        UUID taskId = seedTask("SURVEY_TASK", null);
        var proposed = propose(taskId, AppointmentType.SURVEY, "db-check-propose");
        UUID revisionId = proposed.revisionId();

        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE apt_appointment_revision SET window_end = window_start
                 WHERE revision_id = :revisionId
                """).param("revisionId", revisionId).update())
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.sql("SELECT window_end > window_start FROM apt_appointment_revision")
                .query(Boolean.class).single()).isTrue();

        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE apt_appointment_revision SET note = 'tampered'
                 WHERE revision_id = :revisionId
                """).param("revisionId", revisionId).update())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void repeatedContactAttemptsRemainOrderedImmutableAndReplaySafe() {
        UUID taskId = seedTask("SURVEY_TASK", "tech-contact");
        seedServiceResponsibility(taskId, "network-a", "tech-contact");
        Instant first = Instant.parse("2026-07-14T01:00:00Z");

        for (int index = 0; index < 4; index++) {
            ContactResultCode result = index == 3 ? ContactResultCode.CONNECTED : ContactResultCode.NO_ANSWER;
            RecordContactAttemptCommand command = new RecordContactAttemptCommand(
                    taskId, "PHONE", "customer-ref", first.plusSeconds(index * 600L),
                    first.plusSeconds(index * 600L + 60), result, null,
                    result == ContactResultCode.NO_ANSWER ? first.plusSeconds((index + 1) * 600L) : null,
                    index == 3 ? "recording://call-4" : null);
            var created = appointments.recordContactAttempt(principal(), metadata("contact-" + index), command);
            if (index == 0) {
                assertThat(appointments.recordContactAttempt(principal(), metadata("contact-0"), command))
                        .isEqualTo(created);
            }
        }

        var history = appointments.listContactAttempts(principal(), "corr-contact-history", taskId);
        assertThat(history).hasSize(4);
        assertThat(history).extracting(attempt -> attempt.resultCode())
                .containsExactly(ContactResultCode.NO_ANSWER, ContactResultCode.NO_ANSWER,
                        ContactResultCode.NO_ANSWER, ContactResultCode.CONNECTED);
        assertThat(history.getFirst().startedAt()).isEqualTo(first);
        // M160：按 ID 读取与列表投影一致，并拒绝跨主体与缺失资源。
        var byId = appointments.getContactAttempt(principal(), "corr-get-contact",
                history.getLast().contactAttemptId());
        assertThat(byId.contactAttemptId()).isEqualTo(history.getLast().contactAttemptId());
        assertThat(byId.resultCode()).isEqualTo(ContactResultCode.CONNECTED);
        assertThat(byId.recordingRef()).isEqualTo("recording://call-4");
        assertThatThrownBy(() -> appointments.getContactAttempt(
                principal("intruder"), "corr-get-contact-deny", history.getLast().contactAttemptId()))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThatThrownBy(() -> appointments.getContactAttempt(
                principal(), "corr-missing-contact", UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type = 'contact.attempt.recorded'")
                .query(Long.class).single()).isEqualTo(4);
        assertThatThrownBy(() -> jdbc.sql("UPDATE apt_contact_attempt SET note = 'tampered'").update())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void cancellationCreatesTerminalRevisionAndFrozenNotificationEvent() {
        UUID taskId = seedTask("SURVEY_TASK", null);
        var proposed = propose(taskId, AppointmentType.SURVEY, "cancel-propose");
        CancelAppointmentCommand command = new CancelAppointmentCommand(
                proposed.appointmentId(), 1, "CUSTOMER_CANCELLED", "客户取消");

        var cancelled = appointments.cancel(principal(), metadata("cancel-command"), command);
        var replay = appointments.cancel(principal(), metadata("cancel-command"), command);

        assertThat(replay).isEqualTo(cancelled);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        var view = appointments.get(principal(), "corr-cancelled", proposed.appointmentId());
        assertThat(view.allowedActions()).isEmpty();
        assertThat(view.revisions().getLast().revisionKind()).isEqualTo("CANCEL");
        assertThat(view.revisions().getLast().reasonCode()).isEqualTo("CUSTOMER_CANCELLED");
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event WHERE event_type = 'appointment.cancelled'")
                .query(String.class).single()).isEqualTo("appointment.cancelled");
    }

    @Test
    void noShowRequiresEndedConfirmedWindowAndPersistsEvidenceReferences() {
        UUID futureTask = seedTask("SURVEY_TASK", null);
        var future = propose(futureTask, AppointmentType.SURVEY, "future-no-show-propose");
        var futureConfirmed = appointments.confirm(principal(), metadata("future-no-show-confirm"),
                new ConfirmAppointmentCommand(future.appointmentId(), 1, "CUSTOMER", "customer-ref", "PHONE"));
        assertThatThrownBy(() -> appointments.markNoShow(principal(), metadata("future-no-show"),
                new MarkAppointmentNoShowCommand(future.appointmentId(), futureConfirmed.aggregateVersion(),
                        "CUSTOMER", "customer-ref", "CUSTOMER_ABSENT", java.util.List.of("file-ref-1"))))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.APPOINTMENT_WINDOW_NOT_ENDED));

        UUID pastTask = seedTask("SURVEY_TASK", null);
        var past = appointments.propose(principal(), metadata("past-no-show-propose"),
                new ProposeAppointmentCommand(pastTask, AppointmentType.SURVEY,
                        window("2026-07-01T01:00:00Z"), "address-ref", "address-v1"));
        var confirmed = appointments.confirm(principal(), metadata("past-no-show-confirm"),
                new ConfirmAppointmentCommand(past.appointmentId(), 1, "CUSTOMER", "customer-ref", "PHONE"));
        var noShow = appointments.markNoShow(principal(), metadata("past-no-show"),
                new MarkAppointmentNoShowCommand(past.appointmentId(), confirmed.aggregateVersion(),
                        "CUSTOMER", "customer-ref", "CUSTOMER_ABSENT",
                        java.util.List.of("file-ref-1", "file-ref-2")));

        assertThat(noShow.status()).isEqualTo("NO_SHOW");
        var revision = appointments.get(principal(), "corr-no-show", past.appointmentId())
                .revisions().getLast();
        assertThat(revision.revisionKind()).isEqualTo("NO_SHOW");
        assertThat(revision.noShowEvidenceRefs()).containsExactly("file-ref-1", "file-ref-2");
        assertThat(jdbc.sql("SELECT event_type FROM rel_outbox_event WHERE event_type = 'appointment.no-show-marked'")
                .query(String.class).single()).isEqualTo("appointment.no-show-marked");
    }

    private com.serviceos.appointment.api.AppointmentCommandReceipt propose(
            UUID taskId, AppointmentType type, String key
    ) {
        return appointments.propose(principal(), metadata(key),
                new ProposeAppointmentCommand(
                        taskId, type, window("2026-08-02T01:00:00Z"),
                        "address-ref", "address-v1"));
    }

    private AppointmentWindow window(String start) {
        Instant from = Instant.parse(start);
        return new AppointmentWindow(from, from.plusSeconds(3 * 60 * 60), "Asia/Shanghai", 120);
    }

    private UUID seedTask(String taskType, String responsible) {
        return seedTaskForTenant(TENANT, taskType, responsible);
    }

    private UUID seedTaskForTenant(String tenant, String taskType, String responsible) {
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
                    :taskId, :tenant, :taskType, 'HUMAN', :businessKey, :digest,
                    100, 'READY', now(), 0, 1,
                    'corr-fixture', 1, now(), now(),
                    :projectId, :workOrderId, :workflowId, :stageId,
                    :nodeInstanceId, :nodeId, :definitionId, :digest, :bundleId, :digest, 'SURVEY'
                )
                """).param("taskId", taskId).param("tenant", tenant).param("taskType", taskType)
                .param("businessKey", taskId.toString()).param("digest", "a".repeat(64))
                .param("projectId", PROJECT).param("workOrderId", WORK_ORDER)
                .param("workflowId", UUID.randomUUID()).param("stageId", UUID.randomUUID())
                .param("nodeInstanceId", UUID.randomUUID()).param("nodeId", taskType)
                .param("definitionId", UUID.randomUUID()).param("bundleId", UUID.randomUUID()).update();
        if (responsible != null) {
            jdbc.sql("""
                    INSERT INTO tsk_task_assignment (
                        task_assignment_id, tenant_id, task_id, assignment_kind,
                        principal_type, principal_id, status, source_type, source_id,
                        effective_from, created_by, created_at
                    ) VALUES (
                        :assignmentId, :tenant, :taskId, 'RESPONSIBLE',
                        'USER', :responsible, 'ACTIVE', 'MANUAL', 'M30-FIXTURE',
                        now(), 'fixture', now()
                    )
                    """).param("assignmentId", UUID.randomUUID()).param("tenant", tenant)
                    .param("taskId", taskId).param("responsible", principalId(responsible)).update();
        }
        return taskId;
    }

    private void seedServiceResponsibility(UUID taskId, String networkId, String technicianId) {
        seedTechnicianIdentity(technicianId);
        insertServiceAssignment(taskId, "NETWORK", networkId);
        insertServiceAssignment(taskId, "TECHNICIAN", profileId(technicianId));
    }

    private void seedTechnicianIdentity(String label) {
        String principal = principalId(label);
        String profile = profileId(label);
        jdbc.sql("""
                INSERT INTO idn_security_principal (
                    principal_id, tenant_id, principal_type, principal_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (:principal, :tenant, 'USER', 'ACTIVE', 1, now(), now())
                ON CONFLICT (principal_id) DO NOTHING
                """).param("principal", UUID.fromString(principal)).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO net_technician_profile (
                    technician_profile_id, tenant_id, principal_id, display_name, profile_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (:profile, :tenant, :principal, :name, 'ACTIVE', 1, now(), now())
                ON CONFLICT (technician_profile_id) DO NOTHING
                """).param("profile", UUID.fromString(profile))
                .param("tenant", TENANT)
                .param("principal", UUID.fromString(principal))
                .param("name", label)
                .update();
    }

    private static String principalId(String label) {
        return UUID.nameUUIDFromBytes(("appointment-principal:" + label)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String profileId(String label) {
        return UUID.nameUUIDFromBytes(("appointment-profile:" + label)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void insertServiceAssignment(UUID taskId, String level, String assigneeId) {
        jdbc.sql("""
                INSERT INTO dsp_service_assignment (
                    service_assignment_id, tenant_id, work_order_id, task_id,
                    responsibility_level, assignee_id, business_type, source_decision_id,
                    status, activation_saga_id, effective_from, authority_assignment_id,
                    authority_version, fence_decision_id, fence_policy_version,
                    created_by, created_at
                ) VALUES (
                    :id, :tenant, :workOrderId, :taskId,
                    :level, :assigneeId, 'SURVEY', :decision,
                    'ACTIVE', :sagaId, now(), :authorityId,
                    1, :fenceId, 'fence-v1', 'fixture', now()
                )
                """).param("id", UUID.randomUUID()).param("tenant", TENANT)
                .param("workOrderId", WORK_ORDER).param("taskId", taskId)
                .param("level", level).param("assigneeId", assigneeId)
                .param("decision", UUID.randomUUID().toString()).param("sagaId", UUID.randomUUID())
                .param("authorityId", UUID.randomUUID().toString())
                .param("fenceId", UUID.randomUUID().toString()).update();
    }

    private void seedGrant(String actor) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:role, :tenant, 'appointment-manager', '预约协同', 'ACTIVE', now())
                """).param("role", roleId).param("tenant", TENANT).update();
        for (String capability : Set.of("appointment.read", "appointment.propose", "appointment.manage",
                "appointment.recordContact", "appointment.cancel")) {
            jdbc.sql("""
                    INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                    VALUES (:role, :capability, now())
                    """).param("role", roleId).param("capability", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at
                ) VALUES (
                    :grant, :tenant, :actor, :role, 'PROJECT', :project,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M30-TEST', now()
                )
                """).param("grant", UUID.randomUUID()).param("tenant", TENANT)
                .param("actor", actor).param("role", roleId).param("project", PROJECT.toString()).update();
    }

    private CurrentPrincipal principal() {
        return principal("scheduler");
    }

    private CurrentPrincipal principal(String actor) {
        return new CurrentPrincipal(
                actor, TENANT, CurrentPrincipal.PrincipalType.USER, "appointment-it", Set.of());
    }

    private CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }
}
