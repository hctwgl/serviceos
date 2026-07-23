package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.fieldwork.api.CheckInVisitCommand;
import com.serviceos.fieldwork.api.TechnicianVisitCommandService;
import com.serviceos.fieldwork.api.VisitLocation;
import com.serviceos.forms.api.TechnicianFormService;
import com.serviceos.readmodel.api.TechnicianPortalFeedItem;
import com.serviceos.readmodel.api.TechnicianPortalFeedPage;
import com.serviceos.readmodel.api.TechnicianPortalQueryService;
import com.serviceos.readmodel.api.TechnicianPortalSchedulePage;
import com.serviceos.readmodel.api.TechnicianPortalSyncSummary;
import com.serviceos.readmodel.api.TechnicianPortalTaskDetail;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M195 Technician Portal Feed：本人 ACTIVE 责任、tombstone、日程、sync-summary、跨网点与非师傅拒绝。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TechnicianPortalFeedPostgresIT {
    private static final String TENANT = "tenant-technician-portal-m195";
    private static final UUID TECH_PRINCIPAL = UUID.fromString("019f83b0-1111-7f8c-9505-36fe5c0e8801");
    private static final UUID OTHER_TECH_PRINCIPAL = UUID.fromString("019f83b0-1112-7f8c-9505-36fe5c0e8802");
    private static final UUID NON_TECH_PRINCIPAL = UUID.fromString("019f83b0-1113-7f8c-9505-36fe5c0e8803");
    private static final UUID NETWORK_A = UUID.fromString("019f83b0-2222-7f8c-9505-36fe5c0e8804");
    private static final UUID NETWORK_B = UUID.fromString("019f83b0-3333-7f8c-9505-36fe5c0e8805");
    private static final UUID PARTNER = UUID.fromString("019f83b0-4444-7f8c-9505-36fe5c0e8806");
    private static final UUID TECH_PROFILE_A = UUID.fromString("019f83b0-5555-7f8c-9505-36fe5c0e8807");
    private static final UUID TECH_PROFILE_B = UUID.fromString("019f83b0-5556-7f8c-9505-36fe5c0e8808");
    private static final UUID WO_A = UUID.fromString("019f83b0-7777-7f8c-9505-36fe5c0e8809");
    private static final UUID WO_B = UUID.fromString("019f83b0-8888-7f8c-9505-36fe5c0e880a");
    private static final UUID TASK_A = UUID.fromString("019f83b0-9999-7f8c-9505-36fe5c0e880b");
    private static final UUID TASK_B = UUID.fromString("019f83b0-aaaa-7f8c-9505-36fe5c0e880c");
    private static final UUID TASK_OTHER = UUID.fromString("019f83b0-bbbb-7f8c-9505-36fe5c0e880d");
    private static final UUID PROJECT_A = UUID.fromString("019f83b0-cccc-7f8c-9505-36fe5c0e880e");
    private static final UUID APPOINTMENT_A = UUID.fromString("019f83b0-dddd-7f8c-9505-36fe5c0e880f");
    private static final UUID CONTACT_ATTEMPT_A = UUID.fromString("019f83b0-ddde-7f8c-9505-36fe5c0e8811");
    private static final UUID VISIT_A = UUID.fromString("019f83b0-dddf-7f8c-9505-36fe5c0e8812");
    private static final UUID TECH_ASSIGNMENT_A = UUID.fromString("019f83b0-eeee-7f8c-9505-36fe5c0e8810");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos")
            .withUsername("serviceos_test")
            .withPassword("serviceos_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("serviceos.outbox.scheduling-enabled", () -> "false");
        registry.add("serviceos.task.scheduling-enabled", () -> "false");
    }

    @Autowired TechnicianPortalQueryService portal;
    @Autowired TechnicianVisitCommandService technicianVisits;
    @Autowired TechnicianFormService technicianForms;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.sql("""
                TRUNCATE TABLE apt_appointment_command_result, apt_contact_attempt_command_result,
                    fld_visit_command_result, fld_visit_fact, fld_visit, fld_geofence_policy,
                    apt_contact_attempt, apt_appointment_status_history, apt_appointment,
                    apt_appointment_revision,
                    dsp_assignment_command_result, dsp_capacity_command_result,
                    dsp_service_assignment_activation_saga, dsp_capacity_reservation,
                    dsp_service_assignment, dsp_capacity_counter,
                    tsk_task_assignment, tsk_task_assignment_batch, tsk_task,
                    wo_work_order, cfg_configuration_bundle, prj_project,
                    auth_delegation_capability, auth_delegation, auth_role_grant_event,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role,
                    net_technician_qualification, net_network_technician_membership,
                    net_technician_profile, net_network_membership, net_service_network,
                    net_partner_organization, net_directory_event, net_clearance_work_item,
                    idn_principal_lifecycle_event, idn_principal_persona, idn_identity_link,
                    idn_person_profile, idn_security_principal,
                    rel_idempotency_record, aud_audit_record CASCADE
                """).update();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("149");
        assertThat(flyway.info().applied()).hasSizeGreaterThanOrEqualTo(127);

        seedPrincipal(TECH_PRINCIPAL, "Technician A");
        seedPrincipal(OTHER_TECH_PRINCIPAL, "Technician B");
        seedPrincipal(NON_TECH_PRINCIPAL, "Staff Only");
        seedPersona(TECH_PRINCIPAL, "TECHNICIAN");
        seedPersona(OTHER_TECH_PRINCIPAL, "TECHNICIAN");
        seedPersona(NON_TECH_PRINCIPAL, "NETWORK_MEMBER");
        seedPartnerAndNetworks();
        seedTechnician(TECH_PROFILE_A, TECH_PRINCIPAL, NETWORK_A, "师傅甲");
        seedTechnician(TECH_PROFILE_B, OTHER_TECH_PRINCIPAL, NETWORK_B, "师傅乙");
        seedGrant(TECH_PRINCIPAL, "task.readAssigned", "NETWORK", NETWORK_A.toString());
        seedGrant(TECH_PRINCIPAL, "form.read", "PROJECT", PROJECT_A.toString());
        seedGrant(OTHER_TECH_PRINCIPAL, "task.readAssigned", "NETWORK", NETWORK_B.toString());
        seedWorkOrder(WO_A, PROJECT_A, "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                "370000", "370100", "370102");
        seedWorkOrder(WO_B, PROJECT_A, "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                "370000", "370100", "370102");
        seedHumanTask(TASK_A, WO_A, PROJECT_A);
        seedHumanTask(TASK_B, WO_B, UUID.randomUUID());
        seedHumanTask(TASK_OTHER, UUID.randomUUID(), UUID.randomUUID());
        // TECHNICIAN 服务责任保存师傅档案 ID；登录主体只用于认证、Task 和现场操作。
        seedActivePair(NETWORK_A, WO_A, TASK_A, TECH_PROFILE_A.toString(), TECH_ASSIGNMENT_A);
        // 同网点另一师傅责任（不应出现在本人 feed）
        seedActivePair(NETWORK_A, UUID.randomUUID(), TASK_OTHER, "other-assignee", UUID.randomUUID());
        // 跨网点师傅责任
        seedActivePair(NETWORK_B, WO_B, TASK_B, TECH_PROFILE_B.toString(), UUID.randomUUID());
        seedAppointment(APPOINTMENT_A, TASK_A, WO_A, PROJECT_A);
        seedContactAttempt(CONTACT_ATTEMPT_A, TASK_A, WO_A, PROJECT_A);
        seedVisit(VISIT_A, APPOINTMENT_A, TASK_A, WO_A, PROJECT_A);
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void feedContainsOnlyCurrentTechnicianAssignments() {
        String context = "TECHNICIAN|NETWORK|" + NETWORK_A;
        TechnicianPortalFeedPage feed = portal.taskFeed(actor(TECH_PRINCIPAL), "corr-feed", context, null, null);
        assertThat(feed.networkId()).isEqualTo(NETWORK_A);
        assertThat(feed.items()).extracting(TechnicianPortalFeedItem::taskId).containsExactly(TASK_A);
        assertThat(feed.items()).noneMatch(item -> TASK_B.equals(item.taskId()) || TASK_OTHER.equals(item.taskId()));
        assertThat(feed.items().getFirst().itemType()).isEqualTo("ASSIGNMENT");
        assertThat(feed.items().getFirst().taskStatus()).isEqualTo("READY");
        assertThat(feed.items().getFirst().invalidationReason()).isNull();

        // 纯 UUID 形态在 ACTIVE 师傅成员下也可接受
        TechnicianPortalFeedPage byUuid =
                portal.taskFeed(actor(TECH_PRINCIPAL), "corr-uuid", NETWORK_A.toString(), null, null);
        assertThat(byUuid.items()).hasSize(1);
    }

    @Test
    void technicianContextCheckInPersistsOnlineVisitAndRejectsForgedNetwork() {
        UUID taskId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        seedHumanTask(taskId, workOrderId, projectId);
        seedActivePair(NETWORK_A, workOrderId, taskId, TECH_PROFILE_A.toString(), UUID.randomUUID());
        jdbc.sql("""
                INSERT INTO tsk_task_assignment (
                    task_assignment_id, tenant_id, task_id, assignment_kind,
                    principal_type, principal_id, status, source_type, source_id,
                    effective_from, created_by, created_at)
                VALUES (:id, :tenant, :task, 'RESPONSIBLE', 'USER', :principal,
                    'ACTIVE', 'MANUAL', 'M262-FIXTURE', now(), 'test', now())
                """).param("id", UUID.randomUUID()).param("tenant", TENANT).param("task", taskId)
                .param("principal", TECH_PRINCIPAL.toString()).update();
        seedAppointment(appointmentId, taskId, workOrderId, projectId);
        seedGrant(TECH_PRINCIPAL, "visit.checkIn", "PROJECT", projectId.toString());
        String commandId = "m262-device-command";
        CheckInVisitCommand command = new CheckInVisitCommand(
                appointmentId,
                Instant.parse("2026-07-18T08:10:00Z"),
                commandId,
                "m262-ios-device",
                new VisitLocation(36.067, 120.382, 12),
                false);

        assertThatThrownBy(() -> technicianVisits.checkIn(
                actor(TECH_PRINCIPAL), new CommandMetadata("corr-m262-forged", commandId),
                "TECHNICIAN|NETWORK|" + NETWORK_B, command))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));

        var receipt = technicianVisits.checkIn(
                actor(TECH_PRINCIPAL), new CommandMetadata("corr-m262", commandId),
                "TECHNICIAN|NETWORK|" + NETWORK_A, command);
        assertThat(receipt.status()).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.sql("""
                SELECT network_id, technician_id, offline_flag
                  FROM fld_visit WHERE visit_id = :id
                """).param("id", receipt.visitId()).query((rs, row) -> Set.of(
                        rs.getString("network_id"), rs.getString("technician_id"),
                        Boolean.toString(rs.getBoolean("offline_flag")))).single())
                .containsExactlyInAnyOrder(NETWORK_A.toString(), TECH_PRINCIPAL.toString(), "false");
        assertThat(jdbc.sql("SELECT status FROM apt_appointment WHERE appointment_id = :id")
                .param("id", appointmentId).query(String.class).single()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void technicianContextFormQueryUsesCurrentTaskAndRejectsForgedNetwork() {
        UUID taskId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        seedHumanTask(taskId, workOrderId, projectId);
        seedActivePair(NETWORK_A, workOrderId, taskId, TECH_PROFILE_A.toString(), UUID.randomUUID());
        jdbc.sql("""
                INSERT INTO tsk_task_assignment (
                    task_assignment_id, tenant_id, task_id, assignment_kind,
                    principal_type, principal_id, status, source_type, source_id,
                    effective_from, created_by, created_at)
                VALUES (:id, :tenant, :task, 'RESPONSIBLE', 'USER', :principal,
                    'ACTIVE', 'MANUAL', 'M263-FIXTURE', now(), 'test', now())
                """).param("id", UUID.randomUUID()).param("tenant", TENANT).param("task", taskId)
                .param("principal", TECH_PRINCIPAL.toString()).update();
        seedGrant(TECH_PRINCIPAL, "form.read", "PROJECT", projectId.toString());

        assertThat(technicianForms.listForTask(
                actor(TECH_PRINCIPAL), "corr-m263", "TECHNICIAN|NETWORK|" + NETWORK_A,
                "TECHNICIAN_WEB", taskId))
                .isEmpty();
        assertThatThrownBy(() -> technicianForms.listForTask(
                actor(TECH_PRINCIPAL), "corr-m263-forged",
                "TECHNICIAN|NETWORK|" + NETWORK_B, "TECHNICIAN_WEB", taskId))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));
    }

    @Test
    void tombstoneAppearsAfterAssignmentEndedWithCursor() {
        String context = "TECHNICIAN|NETWORK|" + NETWORK_A;
        TechnicianPortalFeedPage before = portal.taskFeed(actor(TECH_PRINCIPAL), "corr-before", context, null, null);
        String cursor = before.items().getFirst().cursor();

        Instant endedAt = Instant.parse("2026-07-17T03:00:00Z");
        jdbc.sql("""
                UPDATE dsp_service_assignment
                   SET status='ENDED',
                       effective_to=:endedAt,
                       ended_by='test',
                       end_reason_code='REASSIGNED'
                 WHERE service_assignment_id=:id
                """)
                .param("endedAt", java.sql.Timestamp.from(endedAt))
                .param("id", TECH_ASSIGNMENT_A)
                .update();

        TechnicianPortalFeedPage delta =
                portal.taskFeed(actor(TECH_PRINCIPAL), "corr-tomb", context, null, cursor);
        assertThat(delta.items()).hasSize(1);
        assertThat(delta.items().getFirst().itemType()).isEqualTo("TOMBSTONE");
        assertThat(delta.items().getFirst().taskId()).isEqualTo(TASK_A);
        assertThat(delta.items().getFirst().invalidationReason()).isEqualTo("REASSIGNED");
        assertThat(delta.items().getFirst().workOrderId()).isNull();
        assertThat(delta.items().getFirst().taskStatus()).isNull();
    }

    @Test
    void scheduleAndSyncSummaryFanInActiveTasks() {
        String context = "TECHNICIAN|NETWORK|" + NETWORK_A;
        TechnicianPortalSchedulePage schedule =
                portal.schedule(actor(TECH_PRINCIPAL), "corr-sched", context);
        assertThat(schedule.items()).hasSize(1);
        assertThat(schedule.items().getFirst().appointmentId()).isEqualTo(APPOINTMENT_A);
        assertThat(schedule.items().getFirst().taskId()).isEqualTo(TASK_A);
        assertThat(schedule.items().getFirst().type()).isEqualTo("INSTALLATION");

        TechnicianPortalSyncSummary summary =
                portal.syncSummary(actor(TECH_PRINCIPAL), "corr-sync", context);
        assertThat(summary.pendingFeedItemCount()).isEqualTo(1);
        assertThat(summary.appointmentWindowCount()).isEqualTo(1);
        assertThat(summary.tombstoneCount()).isEqualTo(0);
    }

    @Test
    void taskDetailContainsOnlyCurrentResponsibilityAndNonPiiAppointmentSummary() {
        String context = "TECHNICIAN|NETWORK|" + NETWORK_A;

        TechnicianPortalTaskDetail detail = portal.taskDetail(
                actor(TECH_PRINCIPAL), "corr-detail", context, "TECHNICIAN_WEB", TASK_A);

        assertThat(detail.networkId()).isEqualTo(NETWORK_A);
        assertThat(detail.taskId()).isEqualTo(TASK_A);
        assertThat(detail.workOrderId()).isEqualTo(WO_A);
        assertThat(detail.projectId()).isEqualTo(PROJECT_A);
        assertThat(detail.serviceAssignmentId()).isEqualTo(TECH_ASSIGNMENT_A);
        assertThat(detail.taskType()).isEqualTo("INSTALLATION");
        assertThat(detail.taskKind()).isEqualTo("HUMAN");
        assertThat(detail.stageCode()).isEqualTo("INSTALL");
        assertThat(detail.taskStatus()).isEqualTo("READY");
        assertThat(detail.executionGuarded()).isFalse();
        assertThat(detail.resourceVersion()).isEqualTo(1);
        assertThat(detail.clientCode()).isEqualTo("BYD");
        assertThat(detail.brandCode()).isEqualTo("BYD_OCEAN");
        assertThat(detail.serviceProductCode()).isEqualTo("HOME_CHARGING_SURVEY_INSTALL");
        assertThat(detail.provinceCode()).isEqualTo("370000");
        assertThat(detail.cityCode()).isEqualTo("370100");
        assertThat(detail.districtCode()).isEqualTo("370102");
        assertThat(detail.appointments()).singleElement().satisfies(appointment -> {
            assertThat(appointment.appointmentId()).isEqualTo(APPOINTMENT_A);
            assertThat(appointment.taskId()).isEqualTo(TASK_A);
            assertThat(appointment.timezone()).isEqualTo("Asia/Shanghai");
        });
        assertThat(detail.contactAttempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.contactAttemptId()).isEqualTo(CONTACT_ATTEMPT_A);
            assertThat(attempt.taskId()).isEqualTo(TASK_A);
            assertThat(attempt.channel()).isEqualTo("PHONE");
            assertThat(attempt.resultCode()).isEqualTo("NO_ANSWER");
            assertThat(attempt.nextContactAt()).isEqualTo(Instant.parse("2026-07-17T04:00:00Z"));
        });
        assertThat(detail.visits()).singleElement().satisfies(visit -> {
            assertThat(visit.visitId()).isEqualTo(VISIT_A);
            assertThat(visit.appointmentId()).isEqualTo(APPOINTMENT_A);
            assertThat(visit.status()).isEqualTo("IN_PROGRESS");
            assertThat(visit.geofenceResult()).isEqualTo("WITHIN_GEOFENCE");
            assertThat(visit.policyDecision()).isEqualTo("ACCEPTED");
            assertThat(visit.aggregateVersion()).isEqualTo(1);
        });
        assertThat(detail.formSubmissions()).isEmpty();

        // 同一合法 Portal 上下文下，其他师傅或其他网点任务统一按不存在处理，避免资源枚举。
        assertThatThrownBy(() -> portal.taskDetail(
                actor(TECH_PRINCIPAL), "corr-hidden", context, "TECHNICIAN_WEB", TASK_B))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> portal.taskDetail(
                actor(TECH_PRINCIPAL), "corr-other", context, "TECHNICIAN_WEB", TASK_OTHER))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void taskDetailStillReturnsAppointmentAfterCheckInAdvancesItToInProgress() {
        // 回归：签到把预约从 CONFIRMED 推进为 IN_PROGRESS 后，任务详情仍必须返回该预约。
        // 否则完成任务的前置检查「已有预约安排」在签到后永远无法满足，形成必然死锁。
        jdbc.sql("UPDATE apt_appointment SET status='IN_PROGRESS' WHERE appointment_id=:id")
                .param("id", APPOINTMENT_A).update();

        TechnicianPortalTaskDetail detail = portal.taskDetail(
                actor(TECH_PRINCIPAL), "corr-inprogress",
                "TECHNICIAN|NETWORK|" + NETWORK_A, "TECHNICIAN_WEB", TASK_A);

        assertThat(detail.appointments()).singleElement().satisfies(appointment -> {
            assertThat(appointment.appointmentId()).isEqualTo(APPOINTMENT_A);
            assertThat(appointment.taskId()).isEqualTo(TASK_A);
        });
    }

    @Test
    void taskDetailOmitsFormSubmissionsWithoutIndependentFormReadCapability() {
        jdbc.sql("""
                DELETE FROM auth_role_grant
                 WHERE tenant_id=:tenant
                   AND principal_id=:principal
                   AND role_id IN (
                       SELECT rc.role_id FROM auth_role_capability rc
                        WHERE rc.capability_code='form.read'
                   )
                """)
                .param("tenant", TENANT)
                .param("principal", TECH_PRINCIPAL.toString())
                .update();
        jdbc.sql("UPDATE auth_tenant_grant_generation SET generation=2, updated_at=now() WHERE tenant_id=:tenant")
                .param("tenant", TENANT).update();

        TechnicianPortalTaskDetail detail = portal.taskDetail(
                actor(TECH_PRINCIPAL), "corr-no-form-read", "TECHNICIAN|NETWORK|" + NETWORK_A,
                "TECHNICIAN_WEB", TASK_A);

        assertThat(detail.formSubmissions()).isNull();
        assertThat(detail.visits()).hasSize(1);
    }

    private void seedContactAttempt(UUID attemptId, UUID taskId, UUID workOrderId, UUID projectId) {
        // 故意写入敏感引用/自由文本/录音引用，证明 Technician 投影只取固定安全字段。
        jdbc.sql("""
                INSERT INTO apt_contact_attempt (
                    contact_attempt_id, tenant_id, project_id, work_order_id, task_id,
                    channel, contacted_party_ref, started_at, ended_at, result_code,
                    note, next_contact_at, recording_ref, actor_id, created_at
                ) VALUES (
                    :id, :tenant, :project, :workOrder, :task,
                    'PHONE', 'customer-sensitive-ref',
                    '2026-07-17 03:00:00+00', '2026-07-17 03:02:00+00', 'NO_ANSWER',
                    'sensitive free text', '2026-07-17 04:00:00+00',
                    'recording-sensitive-ref', 'other-actor-sensitive-id', '2026-07-17 03:03:00+00'
                )
                """)
                .param("id", attemptId)
                .param("tenant", TENANT)
                .param("project", projectId)
                .param("workOrder", workOrderId)
                .param("task", taskId)
                .update();
    }

    private void seedVisit(UUID visitId, UUID appointmentId, UUID taskId, UUID workOrderId, UUID projectId) {
        // 坐标、距离、设备和 note 均有真实值，用于证明 Technician 安全投影不会读取这些敏感列。
        jdbc.sql("""
                INSERT INTO fld_visit (
                    visit_id, tenant_id, project_id, work_order_id, task_id, appointment_id,
                    visit_sequence, technician_id, network_id, status,
                    check_in_captured_at, check_in_received_at,
                    check_in_latitude, check_in_longitude, check_in_accuracy_meters,
                    geofence_result, geofence_distance_meters, geofence_policy_version,
                    policy_decision, device_id, device_command_id, offline_flag,
                    note, aggregate_version, created_by, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :project, :workOrder, :task, :appointment,
                    1, :technician, :network, 'IN_PROGRESS',
                    '2026-07-17 05:00:00+00', '2026-07-17 05:00:05+00',
                    36.067000, 120.382000, 18.50,
                    'WITHIN_GEOFENCE', 12.40, 'GEO-V1',
                    'ACCEPTED', 'sensitive-device-id', 'sensitive-device-command', false,
                    'sensitive visit note', 1, :technician, now(), now()
                )
                """)
                .param("id", visitId)
                .param("tenant", TENANT)
                .param("project", projectId)
                .param("workOrder", workOrderId)
                .param("task", taskId)
                .param("appointment", appointmentId)
                .param("technician", TECH_PRINCIPAL.toString())
                .param("network", NETWORK_A.toString())
                .update();
    }

    @Test
    void crossNetworkAndNonTechnicianArePortalContextInvalid() {
        assertThatThrownBy(() -> portal.taskFeed(
                actor(TECH_PRINCIPAL), "corr-cross", "TECHNICIAN|NETWORK|" + NETWORK_B, null, null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));

        assertThatThrownBy(() -> portal.taskFeed(
                actor(NON_TECH_PRINCIPAL), "corr-non", "TECHNICIAN|NETWORK|" + NETWORK_A, null, null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));

        assertThatThrownBy(() -> portal.taskFeed(
                actor(TECH_PRINCIPAL), "corr-forged", "TECHNICIAN|NETWORK|" + UUID.randomUUID(), null, null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));

        assertThatThrownBy(() -> portal.taskFeed(
                actor(TECH_PRINCIPAL), "corr-net-ctx", "NETWORK|NETWORK|" + NETWORK_A, null, null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));
    }

    private void seedPrincipal(UUID principalId, String displayName) {
        jdbc.sql("""
                INSERT INTO idn_security_principal (
                    principal_id, tenant_id, principal_type, principal_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (:id, :tenant, 'USER', 'ACTIVE', 1, now(), now())
                """).param("id", principalId).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO idn_person_profile (
                    principal_id, tenant_id, display_name, employee_number,
                    profile_version, created_at, updated_at, updated_by
                ) VALUES (:id, :tenant, :name, :emp, 1, now(), now(), 'test')
                """)
                .param("id", principalId)
                .param("tenant", TENANT)
                .param("name", displayName)
                .param("emp", "E-" + principalId.toString().substring(24))
                .update();
    }

    private void seedPersona(UUID principalId, String type) {
        jdbc.sql("""
                INSERT INTO idn_principal_persona (
                    persona_id, tenant_id, principal_id, persona_type, persona_status,
                    valid_from, valid_to, persona_version, created_by, created_at
                ) VALUES (
                    :id, :tenant, :principal, :type, 'ACTIVE',
                    now() - interval '1 day', NULL, 1, 'test', now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("principal", principalId)
                .param("type", type)
                .update();
    }

    private void seedPartnerAndNetworks() {
        jdbc.sql("""
                INSERT INTO net_partner_organization (
                    partner_organization_id, tenant_id, partner_code, partner_name,
                    partner_status, aggregate_version, created_at, updated_at
                ) VALUES (:id, :tenant, 'P-195', 'Partner 195', 'ACTIVE', 1, now(), now())
                """).param("id", PARTNER).param("tenant", TENANT).update();
        for (UUID networkId : new UUID[]{NETWORK_A, NETWORK_B}) {
            jdbc.sql("""
                    INSERT INTO net_service_network (
                        service_network_id, tenant_id, partner_organization_id, network_code,
                        network_name, network_status, aggregate_version, created_at, updated_at
                    ) VALUES (
                        :id, :tenant, :partner, :code, :name, 'ACTIVE', 1, now(), now()
                    )
                    """)
                    .param("id", networkId)
                    .param("tenant", TENANT)
                    .param("partner", PARTNER)
                    .param("code", "N-" + networkId.toString().substring(24))
                    .param("name", "Network " + networkId)
                    .update();
        }
    }

    private void seedTechnician(UUID profileId, UUID principalId, UUID networkId, String name) {
        jdbc.sql("""
                INSERT INTO net_technician_profile (
                    technician_profile_id, tenant_id, principal_id, display_name, profile_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :principal, :name, 'ACTIVE', 1, now(), now()
                )
                """)
                .param("id", profileId)
                .param("tenant", TENANT)
                .param("principal", principalId)
                .param("name", name)
                .update();
        jdbc.sql("""
                INSERT INTO net_network_technician_membership (
                    membership_id, tenant_id, service_network_id, technician_profile_id,
                    membership_status, valid_from, created_by, created_at, aggregate_version
                ) VALUES (
                    :id, :tenant, :network, :profile,
                    'ACTIVE', now() - interval '1 day', 'test', now(), 1
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("network", networkId)
                .param("profile", profileId)
                .update();
    }

    private void seedWorkOrder(
            UUID workOrderId,
            UUID projectId,
            String clientCode,
            String brandCode,
            String serviceProductCode,
            String provinceCode,
            String cityCode,
            String districtCode
    ) {
        UUID bundleId = UUID.nameUUIDFromBytes(("m350-bundle-" + workOrderId).getBytes());
        String bundleDigest = "e".repeat(64);
        java.time.OffsetDateTime scopeNow = java.time.OffsetDateTime.parse("2026-07-17T00:00:00Z");
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at)
                VALUES (
                    :projectId, :tenantId, :code, :clientId, 'M350 technician expr fixture',
                    DATE '2026-07-01', NULL, 'ACTIVE', 1, :createdAt)
                ON CONFLICT (project_id) DO NOTHING
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("code", "M350-" + workOrderId.toString().substring(24))
                .param("clientId", clientCode)
                .param("createdAt", scopeNow)
                .update();
        jdbc.sql("""
                INSERT INTO cfg_configuration_bundle (
                    bundle_id, tenant_id, project_id, bundle_code, bundle_version,
                    brand_code, service_product_code, province_code, effective_from, effective_until,
                    manifest_digest, status, published_at)
                VALUES (
                    :bundleId, :tenantId, :projectId, :bundleCode, '1.0.0',
                    :brandCode, :product, :province, :effectiveFrom, NULL,
                    :manifestDigest, 'PUBLISHED', :publishedAt)
                ON CONFLICT (bundle_id) DO NOTHING
                """)
                .param("bundleId", bundleId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("bundleCode", "M350-BUNDLE-" + workOrderId.toString().substring(24))
                .param("brandCode", brandCode)
                .param("product", serviceProductCode)
                .param("province", provinceCode)
                .param("effectiveFrom", scopeNow)
                .param("manifestDigest", bundleDigest)
                .param("publishedAt", scopeNow)
                .update();
        jdbc.sql("""
                INSERT INTO wo_work_order (
                    id, tenant_id, project_id, client_code, brand_code, service_product_code,
                    external_order_code, payload_digest, status, configuration_bundle_id,
                    configuration_bundle_code, configuration_bundle_version,
                    configuration_bundle_digest, province_code, city_code, district_code,
                    customer_name, customer_mobile, service_address, vehicle_vin,
                    external_dispatched_at, received_at, version)
                VALUES (
                    :id, :tenantId, :projectId, :clientCode, :brandCode, :serviceProductCode,
                    :externalOrderCode, :payloadDigest, 'RECEIVED', :bundleId,
                    'M350-BUNDLE', '1.0.0', :bundleDigest, :provinceCode, :cityCode, :districtCode,
                    '测试用户', '13800000000', '测试地址', 'VINM350000000001',
                    :dispatchedAt, :receivedAt, 1)
                """)
                .param("id", workOrderId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("clientCode", clientCode)
                .param("brandCode", brandCode)
                .param("serviceProductCode", serviceProductCode)
                .param("externalOrderCode", "M350-" + workOrderId)
                .param("payloadDigest", "d".repeat(64))
                .param("bundleId", bundleId)
                .param("bundleDigest", bundleDigest)
                .param("provinceCode", provinceCode)
                .param("cityCode", cityCode)
                .param("districtCode", districtCode)
                .param("dispatchedAt", java.time.LocalDateTime.parse("2026-07-17T00:00:00"))
                .param("receivedAt", scopeNow)
                .update();
    }

    private void seedHumanTask(UUID taskId, UUID workOrderId, UUID projectId) {
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
                .param("businessKey", "m195:" + taskId)
                .param("digest", "a".repeat(64))
                .param("now", java.sql.Timestamp.from(now))
                .param("projectId", projectId)
                .param("workOrderId", workOrderId)
                .param("workflowInstanceId", UUID.randomUUID())
                .param("stageInstanceId", UUID.randomUUID())
                .param("workflowNodeInstanceId", UUID.randomUUID())
                .param("definitionId", UUID.randomUUID())
                .param("definitionDigest", "b".repeat(64))
                .param("bundleId", UUID.randomUUID())
                .param("bundleDigest", "c".repeat(64))
                .update();
    }

    private void seedActivePair(
            UUID networkId, UUID workOrderId, UUID taskId, String technicianAssigneeId,
            UUID techAssignmentId
    ) {
        Instant now = Instant.parse("2026-07-17T01:00:00Z");
        insertAssignment(UUID.randomUUID(), workOrderId, taskId, "NETWORK", networkId.toString(),
                UUID.randomUUID(), now);
        insertAssignment(techAssignmentId, workOrderId, taskId, "TECHNICIAN", technicianAssigneeId,
                UUID.randomUUID(), now);
    }

    private void insertAssignment(
            UUID assignmentId, UUID workOrderId, UUID taskId, String level, String assigneeId,
            UUID sagaId, Instant now
    ) {
        jdbc.sql("""
                INSERT INTO dsp_service_assignment (
                    service_assignment_id, tenant_id, work_order_id, task_id,
                    responsibility_level, assignee_id, business_type, source_decision_id,
                    status, activation_saga_id, effective_from, created_by, created_at,
                    authority_assignment_id, authority_version,
                    fence_decision_id, fence_policy_version
                ) VALUES (
                    :id, :tenant, :workOrderId, :taskId,
                    :level, :assignee, 'INSTALLATION', :decision,
                    'ACTIVE', :saga, :now, 'test', :now,
                    :authorityId, 1,
                    :fenceDecision, :fencePolicy
                )
                """)
                .param("id", assignmentId)
                .param("tenant", TENANT)
                .param("workOrderId", workOrderId)
                .param("taskId", taskId)
                .param("level", level)
                .param("assignee", assigneeId)
                .param("decision", "decision://" + assignmentId)
                .param("saga", sagaId)
                .param("now", java.sql.Timestamp.from(now))
                .param("authorityId", "authority://" + assignmentId)
                .param("fenceDecision", "fence://" + assignmentId)
                .param("fencePolicy", "fence-policy-v1")
                .update();
    }

    private void seedAppointment(UUID appointmentId, UUID taskId, UUID workOrderId, UUID projectId) {
        UUID revisionId = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-18T08:00:00Z");
        Instant end = Instant.parse("2026-07-18T10:00:00Z");
        Instant createdAt = Instant.parse("2026-07-17T01:30:00Z");
        // current_revision FK 为 DEFERRABLE：同事务内先插预约再插修订
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.sql("""
                    INSERT INTO apt_appointment (
                        appointment_id, tenant_id, project_id, work_order_id, task_id,
                        appointment_type, status, current_revision_id, current_revision_no,
                        assigned_network_id, technician_id, aggregate_version, created_by, created_at
                    ) VALUES (
                        :id, :tenant, :projectId, :workOrderId, :taskId,
                        'INSTALLATION', 'CONFIRMED', :revisionId, 1,
                        :network, :tech, 1, 'test', :createdAt
                    )
                    """)
                    .param("id", appointmentId)
                    .param("tenant", TENANT)
                    .param("projectId", projectId)
                    .param("workOrderId", workOrderId)
                    .param("taskId", taskId)
                    .param("revisionId", revisionId)
                    .param("network", NETWORK_A.toString())
                    .param("tech", TECH_PRINCIPAL.toString())
                    .param("createdAt", java.sql.Timestamp.from(createdAt))
                    .update();
            jdbc.sql("""
                    INSERT INTO apt_appointment_revision (
                        revision_id, tenant_id, appointment_id, revision_no, previous_revision_id,
                        window_start, window_end, timezone, estimated_duration_minutes,
                        address_ref, address_version,
                        confirmed_party_type, confirmed_party_ref, confirmation_channel, confirmed_at,
                        reason_code, note, revision_kind, created_by, created_at
                    ) VALUES (
                        :revisionId, :tenant, :appointmentId, 1, NULL,
                        :start, :end, 'Asia/Shanghai', 120,
                        'addr-ref', 'v1',
                        'CUSTOMER', 'customer-ref', 'PHONE', :confirmedAt,
                        NULL, 'note-should-not-leak', 'CONFIRM', 'test', :createdAt
                    )
                    """)
                    .param("revisionId", revisionId)
                    .param("tenant", TENANT)
                    .param("appointmentId", appointmentId)
                    .param("start", java.sql.Timestamp.from(start))
                    .param("end", java.sql.Timestamp.from(end))
                    .param("confirmedAt", java.sql.Timestamp.from(createdAt))
                    .param("createdAt", java.sql.Timestamp.from(createdAt))
                    .update();
        });
    }

    private void seedGrant(UUID principalId, String capability, String scopeType, String scopeRef) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (
                    role_id, tenant_id, role_code, role_name, role_status, role_kind,
                    aggregate_version, created_at, updated_at
                ) VALUES (
                    :roleId, :tenant, :code, :code, 'ACTIVE', 'TENANT', 1, now(), now()
                )
                """)
                .param("roleId", roleId)
                .param("tenant", TENANT)
                .param("code", "m195-" + capability + "-" + UUID.randomUUID())
                .update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, :capability, now())
                """)
                .param("roleId", roleId)
                .param("capability", capability)
                .update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, grant_status, grant_effect,
                    aggregate_version, created_at, updated_at
                ) VALUES (
                    :grantId, :tenant, :principal, :roleId, :scopeType, :scopeRef,
                    now() - interval '1 day', 'TEST_FIXTURE', 'ACTIVE', 'ALLOW',
                    1, now(), now()
                )
                """)
                .param("grantId", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("principal", principalId.toString())
                .param("roleId", roleId)
                .param("scopeType", scopeType)
                .param("scopeRef", scopeRef)
                .update();
    }

    private static CurrentPrincipal actor(UUID principalId) {
        return new CurrentPrincipal(principalId.toString(), TENANT, CurrentPrincipal.PrincipalType.USER,
                "technician-portal", Set.of());
    }
}
