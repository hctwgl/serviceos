package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ApproveConfigurationDraftCommand;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationDraftDiffView;
import com.serviceos.configuration.api.ConfigurationDraftService;
import com.serviceos.configuration.api.ConfigurationDraftView;
import com.serviceos.configuration.api.CreateConfigurationDraftCommand;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.UpdateConfigurationDraftCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M285：Diff 与审批门禁。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConfigurationDraftDiffApprovalPostgresIT {
    private static final String TENANT = "tenant-cfg-m285-it";
    private static final String ACTOR = "designer-m285";

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
        registry.add("serviceos.outbox.worker-id", () -> "cfg-m285-it");
    }

    @Autowired ConfigurationDraftService drafts;
    @Autowired ConfigurationService configurations;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE cfg_configuration_asset_draft, cfg_configuration_bundle_item,
                    cfg_configuration_bundle, cfg_configuration_asset_version,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        seedGrants();
    }

    @Test
    void diffApproveThenPublish() {
        String v1 = sla("designer.sla.m285", "1.0.0", 3600);
        configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.SLA, "designer.sla.m285", "1.0.0", "1.0.0",
                v1, Sha256.digest(v1)));

        String v2 = sla("designer.sla.m285", "1.0.1", 7200);
        ConfigurationDraftView created = drafts.create(principal(), meta("c1"),
                new CreateConfigurationDraftCommand(
                        ConfigurationAssetType.SLA, "designer.sla.m285", "1.0.1", "1.0.0", v2, null));
        assertThat(created.baseVersionId()).isNotNull();

        ConfigurationDraftView validated = drafts.validate(principal(), meta("v1"), created.draftId());
        ConfigurationDraftDiffView diff = drafts.diff(principal(), "corr-diff", created.draftId());
        assertThat(diff.identical()).isFalse();
        assertThat(diff.unifiedDiff()).contains("+").contains("7200");

        assertThatThrownBy(() -> drafts.publish(principal(), meta("p-early"), created.draftId()))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.VERSION_CONFLICT);

        ConfigurationDraftView approved = drafts.approve(principal(), meta("a1"),
                new ApproveConfigurationDraftCommand(
                        created.draftId(), validated.aggregateVersion(), "APR-M285-1"));
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.approvalRef()).isEqualTo("APR-M285-1");

        ConfigurationDraftView published = drafts.publish(principal(), meta("p1"), created.draftId());
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.publishedVersionId()).isNotNull();
    }

    @Test
    void editingApprovedDraftClearsApproval() {
        ConfigurationDraftView created = drafts.create(principal(), meta("c2"),
                new CreateConfigurationDraftCommand(
                        ConfigurationAssetType.SLA, "designer.sla.edit", "1.0.0", "1.0.0",
                        sla("designer.sla.edit", "1.0.0", 1000), null));
        ConfigurationDraftView validated = drafts.validate(principal(), meta("v2"), created.draftId());
        ConfigurationDraftView approved = drafts.approve(principal(), meta("a2"),
                new ApproveConfigurationDraftCommand(
                        created.draftId(), validated.aggregateVersion(), "APR-EDIT"));
        ConfigurationDraftView edited = drafts.update(principal(), meta("u2"),
                new UpdateConfigurationDraftCommand(
                        created.draftId(), approved.aggregateVersion(),
                        sla("designer.sla.edit", "1.0.0", 2000)));
        assertThat(edited.status()).isEqualTo("DRAFT");
        assertThat(edited.approvalRef()).isNull();
    }

    private void seedGrants() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'cfg-designer-m285', '配置设计器M285', 'ACTIVE', now())
                """).param("id", roleId).param("tenant", TENANT).update();
        for (String capability : List.of(
                "configuration.draft.write", "configuration.approve", "configuration.publish")) {
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
                    now() - interval '1 day', 'TEST', 'm285', now()
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
                        "configuration.draft.write", "configuration.approve", "configuration.publish"));
    }

    private static CommandMetadata meta(String key) {
        return new CommandMetadata("corr-" + key, "idem-" + key);
    }

    private static String sla(String key, String version, int seconds) {
        return """
                {"policyKey":"%s","version":"%s","subjectType":"TASK",
                 "taskTypes":["DESIGNER_TASK"],"startEvent":"TASK_CREATED","stopEvent":"TASK_COMPLETED",
                 "clockMode":"ELAPSED","targetDurationSeconds":%d}
                """.formatted(key, version, seconds).trim();
    }
}
