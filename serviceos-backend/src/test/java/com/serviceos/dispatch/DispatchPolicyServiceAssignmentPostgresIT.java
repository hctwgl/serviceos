package com.serviceos.dispatch;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.reliability.application.OutboxWorker;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M324：冻结 DISPATCH 在 task.created 后自动激活 NETWORK ServiceAssignment。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DispatchPolicyServiceAssignmentPostgresIT {
    private static final String TENANT = "tenant-m324-dispatch";
    private static final UUID PARTNER = UUID.fromString("32400000-0000-4000-8000-000000000001");
    private static final UUID NETWORK_STRONG = UUID.fromString("32400000-0000-4000-8000-0000000000a1");
    private static final UUID NETWORK_WEAK = UUID.fromString("32400000-0000-4000-8000-0000000000a2");

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
        registry.add("serviceos.outbox.worker-id", () -> "m324-dispatch-it");
    }

    @Autowired WorkOrderCommandService workOrders;
    @Autowired ConfigurationService configurations;
    @Autowired OutboxWorker outboxWorker;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    UUID dispatchVersionId;
    ConfigurationBundleReference bundle;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_work_order_timeline_entry, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_inbox_record, rel_idempotency_record, dsp_assignment_command_result,
                    dsp_capacity_reservation, dsp_service_assignment_activation_saga,
                    dsp_service_assignment, dsp_capacity_counter,
                    tsk_task_assignment, tsk_task_assignment_batch, tsk_task_execution_attempt, tsk_task,
                    wfl_node_instance, wfl_stage_instance, wfl_workflow_instance, wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle, cfg_configuration_asset_version,
                    prj_project_network, prj_project, aud_audit_record,
                    net_service_network, net_partner_organization CASCADE
                """).update();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'M324-DISPATCH', 'BYD', 'M324 Dispatch',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        seedNetworksAndCapacity();
        dispatchVersionId = publishBundleWithDispatchPolicy();
    }

    @Test
    void taskCreatedActivatesTopRankedNetworkFromFrozenDispatch() {
        workOrders.receive(receiveCommand(
                "M324-ORD-1", "a".repeat(64), "VINM3240001", "corr-m324", "cause-m324"));
        drainOutbox(50);

        assertThat(jdbc.sql("SELECT dispatch_policy_ref FROM tsk_task")
                .query(String.class).single()).isEqualTo("default-dispatch");
        assertThat(jdbc.sql("""
                SELECT assignee_id, status FROM dsp_service_assignment
                 WHERE responsibility_level = 'NETWORK'
                """).query().singleRow())
                .containsEntry("assignee_id", NETWORK_STRONG.toString())
                .containsEntry("status", "ACTIVE");
        assertThat(jdbc.sql("""
                SELECT status FROM dsp_capacity_reservation
                 WHERE tenant_id = :tenantId
                """).param("tenantId", TENANT).query(String.class).single())
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_inbox_record
                 WHERE consumer_name = 'task.dispatch-policy.created.v1'
                   AND status = 'SUCCEEDED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'SERVICE_DISPATCH_POLICY_APPLIED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
        assertThat(dispatchVersionId).isNotNull();
    }

    @Test
    void emptyCapacityLeavesTaskWithoutNetworkAssignment() {
        jdbc.sql("DELETE FROM dsp_capacity_counter").update();
        workOrders.receive(receiveCommand(
                "M324-ORD-2", "b".repeat(64), "VINM3240002", "corr-m324-empty", "cause-m324-empty"));
        drainOutbox(50);

        assertThat(jdbc.sql("SELECT status FROM tsk_task").query(String.class).single())
                .isEqualTo("READY");
        assertThat(jdbc.sql("SELECT count(*) FROM dsp_service_assignment")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'SERVICE_DISPATCH_POLICY_MANUAL'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
    }

    private ReceiveExternalWorkOrderCommand receiveCommand(
            String externalOrderCode,
            String payloadDigest,
            String vehicleVin,
            String correlationId,
            String causationId
    ) {
        return new ReceiveExternalWorkOrderCommand(
                TENANT, projectId, "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                externalOrderCode, payloadDigest,
                bundle.bundleId(), bundle.bundleCode(), bundle.bundleVersion(), bundle.manifestDigest(),
                "370000", "370100", "370102", "客户", "13800000000", "地址", vehicleVin,
                LocalDateTime.of(2026, 7, 19, 10, 0), correlationId, causationId);
    }

    private UUID publishBundleWithDispatchPolicy() {
        String workflow = """
                {"workflowKey":"M324_DISPATCH","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"HUMAN","nodeType":"USER_TASK","name":"派单",
                    "stageCode":"DISPATCH","taskType":"NETWORK_DISPATCH",
                    "dispatchPolicyRef":"default-dispatch"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"HUMAN"},
                   {"transitionId":"t2","from":"HUMAN","to":"END"}]}
                """.replaceAll("\\s+", "");
        String dispatch = """
                {"policyKey":"default-dispatch","version":"1.0.0",
                 "scope":{"brandCodes":["BYD_OCEAN"],"businessTypes":["HOME_CHARGING_SURVEY_INSTALL"],
                          "regionCodes":["370000"]},
                 "hardFilters":[
                   {"filterKey":"ENABLED","order":1,
                    "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                    "failureCode":"DISABLED"},
                   {"filterKey":"CAPACITY","order":2,
                    "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                    "failureCode":"NO_CAPACITY"},
                   {"filterKey":"BRAND_SCOPE","order":3,
                    "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                    "failureCode":"BRAND_MISMATCH"}],
                 "scoring":[{"factorKey":"REMAINING_CAPACITY","weight":1.0,
                    "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""}}],
                 "capacity":{"reservationRequired":true},
                 "fallback":{"onNoCandidate":"MANUAL_INTERVENTION","manualRole":"OPS","resolutionHours":4}}
                """.replaceAll("\\s+", "");
        var workflowAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "M324_DISPATCH",
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        var dispatchAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.DISPATCH, "default-dispatch",
                "1.0.0", "1.0.0", dispatch, Sha256.digest(dispatch)));
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "M324-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowAsset.versionId(), dispatchAsset.versionId())));
        return dispatchAsset.versionId();
    }

    private void seedNetworksAndCapacity() {
        jdbc.sql("""
                INSERT INTO net_partner_organization (
                    partner_organization_id, tenant_id, partner_code, partner_name,
                    partner_status, aggregate_version, created_at, updated_at
                ) VALUES (:id, :tenant, 'P-324', 'Partner 324', 'ACTIVE', 1, now(), now())
                """).param("id", PARTNER).param("tenant", TENANT).update();
        for (UUID networkId : List.of(NETWORK_STRONG, NETWORK_WEAK)) {
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
            jdbc.sql("""
                    INSERT INTO prj_project_network (
                        project_network_id, tenant_id, project_id, network_id,
                        valid_from, created_by, created_at
                    ) VALUES (
                        :id, :tenant, :projectId, :networkId,
                        now() - interval '1 day', 'm324', now()
                    )
                    """)
                    .param("id", UUID.randomUUID())
                    .param("tenant", TENANT)
                    .param("projectId", projectId)
                    .param("networkId", networkId.toString())
                    .update();
        }
        seedCapacity(NETWORK_STRONG.toString(), 10, 1);
        seedCapacity(NETWORK_WEAK.toString(), 3, 1);
    }

    private void seedCapacity(String assigneeId, int maxUnits, int occupied) {
        jdbc.sql("""
                INSERT INTO dsp_capacity_counter (
                    capacity_counter_id, tenant_id, responsibility_level, assignee_id,
                    business_type, max_units, occupied_units, version, updated_by, updated_at
                ) VALUES (
                    :id, :tenant, 'NETWORK', :assignee,
                    'HOME_CHARGING_SURVEY_INSTALL', :maxUnits, :occupied, 1, 'm324', now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("assignee", assigneeId)
                .param("maxUnits", maxUnits)
                .param("occupied", occupied)
                .update();
    }

    private void drainOutbox(int maxRounds) {
        for (int index = 0; index < maxRounds; index++) {
            OutboxWorker.RunResult result = outboxWorker.runOnce();
            if (result == OutboxWorker.RunResult.EMPTY) {
                return;
            }
        }
    }
}
