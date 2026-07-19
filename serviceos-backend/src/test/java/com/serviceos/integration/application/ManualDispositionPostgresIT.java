package com.serviceos.integration.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.ManualDispositionView;
import com.serviceos.integration.api.OutboundDeliveryService;
import com.serviceos.integration.api.RecordManualAckCommand;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ManualDispositionPostgresIT {
    private static final String TENANT = "tenant-manual-disposition-it";
    private static final String OPERATOR = "manual-disposition-ops";

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
        registry.add("serviceos.task.scheduling-enabled", () -> "false");
    }

    @Autowired OutboundDeliveryService deliveries;
    @Autowired JdbcClient jdbc;

    UUID projectId;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE
                    int_delivery_manual_disposition, int_delivery_replay_request,
                    int_external_acknowledgement, int_delivery_attempt, int_outbound_delivery,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_inbox_record, rel_idempotency_record,
                    auth_role_grant, auth_role_capability, auth_role,
                    prj_project CASCADE
                """).update();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at)
                VALUES (:projectId, :tenantId, 'MANUAL-ACK-IT', 'BYD', '人工处置测试项目',
                    :startsOn, 'ACTIVE', 1, now())
                """).param("projectId", projectId).param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1)).update();
        grant(OPERATOR, "integration.recordManualOutboundAck");
    }

    @Test
    void confirmsUnknownDeliveryWithoutClientCase() {
        UUID deliveryId = seedUnknown("confirm-1");
        ManualDispositionView disposition = deliveries.recordManualAck(
                operator(), new CommandMetadata("corr-manual-confirm", "idem-manual-confirm"),
                new RecordManualAckCommand(
                        deliveryId, 1L, "MANUAL_CONFIRMED", "外部客服确认已收单",
                        "approval://ops/manual-1", "EXT-ACK-1", List.of("ticket://ops/99")));
        assertThat(disposition.result()).isEqualTo("MANUAL_CONFIRMED");
        assertThat(jdbc.sql("SELECT status FROM int_outbound_delivery WHERE delivery_id=:id")
                .param("id", deliveryId).query(String.class).single())
                .isEqualTo("UNKNOWN");
        assertThat(jdbc.sql("""
                SELECT result FROM int_delivery_manual_disposition WHERE delivery_id=:id
                """).param("id", deliveryId).query(String.class).single())
                .isEqualTo("MANUAL_CONFIRMED");
        assertThat(jdbc.sql("SELECT aggregate_version FROM int_outbound_delivery WHERE delivery_id=:id")
                .param("id", deliveryId).query(Long.class).single())
                .isEqualTo(1L);
    }

    @Test
    void abandonsUnknownDelivery() {
        UUID deliveryId = seedUnknown("abandon-1");
        ManualDispositionView disposition = deliveries.recordManualAck(
                operator(), new CommandMetadata("corr-manual-abandon", "idem-manual-abandon"),
                new RecordManualAckCommand(
                        deliveryId, 1L, "ABANDONED", "业务取消不再外发",
                        "approval://ops/manual-2", null, List.of()));
        assertThat(disposition.result()).isEqualTo("ABANDONED");
        assertThat(jdbc.sql("SELECT status FROM int_outbound_delivery WHERE delivery_id=:id")
                .param("id", deliveryId).query(String.class).single())
                .isEqualTo("UNKNOWN");
        assertThat(jdbc.sql("""
                SELECT result FROM int_delivery_manual_disposition WHERE delivery_id=:id
                """).param("id", deliveryId).query(String.class).single())
                .isEqualTo("ABANDONED");
    }

    @Test
    void failsClosedWithoutEvidenceForManualConfirmed() {
        UUID deliveryId = seedUnknown("confirm-missing-evidence");
        assertThatThrownBy(() -> deliveries.recordManualAck(
                operator(), new CommandMetadata("corr-manual-bad", "idem-manual-bad"),
                new RecordManualAckCommand(
                        deliveryId, 1L, "MANUAL_CONFIRMED", "缺少证据",
                        "approval://ops/manual-3", null, List.of())))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    private UUID seedUnknown(String marker) {
        UUID deliveryId = UUID.randomUUID();
        String digest = "a".repeat(64);
        jdbc.sql("""
                INSERT INTO int_outbound_delivery (
                    delivery_id, tenant_id, project_id, connector_version_id, mapping_version_id,
                    business_message_type, business_key, source_review_case_id, source_task_id,
                    source_work_order_id, source_snapshot_id, source_snapshot_digest,
                    external_order_code, operator_principal_id, operator_display_value,
                    payload_object_ref, payload_digest, external_idempotency_key,
                    failure_policy_version_id, status, attempt_count, created_by, created_at,
                    aggregate_version, delivered_at, acknowledged_at, client_review_case_id,
                    review_route_id
                ) VALUES (
                    :id, :tenantId, :projectId, 'byd-cpim-v7.3.1', 'byd-ocean-shandong-submit-review-v1',
                    'SUBMIT_CLIENT_REVIEW', :businessKey, :reviewCaseId, :taskId,
                    :workOrderId, :snapshotId, :digest,
                    :orderCode, 'operator-secret', 'OP',
                    's3://private/payload-secret', :digest, :idem,
                    'byd-submit-review-fail-closed-v1', 'UNKNOWN', 1, 'operator-secret', now(),
                    1, NULL, NULL, NULL, NULL)
                """)
                .param("id", deliveryId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("businessKey", "m318:" + marker + ":" + deliveryId)
                .param("reviewCaseId", UUID.randomUUID())
                .param("taskId", UUID.randomUUID())
                .param("workOrderId", UUID.randomUUID())
                .param("snapshotId", UUID.randomUUID())
                .param("digest", digest)
                .param("idem", "idem-" + marker)
                .param("orderCode", "ORD-" + marker)
                .update();
        return deliveryId;
    }

    private void grant(String principalId, String... capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, :code, '人工处置角色', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT)
                .param("code", "manual-ack-role-" + roleId).update();
        for (String capability : capabilities) {
            jdbc.sql("""
                    INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                    VALUES (:roleId, :code, now())
                    """).param("roleId", roleId).param("code", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (
                    :grantId, :tenantId, :principalId, :roleId, 'PROJECT', :projectId,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M318-MANUAL', now())
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("principalId", principalId).param("roleId", roleId)
                .param("projectId", projectId.toString()).update();
    }

    private CurrentPrincipal operator() {
        return new CurrentPrincipal(
                OPERATOR, TENANT, CurrentPrincipal.PrincipalType.USER, "ops-web", Set.of());
    }
}
