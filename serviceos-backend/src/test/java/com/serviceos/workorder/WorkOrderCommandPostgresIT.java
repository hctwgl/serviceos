package com.serviceos.workorder;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.ActivateWorkOrderCommand;
import com.serviceos.workorder.api.ExternalWorkOrderConflictException;
import com.serviceos.workorder.api.FulfillWorkOrderCommand;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkOrderCommandPostgresIT {
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

    @Autowired
    WorkOrderCommandService workOrders;

    @Autowired
    ConfigurationService configurations;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Flyway flyway;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE rel_outbox_publish_attempt, rel_outbox_event, wo_work_order,
                    cfg_configuration_bundle_item,
                    cfg_configuration_bundle, cfg_configuration_asset_version,
                    prj_project CASCADE
                """).update();
    }

    @Test
    void businessIdempotencyIsTenantScopedAndConflictingPayloadFailsClosed() {
        Scope tenantA = scope("tenant-a", "PROJECT-A", "BUNDLE-A");
        Scope tenantB = scope("tenant-b", "PROJECT-B", "BUNDLE-B");
        ReceiveExternalWorkOrderCommand firstCommand = command(tenantA, "a".repeat(64));

        var first = workOrders.receive(firstCommand);
        var replay = workOrders.receive(firstCommand);
        var otherTenant = workOrders.receive(command(tenantB, "b".repeat(64)));

        assertThat(replay.workOrderId()).isEqualTo(first.workOrderId());
        assertThat(replay.replay()).isTrue();
        assertThat(otherTenant.workOrderId()).isNotEqualTo(first.workOrderId());
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order")
                .query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event")
                .query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type = 'workorder.received'")
                .query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM wo_project_personnel_snapshot")
                .query(Long.class).single()).isEqualTo(6);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM wo_project_personnel_snapshot
                 WHERE match_status = 'MISSING' AND snapshot_status = 'CURRENT'
                """).query(Long.class).single()).isEqualTo(6);

        assertThatThrownBy(() -> workOrders.receive(command(tenantA, "c".repeat(64))))
                .isInstanceOf(ExternalWorkOrderConflictException.class)
                .hasMessageContaining("different payload");
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order")
                .query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void databaseRejectsCrossTenantProjectOrBundleReference() {
        Scope tenantA = scope("tenant-a", "PROJECT-A", "BUNDLE-A");
        Scope tenantB = scope("tenant-b", "PROJECT-B", "BUNDLE-B");
        ReceiveExternalWorkOrderCommand invalid = new ReceiveExternalWorkOrderCommand(
                tenantA.tenantId(),
                tenantB.projectId(),
                "BYD",
                "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL",
                "BYD-SD-WO-001",
                "d".repeat(64),
                tenantA.bundle().bundleId(),
                tenantA.bundle().bundleCode(),
                tenantA.bundle().bundleVersion(),
                tenantA.bundle().manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000",
                "山东省济南市历下区测试路1号", "LGXCE6CD0RA123456",
                LocalDateTime.of(2026, 7, 13, 10, 0), "corr-invalid", "cause-invalid");

        assertThatThrownBy(() -> workOrders.receive(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order")
                .query(Long.class).single()).isZero();
    }

    @Test
    void fulfillmentIsIdempotentAndPublishesExactlyOneDomainEvent() {
        Scope scope = scope("tenant-a", "PROJECT-A", "BUNDLE-A");
        var received = workOrders.receive(command(scope, "e".repeat(64)));
        UUID activationEventId = UUID.randomUUID();
        workOrders.activate(new ActivateWorkOrderCommand(
                scope.tenantId(), received.workOrderId(), activationEventId, "corr-activate"));

        UUID workflowInstanceId = UUID.randomUUID();
        FulfillWorkOrderCommand command = new FulfillWorkOrderCommand(
                scope.tenantId(), received.workOrderId(), workflowInstanceId,
                UUID.randomUUID(), "corr-fulfill", List.of("INTAKE", "REVIEW"));
        var fulfilled = workOrders.fulfill(command);
        var replay = workOrders.fulfill(command);

        assertThat(fulfilled.replay()).isFalse();
        assertThat(replay.replay()).isTrue();
        assertThat(replay.workOrderId()).isEqualTo(fulfilled.workOrderId());
        assertThat(replay.fulfilledAt()).isEqualTo(fulfilled.fulfilledAt());
        assertThat(jdbc.sql("SELECT status FROM wo_work_order WHERE id = :id")
                .param("id", received.workOrderId()).query(String.class).single()).isEqualTo("FULFILLED");
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type = 'workorder.fulfilled'")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void fulfillmentFailsClosedUnlessWorkOrderIsActive() {
        Scope scope = scope("tenant-a", "PROJECT-A", "BUNDLE-A");
        var received = workOrders.receive(command(scope, "f".repeat(64)));

        assertThatThrownBy(() -> workOrders.fulfill(new FulfillWorkOrderCommand(
                scope.tenantId(), received.workOrderId(), UUID.randomUUID(),
                UUID.randomUUID(), "corr-invalid-fulfill", List.of("INTAKE"))))
                .isInstanceOf(ExternalWorkOrderConflictException.class)
                .hasMessageContaining("cannot be fulfilled from status RECEIVED");
        assertThat(jdbc.sql("SELECT status FROM wo_work_order WHERE id = :id")
                .param("id", received.workOrderId()).query(String.class).single()).isEqualTo("RECEIVED");
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type = 'workorder.fulfilled'")
                .query(Long.class).single()).isZero();
    }

    @Test
    void migrationSetIsCurrentAndRepeatable() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("151");
        assertThat(flyway.info().applied()).hasSize(153);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private Scope scope(String tenantId, String projectCode, String bundleCode) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, :projectCode, 'BYD', :projectName,
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", tenantId)
                .param("projectCode", projectCode)
                .param("projectName", "项目 " + projectCode)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        String definition = "{\"workflowCode\":\"" + projectCode + "\"}";
        UUID assetId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                tenantId, ConfigurationAssetType.WORKFLOW, projectCode + "-WORKFLOW", "1.0.0",
                "1.0.0", definition, Sha256.digest(definition))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        tenantId, projectId, bundleCode, "1.0.0", "BYD_OCEAN",
                        "HOME_CHARGING_SURVEY_INSTALL", "370000",
                        Instant.now().minusSeconds(3600), null, List.of(assetId)));
        return new Scope(tenantId, projectId, bundle);
    }

    private static ReceiveExternalWorkOrderCommand command(Scope scope, String payloadDigest) {
        return new ReceiveExternalWorkOrderCommand(
                scope.tenantId(), scope.projectId(), "BYD", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "BYD-SD-WO-001", payloadDigest,
                scope.bundle().bundleId(), scope.bundle().bundleCode(), scope.bundle().bundleVersion(),
                scope.bundle().manifestDigest(),
                "370000", "370100", "370102", "测试用户", "13800000000",
                "山东省济南市历下区测试路1号", "LGXCE6CD0RA123456",
                LocalDateTime.of(2026, 7, 13, 10, 0), "corr-work-order-it", "cause-work-order-it");
    }

    private record Scope(String tenantId, UUID projectId, ConfigurationBundleReference bundle) {
    }
}
