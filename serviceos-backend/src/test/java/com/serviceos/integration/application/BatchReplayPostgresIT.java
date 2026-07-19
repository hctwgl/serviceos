package com.serviceos.integration.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.ApproveBatchReplayCommand;
import com.serviceos.integration.api.BatchReplayRequestView;
import com.serviceos.integration.api.BatchReplayService;
import com.serviceos.integration.api.CreateBatchReplayCommand;
import com.serviceos.shared.CommandMetadata;
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

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BatchReplayPostgresIT {
    private static final String TENANT = "tenant-batch-replay-it";
    private static final String OPERATOR = "batch-replay-ops";

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

    @Autowired BatchReplayService batches;
    @Autowired JdbcClient jdbc;

    UUID projectId;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE
                    int_batch_replay_item, int_batch_replay_request,
                    int_delivery_manual_disposition, int_delivery_replay_request,
                    int_external_acknowledgement, int_delivery_attempt, int_outbound_delivery,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_inbox_record, rel_idempotency_record,
                    auth_role_grant, auth_role_capability, auth_role,
                    tsk_task, prj_project CASCADE
                """).update();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at)
                VALUES (:projectId, :tenantId, 'BATCH-REPLAY-IT', 'BYD', '批量重放测试',
                    :startsOn, 'ACTIVE', 1, now())
                """).param("projectId", projectId).param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1)).update();
        grant(OPERATOR, "integration.batchReplayUnknownDelivery", "integration.retryUnknownDelivery");
    }

    @Test
    void previewMarksEligibleAndIneligible() {
        UUID unknown = seedUnknown("ok");
        UUID pending = seedStatus("pending", "PENDING");
        BatchReplayRequestView preview = batches.create(
                operator(), new CommandMetadata("corr-preview", "idem-preview"),
                new CreateBatchReplayCommand(
                        List.of(unknown, pending), "PREVIEW", "预演批量重放", null, 20));
        assertThat(preview.status()).isEqualTo("PREVIEWED");
        assertThat(preview.items()).hasSize(2);
        assertThat(preview.items()).anyMatch(i ->
                i.deliveryId().equals(unknown) && "ELIGIBLE".equals(i.eligibility()));
        assertThat(preview.items()).anyMatch(i ->
                i.deliveryId().equals(pending) && "INELIGIBLE".equals(i.eligibility())
                        && "NOT_UNKNOWN".equals(i.ineligibilityCode()));
    }

    @Test
    void submitApproveSchedulesSingleReplay() {
        UUID unknown = seedUnknown("schedule");
        BatchReplayRequestView submitted = batches.create(
                operator(), new CommandMetadata("corr-submit", "idem-submit"),
                new CreateBatchReplayCommand(
                        List.of(unknown), "SUBMIT", "提交批量重放", "approval://batch/1", 20));
        assertThat(submitted.status()).isEqualTo("PENDING_APPROVAL");

        BatchReplayRequestView approved = batches.approve(
                operator(), new CommandMetadata("corr-approve", "idem-approve"),
                new ApproveBatchReplayCommand(submitted.batchId(), "APPROVE", "同意", 20));
        assertThat(approved.status()).isEqualTo("COMPLETED");
        assertThat(approved.items().getFirst().itemStatus()).isEqualTo("SCHEDULED");
        assertThat(approved.items().getFirst().singleReplayRequestId()).isNotNull();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM int_delivery_replay_request WHERE delivery_id=:id
                """).param("id", unknown).query(Long.class).single()).isEqualTo(1L);
    }

    private UUID seedUnknown(String marker) {
        return seedStatus(marker, "UNKNOWN");
    }

    private UUID seedStatus(String marker, String status) {
        UUID deliveryId = UUID.randomUUID();
        String digest = "b".repeat(64);
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
                    'byd-submit-review-fail-closed-v1', :status, 1, 'operator-secret', now(),
                    1, NULL, NULL, NULL, NULL)
                """)
                .param("id", deliveryId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("businessKey", "m319:" + marker + ":" + deliveryId)
                .param("reviewCaseId", UUID.randomUUID())
                .param("taskId", UUID.randomUUID())
                .param("workOrderId", UUID.randomUUID())
                .param("snapshotId", UUID.randomUUID())
                .param("digest", digest)
                .param("idem", "idem-" + marker)
                .param("orderCode", "ORD-" + marker)
                .param("status", status)
                .update();
        return deliveryId;
    }

    private void grant(String principalId, String... capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, :code, '批量重放角色', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT)
                .param("code", "batch-replay-role-" + roleId).update();
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
                    now() - interval '1 day', 'TEST_FIXTURE', 'M319-BATCH', now())
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("principalId", principalId).param("roleId", roleId)
                .param("projectId", projectId.toString()).update();
    }

    private CurrentPrincipal operator() {
        return new CurrentPrincipal(
                OPERATOR, TENANT, CurrentPrincipal.PrincipalType.USER, "ops-web", Set.of());
    }
}
