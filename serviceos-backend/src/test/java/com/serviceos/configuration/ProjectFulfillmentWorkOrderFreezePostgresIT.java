package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.CreateProjectFulfillmentProfileCommand;
import com.serviceos.configuration.api.ProjectFulfillmentDraftView;
import com.serviceos.configuration.api.ProjectFulfillmentProfileDetail;
import com.serviceos.configuration.api.ProjectFulfillmentProfileService;
import com.serviceos.configuration.api.ProjectFulfillmentRevisionView;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.configuration.api.UpdateProjectFulfillmentDraftCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.Sha256;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M383：发布 v1 建单 A、发布 v2 建单 B，验证冻结隔离与快照。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectFulfillmentWorkOrderFreezePostgresIT {
    private static final String TENANT = "tenant-pfp-freeze-it";
    private static final String ACTOR = "freeze-operator";

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
        registry.add("serviceos.outbox.worker-id", () -> "pfp-freeze-it");
    }

    @Autowired ProjectFulfillmentProfileService profiles;
    @Autowired WorkOrderCommandService workOrders;
    @Autowired ConfigurationService configurations;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    UUID workflowVersionId;
    ConfigurationBundleReference bundle;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE wo_work_order,
                    cfg_project_fulfillment_revision, cfg_project_fulfillment_profile,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    aud_audit_record, auth_role_grant, auth_role_capability, auth_role
                    CASCADE
                """).update();
        seedGrants();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'PFP-FREEZE', 'BYD', '冻结隔离项目',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        String definition = "{"
                + "\"workflowKey\":\"freeze.survey\",\"semanticVersion\":\"1.0.0\",\"startNodeId\":\"START\","
                + "\"nodes\":["
                + "{\"nodeId\":\"START\",\"nodeType\":\"START\",\"name\":\"start\"},"
                + "{\"nodeId\":\"TASK_A\",\"nodeType\":\"SERVICE_TASK\",\"name\":\"task-a\","
                + "\"stageCode\":\"STAGE_A\",\"taskType\":\"DESIGNER_TASK\"},"
                + "{\"nodeId\":\"END\",\"nodeType\":\"END\",\"name\":\"end\"}],"
                + "\"transitions\":["
                + "{\"transitionId\":\"t1\",\"from\":\"START\",\"to\":\"TASK_A\"},"
                + "{\"transitionId\":\"t2\",\"from\":\"TASK_A\",\"to\":\"END\"}]}";
        workflowVersionId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "freeze.survey", "1.0.0",
                "1.0.0", definition, Sha256.digest(definition))).versionId();
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "FREEZE-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000",
                Instant.now().minusSeconds(60), null, List.of(workflowVersionId)));
    }

    @Test
    void workOrderAKeepsV1AfterV2PublishAndSnapshotsDiffer() {
        ProjectFulfillmentProfileDetail created = profiles.create(principal(), meta("c1"),
                new CreateProjectFulfillmentProfileCommand(
                        projectId, "HOME_CHARGING_SURVEY_INSTALL", "冻结方案",
                        null, "HOME_CHARGING_SURVEY_INSTALL", null));
        ProjectFulfillmentDraftView draft = profiles.getDraft(
                principal(), "d1", projectId, created.profileId());
        profiles.updateDraft(principal(), meta("u1"),
                new UpdateProjectFulfillmentDraftCommand(
                        created.profileId(), draft.aggregateVersion(),
                        "冻结方案", null, draft.documentJson(),
                        workflowVersionId, bundle.bundleId()));
        ProjectFulfillmentProfileDetail ready = profiles.get(
                principal(), "g1", projectId, created.profileId());
        Instant t1 = Instant.now().plusSeconds(2);
        ProjectFulfillmentRevisionView v1 = profiles.publish(
                principal(), meta("p1"), projectId, created.profileId(),
                ready.aggregateVersion(), t1, "v1");

        WorkOrderReceipt orderA = workOrders.receive(new ReceiveExternalWorkOrderCommand(
                TENANT, projectId, "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                "WO-FREEZE-A", "a".repeat(64),
                v1.sourceBundleId(), bundle.bundleCode(), bundle.bundleVersion(),
                bundle.manifestDigest(),
                "370000", "370100", "370102", "客户A", "13800000001", "地址A", "VINA0000000000001",
                LocalDateTime.of(2026, 7, 20, 10, 0), "corr-a", "cause-a",
                "PROFILE_REVISION", created.profileId(), v1.revisionId(), "1"));

        ProjectFulfillmentProfileDetail afterV1 = profiles.get(
                principal(), "g2", projectId, created.profileId());
        ProjectFulfillmentDraftView draft2 = profiles.getDraft(
                principal(), "d2", projectId, created.profileId());
        profiles.updateDraft(principal(), meta("u2"),
                new UpdateProjectFulfillmentDraftCommand(
                        created.profileId(), draft2.aggregateVersion(),
                        "冻结方案 v2", "资料加严", draft2.documentJson(),
                        workflowVersionId, bundle.bundleId()));
        ProjectFulfillmentProfileDetail ready2 = profiles.get(
                principal(), "g3", projectId, created.profileId());
        Instant t2 = Instant.now().plusSeconds(30);
        ProjectFulfillmentRevisionView v2 = profiles.publish(
                principal(), meta("p2"), projectId, created.profileId(),
                ready2.aggregateVersion(), t2, "v2");

        WorkOrderReceipt orderB = workOrders.receive(new ReceiveExternalWorkOrderCommand(
                TENANT, projectId, "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                "WO-FREEZE-B", "b".repeat(64),
                v2.sourceBundleId(), bundle.bundleCode(), bundle.bundleVersion(),
                bundle.manifestDigest(),
                "370000", "370100", "370102", "客户B", "13800000002", "地址B", "VINB0000000000001",
                LocalDateTime.of(2026, 7, 20, 11, 0), "corr-b", "cause-b",
                "PROFILE_REVISION", created.profileId(), v2.revisionId(), "2"));

        assertThat(jdbc.sql("""
                SELECT fulfillment_revision_id FROM wo_work_order WHERE id = :id
                """).param("id", orderA.workOrderId()).query(UUID.class).single())
                .isEqualTo(v1.revisionId());
        assertThat(jdbc.sql("""
                SELECT fulfillment_revision_id FROM wo_work_order WHERE id = :id
                """).param("id", orderB.workOrderId()).query(UUID.class).single())
                .isEqualTo(v2.revisionId());
        assertThat(jdbc.sql("""
                SELECT fulfillment_version FROM wo_work_order WHERE id = :id
                """).param("id", orderA.workOrderId()).query(String.class).single())
                .isEqualTo("1");
        assertThat(jdbc.sql("""
                SELECT fulfillment_version FROM wo_work_order WHERE id = :id
                """).param("id", orderB.workOrderId()).query(String.class).single())
                .isEqualTo("2");

        assertThat(jdbc.sql("""
                SELECT fulfillment_config_kind FROM wo_work_order WHERE id = :id
                """).param("id", orderA.workOrderId()).query(String.class).single())
                .isEqualTo("PROFILE_REVISION");
        assertThat(jdbc.sql("""
                SELECT r.content_digest
                  FROM wo_work_order w
                  JOIN cfg_project_fulfillment_revision r ON r.revision_id = w.fulfillment_revision_id
                 WHERE w.id = :id
                """).param("id", orderA.workOrderId()).query(String.class).single())
                .isNotEqualTo(jdbc.sql("""
                SELECT r.content_digest
                  FROM wo_work_order w
                  JOIN cfg_project_fulfillment_revision r ON r.revision_id = w.fulfillment_revision_id
                 WHERE w.id = :id
                """).param("id", orderB.workOrderId()).query(String.class).single());
        assertThat(v1.revisionId()).isNotEqualTo(v2.revisionId());
    }

    private void seedGrants() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'pfp-freeze', '冻结IT', 'ACTIVE', now())
                """).param("id", roleId).param("tenant", TENANT).update();
        for (String capability : List.of(
                "project.fulfillment.read",
                "project.fulfillment.create",
                "project.fulfillment.draft.write",
                "project.fulfillment.validate",
                "project.fulfillment.publish",
                "project.fulfillment.suspend",
                "project.fulfillment.resume",
                "project.fulfillment.revision.read",
                "project.fulfillment.snapshot.read")) {
            jdbc.sql("""
                    INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                    VALUES (:id, :cap, now())
                    """).param("id", roleId).param("cap", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at
                ) VALUES (
                    :grant, :tenant, :principal, :role, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST', 'freeze', now()
                )
                """)
                .param("grant", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("principal", ACTOR)
                .param("role", roleId)
                .update();
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(ACTOR, TENANT, CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of(
                "project.fulfillment.read",
                "project.fulfillment.create",
                "project.fulfillment.draft.write",
                "project.fulfillment.validate",
                "project.fulfillment.publish",
                "project.fulfillment.snapshot.read"));
    }

    private static CommandMetadata meta(String key) {
        return new CommandMetadata("corr-" + key, "idem-" + key);
    }
}
