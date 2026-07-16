package com.serviceos.workflow;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.reliability.application.OutboxWorker;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxPublisher;
import com.serviceos.shared.Sha256;
import com.serviceos.task.application.TaskExecutionQueue;
import com.serviceos.task.application.TaskExecutionWorker;
import com.serviceos.task.application.TaskHandlerRegistry;
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.AssignTaskCandidatesCommand;
import com.serviceos.task.api.AssignmentSourceType;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.StartHumanTaskCommand;
import com.serviceos.task.api.TaskAssignmentService;
import com.serviceos.task.api.WorkOrderTaskQueryService;
import com.serviceos.task.api.WorkOrderTaskSummary;
import com.serviceos.task.spi.AutomatedTaskHandler;
import com.serviceos.task.spi.TaskExecutionContext;
import com.serviceos.task.spi.TaskExecutionResult;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workflow.api.WorkflowExecutionQueryService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** M18：以真实 PostgreSQL 验证 TaskCompleted、Inbox 与下一节点创建的原子闭环。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkflowLinearProgressionPostgresIT {
    private static final String TENANT = "tenant-workflow-m18-it";

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
        registry.add("serviceos.outbox.worker-id", () -> "workflow-m18-it");
    }

    @Autowired WorkOrderCommandService workOrders;
    @Autowired HumanTaskCommandService humanTasks;
    @Autowired TaskAssignmentService taskAssignments;
    @Autowired ConfigurationService configurations;
    @Autowired TaskExecutionQueue taskQueue;
    @Autowired OutboxWorker outboxWorker;
    @Autowired OutboxPublisher publisher;
    @Autowired JdbcClient jdbc;
    @Autowired WorkflowExecutionQueryService workflowQueries;
    @Autowired WorkOrderTaskQueryService taskQueries;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_work_order_timeline_entry, rel_outbox_publish_attempt, rel_outbox_event, rel_inbox_record,
                    tsk_task_execution_attempt, tsk_task, wfl_node_instance,
                    wfl_stage_instance, wfl_workflow_instance, wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    auth_role_field_policy, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        seedHumanTaskGrant();
    }

    @Test
    void completedAutomatedTaskAdvancesTheFrozenWorkflowExactlyOnce() throws Exception {
        Scope scope = scope(linearWorkflow("INTAKE"));
        workOrders.receive(command(scope));
        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();

        TaskExecutionWorker taskWorker = taskWorker();
        assertThat(taskWorker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        assertThat(jdbc.sql("""
                SELECT event_type FROM rel_outbox_event
                 WHERE event_type IN ('task.execution.succeeded', 'task.completed')
                 ORDER BY event_type
                """).query(String.class).list())
                .containsExactly("task.completed", "task.execution.succeeded");

        publishUntil("task.completed");
        assertThat(jdbc.sql("""
                SELECT node_id || ':' || status FROM wfl_node_instance ORDER BY activated_at, node_id
                """).query(String.class).list())
                .containsExactly("ASSIGN_COORDINATORS:COMPLETED", "INITIAL_REVIEW:ACTIVE");
        assertThat(jdbc.sql("""
                SELECT task_type || ':' || status FROM tsk_task ORDER BY created_at, task_type
                """).query(String.class).list())
                .containsExactly("ASSIGN_COORDINATORS:SUCCEEDED", "INITIAL_REVIEW:PENDING");
        assertThat(jdbc.sql("SELECT form_ref FROM tsk_task ORDER BY created_at, task_type")
                .query(String.class).list()).containsExactly("intake.form", "review.form");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_inbox_record
                 WHERE consumer_name = 'workflow.task-completed.v1' AND status = 'SUCCEEDED'
                """).query(Long.class).single()).isEqualTo(1);

        OutboxMessage completed = eventMessage("task.completed");
        publisher.publish(completed);
        assertThat(count("wfl_node_instance")).isEqualTo(2);
        assertThat(count("tsk_task")).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type = 'task.created'")
                .query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void completedTaskActivatesTheOnlyUnconditionalNextStageAtomically() {
        Scope scope = scope(linearWorkflow("REVIEW"));
        var received = workOrders.receive(command(scope));
        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();
        assertThat(taskWorker().runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);

        publishUntil("task.completed");

        assertThat(jdbc.sql("SELECT stage_code || ':' || status FROM wfl_stage_instance ORDER BY sequence_no")
                .query(String.class).list()).containsExactly("INTAKE:COMPLETED", "REVIEW:ACTIVE");
        assertThat(jdbc.sql("SELECT node_id || ':' || status FROM wfl_node_instance ORDER BY activated_at")
                .query(String.class).list())
                .containsExactly("ASSIGN_COORDINATORS:COMPLETED", "INITIAL_REVIEW:ACTIVE");
        assertThat(count("tsk_task")).isEqualTo(2);
        assertThat(jdbc.sql("""
                SELECT event_type FROM rel_outbox_event
                 WHERE event_type IN ('stage.completed', 'stage.activated') ORDER BY event_type
                """).query(String.class).list())
                .containsExactly("stage.activated", "stage.activated", "stage.completed");

        CurrentPrincipal reader = reader();
        var execution = workflowQueries.get(reader, "corr-m69-stages", received.workOrderId());
        assertThat(execution.workflow().status()).isEqualTo("ACTIVE");
        assertThat(execution.stages()).extracting(stage -> stage.stageCode() + ":" + stage.status())
                .containsExactly("INTAKE:COMPLETED", "REVIEW:ACTIVE");
        var first = taskQueries.list(reader, "corr-m69-tasks-1", received.workOrderId(), null, 1);
        var second = taskQueries.list(reader, "corr-m69-tasks-2", received.workOrderId(), first.nextCursor(), 1);
        assertThat(first.items()).extracting(WorkOrderTaskSummary::taskType).containsExactly("ASSIGN_COORDINATORS");
        assertThat(second.items()).extracting(WorkOrderTaskSummary::taskType).containsExactly("INITIAL_REVIEW");
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void receivedWorkOrderBeforeAsyncInitializationExposesExplicitEmptyExecutionProjection() {
        Scope scope = scope(linearWorkflow("REVIEW"));
        var received = workOrders.receive(command(scope));

        var execution = workflowQueries.get(reader(), "corr-m69-not-initialized", received.workOrderId());

        assertThat(execution.workflow()).isNull();
        assertThat(execution.stages()).isEmpty();
        assertThat(taskQueries.list(reader(), "corr-m69-no-tasks", received.workOrderId(), null, 20).items())
                .isEmpty();
    }

    @Test
    void completedTaskReachingEndCompletesStageAndWorkflowWithoutCreatingAnotherTask() {
        Scope scope = scope(endWorkflow());
        workOrders.receive(command(scope));
        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();
        assertThat(taskWorker().runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);

        publishUntil("task.completed");

        assertThat(jdbc.sql("SELECT status FROM wfl_node_instance")
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT status FROM wfl_stage_instance")
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT status FROM wfl_workflow_instance")
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT status FROM wo_work_order")
                .query(String.class).single()).isEqualTo("FULFILLED");
        assertThat(count("tsk_task")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type = 'workflow.completed'")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type = 'workorder.fulfilled'")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void endProgressionRollsBackAllWorkflowFactsWhenWorkOrderCannotBeFulfilled() {
        Scope scope = scope(endWorkflow());
        workOrders.receive(command(scope));
        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();
        assertThat(taskWorker().runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);

        // 模拟任务完成前工单已被其他业务路径取消，验证 END 推进与履约必须保持同一事务边界。
        jdbc.sql("UPDATE wo_work_order SET status = 'CANCELLED' WHERE tenant_id = :tenantId")
                .param("tenantId", TENANT)
                .update();
        publishUntilAttempted("task.completed");

        assertThat(jdbc.sql("SELECT status FROM rel_outbox_event WHERE event_type = 'task.completed'")
                .query(String.class).single()).isEqualTo("FAILED");
        assertThat(jdbc.sql("SELECT status FROM wfl_node_instance")
                .query(String.class).single()).isEqualTo("ACTIVE");
        assertThat(jdbc.sql("SELECT status FROM wfl_stage_instance")
                .query(String.class).single()).isEqualTo("ACTIVE");
        assertThat(jdbc.sql("SELECT status FROM wfl_workflow_instance")
                .query(String.class).single()).isEqualTo("ACTIVE");
        assertThat(jdbc.sql("SELECT status FROM wo_work_order")
                .query(String.class).single()).isEqualTo("CANCELLED");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_outbox_event
                 WHERE event_type IN ('stage.completed', 'workflow.completed', 'workorder.fulfilled')
                """).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_inbox_record
                 WHERE consumer_name = 'workflow.task-completed.v1'
                """).query(Long.class).single()).isZero();
    }

    @Test
    void humanTaskClaimStartCompleteDrivesTheSameReliableEndProgression() {
        Scope scope = scope(humanEndWorkflow());
        workOrders.receive(command(scope));
        assertThat(outboxWorker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        drainOutbox();
        UUID taskId = jdbc.sql("SELECT task_id FROM tsk_task")
                .query(UUID.class).single();

        CurrentPrincipal actor = new CurrentPrincipal(
                "human-actor", TENANT, CurrentPrincipal.PrincipalType.USER, "m20-workflow-it",
                java.util.Set.of("task.assign", "task.claim", "task.start", "task.complete"));
        taskAssignments.assignCandidates(
                actor, new CommandMetadata("corr-human-assign", "idem-human-assign"),
                new AssignTaskCandidatesCommand(
                        taskId, 1, java.util.List.of("human-actor"),
                        AssignmentSourceType.ASSIGNEE_POLICY, "policy://workflow/human"));
        humanTasks.claim(actor, new CommandMetadata("corr-human-claim", "idem-human-claim"),
                new ClaimHumanTaskCommand(taskId, 2));
        humanTasks.start(actor, new CommandMetadata("corr-human-start", "idem-human-start"),
                new StartHumanTaskCommand(taskId, 3));
        humanTasks.complete(actor, new CommandMetadata("corr-human-complete", "idem-human-complete"),
                new CompleteHumanTaskCommand(taskId, 4, "survey://submission/1", "f".repeat(64)));
        publishUntil("task.completed");

        assertThat(jdbc.sql("SELECT status FROM tsk_task")
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT status FROM wfl_node_instance")
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT status FROM wfl_stage_instance")
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT status FROM wfl_workflow_instance")
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT status FROM wo_work_order")
                .query(String.class).single()).isEqualTo("FULFILLED");
        assertThat(jdbc.sql("""
                SELECT event_type FROM rel_outbox_event
                 WHERE event_type IN ('task.claimed', 'task.started', 'task.completed',
                                      'workflow.completed', 'workorder.fulfilled')
                 ORDER BY created_at, event_type
                """).query(String.class).list())
                .containsExactlyInAnyOrder(
                        "task.claimed", "task.started", "task.completed",
                        "workflow.completed", "workorder.fulfilled");
    }

    private TaskExecutionWorker taskWorker() {
        AutomatedTaskHandler handler = new AutomatedTaskHandler() {
            @Override
            public String taskType() {
                return "ASSIGN_COORDINATORS";
            }

            @Override
            public TaskExecutionResult execute(TaskExecutionContext context) {
                return TaskExecutionResult.succeeded("assignment://" + context.taskId());
            }
        };
        return new TaskExecutionWorker(
                taskQueue, new TaskHandlerRegistry(List.of(handler)),
                "m18-task-worker", Duration.ofSeconds(30));
    }

    private void drainOutbox() {
        for (int index = 0; index < 20; index++) {
            if (outboxWorker.runOnce() == OutboxWorker.RunResult.EMPTY) {
                return;
            }
        }
        throw new AssertionError("outbox did not drain");
    }

    private void publishUntil(String eventType) {
        for (int index = 0; index < 20; index++) {
            String status = jdbc.sql("SELECT status FROM rel_outbox_event WHERE event_type = :eventType")
                    .param("eventType", eventType).query(String.class).single();
            if ("PUBLISHED".equals(status)) {
                return;
            }
            OutboxWorker.RunResult result = outboxWorker.runOnce();
            if (result == OutboxWorker.RunResult.EMPTY) {
                String diagnostic = jdbc.sql("""
                                SELECT status || ':' || COALESCE(last_error_code, 'none')
                                  FROM rel_outbox_event
                                 WHERE event_type = :eventType
                                """)
                        .param("eventType", eventType).query(String.class).single();
                throw new AssertionError(eventType + " cannot be published: " + diagnostic);
            }
        }
        throw new AssertionError(eventType + " was not published");
    }

    private void publishUntilAttempted(String eventType) {
        for (int index = 0; index < 20; index++) {
            String status = jdbc.sql("SELECT status FROM rel_outbox_event WHERE event_type = :eventType")
                    .param("eventType", eventType).query(String.class).single();
            if (!"PENDING".equals(status)) {
                return;
            }
            assertThat(outboxWorker.runOnce()).isNotEqualTo(OutboxWorker.RunResult.EMPTY);
        }
        throw new AssertionError(eventType + " was not attempted");
    }

    private Scope scope(String workflowDefinition) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'BYD-WORKFLOW-M18', 'BYD', 'M18 线性推进测试',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        String normalizedDefinition = workflowDefinition.trim();
        String digest = Sha256.digest(normalizedDefinition);
        UUID assetId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "BYD-WORKFLOW-M18", "1.0.0", "1.0.0",
                normalizedDefinition, digest)).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "BYD-WORKFLOW-M18-BUNDLE", "1.0.0", "BYD_OCEAN",
                        "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                        null, List.of(assetId)));
        return new Scope(projectId, bundle);
    }

    private ReceiveExternalWorkOrderCommand command(Scope scope) {
        return new ReceiveExternalWorkOrderCommand(
                TENANT, scope.projectId(), "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                "BYD-WORKFLOW-M18-ORDER", "c".repeat(64), scope.bundle().bundleId(),
                scope.bundle().bundleCode(), scope.bundle().bundleVersion(), scope.bundle().manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000", "济南测试地址",
                "LGXCE6CD0RA123456", LocalDateTime.of(2026, 7, 14, 9, 0),
                "corr-workflow-m18", "cause-workflow-m18");
    }

    private OutboxMessage eventMessage(String eventType) {
        return jdbc.sql("SELECT * FROM rel_outbox_event WHERE event_type = :eventType")
                .param("eventType", eventType).query(this::mapMessage).single();
    }

    private OutboxMessage mapMessage(ResultSet rs, int rowNumber) throws SQLException {
        return new OutboxMessage(
                rs.getObject("outbox_id", UUID.class), rs.getObject("event_id", UUID.class),
                rs.getString("module_name"), rs.getString("event_type"), rs.getInt("schema_version"),
                rs.getString("aggregate_type"), rs.getString("aggregate_id"), rs.getLong("aggregate_version"),
                rs.getString("tenant_id"), rs.getString("correlation_id"), rs.getString("causation_id"),
                rs.getString("partition_key"), rs.getString("payload"), rs.getString("payload_digest"),
                rs.getTimestamp("occurred_at").toInstant(), 1,
                rs.getString("trace_parent"), rs.getString("trace_state"));
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private static String linearWorkflow(String nextStage) {
        return """
                {"workflowKey":"byd.survey-install","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"ASSIGN_COORDINATORS","nodeType":"SERVICE_TASK","name":"分配跟进人",
                    "stageCode":"INTAKE","taskType":"ASSIGN_COORDINATORS","formRef":"intake.form"},
                   {"nodeId":"INITIAL_REVIEW","nodeType":"SERVICE_TASK","name":"工单初审",
                    "stageCode":"%s","taskType":"INITIAL_REVIEW","formRef":"review.form"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"ASSIGN_COORDINATORS"},
                   {"transitionId":"t2","from":"ASSIGN_COORDINATORS","to":"INITIAL_REVIEW"}]}
                """.formatted(nextStage);
    }

    private static String endWorkflow() {
        return """
                {"workflowKey":"byd.survey-install","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"ASSIGN_COORDINATORS","nodeType":"SERVICE_TASK","name":"分配跟进人",
                    "stageCode":"INTAKE","taskType":"ASSIGN_COORDINATORS"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"ASSIGN_COORDINATORS"},
                   {"transitionId":"t2","from":"ASSIGN_COORDINATORS","to":"END"}]}
                """;
    }

    private static String humanEndWorkflow() {
        return """
                {"workflowKey":"byd.human-survey","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"SITE_SURVEY","nodeType":"USER_TASK","name":"现场勘测",
                    "stageCode":"SURVEY","taskType":"SITE_SURVEY"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"SITE_SURVEY"},
                   {"transitionId":"t2","from":"SITE_SURVEY","to":"END"}]}
                """;
    }

    private void seedHumanTaskGrant() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, 'workflow-human-worker', '流程人工执行人', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT).update();
        for (String capability : java.util.Set.of(
                "task.assign", "task.claim", "task.start", "task.complete", "task.release", "workOrder.read")) {
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
                    :grantId, :tenantId, 'human-actor', :roleId, 'TENANT', :tenantId,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M20-WORKFLOW-IT', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("roleId", roleId).update();
    }

    private record Scope(UUID projectId, ConfigurationBundleReference bundle) {
    }

    private static CurrentPrincipal reader() {
        return new CurrentPrincipal("human-actor", TENANT, CurrentPrincipal.PrincipalType.USER,
                "m69-workspace-it", java.util.Set.of());
    }
}
