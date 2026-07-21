package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationTaskTemplateItem;
import com.serviceos.configuration.api.ConfigurationTaskTemplateQuery;
import com.serviceos.configuration.api.CreateConfigurationDraftCommand;
import com.serviceos.configuration.api.ConfigurationDraftService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;
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

/** M387：任务模板读模型从 WORKFLOW 草稿投影。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConfigurationTaskTemplateQueryPostgresIT {
    private static final String TENANT = "tenant-task-template-m387";
    private static final String ACTOR = "designer-m387";

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
        registry.add("serviceos.outbox.worker-id", () -> "task-template-m387-it");
    }

    @Autowired ConfigurationTaskTemplateQuery query;
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
    void listsTaskTemplatesProjectedFromWorkflowDraft() {
        String definition = """
                {
                  "workflowKey":"platform.workflow.home-charging",
                  "semanticVersion":"1.0.0",
                  "startNodeId":"START",
                  "nodes":[
                    {"nodeId":"START","nodeType":"START","name":"开始"},
                    {"nodeId":"SURVEY","nodeType":"USER_TASK","name":"上门勘测","taskType":"SURVEY","stageCode":"SURVEY","formRef":"form.survey","evidenceRef":"ev.survey","slaRef":"sla.survey"},
                    {"nodeId":"INSTALL","nodeType":"USER_TASK","name":"上门安装","taskType":"INSTALL","stageCode":"INSTALL","formRef":"form.install"},
                    {"nodeId":"END","nodeType":"END","name":"完成"}
                  ],
                  "transitions":[],
                  "metadata":{"displayName":"家充勘测安装流程"}
                }
                """;
        drafts.create(principal(), meta("c1"),
                new CreateConfigurationDraftCommand(
                        ConfigurationAssetType.WORKFLOW,
                        "platform.workflow.home-charging",
                        "1.0.0",
                        "1.0.0",
                        definition,
                        null));

        List<ConfigurationTaskTemplateItem> items = query.list(principal(), "corr-list");
        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(item ->
                "SURVEY".equals(item.taskTypeCode())
                        && item.templateName().contains("勘测")
                        && item.categoryLabel().equals("勘测类")
                        && item.referencedWorkflowCount() >= 1
                        && item.referencedWorkflowNames().contains("家充勘测安装流程")
                        && item.formSummary().contains("form.survey"));
        assertThat(items).anyMatch(item -> "INSTALL".equals(item.taskTypeCode()));
        assertThat(items.getFirst().gaps()).isNotEmpty();
    }

    private void seedGrants() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'cfg-m387', '配置M387', 'ACTIVE', now())
                """).param("id", roleId).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:id, 'configuration.draft.write', now())
                """).param("id", roleId).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at
                ) VALUES (
                    :grant, :tenant, :principal, :role, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST', 'm387', now()
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
                "admin-web", Set.of("configuration.draft.write"));
    }

    private static CommandMetadata meta(String key) {
        return new CommandMetadata("corr-" + key, "idem-" + key);
    }
}
