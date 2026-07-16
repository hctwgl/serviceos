package com.serviceos.sla.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ReviseProjectScopeRelationsCommand;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.Sha256;
import com.serviceos.sla.api.SlaClockService;
import com.serviceos.sla.api.SlaInstanceQuery;
import com.serviceos.sla.api.SlaQueryService;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskCompletedPayload;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.task.api.WorkflowTaskKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M61：真实 PostgreSQL 证明 Task ELAPSED SLA 的冻结、对账、幂等和事务回滚。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = {ServiceOsApplication.class, SlaClockPostgresIT.ClockConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SlaClockPostgresIT {
    private static final String TENANT = "tenant-sla-it";
    private static final Instant BASE_TIME = Instant.parse("2026-07-15T00:00:00Z");

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

    @Autowired JdbcClient jdbc;
    @Autowired ConfigurationService configurations;
    @Autowired TaskSchedulingService tasks;
    @Autowired TaskSlaEventHandler handler;
    @Autowired SlaClockService clocks;
    @Autowired SlaQueryService queries;
    @Autowired ProjectCommandService projectCommands;
    @Autowired ObjectMapper objectMapper;
    @Autowired MutableClock clock;

    private UUID projectId;
    private UUID workflowVersionId;
    private String workflowDigest;
    private ConfigurationBundleReference bundle;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                DROP TRIGGER IF EXISTS trg_test_fail_sla_outbox ON rel_outbox_event;
                DROP FUNCTION IF EXISTS test_fail_sla_outbox();
                TRUNCATE TABLE sla_milestone, sla_clock_segment, sla_instance,
                    tsk_task_reassignment_command_result, tsk_task_execution_guard,
                    tsk_task_assignment, tsk_task_assignment_batch,
                    tsk_human_task_command_result, tsk_task_execution_attempt, tsk_task,
                    wo_work_order, aud_audit_record,
                    auth_role_grant, auth_role_capability, auth_role,
                    rel_inbox_record, rel_outbox_publish_attempt, rel_outbox_event,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project CASCADE
                """).update();
        clock.set(BASE_TIME);
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at)
                VALUES (:projectId, :tenantId, 'SLA-IT', 'BYD', 'SLA 集成测试项目',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt)
                """)
                .param("projectId", projectId).param("tenantId", TENANT)
                .param("startsOn", LocalDate.of(2026, 7, 1))
                .param("createdAt", OffsetDateTime.ofInstant(BASE_TIME, ZoneOffset.UTC)).update();
        jdbc.sql("""
                INSERT INTO prj_project_region (
                    project_region_id, tenant_id, project_id, region_code,
                    valid_from, created_by, created_at)
                VALUES (:bindingId, :tenantId, :projectId, 'CN-3702',
                    :validFrom, 'sla-it', :createdAt)
                """)
                .param("bindingId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("validFrom", OffsetDateTime.ofInstant(BASE_TIME.minusSeconds(60), ZoneOffset.UTC))
                .param("createdAt", OffsetDateTime.ofInstant(BASE_TIME, ZoneOffset.UTC)).update();
        jdbc.sql("""
                INSERT INTO prj_project_network (
                    project_network_id, tenant_id, project_id, network_id,
                    valid_from, created_by, created_at)
                VALUES (:bindingId, :tenantId, :projectId, 'network-qingdao-a',
                    :validFrom, 'sla-it', :createdAt)
                """)
                .param("bindingId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("validFrom", OffsetDateTime.ofInstant(BASE_TIME.minusSeconds(60), ZoneOffset.UTC))
                .param("createdAt", OffsetDateTime.ofInstant(BASE_TIME, ZoneOffset.UTC)).update();
        publishBundle();
        seedSlaReadGrant();
    }

    @Test
    void startsOnceAndStopsOnDeadlineAsMet() {
        ScheduledTaskView task = createTask("survey-response-on-time");
        OutboxMessage created = taskCreated(task.taskId());

        handler.handle(created);
        handler.handle(created);
        OutboxMessage mutatedReplay = new OutboxMessage(
                created.outboxId(), created.eventId(), created.module(), created.eventType(),
                created.schemaVersion(), created.aggregateType(), created.aggregateId(),
                created.aggregateVersion(), created.tenantId(), created.correlationId(),
                created.causationId(), created.partitionKey(), created.payload() + " ",
                Sha256.digest(created.payload() + " "), created.occurredAt(), created.attemptNo());
        assertThatThrownBy(() -> handler.handle(mutatedReplay)).isInstanceOf(BusinessProblem.class);
        handler.handle(completed(task.taskId(), created.occurredAt().plusSeconds(60)));

        var view = clocks.findByTask(TENANT, task.taskId()).orElseThrow();
        assertThat(view.status()).isEqualTo("MET");
        assertThat(view.targetDurationSeconds()).isEqualTo(60);
        assertThat(view.deadlineAt()).isEqualTo(created.occurredAt().plusSeconds(60));
        assertThat(view.elapsedSeconds()).isEqualTo(60);
        assertThat(jdbc.sql("SELECT status FROM sla_milestone")
                .query(String.class).single()).isEqualTo("CANCELLED");
        assertThat(jdbc.sql("SELECT elapsed_seconds FROM sla_clock_segment")
                .query(Long.class).single()).isEqualTo(60);
        assertThat(slaEventTypes()).containsExactly("sla.started", "sla.met");
        assertThat(jdbc.sql("SELECT count(*) FROM rel_inbox_record WHERE consumer_name LIKE 'sla.%'")
                .query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void reconcilesDueMilestoneOnceAndPreservesBreachWhenTaskCompletesLate() {
        ScheduledTaskView task = createTask("survey-response-late");
        OutboxMessage created = taskCreated(task.taskId());
        handler.handle(created);
        clock.set(created.occurredAt().plusSeconds(61));

        assertThat(clocks.detectNextBreach()).isTrue();
        assertThat(clocks.detectNextBreach()).isFalse();
        handler.handle(completed(task.taskId(), clock.instant()));

        var view = clocks.findByTask(TENANT, task.taskId()).orElseThrow();
        assertThat(view.status()).isEqualTo("MET_LATE");
        assertThat(view.breachedAt()).isEqualTo(created.occurredAt().plusSeconds(60));
        assertThat(view.breachDetectedAt()).isEqualTo(clock.instant());
        assertThat(view.aggregateVersion()).isEqualTo(3);
        assertThat(slaEventTypes()).containsExactly("sla.started", "sla.breached", "sla.met");
        assertThat(jdbc.sql("""
                SELECT aggregate_version FROM rel_outbox_event
                 WHERE module_name='sla' ORDER BY aggregate_version
                """).query(Long.class).list()).containsExactly(1L, 2L, 3L);
        assertThat(jdbc.sql("SELECT count(*) FROM sla_milestone WHERE status='TRIGGERED'")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void outboxFailureRollsBackSlaInboxInstanceSegmentAndMilestone() {
        ScheduledTaskView task = createTask("survey-response-rollback");
        OutboxMessage created = taskCreated(task.taskId());
        jdbc.sql("""
                CREATE FUNCTION test_fail_sla_outbox() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'sla.started' THEN
                        RAISE EXCEPTION 'injected SLA outbox failure';
                    END IF;
                    RETURN NEW;
                END;
                $$;
                CREATE TRIGGER trg_test_fail_sla_outbox
                    BEFORE INSERT ON rel_outbox_event
                    FOR EACH ROW EXECUTE FUNCTION test_fail_sla_outbox();
                """).update();

        assertThatThrownBy(() -> handler.handle(created)).isInstanceOf(DataAccessException.class);

        assertThat(count("sla_instance")).isZero();
        assertThat(count("sla_clock_segment")).isZero();
        assertThat(count("sla_milestone")).isZero();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_inbox_record WHERE consumer_name='sla.task-created.v1'
                """).query(Long.class).single()).isZero();
    }

    @Test
    void reconciliationDoesNotBreachCompletedTaskBeforeCompletionEventIsConsumed() {
        ScheduledTaskView task = createTask("survey-response-completion-lag");
        OutboxMessage created = taskCreated(task.taskId());
        handler.handle(created);
        Instant completedAt = created.occurredAt().plusSeconds(61);
        jdbc.sql("""
                UPDATE tsk_task
                   SET status='COMPLETED', claimed_by='technician-1', claimed_at=:startedAt,
                       started_at=:startedAt, result_ref='result://completion-lag',
                       result_digest=:resultDigest, completed_at=:completedAt,
                       updated_at=:completedAt, version=version+1
                 WHERE task_id=:taskId
                """)
                .param("startedAt", OffsetDateTime.ofInstant(created.occurredAt(), ZoneOffset.UTC))
                .param("completedAt", OffsetDateTime.ofInstant(completedAt, ZoneOffset.UTC))
                .param("resultDigest", Sha256.digest("completion-lag"))
                .param("taskId", task.taskId()).update();
        clock.set(completedAt.plusSeconds(30));

        assertThat(clocks.detectNextBreach()).isFalse();
        assertThat(clocks.findByTask(TENANT, task.taskId()).orElseThrow().status()).isEqualTo("RUNNING");
        handler.handle(completed(task.taskId(), completedAt));
        assertThat(clocks.findByTask(TENANT, task.taskId()).orElseThrow().status()).isEqualTo("MET_LATE");
    }

    @Test
    void databaseRejectsPublishedPolicyVersionOutsideTasksFrozenBundle() {
        ScheduledTaskView task = createTask("survey-response-foreign-policy");
        String policyV2 = """
                {"policyKey":"survey.response.sla","version":"2.0.0","subjectType":"TASK",
                 "taskTypes":["SURVEY_RESPONSE"],"startEvent":"TASK_CREATED",
                 "stopEvent":"TASK_COMPLETED","clockMode":"ELAPSED","targetDurationSeconds":120}
                """.trim();
        var foreignPolicy = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.SLA, "survey.response.sla", "2.0.0", "1.0.0",
                policyV2, Sha256.digest(policyV2)));
        UUID workOrderId = jdbc.sql("SELECT work_order_id FROM tsk_task WHERE task_id=:taskId")
                .param("taskId", task.taskId()).query(UUID.class).single();

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO sla_instance (
                    sla_instance_id, tenant_id, project_id, work_order_id, task_id, sla_ref,
                    policy_version_id, policy_semantic_version, policy_content_digest,
                    clock_mode, target_duration_seconds, start_event_id, started_at, deadline_at,
                    status, aggregate_version, correlation_id, created_at, updated_at)
                VALUES (:instanceId, :tenantId, :projectId, :workOrderId, :taskId, 'survey.response.sla',
                    :policyVersionId, '2.0.0', :policyDigest, 'ELAPSED', 120, :eventId,
                    :startedAt, :deadlineAt, 'RUNNING', 1, 'corr-foreign-policy', :startedAt, :startedAt)
                """)
                .param("instanceId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("projectId", projectId).param("workOrderId", workOrderId)
                .param("taskId", task.taskId()).param("policyVersionId", foreignPolicy.versionId())
                .param("policyDigest", foreignPolicy.contentDigest()).param("eventId", UUID.randomUUID())
                .param("startedAt", OffsetDateTime.ofInstant(BASE_TIME, ZoneOffset.UTC))
                .param("deadlineAt", OffsetDateTime.ofInstant(BASE_TIME.plusSeconds(120), ZoneOffset.UTC))
                .update()).isInstanceOf(DataAccessException.class);
    }

    @Test
    void authorizedProjectionUsesStableCursorServerClockAndCompleteDetail() {
        ScheduledTaskView firstTask = createTask("sla-query-first");
        ScheduledTaskView secondTask = createTask("sla-query-second");
        handler.handle(taskCreated(firstTask.taskId()));
        handler.handle(taskCreated(secondTask.taskId()));
        clock.set(BASE_TIME.plusSeconds(30));

        var firstPage = queries.list(principal(), "corr-query-1",
                new SlaInstanceQuery(projectId, "running", null, 1));
        var secondPage = queries.list(principal(), "corr-query-2",
                new SlaInstanceQuery(projectId, "RUNNING", firstPage.nextCursor(), 1));
        assertThat(firstPage.items()).hasSize(1);
        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.nextCursor()).isNull();
        assertThat(List.of(firstPage.items().getFirst().taskId(), secondPage.items().getFirst().taskId()))
                .containsExactlyInAnyOrder(firstTask.taskId(), secondTask.taskId());
        assertThat(firstPage.asOf()).isEqualTo(clock.instant());
        assertThat(firstPage.items().getFirst().remainingSeconds()).isEqualTo(30);
        assertThat(firstPage.items().getFirst().overdueSeconds()).isNull();
        assertThat(firstPage.items().getFirst().allowedActions()).isEmpty();

        UUID instanceId = firstPage.items().getFirst().slaInstanceId();
        var detail = queries.get(principal(), "corr-query-detail", instanceId);
        assertThat(detail.instance().slaInstanceId()).isEqualTo(instanceId);
        assertThat(detail.segments()).singleElement().satisfies(segment -> {
            assertThat(segment.segmentNo()).isEqualTo(1);
            assertThat(segment.endedAt()).isNull();
        });
        assertThat(detail.milestones()).singleElement().satisfies(milestone -> {
            assertThat(milestone.milestoneType()).isEqualTo("TARGET_DUE");
            assertThat(milestone.status()).isEqualTo("PENDING");
        });

        UUID workOrderId = detail.instance().workOrderId();
        assertThat(queries.listForWorkOrder(
                principal(), "corr-query-work-order", workOrderId, null, 50).items())
                .extracting(item -> item.taskId()).containsExactly(detail.instance().taskId());

        clock.set(BASE_TIME.plusSeconds(61));
        var overdueButUnreconciled = queries.get(principal(), "corr-query-overdue", instanceId).instance();
        assertThat(overdueButUnreconciled.status()).isEqualTo("RUNNING");
        assertThat(overdueButUnreconciled.remainingSeconds()).isZero();
        assertThat(overdueButUnreconciled.overdueSeconds()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM sla_instance WHERE sla_instance_id=:instanceId")
                .param("instanceId", instanceId).query(String.class).single()).isEqualTo("RUNNING");
    }

    @Test
    void terminalProjectionUsesFrozenCompletionFactsInsteadOfCurrentClock() {
        ScheduledTaskView lateTask = createTask("sla-query-terminal-late");
        OutboxMessage lateCreated = taskCreated(lateTask.taskId());
        handler.handle(lateCreated);
        handler.handle(completed(lateTask.taskId(), lateCreated.occurredAt().plusSeconds(75)));

        ScheduledTaskView metTask = createTask("sla-query-terminal-met");
        OutboxMessage metCreated = taskCreated(metTask.taskId());
        handler.handle(metCreated);
        handler.handle(completed(metTask.taskId(), metCreated.occurredAt().plusSeconds(40)));
        clock.set(BASE_TIME.plusSeconds(600));

        var page = queries.list(principal(), "corr-query-terminal",
                new SlaInstanceQuery(projectId, null, null, 10));
        var late = page.items().stream().filter(item -> item.taskId().equals(lateTask.taskId()))
                .findFirst().orElseThrow();
        var met = page.items().stream().filter(item -> item.taskId().equals(metTask.taskId()))
                .findFirst().orElseThrow();
        assertThat(late.status()).isEqualTo("MET_LATE");
        assertThat(late.overdueSeconds()).isEqualTo(15);
        assertThat(late.remainingSeconds()).isNull();
        assertThat(met.status()).isEqualTo("MET");
        assertThat(met.remainingSeconds()).isNull();
        assertThat(met.overdueSeconds()).isNull();
    }

    @Test
    void projectionRejectsMissingProjectGrantCrossTenantAndCursorScopeMutation() {
        ScheduledTaskView task = createTask("sla-query-security");
        ScheduledTaskView secondTask = createTask("sla-query-security-second");
        handler.handle(taskCreated(task.taskId()));
        handler.handle(taskCreated(secondTask.taskId()));
        UUID instanceId = clocks.findByTask(TENANT, task.taskId()).orElseThrow().slaInstanceId();
        var page = queries.list(principal(), "corr-cursor-source",
                new SlaInstanceQuery(projectId, null, null, 1));

        assertThatThrownBy(() -> queries.get(new CurrentPrincipal(
                "ungranted-user", TENANT, CurrentPrincipal.PrincipalType.USER, "sla-it", Set.of()),
                "corr-denied", instanceId))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(com.serviceos.shared.ProblemCode.ACCESS_DENIED));
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name='AUTHORIZATION_DENIED' AND capability_code='sla.read'
                """).query(Long.class).single()).isEqualTo(1);
        assertThatThrownBy(() -> queries.get(new CurrentPrincipal(
                "sla-reader", "tenant-sla-other", CurrentPrincipal.PrincipalType.USER, "sla-it", Set.of()),
                "corr-cross-tenant", instanceId))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(com.serviceos.shared.ProblemCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> queries.list(principal(), "corr-cursor-mutated",
                new SlaInstanceQuery(projectId, "RUNNING", page.nextCursor(), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor is invalid");
    }

    @Test
    void authorizedProjectCollectionUsesOneScopedQueryAndGrantChangesInvalidateCursor() {
        ScheduledTaskView firstTask = createTask("sla-collection-first");
        ScheduledTaskView secondTask = createTask("sla-collection-second");
        handler.handle(taskCreated(firstTask.taskId()));
        handler.handle(taskCreated(secondTask.taskId()));

        var firstPage = queries.list(principal(), "corr-collection", new SlaInstanceQuery(
                null, "RUNNING", null, 1));
        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.nextCursor()).isNotBlank();

        UUID readerRole = jdbc.sql("""
                SELECT role_id FROM auth_role
                 WHERE tenant_id=:tenantId AND role_code='sla-reader'
                """).param("tenantId", TENANT).query(UUID.class).single();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (:grantId, :tenantId, 'sla-reader', :roleId, 'PROJECT', :projectId,
                    :validFrom, 'TEST_FIXTURE', 'M63-TEST', :createdAt)
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("roleId", readerRole).param("projectId", UUID.randomUUID().toString())
                .param("validFrom", fixtureTime().minusDays(1))
                .param("createdAt", fixtureTime()).update();

        assertThatThrownBy(() -> queries.list(principal(), "corr-collection-stale",
                new SlaInstanceQuery(null, "RUNNING", firstPage.nextCursor(), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor is invalid");
    }

    @Test
    void regionRoleGrantResolvesProjectBindingIntoTheCrossProjectSlaQueue() {
        ScheduledTaskView task = createTask("sla-region-scope");
        ScheduledTaskView secondTask = createTask("sla-region-scope-second");
        handler.handle(taskCreated(task.taskId()));
        handler.handle(taskCreated(secondTask.taskId()));
        seedSlaRegionReadGrant();

        CurrentPrincipal regionReader = new CurrentPrincipal(
                "sla-region-reader", TENANT, CurrentPrincipal.PrincipalType.USER, "sla-it", Set.of());
        var page = queries.list(regionReader, "corr-region-collection",
                new SlaInstanceQuery(null, "RUNNING", null, 1));

        assertThat(page.items()).singleElement()
                .satisfies(item -> assertThat(item.projectId()).isEqualTo(projectId));
        assertThat(page.nextCursor()).isNotBlank();

        UUID newlyScopedProject = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at)
                VALUES (:projectId, :tenantId, 'SLA-REGION-SECOND', 'BYD', '区域新增项目',
                    :startsOn, 'ACTIVE', 1, :createdAt)
                """).param("projectId", newlyScopedProject).param("tenantId", TENANT)
                .param("startsOn", LocalDate.of(2026, 7, 1))
                .param("createdAt", OffsetDateTime.ofInstant(BASE_TIME, ZoneOffset.UTC)).update();
        jdbc.sql("""
                INSERT INTO prj_project_region (
                    project_region_id, tenant_id, project_id, region_code,
                    valid_from, created_by, created_at)
                VALUES (:id, :tenantId, :projectId, 'CN-3702', :validFrom, 'sla-it', :createdAt)
                """).param("id", UUID.randomUUID()).param("tenantId", TENANT)
                .param("projectId", newlyScopedProject)
                .param("validFrom", OffsetDateTime.ofInstant(BASE_TIME.minusSeconds(60), ZoneOffset.UTC))
                .param("createdAt", OffsetDateTime.ofInstant(BASE_TIME, ZoneOffset.UTC)).update();

        assertThatThrownBy(() -> queries.list(regionReader, "corr-region-stale",
                new SlaInstanceQuery(null, "RUNNING", page.nextCursor(), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor is invalid");
    }

    @Test
    void networkRoleGrantResolvesProjectBindingAndRelationChangesInvalidateTheCursor() {
        ScheduledTaskView task = createTask("sla-network-scope");
        ScheduledTaskView secondTask = createTask("sla-network-scope-second");
        handler.handle(taskCreated(task.taskId()));
        handler.handle(taskCreated(secondTask.taskId()));
        seedSlaNetworkReadGrant();

        CurrentPrincipal networkReader = new CurrentPrincipal(
                "sla-network-reader", TENANT, CurrentPrincipal.PrincipalType.USER, "sla-it", Set.of());
        var page = queries.list(networkReader, "corr-network-collection",
                new SlaInstanceQuery(null, "RUNNING", null, 1));

        assertThat(page.items()).singleElement()
                .satisfies(item -> assertThat(item.projectId()).isEqualTo(projectId));
        assertThat(page.nextCursor()).isNotBlank();

        UUID newlyScopedProject = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at)
                VALUES (:projectId, :tenantId, 'SLA-NETWORK-SECOND', 'BYD', '网点新增项目',
                    :startsOn, 'ACTIVE', 1, :createdAt)
                """).param("projectId", newlyScopedProject).param("tenantId", TENANT)
                .param("startsOn", LocalDate.of(2026, 7, 1))
                .param("createdAt", OffsetDateTime.ofInstant(BASE_TIME, ZoneOffset.UTC)).update();
        seedProjectScopeRevisionGrant();
        CurrentPrincipal scopeAdmin = new CurrentPrincipal(
                "scope-admin", TENANT, CurrentPrincipal.PrincipalType.USER, "sla-it",
                Set.of("project.reviseScopeRelations"));
        projectCommands.reviseScopeRelations(
                scopeAdmin, new CommandMetadata("corr-m66-sla-scope", "idem-m66-sla-scope"),
                new ReviseProjectScopeRelationsCommand(
                        newlyScopedProject, 1, List.of(), List.of("network-qingdao-a"),
                        "新增网点授权项目关系"));

        assertThatThrownBy(() -> queries.list(networkReader, "corr-network-stale",
                new SlaInstanceQuery(null, "RUNNING", page.nextCursor(), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor is invalid");
    }

    private void publishBundle() {
        workflowDigest = Sha256.digest(workflowDefinition());
        workflowVersionId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "survey.workflow", "1.0.0", "1.0.0",
                workflowDefinition(), workflowDigest)).versionId();
        String sla = """
                {"policyKey":"survey.response.sla","version":"1.0.0","subjectType":"TASK",
                 "taskTypes":["SURVEY_RESPONSE"],"startEvent":"TASK_CREATED",
                 "stopEvent":"TASK_COMPLETED","clockMode":"ELAPSED","targetDurationSeconds":60}
                """.trim();
        UUID slaVersionId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.SLA, "survey.response.sla", "1.0.0", "1.0.0",
                sla, Sha256.digest(sla))).versionId();
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "SLA-IT-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING", null, BASE_TIME.minusSeconds(60), null,
                List.of(workflowVersionId, slaVersionId)));
    }

    private String workflowDefinition() {
        return """
                {"workflowKey":"survey.workflow","semanticVersion":"1.0.0","startNodeId":"start",
                 "nodes":[{"nodeId":"start","nodeType":"START"},
                   {"nodeId":"survey","nodeType":"HUMAN_TASK","stageCode":"SURVEY",
                    "taskType":"SURVEY_RESPONSE","slaRef":"survey.response.sla"},
                   {"nodeId":"end","nodeType":"END"}],
                 "transitions":[{"from":"start","to":"survey"},{"from":"survey","to":"end"}]}
                """.trim();
    }

    private ScheduledTaskView createTask(String businessKey) {
        UUID workOrderId = createWorkOrder(businessKey);
        return tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                TENANT, projectId, workOrderId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "survey", workflowVersionId, workflowDigest,
                bundle.bundleId(), bundle.manifestDigest(), "SURVEY", "SURVEY_RESPONSE",
                WorkflowTaskKind.HUMAN, null, "survey.response.sla",
                "work-order://" + businessKey, Sha256.digest(businessKey),
                500, clock.instant(), 1, "corr-" + businessKey, "cause-" + businessKey));
    }

    private UUID createWorkOrder(String businessKey) {
        UUID workOrderId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO wo_work_order (
                    id, tenant_id, project_id, client_code, brand_code, service_product_code,
                    external_order_code, payload_digest, status, configuration_bundle_id,
                    configuration_bundle_code, configuration_bundle_version,
                    configuration_bundle_digest, province_code, city_code, district_code,
                    customer_name, customer_mobile, service_address, vehicle_vin,
                    external_dispatched_at, received_at, version)
                VALUES (
                    :id, :tenantId, :projectId, 'BYD', 'BYD_OCEAN', 'HOME_CHARGING',
                    :externalOrderCode, :payloadDigest, 'RECEIVED', :bundleId,
                    'SLA-IT-BUNDLE', '1.0.0', :bundleDigest, '370000', '370100', '370102',
                    '测试用户', '13800000000', '测试地址', 'LSLQUERY123456789',
                    :dispatchedAt, :receivedAt, 1)
                """)
                .param("id", workOrderId).param("tenantId", TENANT).param("projectId", projectId)
                .param("externalOrderCode", businessKey).param("payloadDigest", Sha256.digest(businessKey))
                .param("bundleId", bundle.bundleId()).param("bundleDigest", bundle.manifestDigest())
                .param("dispatchedAt", java.time.LocalDateTime.ofInstant(BASE_TIME, ZoneOffset.UTC))
                .param("receivedAt", OffsetDateTime.ofInstant(BASE_TIME, ZoneOffset.UTC)).update();
        return workOrderId;
    }

    private void seedSlaReadGrant() {
        UUID roleId = UUID.randomUUID();
        OffsetDateTime createdAt = fixtureTime();
        OffsetDateTime validFrom = fixtureTime().minusDays(1);
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, 'sla-reader', 'SLA 查看人', 'ACTIVE', :createdAt)
                """).param("roleId", roleId).param("tenantId", TENANT)
                .param("createdAt", createdAt).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, 'sla.read', :grantedAt)
                """).param("roleId", roleId).param("grantedAt", createdAt).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (
                    :grantId, :tenantId, 'sla-reader', :roleId, 'PROJECT', :projectId,
                    :validFrom, 'TEST_FIXTURE', 'M62-TEST', :createdAt)
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("roleId", roleId).param("projectId", projectId.toString())
                .param("validFrom", validFrom).param("createdAt", createdAt).update();
    }

    private void seedSlaRegionReadGrant() {
        UUID roleId = UUID.randomUUID();
        OffsetDateTime createdAt = fixtureTime();
        OffsetDateTime validFrom = fixtureTime().minusDays(1);
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, 'sla-region-reader', '区域 SLA 查看人', 'ACTIVE', :createdAt)
                """).param("roleId", roleId).param("tenantId", TENANT)
                .param("createdAt", createdAt).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, 'sla.read', :grantedAt)
                """).param("roleId", roleId).param("grantedAt", createdAt).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (
                    :grantId, :tenantId, 'sla-region-reader', :roleId, 'REGION', 'CN-3702',
                    :validFrom, 'TEST_FIXTURE', 'M64-TEST', :createdAt)
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("roleId", roleId)
                .param("validFrom", validFrom).param("createdAt", createdAt).update();
    }

    private void seedSlaNetworkReadGrant() {
        UUID roleId = UUID.randomUUID();
        OffsetDateTime createdAt = fixtureTime();
        OffsetDateTime validFrom = fixtureTime().minusDays(1);
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, 'sla-network-reader', '网点 SLA 查看人', 'ACTIVE', :createdAt)
                """).param("roleId", roleId).param("tenantId", TENANT)
                .param("createdAt", createdAt).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, 'sla.read', :grantedAt)
                """).param("roleId", roleId).param("grantedAt", createdAt).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (
                    :grantId, :tenantId, 'sla-network-reader', :roleId, 'NETWORK', 'network-qingdao-a',
                    :validFrom, 'TEST_FIXTURE', 'M65-TEST', :createdAt)
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("roleId", roleId)
                .param("validFrom", validFrom).param("createdAt", createdAt).update();
    }

    private void seedProjectScopeRevisionGrant() {
        UUID roleId = UUID.randomUUID();
        OffsetDateTime createdAt = fixtureTime();
        OffsetDateTime validFrom = fixtureTime().minusDays(1);
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, 'project-scope-admin', '项目范围管理员', 'ACTIVE', :createdAt)
                """).param("roleId", roleId).param("tenantId", TENANT)
                .param("createdAt", createdAt).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, 'project.reviseScopeRelations', :grantedAt)
                """).param("roleId", roleId).param("grantedAt", createdAt).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (
                    :grantId, :tenantId, 'scope-admin', :roleId, 'TENANT', :tenantId,
                    :validFrom, 'TEST_FIXTURE', 'M66-TEST', :createdAt)
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("roleId", roleId)
                .param("validFrom", validFrom).param("createdAt", createdAt).update();
    }

    /** 授权夹具时间必须对齐 MutableClock，避免墙钟 now() 越过 BASE_TIME 后 grant 尚未生效。 */
    private static OffsetDateTime fixtureTime() {
        return OffsetDateTime.ofInstant(BASE_TIME, ZoneOffset.UTC);
    }

    private CurrentPrincipal principal() {
        return new CurrentPrincipal(
                "sla-reader", TENANT, CurrentPrincipal.PrincipalType.USER, "sla-it", Set.of());
    }

    private OutboxMessage taskCreated(UUID taskId) {
        return jdbc.sql("""
                SELECT outbox_id, event_id, module_name, event_type, schema_version,
                       aggregate_type, aggregate_id, aggregate_version, tenant_id,
                       correlation_id, causation_id, partition_key, payload::text AS payload,
                       payload_digest, occurred_at, attempt_count
                  FROM rel_outbox_event
                 WHERE event_type='task.created' AND aggregate_id=:taskId
                """).param("taskId", taskId.toString()).query(this::message).single();
    }

    private OutboxMessage completed(UUID taskId, Instant completedAt) {
        TaskCompletedPayload value = new TaskCompletedPayload(
                taskId, projectId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "survey", "SURVEY_RESPONSE", workflowVersionId, workflowDigest,
                "result://sla-it", Sha256.digest("result"), completedAt);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException(exception);
        }
        return new OutboxMessage(
                UUID.randomUUID(), UUID.randomUUID(), "task", "task.completed", 2,
                "Task", taskId.toString(), 2, TENANT, "corr-completed", "cause-completed",
                taskId.toString(), payload, Sha256.digest(payload), completedAt, 0);
    }

    private OutboxMessage message(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new OutboxMessage(
                rs.getObject("outbox_id", UUID.class), rs.getObject("event_id", UUID.class),
                rs.getString("module_name"), rs.getString("event_type"), rs.getInt("schema_version"),
                rs.getString("aggregate_type"), rs.getString("aggregate_id"),
                rs.getLong("aggregate_version"), rs.getString("tenant_id"),
                rs.getString("correlation_id"), rs.getString("causation_id"),
                rs.getString("partition_key"), rs.getString("payload"), rs.getString("payload_digest"),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getInt("attempt_count"));
    }

    private List<String> slaEventTypes() {
        return jdbc.sql("""
                SELECT event_type FROM rel_outbox_event
                 WHERE module_name='sla' ORDER BY aggregate_version
                """).query(String.class).list();
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(BASE_TIME);
        }
    }

    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("M61 test clock only supports UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
