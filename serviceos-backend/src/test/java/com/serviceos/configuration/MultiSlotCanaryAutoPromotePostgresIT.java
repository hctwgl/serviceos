package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ActivateBundleChannelCommand;
import com.serviceos.configuration.api.AdjustCanaryTrafficCommand;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M290：多槽位 CANARY、流量预算与满量自动晋级。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MultiSlotCanaryAutoPromotePostgresIT {
    private static final String TENANT = "tenant-cfg-m290-it";
    private static final String ACTOR = "releaser-m290";
    private static final String PROJECT_CODE = "PLATFORM-M290";

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
        registry.add("serviceos.outbox.worker-id", () -> "cfg-m290-it");
    }

    @Autowired ConfigurationService configurations;
    @Autowired BundleChannelActivationService activations;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    ConfigurationBundleReference stableBundle;
    ConfigurationBundleReference canaryA;
    ConfigurationBundleReference canaryB;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE cfg_bundle_channel_activation, cfg_configuration_bundle_item,
                    cfg_configuration_bundle, cfg_configuration_asset_version,
                    auth_role_grant, auth_role_capability, auth_role, prj_project CASCADE
                """).update();
        seedGrants();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, :projectCode, 'PLATFORM', 'M290 多槽位灰度',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("projectCode", PROJECT_CODE)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        Instant from = Instant.now().minusSeconds(60);
        stableBundle = publishBundle("BUNDLE-M290-S", "1.0.0", "370000", "platform.m290.s", "TASK_S");
        canaryA = publishBundle("BUNDLE-M290-A", "1.1.0", "370100", "platform.m290.a", "TASK_A");
        canaryB = publishBundle("BUNDLE-M290-B", "1.2.0", "370200", "platform.m290.b", "TASK_B");
        activations.activate(principal(), meta("s"), new ActivateBundleChannelCommand(
                projectId, BundleChannel.STABLE, stableBundle.bundleId(), "APR-S", 100));
    }

    @Test
    void multiSlotTrafficBudgetAndAutoPromote() {
        BundleChannelActivationView slotA = activations.activate(principal(), meta("a"),
                new ActivateBundleChannelCommand(
                        projectId, BundleChannel.CANARY, canaryA.bundleId(), "APR-A",
                        30, "slot-a", false));
        BundleChannelActivationView slotB = activations.activate(principal(), meta("b"),
                new ActivateBundleChannelCommand(
                        projectId, BundleChannel.CANARY, canaryB.bundleId(), "APR-B",
                        20, "slot-b", false));
        assertThat(slotA.slotCode()).isEqualTo("slot-a");
        assertThat(slotB.slotCode()).isEqualTo("slot-b");

        assertThatThrownBy(() -> activations.activate(principal(), meta("overflow"),
                new ActivateBundleChannelCommand(
                        projectId, BundleChannel.CANARY, canaryA.bundleId(), "APR-X",
                        60, "slot-c", false)))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.VALIDATION_FAILED);

        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            seen.add(resolve("key-" + i).bundleId());
        }
        assertThat(seen).contains(stableBundle.bundleId());
        assertThat(seen.stream().anyMatch(id -> id.equals(canaryA.bundleId()) || id.equals(canaryB.bundleId())))
                .isTrue();

        BundleChannelActivationView promoted = activations.adjustCanaryTraffic(principal(), meta("adj"),
                new AdjustCanaryTrafficCommand(slotA.activationId(), slotA.aggregateVersion(), 100, true));
        assertThat(promoted.channel()).isEqualTo(BundleChannel.STABLE);
        assertThat(promoted.bundleId()).isEqualTo(canaryA.bundleId());
        assertThat(resolve("after-promote").bundleId()).isEqualTo(canaryA.bundleId());
    }

    private ConfigurationBundleReference resolve(String routingKey) {
        return configurations.resolve(new ResolveConfigurationBundleQuery(
                TENANT, PROJECT_CODE, "PLATFORM", "HOME_CHARGING_SURVEY_INSTALL",
                "370000", Instant.now(), false, routingKey));
    }

    private ConfigurationBundleReference publishBundle(
            String code, String version, String province, String workflowKey, String taskType
    ) {
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
                """.formatted(workflowKey, taskType).trim();
        UUID workflowId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, workflowKey, "1.0.0", "1.0.0",
                definition, Sha256.digest(definition))).versionId();
        return configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, code, version, "PLATFORM",
                "HOME_CHARGING_SURVEY_INSTALL", province, Instant.now().minusSeconds(60),
                null, List.of(workflowId)));
    }

    private void seedGrants() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'cfg-release-m290', '配置发布M290', 'ACTIVE', now())
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
                    now() - interval '1 day', 'TEST', 'm290', now()
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
