package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.WorkOrderTimelineItem;
import com.serviceos.readmodel.api.WorkOrderTimelineQueryService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.TaskClaimedPayload;
import com.serviceos.task.api.TaskCreatedPayload;
import com.serviceos.task.api.TaskReleasedPayload;
import com.serviceos.task.api.WorkflowTaskKind;
import com.serviceos.workflow.api.WorkflowStartedPayload;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderReceivedPayload;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M73/M74：真实 PostgreSQL 证明跨模块事件投影、现场履约合并、Inbox、乱序、授权与稳定分页。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkOrderTimelinePostgresIT {
    private static final String TENANT = "tenant-timeline-it";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos")
            .withUsername("serviceos_test")
            .withPassword("serviceos_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    @Qualifier("workOrderCoreTimelineHandler")
    private OutboxMessageHandler handler;

    @Autowired
    private WorkOrderTimelineQueryService timelines;

    @Autowired
    private WorkOrderCommandService workOrders;

    @Autowired
    private ConfigurationService configurations;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Flyway flyway;

    private UUID projectId;
    private ConfigurationBundleReference bundle;
    private UUID workOrderId;
    private UUID taskId;
    private UUID readerGrantId;

    @BeforeEach
    void seed() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_work_order_timeline_entry, rel_inbox_record,
                    aud_audit_record, tsk_task_execution_attempt, tsk_task,
                    rel_outbox_publish_attempt, rel_outbox_event,
                    wo_work_order, cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    auth_role_field_policy, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        projectId = project();
        bundle = bundle();
        workOrderId = receive("M73-ORDER-1");
        taskId = task(projectId, workOrderId, "SITE_SURVEY");
        readerGrantId = seedReader("reader", projectId);
    }

    @Test
    void projectsCoreEventsIdempotentlyAndQueriesByBusinessTime() {
        Instant t0 = Instant.parse("2026-07-16T01:00:00Z");
        UUID workflowId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        OutboxMessage claimed = message(
                "task", "task.claimed", 1, "Task", taskId, 2,
                new TaskClaimedPayload(taskId, "technician-1", t0.plusSeconds(30)),
                t0.plusSeconds(30));
        // claimed 先于 created 到达，仍通过 Task 公共上下文解析，不依赖投影消费顺序。
        handler.handle(claimed);
        handler.handle(message(
                "workorder", "workorder.received", 1, "WorkOrder", workOrderId, 1,
                new WorkOrderReceivedPayload(
                        workOrderId, projectId, "M73-ORDER-1", "BYD", "BYD_OCEAN",
                        "HOME_CHARGING_SURVEY_INSTALL",
                        new WorkOrderReceivedPayload.ConfigurationBundleRef(
                                bundle.bundleId(), bundle.bundleCode(), bundle.bundleVersion(),
                                bundle.manifestDigest()),
                        t0),
                t0));
        handler.handle(message(
                "workflow", "workflow.started", 1, "Workflow", workflowId, 1,
                new WorkflowStartedPayload(
                        workflowId, projectId, workOrderId, bundle.bundleId(), definitionId,
                        "SURVEY_INSTALL", "1.0.0", "b".repeat(64), t0.plusSeconds(10)),
                t0.plusSeconds(10)));
        handler.handle(message(
                "task", "task.created", 1, "Task", taskId, 1,
                new TaskCreatedPayload(
                        taskId, projectId, workOrderId, workflowId, stageId, nodeId,
                        "SURVEY_NODE", "SITE_SURVEY", WorkflowTaskKind.HUMAN, "READY",
                        definitionId, "b".repeat(64), t0.plusSeconds(20)),
                t0.plusSeconds(20)));
        OutboxMessage released = message(
                "task", "task.released", 1, "Task", taskId, 3,
                new TaskReleasedPayload(
                        taskId, "technician-1", "SHIFT_ENDED", t0.plusSeconds(40)),
                t0.plusSeconds(40));
        handler.handle(released);
        handler.handle(released);

        var first = timelines.list(principal("reader", TENANT), "corr-page-1", workOrderId, null, 2);
        assertThat(first.items()).extracting(WorkOrderTimelineItem::eventType)
                .containsExactly("task.released", "task.claimed");
        assertThat(first.items().getFirst().outcomeCode()).isEqualTo("SHIFT_ENDED");
        assertThat(first.items().get(1).actorId()).isEqualTo("technician-1");
        assertThat(first.nextCursor()).isNotBlank();
        assertThat(first.lastProjectedAt()).isNotNull();
        assertThat(first.freshnessStatus()).isEqualTo("UNKNOWN");

        var second = timelines.list(
                principal("reader", TENANT), "corr-page-2", workOrderId, first.nextCursor(), 10);
        assertThat(second.items()).extracting(WorkOrderTimelineItem::eventType)
                .containsExactly("task.created", "workflow.started", "workorder.received");
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(5);
        assertThat(count("rel_inbox_record")).isEqualTo(5);

        OutboxMessage changedDigest = new OutboxMessage(
                released.outboxId(), released.eventId(), released.module(), released.eventType(),
                released.schemaVersion(), released.aggregateType(), released.aggregateId(),
                released.aggregateVersion(), released.tenantId(), released.correlationId(),
                released.causationId(), released.partitionKey(), released.payload(),
                "f".repeat(64), released.occurredAt(), 2);
        assertThatThrownBy(() -> handler.handle(changedDigest))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.EVENT_PAYLOAD_MISMATCH));
    }

    @Test
    void rejectsIdentityMismatchAndExplicitlyIgnoresNonWorkOrderTask() {
        Instant occurredAt = Instant.parse("2026-07-16T02:00:00Z");
        UUID workflowId = UUID.randomUUID();
        OutboxMessage wrongProject = message(
                "workflow", "workflow.started", 1, "Workflow", workflowId, 1,
                new WorkflowStartedPayload(
                        workflowId, UUID.randomUUID(), workOrderId, bundle.bundleId(),
                        UUID.randomUUID(), "SURVEY_INSTALL", "1.0.0", "c".repeat(64), occurredAt),
                occurredAt);
        assertThatThrownBy(() -> handler.handle(wrongProject))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project");
        assertThat(count("rdm_work_order_timeline_entry")).isZero();
        assertThat(count("rel_inbox_record")).isZero();

        UUID standaloneTask = task(null, null, "OPERATIONS_REPAIR");
        OutboxMessage standalone = message(
                "task", "task.claimed", 1, "Task", standaloneTask, 2,
                new TaskClaimedPayload(standaloneTask, "operator", occurredAt.plusSeconds(10)),
                occurredAt.plusSeconds(10));
        handler.handle(standalone);
        assertThat(count("rdm_work_order_timeline_entry")).isZero();
        assertThat(jdbc.sql("""
                SELECT status FROM rel_inbox_record WHERE event_id=:eventId
                """).param("eventId", standalone.eventId()).query(String.class).single())
                .isEqualTo("SUCCEEDED");
    }

    @Test
    void cursorAndEveryPageFailClosedAfterAuthorizationChanges() {
        Instant occurredAt = Instant.parse("2026-07-16T03:00:00Z");
        handler.handle(message(
                "task", "task.claimed", 1, "Task", taskId, 2,
                new TaskClaimedPayload(taskId, "technician-1", occurredAt), occurredAt));
        handler.handle(message(
                "task", "task.released", 1, "Task", taskId, 3,
                new TaskReleasedPayload(
                        taskId, "technician-1", "SHIFT_ENDED", occurredAt.plusSeconds(1)),
                occurredAt.plusSeconds(1)));

        CurrentPrincipal reader = principal("reader", TENANT);
        var first = timelines.list(reader, "corr-before-revoke", workOrderId, null, 1);
        UUID anotherWorkOrder = receive("M73-ORDER-2");
        assertThatThrownBy(() -> timelines.list(
                reader, "corr-wrong-cursor", anotherWorkOrder, first.nextCursor(), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor");

        jdbc.sql("DELETE FROM auth_role_grant WHERE grant_id=:grantId")
                .param("grantId", readerGrantId)
                .update();
        assertThatThrownBy(() -> timelines.list(
                reader, "corr-revoked", workOrderId, first.nextCursor(), 1))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThat(jdbc.sql("""
                SELECT decision_code FROM aud_audit_record WHERE correlation_id='corr-revoked'
                """).query(String.class).single()).isEqualTo("DENY");

        assertThatThrownBy(() -> timelines.list(
                principal("reader", "another-tenant"),
                "corr-cross", workOrderId, null, 10))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("073");
        assertThat(flyway.info().applied()).hasSize(75);
    }

    @Test
    void projectsSlaEventsViaTaskContextAndIgnoresStandaloneTask() {
        Instant t0 = Instant.parse("2026-07-16T05:00:00Z");
        UUID slaInstanceId = UUID.randomUUID();

        handler.handle(message(
                "sla", "sla.started", 1, "SlaInstance", slaInstanceId, 1,
                Map.of(
                        "slaInstanceId", slaInstanceId,
                        "taskId", taskId,
                        "projectId", projectId,
                        "workOrderId", workOrderId,
                        "slaRef", "TASK_ELAPSED_4H",
                        "startedAt", t0),
                t0));
        handler.handle(message(
                "sla", "sla.breached", 1, "SlaInstance", slaInstanceId, 2,
                Map.of(
                        "slaInstanceId", slaInstanceId,
                        "taskId", taskId,
                        "detectedAt", t0.plusSeconds(10)),
                t0.plusSeconds(10)));
        OutboxMessage met = message(
                "sla", "sla.met", 1, "SlaInstance", slaInstanceId, 3,
                Map.of(
                        "slaInstanceId", slaInstanceId,
                        "taskId", taskId,
                        "status", "MET_LATE",
                        "completedAt", t0.plusSeconds(20)),
                t0.plusSeconds(20));
        handler.handle(met);
        handler.handle(met);

        var page = timelines.list(principal("reader", TENANT), "corr-sla", workOrderId, null, 10);
        assertThat(page.items()).extracting(WorkOrderTimelineItem::eventType)
                .containsExactly("sla.met", "sla.breached", "sla.started");
        assertThat(page.items()).extracting(WorkOrderTimelineItem::category)
                .containsOnly("SLA");
        assertThat(page.items().get(0).outcomeCode()).isEqualTo("MET_LATE");
        assertThat(page.items().get(1).outcomeCode()).isEqualTo("BREACHED");
        assertThat(page.items().get(2).resourceCode()).isEqualTo("TASK_ELAPSED_4H");
        assertThat(page.items().get(2).outcomeCode()).isEqualTo("STARTED");
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(3);

        UUID standaloneTask = task(null, null, "OPERATIONS_REPAIR");
        UUID standaloneSla = UUID.randomUUID();
        OutboxMessage standalone = message(
                "sla", "sla.breached", 1, "SlaInstance", standaloneSla, 2,
                Map.of(
                        "slaInstanceId", standaloneSla,
                        "taskId", standaloneTask,
                        "detectedAt", t0.plusSeconds(30)),
                t0.plusSeconds(30));
        handler.handle(standalone);
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(3);
        assertThat(jdbc.sql("""
                SELECT status FROM rel_inbox_record WHERE event_id=:eventId
                """).param("eventId", standalone.eventId()).query(String.class).single())
                .isEqualTo("SUCCEEDED");
        assertThat(count("rel_inbox_record")).isEqualTo(4);
    }

    @Test
    void projectsFieldOpsEventsWithoutSensitivePayloadAndRejectsMismatch() {
        Instant t0 = Instant.parse("2026-07-16T04:00:00Z");
        UUID appointmentId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();

        OutboxMessage contact = message(
                "appointment", "contact.attempt.recorded", 1, "ContactAttempt", contactId, 1,
                Map.of(
                        "contactAttemptId", contactId,
                        "projectId", projectId,
                        "workOrderId", workOrderId,
                        "taskId", taskId,
                        "channel", "PHONE",
                        "resultCode", "NO_ANSWER",
                        "actorId", "csr-1",
                        "occurredAt", t0),
                t0);
        handler.handle(contact);
        handler.handle(contact);

        handler.handle(message(
                "appointment", "appointment.proposed", 1, "Appointment", appointmentId, 1,
                Map.of(
                        "appointmentId", appointmentId,
                        "projectId", projectId,
                        "workOrderId", workOrderId,
                        "taskId", taskId,
                        "appointmentType", "SURVEY",
                        "status", "PROPOSED",
                        "occurredAt", t0.plusSeconds(10)),
                t0.plusSeconds(10)));
        handler.handle(message(
                "appointment", "appointment.rescheduled", 1, "Appointment", appointmentId, 3,
                Map.of(
                        "appointmentId", appointmentId,
                        "projectId", projectId,
                        "workOrderId", workOrderId,
                        "taskId", taskId,
                        "appointmentType", "SURVEY",
                        "status", "PROPOSED",
                        "occurredAt", t0.plusSeconds(20)),
                t0.plusSeconds(20)));
        handler.handle(message(
                "appointment", "appointment.cancelled", 1, "Appointment", appointmentId, 4,
                Map.of(
                        "appointmentId", appointmentId,
                        "projectId", projectId,
                        "workOrderId", workOrderId,
                        "taskId", taskId,
                        "appointmentType", "SURVEY",
                        "status", "CANCELLED",
                        "reasonCode", "USER_CANCELLED",
                        "occurredAt", t0.plusSeconds(30)),
                t0.plusSeconds(30)));
        handler.handle(message(
                "fieldwork", "visit.checked-in", 1, "Visit", visitId, 1,
                visitPayload(visitId, "IN_PROGRESS", null, null, t0.plusSeconds(40)),
                t0.plusSeconds(40)));
        handler.handle(message(
                "fieldwork", "visit.checked-out", 1, "Visit", visitId, 2,
                visitPayload(visitId, "COMPLETED", "JOB_DONE", null, t0.plusSeconds(50)),
                t0.plusSeconds(50)));

        var page = timelines.list(principal("reader", TENANT), "corr-field-ops", workOrderId, null, 20);
        assertThat(page.items()).extracting(WorkOrderTimelineItem::eventType)
                .containsExactly(
                        "visit.checked-out",
                        "visit.checked-in",
                        "appointment.cancelled",
                        "appointment.rescheduled",
                        "appointment.proposed",
                        "contact.attempt.recorded");
        assertThat(page.items()).extracting(WorkOrderTimelineItem::category)
                .containsExactly(
                        "VISIT", "VISIT", "APPOINTMENT", "APPOINTMENT", "APPOINTMENT", "CONTACT_ATTEMPT");
        assertThat(page.items().get(0).outcomeCode()).isEqualTo("JOB_DONE");
        assertThat(page.items().get(0).actorId()).isEqualTo("tech-9");
        assertThat(page.items().get(1).outcomeCode()).isEqualTo("IN_PROGRESS");
        assertThat(page.items().get(2).outcomeCode()).isEqualTo("USER_CANCELLED");
        assertThat(page.items().get(3).outcomeCode()).isEqualTo("RESCHEDULED");
        assertThat(page.items().get(3).resourceCode()).isEqualTo("SURVEY");
        assertThat(page.items().get(5).outcomeCode()).isEqualTo("NO_ANSWER");
        assertThat(page.items().get(5).resourceCode()).isEqualTo("PHONE");
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(6);
        assertThat(count("rel_inbox_record")).isEqualTo(6);

        UUID wrongVisit = UUID.randomUUID();
        assertThatThrownBy(() -> handler.handle(message(
                "fieldwork", "visit.interrupted", 1, "Visit", wrongVisit, 2,
                visitPayload(wrongVisit, UUID.randomUUID(), "INTERRUPTED", null, "PARTS_MISSING",
                        t0.plusSeconds(60)),
                t0.plusSeconds(60))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project");
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(6);
        assertThat(count("rel_inbox_record")).isEqualTo(6);

        OutboxMessage changedDigest = new OutboxMessage(
                contact.outboxId(), contact.eventId(), contact.module(), contact.eventType(),
                contact.schemaVersion(), contact.aggregateType(), contact.aggregateId(),
                contact.aggregateVersion(), contact.tenantId(), contact.correlationId(),
                contact.causationId(), contact.partitionKey(), contact.payload(),
                "e".repeat(64), contact.occurredAt(), 2);
        assertThatThrownBy(() -> handler.handle(changedDigest))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.EVENT_PAYLOAD_MISMATCH));
    }

    private Map<String, Object> visitPayload(
            UUID visitId,
            String status,
            String resultCode,
            String exceptionCode,
            Instant occurredAt
    ) {
        return visitPayload(visitId, projectId, status, resultCode, exceptionCode, occurredAt);
    }

    private Map<String, Object> visitPayload(
            UUID visitId,
            UUID project,
            String status,
            String resultCode,
            String exceptionCode,
            Instant occurredAt
    ) {
        // Map.of 禁止 null；Visit checkout/interrupt 的互斥编码需要显式 null。
        Map<String, Object> payload = new HashMap<>();
        payload.put("visitId", visitId);
        payload.put("projectId", project);
        payload.put("workOrderId", workOrderId);
        payload.put("taskId", taskId);
        payload.put("technicianId", "tech-9");
        payload.put("status", status);
        payload.put("resultCode", resultCode);
        payload.put("exceptionCode", exceptionCode);
        payload.put("occurredAt", occurredAt);
        return payload;
    }

    private OutboxMessage message(
            String module,
            String eventType,
            int schemaVersion,
            String aggregateType,
            UUID aggregateId,
            long aggregateVersion,
            Object payload,
            Instant occurredAt
    ) {
        String json = objectMapper.writeValueAsString(payload);
        return new OutboxMessage(
                UUID.randomUUID(), UUID.randomUUID(), module, eventType, schemaVersion,
                aggregateType, aggregateId.toString(), aggregateVersion, TENANT,
                "corr-" + eventType, UUID.randomUUID().toString(), aggregateId.toString(),
                json, Sha256.digest(json), occurredAt, 1);
    }

    private UUID project() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id,tenant_id,project_code,client_id,project_name,starts_on,
                    project_status,aggregate_version,created_at
                ) VALUES (
                    :id,:tenantId,'M73','BYD','M73 项目',current_date,'ACTIVE',1,now()
                )
                """).param("id", id).param("tenantId", TENANT).update();
        return id;
    }

    private ConfigurationBundleReference bundle() {
        String definition = "{\"workflowCode\":\"M73\"}";
        UUID assetId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "M73-WF", "1.0.0", "1.0.0",
                definition, Sha256.digest(definition))).versionId();
        return configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "M73-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(assetId)));
    }

    private UUID receive(String externalOrderCode) {
        return workOrders.receive(new ReceiveExternalWorkOrderCommand(
                TENANT, projectId, "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                externalOrderCode, Sha256.digest(externalOrderCode), bundle.bundleId(),
                bundle.bundleCode(), bundle.bundleVersion(), bundle.manifestDigest(),
                "370000", "370100", "370102", "敏感姓名", "13800000000", "敏感地址",
                "VIN123456789", LocalDateTime.of(2026, 7, 16, 8, 0),
                "corr-receive-" + externalOrderCode, "cause-m73")).workOrderId();
    }

    private UUID task(UUID project, UUID workOrder, String taskType) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        UUID workflowId = workOrder == null ? null : UUID.randomUUID();
        UUID stageId = workOrder == null ? null : UUID.randomUUID();
        UUID nodeId = workOrder == null ? null : UUID.randomUUID();
        UUID definitionId = workOrder == null ? null : UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id,tenant_id,task_type,task_kind,business_key,payload_digest,
                    priority,status,next_run_at,attempt_count,max_attempts,correlation_id,
                    version,created_at,updated_at,project_id,work_order_id,
                    workflow_instance_id,stage_instance_id,workflow_node_instance_id,
                    workflow_node_id,workflow_definition_version_id,workflow_definition_digest,
                    configuration_bundle_id,configuration_bundle_digest,stage_code
                ) VALUES (
                    :id,:tenantId,:taskType,'HUMAN',:businessKey,:digest,
                    500,'READY',:now,0,3,'corr-task',1,:now,:now,:projectId,:workOrderId,
                    :workflowId,:stageId,:nodeId,:nodeCode,:definitionId,:definitionDigest,
                    :bundleId,:bundleDigest,:stageCode
                )
                """)
                .param("id", id)
                .param("tenantId", TENANT)
                .param("taskType", taskType)
                .param("businessKey", "m73:" + id)
                .param("digest", "a".repeat(64))
                .param("now", java.sql.Timestamp.from(now))
                .param("projectId", project)
                .param("workOrderId", workOrder)
                .param("workflowId", workflowId)
                .param("stageId", stageId)
                .param("nodeId", nodeId)
                .param("nodeCode", workOrder == null ? null : "SURVEY_NODE")
                .param("definitionId", definitionId)
                .param("definitionDigest", workOrder == null ? null : "b".repeat(64))
                .param("bundleId", workOrder == null ? null : bundle.bundleId())
                .param("bundleDigest", workOrder == null ? null : bundle.manifestDigest())
                .param("stageCode", workOrder == null ? null : "SURVEY")
                .update();
        return id;
    }

    private UUID seedReader(String principalId, UUID project) {
        UUID roleId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (
                    role_id,tenant_id,role_code,role_name,role_status,created_at
                ) VALUES (:id,:tenantId,'M73_READER','M73 Reader','ACTIVE',now())
                """).param("id", roleId).param("tenantId", TENANT).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id,capability_code,granted_at)
                VALUES (:id,'workOrder.read',now())
                """).param("id", roleId).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id,tenant_id,principal_id,role_id,scope_type,scope_ref,
                    valid_from,source_code,approval_ref,created_at
                ) VALUES (
                    :grantId,:tenantId,:principalId,:roleId,'PROJECT',:projectId,
                    now()-interval '1 day','TEST','m73',now()
                )
                """)
                .param("grantId", grantId)
                .param("tenantId", TENANT)
                .param("principalId", principalId)
                .param("roleId", roleId)
                .param("projectId", project.toString())
                .update();
        return grantId;
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private static CurrentPrincipal principal(String principalId, String tenantId) {
        return new CurrentPrincipal(
                principalId, tenantId, CurrentPrincipal.PrincipalType.USER, "m73", Set.of());
    }
}
