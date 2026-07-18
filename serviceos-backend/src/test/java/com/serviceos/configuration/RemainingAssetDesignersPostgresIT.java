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

/** M295：NOTIFICATION/ASSIGNEE_POLICY/INTEGRATION/PRICING 设计器。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RemainingAssetDesignersPostgresIT {
    private static final String TENANT = "tenant-cfg-m295-it";
    private static final String ACTOR = "designer-m295";

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
        registry.add("serviceos.outbox.worker-id", () -> "cfg-m295-it");
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
    void remainingAssetTypesPublish() {
        assertPublished(ConfigurationAssetType.NOTIFICATION, "designer.notification.m295", validNotification());
        assertPublished(ConfigurationAssetType.ASSIGNEE_POLICY, "designer.assignee.m295", validAssignee());
        assertPublished(ConfigurationAssetType.INTEGRATION, "designer.integration.m295", validIntegration());
        assertPublished(ConfigurationAssetType.PRICING, "designer.pricing.m295", validPricing());
    }

    @Test
    void invalidNotificationFailsClosed() {
        ConfigurationDraftView created = drafts.create(principal(), meta("bad-n"),
                new CreateConfigurationDraftCommand(
                        ConfigurationAssetType.NOTIFICATION, "designer.notification.bad", "1.0.0", "1.0.0",
                        """
                        {"policyKey":"designer.notification.bad","version":"1.0.0",
                         "defaultChannel":"IN_APP","triggers":[]}
                        """, null));
        assertThatThrownBy(() -> drafts.validate(principal(), meta("v-bad-n"), created.draftId()))
                .isInstanceOf(ConfigurationPublicationException.class);
        assertThat(drafts.get(principal(), "corr-get", created.draftId()).validationErrors()).isNotEmpty();
    }

    private void assertPublished(ConfigurationAssetType type, String key, String definition) {
        ConfigurationDraftView created = drafts.create(principal(), meta("c-" + key),
                new CreateConfigurationDraftCommand(type, key, "1.0.0", "1.0.0", definition, null));
        ConfigurationDraftView validated = drafts.validate(principal(), meta("v-" + key), created.draftId());
        assertThat(validated.status()).isEqualTo("VALIDATED");
        ConfigurationDraftView approved = drafts.approve(principal(), meta("a-" + key),
                new ApproveConfigurationDraftCommand(
                        created.draftId(), validated.aggregateVersion(), "APR-" + key));
        assertThat(approved.status()).isEqualTo("APPROVED");
        ConfigurationDraftView published = drafts.publish(principal(), meta("p-" + key), created.draftId());
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.publishedVersionId()).isNotNull();
    }

    private void seedGrants() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'cfg-designer-m295', '配置设计器M295', 'ACTIVE', now())
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
                    now() - interval '1 day', 'TEST', 'm295', now()
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

    private static String validNotification() {
        return """
                {"policyKey":"designer.notification.m295","version":"1.0.0","defaultChannel":"IN_APP",
                 "triggers":[{"triggerKey":"task-completed","eventType":"task.completed",
                   "templateKey":"task.completed.inapp","channel":"IN_APP",
                   "when":{"language":"SERVICEOS_EXPR_V1","source":"task.taskType == \\"DESIGNER_TASK\\""},
                   "recipientRole":"PROJECT_MANAGER"}]}
                """;
    }

    private static String validAssignee() {
        return """
                {"policyKey":"designer.assignee.m295","version":"1.0.0",
                 "strategies":[{"strategyKey":"role-pool","candidateType":"ROLE","priority":10,
                   "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"PLATFORM\\""},
                   "roleCode":"TECHNICIAN","maxCandidates":20}],
                 "fallback":{"mode":"MANUAL_INTERVENTION","roleCode":"DISPATCHER"}}
                """;
    }

    private static String validIntegration() {
        return """
                {"mappingKey":"designer.integration.m295","version":"1.0.0","connectorCode":"REFERENCE_OEM",
                 "direction":"INBOUND",
                 "fieldMappings":[{"mappingId":"order-code","externalPath":"orderNo",
                   "internalPath":"externalOrderCode","required":true,"transform":"TRIM"}]}
                """;
    }

    private static String validPricing() {
        return """
                {"pricingKey":"designer.pricing.m295","version":"1.0.0","currency":"CNY",
                 "lines":[{"lineKey":"base-install","chargeCode":"INSTALL_BASE","amountMinor":19900,
                   "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.serviceProductCode == \\"INSTALL\\""},
                   "billableTo":"OEM"}]}
                """;
    }
}
