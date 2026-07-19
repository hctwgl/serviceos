package com.serviceos.integration.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.OutboundDeliveryQueryService;
import com.serviceos.integration.api.OutboundDeliveryQueueQuery;
import com.serviceos.shared.BusinessProblem;
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
class OutboundDeliveryQueuePostgresIT {
    private static final String TENANT = "tenant-outbound-queue-it";
    private static final String READER = "outbound-reader-m99";

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

    @Autowired OutboundDeliveryQueryService queries;
    @Autowired JdbcClient jdbc;

    UUID projectId;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE
                    int_delivery_manual_disposition, int_delivery_replay_request,
                    int_external_acknowledgement, int_delivery_attempt,
                    int_outbound_delivery,
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
                VALUES (:projectId, :tenantId, 'OUT-Q-IT', 'BYD', '外发队列测试项目',
                    :startsOn, 'ACTIVE', 1, now())
                """).param("projectId", projectId).param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1)).update();
        grant(READER, "integration.readOutbound");
    }

    @Test
    void authorizedOutboundQueueDefaultsUnknownFiltersAndUsesScopeBoundCursor() {
        UUID first = seedDelivery("queue-1", "UNKNOWN");
        UUID second = seedDelivery("queue-2", "UNKNOWN");
        seedDelivery("acked", "ACKNOWLEDGED");
        UUID firstReview = jdbc.sql("""
                SELECT source_review_case_id FROM int_outbound_delivery WHERE delivery_id = :id
                """).param("id", first).query(UUID.class).single();

        var defaultUnknown = queries.list(
                reader(), "corr-outbound-queue-default",
                new OutboundDeliveryQueueQuery(projectId, null, null, null, null, null, 20));
        assertThat(defaultUnknown.items()).hasSize(2);

        var firstPage = queries.list(
                reader(), "corr-outbound-queue-1",
                new OutboundDeliveryQueueQuery(projectId, "UNKNOWN", null, null, null, null, 1));
        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.nextCursor()).isNotBlank();
        var secondPage = queries.list(
                reader(), "corr-outbound-queue-2",
                new OutboundDeliveryQueueQuery(
                        projectId, "UNKNOWN", null, null, null, firstPage.nextCursor(), 1));
        assertThat(secondPage.items()).hasSize(1);
        assertThat(List.of(
                firstPage.items().getFirst().deliveryId(),
                secondPage.items().getFirst().deliveryId()))
                .containsExactlyInAnyOrder(first, second);
        assertThat(secondPage.toString()).doesNotContain(
                "sourceSnapshotDigest", "payloadDigest", "operatorPrincipalId",
                "payloadObjectRef", "externalIdempotencyKey", "approvalRef", "reason");

        assertThat(queries.list(
                reader(), "corr-outbound-queue-scope",
                new OutboundDeliveryQueueQuery(null, "UNKNOWN", null, null, null, null, 20)).items())
                .extracting(item -> item.deliveryId())
                .containsExactlyInAnyOrder(first, second);
        assertThat(queries.list(
                reader(), "corr-outbound-queue-review",
                new OutboundDeliveryQueueQuery(
                        projectId, "UNKNOWN", "SUBMIT_CLIENT_REVIEW", null, firstReview, null, 20))
                .items())
                .extracting(item -> item.deliveryId())
                .containsExactly(first);

        assertThatThrownBy(() -> queries.list(
                reader(), "corr-outbound-queue-cursor",
                new OutboundDeliveryQueueQuery(
                        projectId, "ACKNOWLEDGED", null, null, null, firstPage.nextCursor(), 1)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> queries.list(
                reader(), "corr-outbound-queue-status",
                new OutboundDeliveryQueueQuery(projectId, "INVALID", null, null, null, null, 20)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    private UUID seedDelivery(String marker, String status) {
        UUID deliveryId = UUID.randomUUID();
        String digest = ("c" + marker + "d".repeat(64)).substring(0, 64);
        String idem = ("e" + marker + "f".repeat(64)).substring(0, 64);
        boolean acknowledged = "ACKNOWLEDGED".equals(status);
        var insert = jdbc.sql("""
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
                    'byd-submit-review-fail-closed-v1', :status, 1, 'operator-secret', now(),
                    1,
                    """ + (acknowledged ? ":deliveredAt, :acknowledgedAt, :clientCase, :route" : "NULL, NULL, NULL, NULL") + """
                )
                """)
                .param("id", deliveryId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("businessKey", "m99:" + marker + ":" + deliveryId)
                .param("reviewCaseId", UUID.randomUUID())
                .param("taskId", UUID.randomUUID())
                .param("workOrderId", UUID.randomUUID())
                .param("snapshotId", UUID.randomUUID())
                .param("digest", digest)
                .param("idem", idem)
                .param("orderCode", "ORD-" + marker)
                .param("status", status);
        if (acknowledged) {
            insert = insert
                    .param("deliveredAt", java.time.OffsetDateTime.now())
                    .param("acknowledgedAt", java.time.OffsetDateTime.now())
                    .param("clientCase", UUID.randomUUID())
                    .param("route", UUID.randomUUID());
        }
        insert.update();
        return deliveryId;
    }

    private void grant(String principalId, String... capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, :code, '外发队列角色', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT)
                .param("code", "outbound-role-" + roleId).update();
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
                    now() - interval '1 day', 'TEST_FIXTURE', 'M99-OUTBOUND', now())
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("principalId", principalId).param("roleId", roleId)
                .param("projectId", projectId.toString()).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (
                    :grantId, :tenantId, :principalId, :roleId, 'TENANT', :tenantId,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M99-OUTBOUND', now())
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("principalId", principalId).param("roleId", roleId).update();
    }

    private CurrentPrincipal reader() {
        return new CurrentPrincipal(
                READER, TENANT, CurrentPrincipal.PrincipalType.USER, "ops-web", Set.of());
    }
}
