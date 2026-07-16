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
                    ops_task_failure_recovery, ops_exception_ack_result, ops_operational_exception,
                    rel_outbox_publish_attempt, rel_outbox_event,
                    int_delivery_attempt, int_external_acknowledgement, int_delivery_replay_request,
                    int_outbound_delivery,
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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("076");
        assertThat(flyway.info().applied()).hasSize(78);
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
    void projectsEvidenceReviewAndFormEventsViaTaskContext() {
        Instant t0 = Instant.parse("2026-07-16T06:00:00Z");
        UUID submissionId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID reviewCaseId = UUID.randomUUID();
        UUID correctionCaseId = UUID.randomUUID();

        handler.handle(message(
                "forms", "form.submitted", 1, "FormSubmission", submissionId, 1,
                Map.of(
                        "submissionId", submissionId,
                        "taskId", taskId,
                        "projectId", projectId,
                        "formKey", "SITE_SURVEY",
                        "validationStatus", "VALIDATED",
                        "occurredAt", t0),
                t0));
        handler.handle(message(
                "evidence", "evidence.set-snapshotted", 1, "EvidenceSetSnapshot", snapshotId, 1,
                Map.of(
                        "evidenceSetSnapshotId", snapshotId,
                        "taskId", taskId,
                        "projectId", projectId,
                        "purpose", "TASK_SUBMISSION",
                        "createdAt", t0.plusSeconds(5)),
                t0.plusSeconds(5)));
        handler.handle(message(
                "evidence", "evidence.review-case-created", 1, "ReviewCase", reviewCaseId, 1,
                Map.of(
                        "reviewCaseId", reviewCaseId,
                        "taskId", taskId,
                        "projectId", projectId,
                        "createdAt", t0.plusSeconds(10)),
                t0.plusSeconds(10)));
        handler.handle(message(
                "evidence", "evidence.review-decided", 1, "ReviewCase", reviewCaseId, 2,
                Map.of(
                        "reviewCaseId", reviewCaseId,
                        "taskId", taskId,
                        "projectId", projectId,
                        "decision", "REJECTED",
                        "decidedBy", "reviewer-1",
                        "decidedAt", t0.plusSeconds(15)),
                t0.plusSeconds(15)));
        handler.handle(message(
                "evidence", "evidence.correction-case-created", 1, "CorrectionCase", correctionCaseId, 1,
                Map.of(
                        "correctionCaseId", correctionCaseId,
                        "taskId", taskId,
                        "projectId", projectId,
                        "createdAt", t0.plusSeconds(20)),
                t0.plusSeconds(20)));
        handler.handle(message(
                "evidence", "evidence.correction-closed", 1, "CorrectionCase", correctionCaseId, 2,
                Map.of(
                        "correctionCaseId", correctionCaseId,
                        "taskId", taskId,
                        "projectId", projectId,
                        "closedBy", "reviewer-1",
                        "closedAt", t0.plusSeconds(25)),
                t0.plusSeconds(25)));

        var page = timelines.list(principal("reader", TENANT), "corr-evidence", workOrderId, null, 20);
        assertThat(page.items()).extracting(WorkOrderTimelineItem::eventType)
                .containsExactly(
                        "evidence.correction-closed",
                        "evidence.correction-case-created",
                        "evidence.review-decided",
                        "evidence.review-case-created",
                        "evidence.set-snapshotted",
                        "form.submitted");
        assertThat(page.items().get(0).category()).isEqualTo("CORRECTION");
        assertThat(page.items().get(0).outcomeCode()).isEqualTo("CLOSED");
        assertThat(page.items().get(2).outcomeCode()).isEqualTo("REJECTED");
        assertThat(page.items().get(2).actorId()).isEqualTo("reviewer-1");
        assertThat(page.items().get(4).category()).isEqualTo("EVIDENCE");
        assertThat(page.items().get(5).category()).isEqualTo("FORM");
        assertThat(page.items().get(5).resourceCode()).isEqualTo("SITE_SURVEY");
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(6);

        assertThatThrownBy(() -> handler.handle(message(
                "evidence", "evidence.review-decided", 1, "ReviewCase", reviewCaseId, 3,
                Map.of(
                        "reviewCaseId", reviewCaseId,
                        "taskId", taskId,
                        "projectId", UUID.randomUUID(),
                        "decision", "APPROVED",
                        "decidedBy", "reviewer-2",
                        "decidedAt", t0.plusSeconds(30)),
                t0.plusSeconds(30))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project");
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(6);
    }

    @Test
    void projectsDeliveryCreatedAndExceptionResolvedV2() {
        Instant t0 = Instant.parse("2026-07-16T07:00:00Z");
        UUID deliveryId = UUID.randomUUID();
        UUID exceptionId = UUID.randomUUID();

        handler.handle(message(
                "integration", "integration.outbound-delivery-created", 1, "OutboundDelivery",
                deliveryId, 1,
                Map.of(
                        "deliveryId", deliveryId,
                        "projectId", projectId,
                        "sourceWorkOrderId", workOrderId,
                        "createdAt", t0),
                t0));
        handler.handle(message(
                "operations", "operational.exception.resolved", 2, "OperationalException",
                exceptionId, 2,
                Map.of(
                        "exceptionId", exceptionId,
                        "sourceTaskId", taskId,
                        "resolutionCode", "DELIVERY_ACK_RECOVERED",
                        "resolvedAt", t0.plusSeconds(10)),
                t0.plusSeconds(10)));

        var page = timelines.list(principal("reader", TENANT), "corr-delivery", workOrderId, null, 10);
        assertThat(page.items()).extracting(WorkOrderTimelineItem::eventType)
                .containsExactly("operational.exception.resolved", "integration.outbound-delivery-created");
        assertThat(page.items().get(0).category()).isEqualTo("EXCEPTION");
        assertThat(page.items().get(0).outcomeCode()).isEqualTo("DELIVERY_ACK_RECOVERED");
        assertThat(page.items().get(1).category()).isEqualTo("DELIVERY");
        assertThat(page.items().get(1).outcomeCode()).isEqualTo("CREATED");
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(2);
    }

    @Test
    void projectsDeliveryAckRecoveredAndReplayViaPublicContextPort() {
        Instant t0 = Instant.parse("2026-07-16T08:00:00Z");
        UUID deliveryId = seedOutboundDelivery();

        handler.handle(message(
                "integration", "integration.outbound-delivery-replay-requested", 1,
                "OutboundDelivery", deliveryId, 2,
                Map.of(
                        "deliveryId", deliveryId,
                        "projectId", projectId,
                        "requestedBy", "ops-1",
                        "requestedAt", t0),
                t0));
        handler.handle(message(
                "integration", "integration.outbound-delivery-acknowledged", 1,
                "OutboundDelivery", deliveryId, 3,
                Map.of(
                        "deliveryId", deliveryId,
                        "projectId", projectId,
                        "acknowledgedAt", t0.plusSeconds(10)),
                t0.plusSeconds(10)));
        handler.handle(message(
                "integration", "integration.outbound-delivery-recovered", 1,
                "OutboundDelivery", deliveryId, 3,
                Map.of(
                        "deliveryId", deliveryId,
                        "acknowledgedAt", t0.plusSeconds(20)),
                t0.plusSeconds(20)));

        var page = timelines.list(principal("reader", TENANT), "corr-delivery-ack", workOrderId, null, 10);
        assertThat(page.items()).extracting(WorkOrderTimelineItem::eventType)
                .containsExactly(
                        "integration.outbound-delivery-recovered",
                        "integration.outbound-delivery-acknowledged",
                        "integration.outbound-delivery-replay-requested");
        assertThat(page.items()).extracting(WorkOrderTimelineItem::outcomeCode)
                .containsExactly("RECOVERED", "ACKNOWLEDGED", "REPLAY_REQUESTED");
        assertThat(page.items().get(2).actorId()).isEqualTo("ops-1");
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(3);

        assertThatThrownBy(() -> handler.handle(message(
                "integration", "integration.outbound-delivery-acknowledged", 1,
                "OutboundDelivery", deliveryId, 4,
                Map.of(
                        "deliveryId", deliveryId,
                        "projectId", UUID.randomUUID(),
                        "acknowledgedAt", t0.plusSeconds(30)),
                t0.plusSeconds(30))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project");

        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> handler.handle(message(
                "integration", "integration.outbound-delivery-recovered", 1,
                "OutboundDelivery", missing, 1,
                Map.of(
                        "deliveryId", missing,
                        "acknowledgedAt", t0.plusSeconds(40)),
                t0.plusSeconds(40))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OutboundDelivery");
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(3);
    }

    @Test
    void projectsExceptionAcknowledgedViaPublicContextPort() {
        Instant t0 = Instant.parse("2026-07-16T09:00:00Z");
        UUID exceptionId = seedOperationalExceptionLinkedToTask(taskId);
        UUID unlinkedException = seedOperationalExceptionUnlinked();

        OutboxMessage ack = message(
                "operations", "operational.exception.acknowledged", 1,
                "OperationalException", exceptionId, 2,
                Map.of(
                        "exceptionId", exceptionId,
                        "status", "ACKNOWLEDGED",
                        "aggregateVersion", 2,
                        "acknowledgedAt", t0,
                        "acknowledgedBy", "ops-ack-1"),
                t0);
        handler.handle(ack);
        handler.handle(ack);

        var page = timelines.list(principal("reader", TENANT), "corr-exception-ack", workOrderId, null, 10);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.eventType()).isEqualTo("operational.exception.acknowledged");
            assertThat(item.category()).isEqualTo("EXCEPTION");
            assertThat(item.outcomeCode()).isEqualTo("ACKNOWLEDGED");
            assertThat(item.actorId()).isEqualTo("ops-ack-1");
            assertThat(item.resourceId()).isEqualTo(exceptionId);
        });
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(1);

        // 无 Task 链接：Inbox 完成但不投影。
        handler.handle(message(
                "operations", "operational.exception.acknowledged", 1,
                "OperationalException", unlinkedException, 2,
                Map.of(
                        "exceptionId", unlinkedException,
                        "status", "ACKNOWLEDGED",
                        "aggregateVersion", 2,
                        "acknowledgedAt", t0.plusSeconds(10),
                        "acknowledgedBy", "ops-ack-2"),
                t0.plusSeconds(10)));
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(1);

        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> handler.handle(message(
                "operations", "operational.exception.acknowledged", 1,
                "OperationalException", missing, 2,
                Map.of(
                        "exceptionId", missing,
                        "status", "ACKNOWLEDGED",
                        "aggregateVersion", 2,
                        "acknowledgedAt", t0.plusSeconds(20),
                        "acknowledgedBy", "ops-ack-3"),
                t0.plusSeconds(20))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OperationalException");
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(1);
    }

    @Test
    void projectsAssignmentLifecycleWithoutAssigneeLeakageAndRejectsMismatch() {
        Instant t0 = Instant.parse("2026-07-16T10:00:00Z");
        UUID assignmentId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();

        // 载荷故意携带 assigneeId；投影记录不得保留指派细节。
        Map<String, Object> pendingPayload = new HashMap<>();
        pendingPayload.put("serviceAssignmentId", assignmentId);
        pendingPayload.put("workOrderId", workOrderId);
        pendingPayload.put("taskId", taskId);
        pendingPayload.put("assigneeId", "technician-secret-001");
        pendingPayload.put("capacityReservationId", UUID.randomUUID());
        pendingPayload.put("guardId", UUID.randomUUID());
        pendingPayload.put("reasonCode", "MANUAL_REASSIGNMENT");
        pendingPayload.put("initiatedBy", "dispatch-manager");
        pendingPayload.put("occurredAt", t0);

        OutboxMessage pending = message(
                "dispatch", "service.assignment.pending-activation", 2,
                "ServiceAssignment", assignmentId, 1, pendingPayload, t0);
        handler.handle(pending);
        handler.handle(pending);

        Map<String, Object> activatedPayload = new HashMap<>(pendingPayload);
        activatedPayload.put("reasonCode", "TASK_ASSIGNMENT_PREPARED");
        activatedPayload.put("occurredAt", t0.plusSeconds(10));
        handler.handle(message(
                "dispatch", "service.assignment.activated", 2,
                "ServiceAssignment", assignmentId, 3,
                activatedPayload, t0.plusSeconds(10)));

        Map<String, Object> completedPayload = new HashMap<>();
        completedPayload.put("serviceAssignmentId", assignmentId);
        completedPayload.put("workOrderId", workOrderId);
        completedPayload.put("taskId", taskId);
        completedPayload.put("assigneeId", "technician-secret-001");
        completedPayload.put("reasonCode", "TASK_ASSIGNMENT_ACTIVATED");
        completedPayload.put("occurredAt", t0.plusSeconds(20));
        handler.handle(message(
                "dispatch", "service.assignment.activation-completed", 1,
                "ServiceAssignment", assignmentId, 4,
                completedPayload, t0.plusSeconds(20)));

        Map<String, Object> timedOutPayload = new HashMap<>();
        timedOutPayload.put("serviceAssignmentId", assignmentId);
        timedOutPayload.put("workOrderId", workOrderId);
        timedOutPayload.put("taskId", taskId);
        timedOutPayload.put("assigneeId", "technician-secret-001");
        timedOutPayload.put("errorCode", "ACTIVATION_SAGA_TIMEOUT");
        timedOutPayload.put("detectedAt", t0.plusSeconds(31));
        handler.handle(message(
                "dispatch", "service.assignment.activation-timed-out", 1,
                "ServiceAssignmentActivationSaga", sagaId, 2,
                timedOutPayload, t0.plusSeconds(31)));

        var page = timelines.list(principal("reader", TENANT), "corr-assignment", workOrderId, null, 10);
        assertThat(page.items()).extracting(WorkOrderTimelineItem::eventType)
                .containsExactly(
                        "service.assignment.activation-timed-out",
                        "service.assignment.activation-completed",
                        "service.assignment.activated",
                        "service.assignment.pending-activation");
        assertThat(page.items()).extracting(WorkOrderTimelineItem::category)
                .containsOnly("ASSIGNMENT");
        assertThat(page.items()).extracting(WorkOrderTimelineItem::resourceType)
                .containsOnly("ServiceAssignment");
        assertThat(page.items()).extracting(WorkOrderTimelineItem::outcomeCode)
                .containsExactly(
                        "ACTIVATION_SAGA_TIMEOUT",
                        "TASK_ASSIGNMENT_ACTIVATED",
                        "TASK_ASSIGNMENT_PREPARED",
                        "MANUAL_REASSIGNMENT");
        assertThat(page.items().get(3).actorId()).isEqualTo("dispatch-manager");
        assertThat(page.items()).allSatisfy(item -> {
            assertThat(item.resourceId()).isEqualTo(assignmentId);
            assertThat(item.resourceCode()).isNull();
            assertThat(objectMapper.writeValueAsString(item)).doesNotContain("technician-secret-001");
        });
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(4);

        assertThatThrownBy(() -> handler.handle(message(
                "dispatch", "service.assignment.pending-activation", 1,
                "ServiceAssignment", assignmentId, 5,
                Map.of(
                        "serviceAssignmentId", assignmentId,
                        "workOrderId", workOrderId,
                        "taskId", taskId,
                        "reasonCode", "MANUAL_REASSIGNMENT",
                        "initiatedBy", "dispatch-manager",
                        "occurredAt", t0.plusSeconds(40)),
                t0.plusSeconds(40))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");

        assertThatThrownBy(() -> handler.handle(message(
                "dispatch", "service.assignment.activated", 2,
                "ServiceAssignment", UUID.randomUUID(), 6,
                activatedPayload, t0.plusSeconds(50))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("信封");
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(4);
    }

    @Test
    void projectsTaskAssignmentGuardAndManualInterventionWithoutCandidateLeakage() {
        Instant t0 = Instant.parse("2026-07-16T11:00:00Z");
        UUID standaloneTask = task(null, null, "OPERATIONS_REPAIR");

        Map<String, Object> assignedPayload = new HashMap<>();
        assignedPayload.put("taskId", taskId);
        assignedPayload.put("assignmentBatchId", UUID.randomUUID());
        assignedPayload.put("candidatePrincipalIds", List.of("technician-secret-a", "technician-secret-b"));
        assignedPayload.put("sourceType", "ASSIGNEE_POLICY");
        assignedPayload.put("sourceId", "policy://secret/v1");
        assignedPayload.put("assignedAt", t0);

        OutboxMessage assigned = message(
                "task", "task.assigned", 1, "Task", taskId, 2, assignedPayload, t0);
        handler.handle(assigned);
        handler.handle(assigned);

        handler.handle(message(
                "task", "task.assignment-prepared", 1, "Task", taskId, 3,
                Map.of(
                        "taskId", taskId,
                        "guardId", UUID.randomUUID(),
                        "taskAssignmentId", UUID.randomUUID(),
                        "preparationKey", "prep-secret-key",
                        "principalId", "tech-principal-1",
                        "status", "PREPARED",
                        "serviceAssignmentId", "service-assignment://secret",
                        "reasonCode", "TECHNICIAN_REASSIGNMENT",
                        "occurredAt", t0.plusSeconds(10)),
                t0.plusSeconds(10)));

        handler.handle(message(
                "task", "task.execution-guard.activated", 1, "Task", taskId, 4,
                Map.of(
                        "taskId", taskId,
                        "guardId", UUID.randomUUID(),
                        "guardType", "REASSIGNMENT",
                        "guardKey", "saga://secret/001",
                        "status", "ACTIVE",
                        "reasonCode", "TECHNICIAN_REASSIGNMENT",
                        "occurredAt", t0.plusSeconds(20)),
                t0.plusSeconds(20)));

        handler.handle(message(
                "task", "task.execution.manual-intervention-required", 1, "Task", taskId, 5,
                Map.of(
                        "taskId", taskId,
                        "attemptId", UUID.randomUUID(),
                        "taskType", "integration.push-status",
                        "businessKey", "secret-business-key",
                        "attemptNo", 3,
                        "status", "MANUAL_INTERVENTION",
                        "errorCode", "REMOTE_RESULT_UNKNOWN"),
                t0.plusSeconds(30)));

        // 独立运营 Task：Inbox 完成但不投影。
        handler.handle(message(
                "task", "task.assigned", 1, "Task", standaloneTask, 2,
                Map.of(
                        "taskId", standaloneTask,
                        "assignmentBatchId", UUID.randomUUID(),
                        "candidatePrincipalIds", List.of("ops-1"),
                        "sourceType", "MANUAL",
                        "sourceId", "manual://ops",
                        "assignedAt", t0.plusSeconds(40)),
                t0.plusSeconds(40)));

        var page = timelines.list(principal("reader", TENANT), "corr-task-assign", workOrderId, null, 10);
        assertThat(page.items()).extracting(WorkOrderTimelineItem::eventType)
                .containsExactly(
                        "task.execution.manual-intervention-required",
                        "task.execution-guard.activated",
                        "task.assignment-prepared",
                        "task.assigned");
        assertThat(page.items()).extracting(WorkOrderTimelineItem::category).containsOnly("TASK");
        assertThat(page.items()).extracting(WorkOrderTimelineItem::outcomeCode)
                .containsExactly(
                        "REMOTE_RESULT_UNKNOWN",
                        "TECHNICIAN_REASSIGNMENT",
                        "TECHNICIAN_REASSIGNMENT",
                        "ASSIGNED");
        assertThat(page.items().get(2).actorId()).isEqualTo("tech-principal-1");
        assertThat(page.items()).allSatisfy(item -> {
            assertThat(item.resourceId()).isEqualTo(taskId);
            String json = objectMapper.writeValueAsString(item);
            assertThat(json).doesNotContain("technician-secret-a", "technician-secret-b",
                    "prep-secret-key", "secret-business-key", "saga://secret");
        });
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(4);

        assertThatThrownBy(() -> handler.handle(message(
                "task", "task.assignment-activated", 1, "Task", UUID.randomUUID(), 6,
                Map.of(
                        "taskId", taskId,
                        "guardId", UUID.randomUUID(),
                        "taskAssignmentId", UUID.randomUUID(),
                        "preparationKey", "prep",
                        "principalId", "tech-principal-1",
                        "status", "ACTIVE",
                        "serviceAssignmentId", "sa://1",
                        "reasonCode", "SERVICE_ASSIGNMENT_ACTIVATED",
                        "occurredAt", t0.plusSeconds(50)),
                t0.plusSeconds(50))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("信封");
        assertThat(count("rdm_work_order_timeline_entry")).isEqualTo(4);
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

    private UUID seedOutboundDelivery() {
        UUID deliveryId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO int_outbound_delivery (
                    delivery_id, tenant_id, project_id, connector_version_id, mapping_version_id,
                    business_message_type, business_key, source_review_case_id, source_task_id,
                    source_work_order_id, source_snapshot_id, source_snapshot_digest,
                    external_order_code, operator_principal_id, operator_display_value,
                    payload_object_ref, payload_digest, external_idempotency_key,
                    failure_policy_version_id, status, attempt_count, created_by, created_at,
                    aggregate_version
                ) VALUES (
                    :id, :tenantId, :projectId, 'BYD_CPIM_V731', 'MAP-1',
                    'REVIEW_SUBMISSION', :businessKey, :reviewCaseId, :taskId,
                    :workOrderId, :snapshotId, :digest,
                    'EXT-ORDER-1', 'operator', 'OP',
                    's3://private/payload', :digest, :idem,
                    'FAIL-1', 'UNKNOWN', 1, 'operator', now(), 1
                )
                """)
                .param("id", deliveryId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("businessKey", "m78:" + deliveryId)
                .param("reviewCaseId", UUID.randomUUID())
                .param("taskId", taskId)
                .param("workOrderId", workOrderId)
                .param("snapshotId", UUID.randomUUID())
                .param("digest", "a".repeat(64))
                .param("idem", "b".repeat(64))
                .update();
        return deliveryId;
    }

    private UUID seedOperationalExceptionLinkedToTask(UUID sourceTaskId) {
        UUID exceptionId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO ops_operational_exception (
                    exception_id, tenant_id, source_type, source_id, source_attempt_id, source_task_type,
                    category_code, severity_code, error_code, status, correlation_id,
                    opened_at, last_detected_at, aggregate_version)
                VALUES (
                    :id, :tenantId, 'TASK', :sourceId, :attemptId, 'integration.byd.submit-review',
                    'AUTOMATION_FINAL_FAILURE', 'P1', 'BYD_TRANSPORT_UNKNOWN', 'OPEN', 'corr-m79',
                    now(), now(), 1)
                """)
                .param("id", exceptionId)
                .param("tenantId", TENANT)
                .param("sourceId", sourceTaskId.toString())
                .param("attemptId", UUID.randomUUID())
                .update();
        return exceptionId;
    }

    private UUID seedOperationalExceptionUnlinked() {
        UUID exceptionId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO ops_operational_exception (
                    exception_id, tenant_id, source_type, source_id, source_attempt_id, source_task_type,
                    category_code, severity_code, error_code, status, correlation_id,
                    opened_at, last_detected_at, aggregate_version)
                VALUES (
                    :id, :tenantId, 'SERVICE_ASSIGNMENT', :sourceId, :attemptId, 'dispatch.timeout',
                    'AUTOMATION_FINAL_FAILURE', 'P2', 'ASSIGNMENT_TIMEOUT', 'OPEN', 'corr-m79-unlinked',
                    now(), now(), 1)
                """)
                .param("id", exceptionId)
                .param("tenantId", TENANT)
                .param("sourceId", UUID.randomUUID().toString())
                .param("attemptId", UUID.randomUUID())
                .update();
        return exceptionId;
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
