package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.AnalyzeConfigurationDependenciesCommand;
import com.serviceos.configuration.api.ApproveConfigurationDraftCommand;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationDependencyAnalysisService;
import com.serviceos.configuration.api.ConfigurationDependencyReport;
import com.serviceos.configuration.api.ConfigurationDependencyStatus;
import com.serviceos.configuration.api.ConfigurationDraftService;
import com.serviceos.configuration.api.ConfigurationDraftView;
import com.serviceos.configuration.api.CreateConfigurationDraftCommand;
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

/** M291：WORKFLOW 依赖分析。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConfigurationDependencyAnalysisPostgresIT {
    private static final String TENANT = "tenant-cfg-m291-it";
    private static final String ACTOR = "designer-m291";

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
        registry.add("serviceos.outbox.worker-id", () -> "cfg-m291-it");
    }

    @Autowired ConfigurationDraftService drafts;
    @Autowired ConfigurationDependencyAnalysisService dependencies;
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
    void publishedSlaSatisfiedMissingFormReported() {
        publish(ConfigurationAssetType.SLA, "dep.sla.m291", validSla("dep.sla.m291"));

        ConfigurationDraftView workflow = drafts.create(principal(), meta("wf"),
                new CreateConfigurationDraftCommand(
                        ConfigurationAssetType.WORKFLOW, "dep.workflow.m291", "1.0.0", "1.0.0",
                        workflowWithRefs("dep.sla.m291", "dep.form.missing"), null));

        ConfigurationDependencyReport report = dependencies.analyzeDraft(
                principal(), "corr-dep", workflow.draftId());

        assertThat(report.draftId()).isEqualTo(workflow.draftId());
        assertThat(report.complete()).isFalse();
        assertThat(report.dependencies()).hasSize(2);
        assertThat(report.dependencies()).anySatisfy(item -> {
            assertThat(item.refField()).isEqualTo("slaRef");
            assertThat(item.refValue()).isEqualTo("dep.sla.m291");
            assertThat(item.status()).isEqualTo(ConfigurationDependencyStatus.SATISFIED);
            assertThat(item.satisfiedVersionId()).isNotNull();
        });
        assertThat(report.dependencies()).anySatisfy(item -> {
            assertThat(item.refField()).isEqualTo("formRef");
            assertThat(item.refValue()).isEqualTo("dep.form.missing");
            assertThat(item.status()).isEqualTo(ConfigurationDependencyStatus.MISSING);
            assertThat(item.detail()).contains("未找到");
        });
    }

    @Test
    void openDraftOnlyStillMissing() {
        drafts.create(principal(), meta("form-draft"), new CreateConfigurationDraftCommand(
                ConfigurationAssetType.FORM, "dep.form.draft-only", "1.0.0", "1.0.0",
                validForm("dep.form.draft-only"), null));

        ConfigurationDependencyReport report = dependencies.analyze(
                principal(), "corr-open",
                new AnalyzeConfigurationDependenciesCommand(
                        ConfigurationAssetType.WORKFLOW, "dep.workflow.open",
                        workflowWithRefs(null, "dep.form.draft-only"), null));

        assertThat(report.complete()).isFalse();
        assertThat(report.dependencies()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(ConfigurationDependencyStatus.MISSING);
            assertThat(item.detail()).contains("仅有开放草稿");
        });
    }

    @Test
    void nonWorkflowRejected() {
        assertThatThrownBy(() -> dependencies.analyze(
                principal(), "corr-form",
                new AnalyzeConfigurationDependenciesCommand(
                        ConfigurationAssetType.FORM, "x", "{}", null)))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.VALIDATION_FAILED);
    }

    private void publish(ConfigurationAssetType type, String key, String definition) {
        ConfigurationDraftView created = drafts.create(principal(), meta("c-" + key),
                new CreateConfigurationDraftCommand(type, key, "1.0.0", "1.0.0", definition, null));
        ConfigurationDraftView validated = drafts.validate(principal(), meta("v-" + key), created.draftId());
        drafts.approve(principal(), meta("a-" + key),
                new ApproveConfigurationDraftCommand(
                        created.draftId(), validated.aggregateVersion(), "APR-" + key));
        drafts.publish(principal(), meta("p-" + key), created.draftId());
    }

    private void seedGrants() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'cfg-designer-m291', '配置设计器M291', 'ACTIVE', now())
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
                    now() - interval '1 day', 'TEST', 'm291', now()
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

    private static String workflowWithRefs(String slaRef, String formRef) {
        StringBuilder task = new StringBuilder(
                """
                {"nodeId":"TASK_A","nodeType":"SERVICE_TASK","name":"任务A",
                 "stageCode":"STAGE_A","taskType":"DESIGNER_TASK"
                """);
        if (slaRef != null) {
            task.append(",\"slaRef\":\"").append(slaRef).append('"');
        }
        if (formRef != null) {
            task.append(",\"formRef\":\"").append(formRef).append('"');
        }
        task.append('}');
        return """
                {"workflowKey":"dep.workflow.m291","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   %s,
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"TASK_A"},
                   {"transitionId":"t2","from":"TASK_A","to":"END"}]}
                """.formatted(task);
    }

    private static String validSla(String key) {
        return """
                {"policyKey":"%s","version":"1.0.0","subjectType":"TASK",
                 "taskTypes":["DESIGNER_TASK"],"startEvent":"TASK_CREATED","stopEvent":"TASK_COMPLETED",
                 "clockMode":"ELAPSED","targetDurationSeconds":3600}
                """.formatted(key);
    }

    private static String validForm(String key) {
        return """
                {"formKey":"%s","version":"1.0.0","stage":"SURVEY",
                 "sections":[{"sectionKey":"base","title":"基础","fields":[{
                   "fieldKey":"result.value","label":"结果","dataType":"STRING",
                   "binding":"task.input.result.value"}]}]}
                """.formatted(key);
    }
}
