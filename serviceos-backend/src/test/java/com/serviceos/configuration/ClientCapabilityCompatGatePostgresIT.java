package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ApproveConfigurationDraftCommand;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationDraftService;
import com.serviceos.configuration.api.ConfigurationDraftView;
import com.serviceos.configuration.api.ConfigurationPublicationException;
import com.serviceos.configuration.api.CreateConfigurationDraftCommand;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M356：FORM/EVIDENCE 客户端能力兼容发布门禁。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ClientCapabilityCompatGatePostgresIT {
    private static final String TENANT = "tenant-cfg-m356-it";
    private static final String ACTOR = "designer-m356";

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
        registry.add("serviceos.outbox.worker-id", () -> "cfg-m356-it");
    }

    @Autowired ConfigurationDraftService drafts;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE cfg_configuration_asset_client_target, cfg_configuration_asset_draft,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version,
                    auth_role_grant, auth_role_capability, auth_role, aud_audit_record CASCADE
                """).update();
        seedGrants();
    }

    @Test
    void enumFormFailsValidationClosed() {
        ConfigurationDraftView created = drafts.create(principal(), meta("enum"),
                new CreateConfigurationDraftCommand(
                        ConfigurationAssetType.FORM, "designer.form.enum.m356", "1.0.0", "1.0.0",
                        """
                        {"formKey":"designer.form.enum.m356","version":"1.0.0","stage":"SURVEY",
                         "sections":[{"sectionKey":"base","title":"基础","fields":[{
                           "fieldKey":"kind","label":"类型","dataType":"ENUM",
                           "binding":"task.input.kind"}]}]}
                        """, null));
        assertThatThrownBy(() -> drafts.validate(principal(), meta("v-enum"), created.draftId()))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("form.fieldType.ENUM");
        ConfigurationDraftView after = drafts.get(principal(), "corr-get", created.draftId());
        assertThat(after.status()).isEqualTo("DRAFT");
        assertThat(after.validationErrors()).anyMatch(item -> item.contains("禁止发布"));
        assertThat(after.clientCompatibility()).isNotNull();
        assertThat(after.clientCompatibility().blocking()).isTrue();
    }

    @Test
    void visibleWhenFormValidatesWithIosGapReport() {
        ConfigurationDraftView created = drafts.create(principal(), meta("cond"),
                new CreateConfigurationDraftCommand(
                        ConfigurationAssetType.FORM, "designer.form.cond.m356", "1.0.0", "1.0.0",
                        """
                        {"formKey":"designer.form.cond.m356","version":"1.0.0","stage":"SURVEY",
                         "sections":[{"sectionKey":"base","title":"基础","fields":[
                           {"fieldKey":"need","label":"需要","dataType":"BOOLEAN","binding":"task.input.need"},
                           {"fieldKey":"detail","label":"详情","dataType":"STRING","binding":"task.input.detail",
                            "visibleWhen":{"language":"SERVICEOS_EXPR_V1","source":"formValues[\\"need\\"] == true"}}]}]}
                        """, null));
        ConfigurationDraftView validated = drafts.validate(principal(), meta("v-cond"), created.draftId());
        assertThat(validated.status()).isEqualTo("VALIDATED");
        assertThat(validated.clientCompatibility().blocking()).isFalse();
        assertThat(validated.clientCompatibility().clientReports())
                .anySatisfy(report -> {
                    assertThat(report.clientKind()).isEqualTo("TECHNICIAN_IOS");
                    assertThat(report.compatible()).isFalse();
                    assertThat(report.missingCapabilities()).contains("form.condition.visibleWhen");
                });
        Integer audits = jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE tenant_id = :tenant
                   AND action_name = 'CONFIGURATION_DRAFT_CLIENT_COMPAT_VALIDATED'
                   AND decision_code = 'ALLOW'
                """).param("tenant", TENANT).query(Integer.class).single();
        assertThat(audits).isGreaterThanOrEqualTo(1);
    }

    @Test
    void signatureEvidenceFailsValidationClosed() {
        ConfigurationDraftView created = drafts.create(principal(), meta("sig"),
                new CreateConfigurationDraftCommand(
                        ConfigurationAssetType.EVIDENCE, "designer.evidence.sig.m356", "1.0.0", "1.0.0",
                        """
                        {"templateKey":"designer.evidence.sig.m356","version":"1.0.0","title":"签名",
                         "stage":"SURVEY","items":[{"evidenceKey":"customer.sign","name":"签名",
                           "mediaType":"SIGNATURE","required":true,
                           "capture":{"allowCamera":false,"allowGallery":false,"minCount":1,"maxCount":1},
                           "reviewPolicy":{"reviewRequired":true,"allowItemLevelReject":true}}]}
                        """, null));
        assertThatThrownBy(() -> drafts.validate(principal(), meta("v-sig"), created.draftId()))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("evidence.mediaType.SIGNATURE");
    }

    @Test
    void webOnlyVisibleWhenValidatesAndPublishesWithClientTarget() {
        ConfigurationDraftView created = drafts.create(principal(), meta("web-only"),
                new CreateConfigurationDraftCommand(
                        ConfigurationAssetType.FORM, "designer.form.webonly.m358", "1.0.0", "1.0.0",
                        """
                        {"formKey":"designer.form.webonly.m358","version":"1.0.0","stage":"SURVEY",
                         "sections":[{"sectionKey":"base","title":"基础","fields":[
                           {"fieldKey":"need","label":"需要","dataType":"BOOLEAN","binding":"task.input.need"},
                           {"fieldKey":"detail","label":"详情","dataType":"STRING","binding":"task.input.detail",
                            "visibleWhen":{"language":"SERVICEOS_EXPR_V1","source":"formValues[\\"need\\"] == true"}}]}]}
                        """,
                        null,
                        List.of("TECHNICIAN_WEB")));
        assertThat(created.supportedClientKinds()).containsExactly("TECHNICIAN_WEB");
        ConfigurationDraftView validated = drafts.validate(principal(), meta("v-web"), created.draftId());
        assertThat(validated.status()).isEqualTo("VALIDATED");
        assertThat(validated.clientCompatibility().blocking()).isFalse();
        assertThat(validated.clientCompatibility().clientReports()).hasSize(1);
        drafts.approve(principal(), meta("a-web"),
                new ApproveConfigurationDraftCommand(
                        created.draftId(), validated.aggregateVersion(), "APR-M358-WEB"));
        ConfigurationDraftView published = drafts.publish(principal(), meta("p-web"), created.draftId());
        assertThat(published.status()).isEqualTo("PUBLISHED");
        Integer targets = jdbc.sql("""
                SELECT count(*) FROM cfg_configuration_asset_client_target
                 WHERE tenant_id = :tenant AND version_id = :versionId
                """)
                .param("tenant", TENANT)
                .param("versionId", published.publishedVersionId())
                .query(Integer.class)
                .single();
        assertThat(targets).isEqualTo(1);
    }

    @Test
    void scalarFormPublishesAndAuditsAllow() {
        ConfigurationDraftView created = drafts.create(principal(), meta("ok"),
                new CreateConfigurationDraftCommand(
                        ConfigurationAssetType.FORM, "designer.form.ok.m356", "1.0.0", "1.0.0",
                        """
                        {"formKey":"designer.form.ok.m356","version":"1.0.0","stage":"SURVEY",
                         "sections":[{"sectionKey":"base","title":"基础","fields":[{
                           "fieldKey":"result.value","label":"结果","dataType":"STRING",
                           "binding":"task.input.result.value"}]}]}
                        """, null));
        ConfigurationDraftView validated = drafts.validate(principal(), meta("v-ok"), created.draftId());
        ConfigurationDraftView approved = drafts.approve(principal(), meta("a-ok"),
                new ApproveConfigurationDraftCommand(
                        created.draftId(), validated.aggregateVersion(), "APR-M356-OK"));
        ConfigurationDraftView published = drafts.publish(principal(), meta("p-ok"), created.draftId());
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.clientCompatibility().blocking()).isFalse();
        assertThat(approved.status()).isEqualTo("APPROVED");
        Integer audits = jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE tenant_id = :tenant
                   AND action_name = 'CONFIGURATION_DRAFT_CLIENT_COMPAT_PUBLISH'
                   AND decision_code = 'ALLOW'
                """).param("tenant", TENANT).query(Integer.class).single();
        assertThat(audits).isEqualTo(1);
    }

    private void seedGrants() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'cfg-designer-m356', '配置设计器M356', 'ACTIVE', now())
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
                    now() - interval '1 day', 'TEST', 'm356', now()
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
        return new CommandMetadata("corr-m356-" + key, "idem-m356-" + key);
    }
}
