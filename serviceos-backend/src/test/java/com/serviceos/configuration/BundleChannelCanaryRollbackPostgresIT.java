package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ActivateBundleChannelCommand;
import com.serviceos.configuration.api.BundleChannel;
import com.serviceos.configuration.api.BundleChannelActivationService;
import com.serviceos.configuration.api.BundleChannelActivationView;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.configuration.api.ResolveConfigurationBundleQuery;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** M286：CANARY 激活、preferCanary 解析、晋级与回滚。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BundleChannelCanaryRollbackPostgresIT {
    private static final String TENANT = "tenant-cfg-m286-it";
    private static final String ACTOR = "releaser-m286";
    private static final String PROJECT_CODE = "PLATFORM-M286";

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
        registry.add("serviceos.outbox.worker-id", () -> "cfg-m286-it");
    }

    @Autowired ConfigurationService configurations;
    @Autowired BundleChannelActivationService activations;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    ConfigurationBundleReference stableBundle;
    ConfigurationBundleReference canaryBundle;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE cfg_bundle_channel_activation, cfg_configuration_asset_draft,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, auth_role_grant, auth_role_capability,
                    auth_role, prj_project CASCADE
                """).update();
        seedGrants();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, :projectCode, 'PLATFORM', 'M286 灰度测试',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("projectCode", PROJECT_CODE)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();

        UUID workflowV1 = publishWorkflow("platform.m286.v1", "TASK_V1");
        UUID workflowV2 = publishWorkflow("platform.m286.v2", "TASK_V2");
        Instant from = Instant.now().minusSeconds(60);
        // 不同 province 作用域避免发布期重叠冲突；通道激活解析不再依赖 province 扫描。
        stableBundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "BUNDLE-M286-STABLE", "1.0.0", "PLATFORM",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", from, null, List.of(workflowV1)));
        canaryBundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "BUNDLE-M286-CANARY", "1.1.0", "PLATFORM",
                "HOME_CHARGING_SURVEY_INSTALL", "370100", from, null, List.of(workflowV2)));

        activations.activate(principal(), meta("stable"), new ActivateBundleChannelCommand(
                projectId, BundleChannel.STABLE, stableBundle.bundleId(), "APR-STABLE-1"));
    }

    @Test
    void canaryResolvePromoteAndRollback() {
        ResolveConfigurationBundleQuery defaultQuery = new ResolveConfigurationBundleQuery(
                TENANT, PROJECT_CODE, "PLATFORM", "HOME_CHARGING_SURVEY_INSTALL",
                "370000", Instant.now());
        assertThat(configurations.resolve(defaultQuery).bundleId()).isEqualTo(stableBundle.bundleId());

        BundleChannelActivationView canary = activations.activate(principal(), meta("canary"),
                new ActivateBundleChannelCommand(
                        projectId, BundleChannel.CANARY, canaryBundle.bundleId(), "APR-CANARY-1"));
        assertThat(canary.status()).isEqualTo("ACTIVE");
        assertThat(canary.channel()).isEqualTo(BundleChannel.CANARY);

        ResolveConfigurationBundleQuery canaryQuery = new ResolveConfigurationBundleQuery(
                TENANT, PROJECT_CODE, "PLATFORM", "HOME_CHARGING_SURVEY_INSTALL",
                "370000", Instant.now(), true);
        assertThat(configurations.resolve(canaryQuery).bundleId()).isEqualTo(canaryBundle.bundleId());
        assertThat(configurations.resolve(defaultQuery).bundleId()).isEqualTo(stableBundle.bundleId());

        BundleChannelActivationView promoted = activations.promoteCanary(
                principal(), meta("promote"), canary.activationId(), "APR-PROMOTE-1");
        assertThat(promoted.channel()).isEqualTo(BundleChannel.STABLE);
        assertThat(promoted.bundleId()).isEqualTo(canaryBundle.bundleId());
        assertThat(promoted.previousActivationId()).isNotNull();
        assertThat(configurations.resolve(defaultQuery).bundleId()).isEqualTo(canaryBundle.bundleId());

        BundleChannelActivationView rolled = activations.rollbackStable(
                principal(), meta("rollback"), promoted.activationId(), "APR-ROLLBACK-1");
        assertThat(rolled.channel()).isEqualTo(BundleChannel.STABLE);
        assertThat(rolled.bundleId()).isEqualTo(stableBundle.bundleId());
        assertThat(configurations.resolve(defaultQuery).bundleId()).isEqualTo(stableBundle.bundleId());
    }

    private UUID publishWorkflow(String key, String taskType) {
        String definition = """
                {"workflowKey":"%s","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"TASK","nodeType":"SERVICE_TASK","name":"任务",
                    "stageCode":"STAGE","taskType":"%s"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"TASK"},
                   {"transitionId":"t2","from":"TASK","to":"END"}]}
                """.formatted(key, taskType).trim();
        return configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, key, "1.0.0", "1.0.0",
                definition, Sha256.digest(definition))).versionId();
    }

    private void seedGrants() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'cfg-release-m286', '配置发布M286', 'ACTIVE', now())
                """).param("id", roleId).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:id, 'configuration.release.manage', now())
                """).param("id", roleId).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at
                ) VALUES (
                    :grant, :tenant, :principal, :role, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST', 'm286', now()
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
                "admin-web", Set.of("configuration.release.manage"));
    }

    private static CommandMetadata meta(String key) {
        return new CommandMetadata("corr-" + key, "idem-" + key);
    }
}
