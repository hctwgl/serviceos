package com.serviceos.configuration.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.reliability.application.OutboxWorker;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.ActivateWorkOrderCommand;
import com.serviceos.workorder.api.FulfillWorkOrderCommand;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderReceipt;
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
 * M327：workorder.fulfilled → 履约事实 + PRICING SHADOW CalculationSnapshot。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PricingCalculationSnapshotPostgresIT {
    private static final String TENANT = "tenant-m327-pricing";

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
        registry.add("serviceos.outbox.worker-id", () -> "m327-pricing-it");
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
                    rel_inbox_record, cfg_calculation_snapshot, cfg_fulfillment_fact,
                    tsk_task_execution_attempt, tsk_task, wfl_node_instance, wfl_stage_instance,
                    wfl_workflow_instance, wo_work_order, cfg_configuration_bundle_item,
                    cfg_configuration_bundle, cfg_configuration_asset_version, prj_project,
                    aud_audit_record CASCADE
                """).update();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'M327-PRICE', 'BYD', 'M327 Pricing',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        publishBundleWithPricing();
    }

    @Test
    void workOrderFulfilledCapturesFactsAndShadowPricingSnapshot() {
        WorkOrderReceipt received = workOrders.receive(receiveCommand(
                "M327-ORD-1", "c".repeat(64), "VINM3270001", "corr-m327", "cause-m327"));
        workOrders.activate(new ActivateWorkOrderCommand(
                TENANT, received.workOrderId(), UUID.randomUUID(), "corr-m327-activate"));
        workOrders.fulfill(new FulfillWorkOrderCommand(
                TENANT, received.workOrderId(), UUID.randomUUID(), UUID.randomUUID(),
                "corr-m327-fulfill", List.of("INTAKE")));
        drainOutbox(40);

        assertThat(jdbc.sql("""
                SELECT fact_code FROM cfg_fulfillment_fact ORDER BY fact_code
                """).query(String.class).list())
                .contains(
                        "workOrder.brandCode",
                        "workOrder.clientCode",
                        "workOrder.serviceProductCode");
        assertThat(jdbc.sql("""
                SELECT pricing_key, currency, total_amount_minor, mode
                  FROM cfg_calculation_snapshot
                """).query().singleRow())
                .containsEntry("pricing_key", "home-install")
                .containsEntry("currency", "CNY")
                .containsEntry("total_amount_minor", 150000L)
                .containsEntry("mode", "SHADOW");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_inbox_record
                 WHERE consumer_name = 'configuration.pricing.workorder-fulfilled.v1'
                   AND status = 'SUCCEEDED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'PRICING_CALCULATION_SNAPSHOT_CAPTURED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);

        // 再次 drain 不得重复快照
        drainOutbox(10);
        assertThat(jdbc.sql("SELECT count(*) FROM cfg_calculation_snapshot")
                .query(Long.class).single()).isEqualTo(1L);
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

    private void publishBundleWithPricing() {
        String workflow = """
                {"workflowKey":"M327_PRICE","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"HUMAN","nodeType":"USER_TASK","name":"受理",
                    "stageCode":"INTAKE","taskType":"INTAKE_REVIEW"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"HUMAN"},
                   {"transitionId":"t2","from":"HUMAN","to":"END"}]}
                """.replaceAll("\\s+", "");
        String pricing = """
                {"pricingKey":"home-install","version":"1.0.0","currency":"CNY",
                 "lines":[{"lineKey":"base","chargeCode":"BASE_INSTALL","amountMinor":150000,
                   "when":{"language":"SERVICEOS_EXPR_V1",
                     "source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                   "billableTo":"OEM"}]}
                """.replaceAll("\\s+", "");
        var workflowAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "M327_PRICE",
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        var pricingAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.PRICING, "home-install",
                "1.0.0", "1.0.0", pricing, Sha256.digest(pricing)));
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "M327-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowAsset.versionId(), pricingAsset.versionId())));
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
