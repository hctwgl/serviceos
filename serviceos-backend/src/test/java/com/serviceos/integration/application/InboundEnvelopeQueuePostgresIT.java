package com.serviceos.integration.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.InboundMessageQueryService;
import com.serviceos.integration.api.InboundEnvelopeQueueQuery;
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
class InboundEnvelopeQueuePostgresIT {
    private static final String TENANT = "tenant-inbound-queue-it";
    private static final String READER = "inbound-reader-m158";

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

    @Autowired InboundMessageQueryService queries;
    @Autowired JdbcClient jdbc;

    UUID projectId;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE
                    int_inbound_item_result, int_external_review_route, int_canonical_message,
                    int_inbound_envelope,
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
                VALUES (:projectId, :tenantId, 'IN-Q-IT', 'BYD', '入站队列测试项目',
                    :startsOn, 'ACTIVE', 1, now())
                """).param("projectId", projectId).param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1)).update();
        grant(READER, "integration.readInbound");
    }

    @Test
    void authorizedInboundQueueDefaultsReceivedFiltersAndUsesScopeBoundCursor() {
        UUID first = seedEnvelope("queue-1", "RECEIVED", projectId, null);
        UUID second = seedEnvelope("queue-2", "RECEIVED", projectId, null);
        seedEnvelope("completed", "COMPLETED", projectId, "WORK_ORDER");
        seedEnvelope("null-project", "RECEIVED", null, null);

        var defaultReceived = queries.list(
                reader(), "corr-inbound-queue-default",
                new InboundEnvelopeQueueQuery(projectId, null, null, null, null, null, null, 20));
        assertThat(defaultReceived.items()).hasSize(2);
        assertThat(defaultReceived.toString()).doesNotContain(
                "rawPayloadDigest", "canonicalPayloadDigest", "raw_payload_object_ref",
                "objectRef", "s3://");

        var firstPage = queries.list(
                reader(), "corr-inbound-queue-1",
                new InboundEnvelopeQueueQuery(projectId, "RECEIVED", null, null, null, null, null, 1));
        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.nextCursor()).isNotBlank();
        var secondPage = queries.list(
                reader(), "corr-inbound-queue-2",
                new InboundEnvelopeQueueQuery(
                        projectId, "RECEIVED", null, null, null, null, firstPage.nextCursor(), 1));
        assertThat(secondPage.items()).hasSize(1);
        assertThat(List.of(
                firstPage.items().getFirst().inboundEnvelopeId(),
                secondPage.items().getFirst().inboundEnvelopeId()))
                .containsExactlyInAnyOrder(first, second);

        assertThat(queries.list(
                reader(), "corr-inbound-queue-scope",
                new InboundEnvelopeQueueQuery(null, "RECEIVED", null, null, null, null, null, 20))
                .items())
                .extracting(item -> item.inboundEnvelopeId())
                .containsExactlyInAnyOrder(first, second);

        String workOrderId = UUID.randomUUID().toString();
        UUID withResult = seedEnvelope("with-result", "COMPLETED", projectId, "WORK_ORDER", workOrderId);
        assertThat(queries.list(
                reader(), "corr-inbound-queue-result",
                new InboundEnvelopeQueueQuery(
                        projectId, "COMPLETED", "CREATE_WORK_ORDER", "WORK_ORDER",
                        workOrderId, null, null, 20))
                .items())
                .extracting(item -> item.inboundEnvelopeId())
                .containsExactly(withResult);

        assertThatThrownBy(() -> queries.list(
                reader(), "corr-inbound-queue-cursor",
                new InboundEnvelopeQueueQuery(
                        projectId, "COMPLETED", null, null, null, null, firstPage.nextCursor(), 1)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> queries.list(
                reader(), "corr-inbound-queue-status",
                new InboundEnvelopeQueueQuery(projectId, "INVALID", null, null, null, null, null, 20)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    private UUID seedEnvelope(String marker, String status, UUID envelopeProjectId, String resultType) {
        return seedEnvelope(marker, status, envelopeProjectId, resultType, null);
    }

    private UUID seedEnvelope(
            String marker,
            String status,
            UUID envelopeProjectId,
            String resultType,
            String resultId
    ) {
        UUID envelopeId = UUID.randomUUID();
        String digest = ("a" + marker + "b".repeat(64)).substring(0, 64);
        boolean terminal = !"RECEIVED".equals(status);
        String resolvedResultId = resultId != null ? resultId : (terminal ? envelopeId.toString() : null);
        jdbc.sql("""
                INSERT INTO int_inbound_envelope (
                    inbound_envelope_id, tenant_id, project_id, connector_version_id, message_type,
                    transport_dedup_key, external_message_id, received_at,
                    raw_payload_object_ref, raw_payload_digest, signature_status, processing_status,
                    mapping_version_id, result_code, result_type, result_id, correlation_id, completed_at
                ) VALUES (
                    :id, :tenantId, :projectId, 'byd-cpim-v7.3.1', 'CREATE_WORK_ORDER',
                    :dedup, :externalId, now() - (:offset || ' seconds')::interval,
                    's3://private/inbound-secret', :digest, 'VALID', :status,
                    :mapping, :resultCode, :resultType, :resultId, :correlationId,
                    """ + (terminal ? "now()" : "NULL") + """
                )
                """)
                .param("id", envelopeId)
                .param("tenantId", TENANT)
                .param("projectId", envelopeProjectId)
                .param("dedup", ("d" + marker + "e".repeat(64)).substring(0, 64))
                .param("externalId", "ext-" + marker)
                .param("offset", Math.abs(marker.hashCode() % 1000))
                .param("digest", digest)
                .param("status", status)
                .param("mapping", terminal ? "map-v1" : null)
                .param("resultCode", terminal ? "ACCEPTED" : null)
                .param("resultType", terminal ? resultType : null)
                .param("resultId", resolvedResultId)
                .param("correlationId", "corr-" + marker)
                .update();
        return envelopeId;
    }

    private void grant(String principalId, String... capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, :code, '入站队列角色', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT)
                .param("code", "inbound-role-" + roleId).update();
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
                    now() - interval '1 day', 'TEST_FIXTURE', 'M158-INBOUND', now())
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("principalId", principalId).param("roleId", roleId)
                .param("projectId", projectId.toString()).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (
                    :grantId, :tenantId, :principalId, :roleId, 'TENANT', :tenantId,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M158-INBOUND', now())
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("principalId", principalId).param("roleId", roleId).update();
    }

    private CurrentPrincipal reader() {
        return new CurrentPrincipal(
                READER, TENANT, CurrentPrincipal.PrincipalType.USER, "ops-web", Set.of());
    }
}
