package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationDraftService;
import com.serviceos.configuration.api.ConfigurationDraftView;
import com.serviceos.configuration.api.ConfigurationPublicationException;
import com.serviceos.configuration.api.CreateConfigurationDraftCommand;
import com.serviceos.configuration.api.UpdateConfigurationDraftCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
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

/** M282：Workflow 草稿设计器 create → validate → publish。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkflowConfigurationDesignerPostgresIT {
    private static final String TENANT = "tenant-cfg-m282-it";
    private static final String ACTOR = "designer-m282";

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
        registry.add("serviceos.outbox.worker-id", () -> "cfg-m282-it");
    }

    @Autowired ConfigurationDraftService drafts;
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
    void draftValidateAndPublishWorkflow() {
        ConfigurationDraftView created = drafts.create(principal(), meta("c1"), new CreateConfigurationDraftCommand(
                ConfigurationAssetType.WORKFLOW, "platform.designer.m282", "1.0.0", "1.0.0",
                validWorkflow(), null));
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.aggregateVersion()).isEqualTo(1L);

        ConfigurationDraftView updated = drafts.update(principal(), meta("u1"),
                new UpdateConfigurationDraftCommand(created.draftId(), 1L, validWorkflow()));
        assertThat(updated.status()).isEqualTo("DRAFT");
        assertThat(updated.aggregateVersion()).isEqualTo(2L);

        ConfigurationDraftView validated = drafts.validate(principal(), meta("v1"), created.draftId());
        assertThat(validated.status()).isEqualTo("VALIDATED");
        assertThat(validated.validationErrors()).isEmpty();

        ConfigurationDraftView published = drafts.publish(principal(), meta("p1"), created.draftId());
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.publishedVersionId()).isNotNull();
        assertThat(jdbc.sql("""
                SELECT status FROM cfg_configuration_asset_version WHERE version_id = :id
                """).param("id", published.publishedVersionId()).query(String.class).single())
                .isEqualTo("PUBLISHED");
    }

    @Test
    void invalidWorkflowFailsValidationClosed() {
        ConfigurationDraftView created = drafts.create(principal(), meta("bad"), new CreateConfigurationDraftCommand(
                ConfigurationAssetType.WORKFLOW, "platform.designer.m282.bad", "1.0.0", "1.0.0",
                """
                {"workflowKey":"bad.m282","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"GW","nodeType":"EXCLUSIVE_GATEWAY","name":"坏网关"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"GW"},
                   {"transitionId":"t2","from":"GW","to":"END"}]}
                """, null));
        assertThatThrownBy(() -> drafts.validate(principal(), meta("v-bad"), created.draftId()))
                .isInstanceOf(ConfigurationPublicationException.class);
        assertThat(drafts.get(principal(), "corr-get", created.draftId()).status()).isEqualTo("DRAFT");
        assertThat(drafts.get(principal(), "corr-get", created.draftId()).validationErrors()).isNotEmpty();
    }

    @Test
    void nonWorkflowAssetTypeRejected() {
        assertThatThrownBy(() -> drafts.create(principal(), meta("form"), new CreateConfigurationDraftCommand(
                ConfigurationAssetType.FORM, "form.m282", "1.0.0", "1.0.0", "{}", null)))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.VALIDATION_FAILED);
    }

    private void seedGrants() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'cfg-designer', '配置设计器', 'ACTIVE', now())
                """).param("id", roleId).param("tenant", TENANT).update();
        for (String capability : List.of("configuration.draft.write", "configuration.publish")) {
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
                    now() - interval '1 day', 'TEST', 'm282', now()
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
                "admin-web", Set.of("configuration.draft.write", "configuration.publish"));
    }

    private static CommandMetadata meta(String key) {
        return new CommandMetadata("corr-" + key, "idem-" + key);
    }

    private static String validWorkflow() {
        return """
                {"workflowKey":"designer.m282","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"TASK_A","nodeType":"SERVICE_TASK","name":"任务A",
                    "stageCode":"STAGE_A","taskType":"DESIGNER_TASK"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"TASK_A"},
                   {"transitionId":"t2","from":"TASK_A","to":"END"}]}
                """;
    }
}
