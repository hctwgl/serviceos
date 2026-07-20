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
 * M366 / ADR-088：自动 TECHNICIAN 池按师傅声明 ∩ Bundle 定向目标硬过滤。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DispatchClientKindFilterPostgresIT {
    private static final String TENANT = "tenant-m366-dispatch";
    private static final UUID PARTNER = UUID.fromString("36600000-0000-4000-8000-000000000001");
    private static final UUID NETWORK = UUID.fromString("36600000-0000-4000-8000-0000000000a1");
    private static final UUID TECH_IOS = UUID.fromString("36600000-0000-4000-8000-0000000000b1");
    private static final UUID TECH_WEB = UUID.fromString("36600000-0000-4000-8000-0000000000b2");
    private static final UUID TECH_UNDECLARED = UUID.fromString("36600000-0000-4000-8000-0000000000b3");
    private static final UUID PRINCIPAL_IOS = UUID.fromString("36600000-0000-4000-8000-0000000000c1");
    private static final UUID PRINCIPAL_WEB = UUID.fromString("36600000-0000-4000-8000-0000000000c2");
    private static final UUID PRINCIPAL_UNDECLARED = UUID.fromString("36600000-0000-4000-8000-0000000000c3");

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
        registry.add("serviceos.outbox.worker-id", () -> "m366-dispatch-it");
    }

    @Autowired WorkOrderCommandService workOrders;
    @Autowired ConfigurationService configurations;
    @Autowired OutboxWorker outboxWorker;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    ConfigurationBundleReference bundle;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE rdm_work_order_timeline_entry, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_inbox_record, rel_idempotency_record, dsp_assignment_command_result,
                    dsp_capacity_reservation, dsp_service_assignment_activation_saga,
                    dsp_service_assignment, dsp_capacity_counter, dsp_network_allocation_target,
                    tsk_task_assignment, tsk_task_assignment_batch, tsk_task_execution_attempt, tsk_task,
                    wfl_node_instance, wfl_stage_instance, wfl_workflow_instance, wo_work_order,
                    cfg_configuration_asset_client_target, cfg_configuration_bundle_item,
                    cfg_configuration_bundle, cfg_configuration_asset_version,
                    prj_project_network, prj_project, aud_audit_record,
                    net_network_technician_membership, net_technician_profile,
                    net_service_network_coverage, net_service_network, net_partner_organization CASCADE
                """).update();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'M366-DISPATCH', 'BYD', 'M366 Dispatch',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        seedNetwork();
        publishDirectedEvidenceBundle(List.of("TECHNICIAN_WEB"));
    }

    @Test
    void directedWebOnlyRejectsIosOnlyTechnicianWithManualReason() {
        seedTechnician(TECH_IOS, PRINCIPAL_IOS, "IOS Tech", List.of("TECHNICIAN_IOS"));
        seedCapacity(TECH_IOS.toString(), 5, 0);
        workOrders.receive(receiveCommand("M366-ORD-IOS", "a".repeat(64), "VINM3660001",
                "corr-m366-ios", "cause-m366-ios"));
        drainOutbox(50);

        assertThat(jdbc.sql("""
                SELECT count(*) FROM dsp_service_assignment
                 WHERE responsibility_level = 'TECHNICIAN'
                """).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'SERVICE_DISPATCH_TECHNICIAN_POLICY_MANUAL'
                   AND error_code = 'CLIENT_KIND_TARGET_EMPTY'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT status FROM dsp_service_assignment
                 WHERE responsibility_level = 'NETWORK'
                """).query(String.class).single()).isEqualTo("ACTIVE");
    }

    @Test
    void directedWebOnlyActivatesMatchingWebTechnician() {
        seedTechnician(TECH_WEB, PRINCIPAL_WEB, "WEB Tech", List.of("TECHNICIAN_WEB"));
        seedCapacity(TECH_WEB.toString(), 5, 0);
        workOrders.receive(receiveCommand("M366-ORD-WEB", "b".repeat(64), "VINM3660002",
                "corr-m366-web", "cause-m366-web"));
        drainOutbox(50);

        assertThat(jdbc.sql("""
                SELECT assignee_id, status FROM dsp_service_assignment
                 WHERE responsibility_level = 'TECHNICIAN'
                """).query().singleRow())
                .containsEntry("assignee_id", TECH_WEB.toString())
                .containsEntry("status", "ACTIVE");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'SERVICE_DISPATCH_TECHNICIAN_POLICY_APPLIED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void directedTargetRejectsUndeclaredTechnician() {
        seedTechnician(TECH_UNDECLARED, PRINCIPAL_UNDECLARED, "Undeclared Tech", null);
        seedCapacity(TECH_UNDECLARED.toString(), 5, 0);
        workOrders.receive(receiveCommand("M366-ORD-UND", "c".repeat(64), "VINM3660003",
                "corr-m366-und", "cause-m366-und"));
        drainOutbox(50);

        assertThat(jdbc.sql("""
                SELECT count(*) FROM dsp_service_assignment
                 WHERE responsibility_level = 'TECHNICIAN'
                """).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'SERVICE_DISPATCH_TECHNICIAN_POLICY_MANUAL'
                   AND error_code = 'CLIENT_KIND_TARGET_EMPTY'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void undirectedBundleKeepsExistingAutoAssignWithoutKindFilter() {
        jdbc.sql("TRUNCATE TABLE cfg_configuration_bundle_item, cfg_configuration_bundle, "
                + "cfg_configuration_asset_client_target, cfg_configuration_asset_version CASCADE")
                .update();
        publishDirectedEvidenceBundle(null);
        seedTechnician(TECH_UNDECLARED, PRINCIPAL_UNDECLARED, "Undeclared Tech", null);
        seedCapacity(TECH_UNDECLARED.toString(), 5, 0);
        workOrders.receive(receiveCommand("M366-ORD-NULL", "d".repeat(64), "VINM3660004",
                "corr-m366-null", "cause-m366-null"));
        drainOutbox(50);

        assertThat(jdbc.sql("""
                SELECT assignee_id FROM dsp_service_assignment
                 WHERE responsibility_level = 'TECHNICIAN' AND status = 'ACTIVE'
                """).query(String.class).single()).isEqualTo(TECH_UNDECLARED.toString());
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

    private void publishDirectedEvidenceBundle(List<String> evidenceKinds) {
        String workflow = """
                {"workflowKey":"M366_DISPATCH","semanticVersion":"1.0.0","startNodeId":"START",
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
                    "failureCode":"BRAND_MISMATCH"},
                   {"filterKey":"REGION_SCOPE","order":4,
                    "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                    "failureCode":"REGION_MISMATCH"}],
                 "scoring":[{"factorKey":"REMAINING_CAPACITY","weight":1.0,
                     "expression":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""}}],
                 "capacity":{"reservationRequired":true},
                 "fallback":{"onNoCandidate":"MANUAL_INTERVENTION","manualRole":"OPS","resolutionHours":4}}
                """.replaceAll("\\s+", "");
        String evidence = """
                {"templateKey":"m366.evidence","version":"1.0.0","title":"现场",
                 "stage":"DISPATCH","items":[{"evidenceKey":"site.photo","name":"现场照",
                   "mediaType":"PHOTO","required":true,
                   "capture":{"allowCamera":true,"allowGallery":true,"minCount":1,"maxCount":3},
                   "reviewPolicy":{"reviewRequired":false,"allowItemLevelReject":false}}]}
                """.replaceAll("\\s+", "");
        var workflowAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "M366_DISPATCH",
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        var dispatchAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.DISPATCH, "default-dispatch",
                "1.0.0", "1.0.0", dispatch, Sha256.digest(dispatch)));
        var evidenceAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, "m366.evidence",
                "1.0.0", "1.0.0", evidence, Sha256.digest(evidence)));
        if (evidenceKinds != null && !evidenceKinds.isEmpty()) {
            jdbc.sql("""
                    INSERT INTO cfg_configuration_asset_client_target (
                        version_id, tenant_id, supported_client_kinds
                    ) VALUES (
                        :versionId, :tenant, CAST(:kinds AS jsonb)
                    )
                    """)
                    .param("versionId", evidenceAsset.versionId())
                    .param("tenant", TENANT)
                    .param("kinds", toJsonArray(evidenceKinds))
                    .update();
        }
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "M366-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowAsset.versionId(), dispatchAsset.versionId(),
                        evidenceAsset.versionId())));
    }

    private void seedNetwork() {
        jdbc.sql("""
                INSERT INTO net_partner_organization (
                    partner_organization_id, tenant_id, partner_code, partner_name,
                    partner_status, aggregate_version, created_at, updated_at
                ) VALUES (:id, :tenant, 'P-366', 'Partner 366', 'ACTIVE', 1, now(), now())
                """).param("id", PARTNER).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO net_service_network (
                    service_network_id, tenant_id, partner_organization_id, network_code,
                    network_name, network_status, aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :partner, 'N-366', 'Network 366', 'ACTIVE', 1, now(), now()
                )
                """)
                .param("id", NETWORK)
                .param("tenant", TENANT)
                .param("partner", PARTNER)
                .update();
        jdbc.sql("""
                INSERT INTO prj_project_network (
                    project_network_id, tenant_id, project_id, network_id,
                    valid_from, created_by, created_at
                ) VALUES (
                    :id, :tenant, :projectId, :networkId,
                    now() - interval '1 day', 'm366', now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("projectId", projectId)
                .param("networkId", NETWORK.toString())
                .update();
        jdbc.sql("""
                INSERT INTO net_service_network_coverage (
                    coverage_id, tenant_id, service_network_id, brand_code, business_type,
                    region_code, coverage_status, valid_from, valid_to, created_at
                ) VALUES (
                    :id, :tenant, :network, 'BYD_OCEAN', 'HOME_CHARGING_SURVEY_INSTALL',
                    '370100', 'ACTIVE', now() - interval '1 day', NULL, now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("network", NETWORK)
                .update();
        jdbc.sql("""
                INSERT INTO dsp_capacity_counter (
                    capacity_counter_id, tenant_id, responsibility_level, assignee_id,
                    business_type, max_units, occupied_units, version, updated_by, updated_at
                ) VALUES (
                    :id, :tenant, 'NETWORK', :assignee,
                    'HOME_CHARGING_SURVEY_INSTALL', 10, 0, 1, 'm366', now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("assignee", NETWORK.toString())
                .update();
    }

    private void seedTechnician(
            UUID profileId, UUID principalId, String name, List<String> kinds
    ) {
        jdbc.sql("""
                INSERT INTO net_technician_profile (
                    technician_profile_id, tenant_id, principal_id, display_name, profile_status,
                    supported_client_kinds, aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :principal, :name, 'ACTIVE',
                    CAST(:kinds AS jsonb), 1, now(), now()
                )
                """)
                .param("id", profileId)
                .param("tenant", TENANT)
                .param("principal", principalId)
                .param("name", name)
                .param("kinds", kinds == null ? null : toJsonArray(kinds))
                .update();
        jdbc.sql("""
                INSERT INTO net_network_technician_membership (
                    membership_id, tenant_id, service_network_id, technician_profile_id,
                    membership_status, valid_from, created_by, created_at, aggregate_version
                ) VALUES (
                    :id, :tenant, :network, :profile,
                    'ACTIVE', now() - interval '1 day', 'm366', now(), 1
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("network", NETWORK)
                .param("profile", profileId)
                .update();
    }

    private void seedCapacity(String assigneeId, int maxUnits, int occupied) {
        jdbc.sql("""
                INSERT INTO dsp_capacity_counter (
                    capacity_counter_id, tenant_id, responsibility_level, assignee_id,
                    business_type, max_units, occupied_units, version, updated_by, updated_at
                ) VALUES (
                    :id, :tenant, 'TECHNICIAN', :assignee,
                    'HOME_CHARGING_SURVEY_INSTALL', :maxUnits, :occupied, 1, 'm366', now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("assignee", assigneeId)
                .param("maxUnits", maxUnits)
                .param("occupied", occupied)
                .update();
    }

    private static String toJsonArray(List<String> kinds) {
        return kinds.stream()
                .map(kind -> "\"" + kind + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
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
