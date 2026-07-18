package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationDraftService;
import com.serviceos.configuration.api.ConfigurationDraftView;
import com.serviceos.configuration.api.ConfigurationPublicationException;
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

/** M283：FORM/EVIDENCE/SLA 设计器草稿校验发布。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FormEvidenceSlaDesignerPostgresIT {
    private static final String TENANT = "tenant-cfg-m283-it";
    private static final String ACTOR = "designer-m283";

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
        registry.add("serviceos.outbox.worker-id", () -> "cfg-m283-it");
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
    void formEvidenceAndSlaDraftsPublish() {
        assertPublished(ConfigurationAssetType.FORM, "designer.form.m283", validForm());
        assertPublished(ConfigurationAssetType.EVIDENCE, "designer.evidence.m283", validEvidence());
        assertPublished(ConfigurationAssetType.SLA, "designer.sla.m283", validSla());
    }

    @Test
    void invalidFormFailsValidationClosed() {
        ConfigurationDraftView created = drafts.create(principal(), meta("bad-form"),
                new CreateConfigurationDraftCommand(
                        ConfigurationAssetType.FORM, "designer.form.bad", "1.0.0", "1.0.0",
                        """
                        {"formKey":"bad.form","version":"1.0.0","stage":"SURVEY","sections":[]}
                        """, null));
        assertThatThrownBy(() -> drafts.validate(principal(), meta("v-bad-form"), created.draftId()))
                .isInstanceOf(ConfigurationPublicationException.class);
        assertThat(drafts.get(principal(), "corr-get", created.draftId()).status()).isEqualTo("DRAFT");
        assertThat(drafts.get(principal(), "corr-get", created.draftId()).validationErrors()).isNotEmpty();
    }

    @Test
    void ruleTypeStillRejected() {
        assertThatThrownBy(() -> drafts.create(principal(), meta("rule"), new CreateConfigurationDraftCommand(
                ConfigurationAssetType.RULE, "rule.m283", "1.0.0", "1.0.0", "{}", null)))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.VALIDATION_FAILED);
    }

    private void assertPublished(ConfigurationAssetType type, String key, String definition) {
        ConfigurationDraftView created = drafts.create(principal(), meta("c-" + key),
                new CreateConfigurationDraftCommand(type, key, "1.0.0", "1.0.0", definition, null));
        ConfigurationDraftView validated = drafts.validate(principal(), meta("v-" + key), created.draftId());
        assertThat(validated.status()).isEqualTo("VALIDATED");
        ConfigurationDraftView published = drafts.publish(principal(), meta("p-" + key), created.draftId());
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.publishedVersionId()).isNotNull();
        assertThat(jdbc.sql("""
                SELECT asset_type FROM cfg_configuration_asset_version WHERE version_id = :id
                """).param("id", published.publishedVersionId()).query(String.class).single())
                .isEqualTo(type.name());
    }

    private void seedGrants() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'cfg-designer-m283', '配置设计器M283', 'ACTIVE', now())
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
                    now() - interval '1 day', 'TEST', 'm283', now()
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

    private static String validForm() {
        return """
                {"formKey":"designer.form.m283","version":"1.0.0","stage":"SURVEY",
                 "sections":[{"sectionKey":"base","title":"基础","fields":[{
                   "fieldKey":"result.value","label":"结果","dataType":"STRING",
                   "binding":"task.input.result.value"}]}]}
                """;
    }

    private static String validEvidence() {
        return """
                {"templateKey":"designer.evidence.m283","version":"1.0.0","title":"现场资料","stage":"SURVEY",
                 "items":[{"evidenceKey":"site.panorama","name":"全景图","mediaType":"PHOTO","required":true,
                   "capture":{"allowCamera":true,"allowGallery":false,"requireRealtimeCapture":true,
                     "requireGps":true,"watermarkFields":["TIME","GPS","WORK_ORDER_NO"],
                     "minCount":1,"maxCount":3,"maxSizeBytes":10485760},
                   "qualityChecks":[{"checkType":"BLUR","severity":"BLOCK"}],
                   "reviewPolicy":{"reviewRequired":true,"allowItemLevelReject":true}}]}
                """;
    }

    private static String validSla() {
        return """
                {"policyKey":"designer.sla.m283","version":"1.0.0","subjectType":"TASK",
                 "taskTypes":["DESIGNER_TASK"],"startEvent":"TASK_CREATED","stopEvent":"TASK_COMPLETED",
                 "clockMode":"ELAPSED","targetDurationSeconds":3600}
                """;
    }
}
