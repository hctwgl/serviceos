package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.NotificationResolution;
import com.serviceos.configuration.api.NotificationResolveCommand;
import com.serviceos.configuration.application.DefaultNotificationRuntime;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultNotificationRuntimeTest {

    @Test
    void matchesTriggerEvaluatesWhenAndSendsInApp() {
        String definition = """
                {
                  "policyKey":"default-notify",
                  "version":"1.0.0",
                  "defaultChannel":"IN_APP",
                  "triggers":[
                    {"triggerKey":"assigned","eventType":"workorder.assigned",
                     "templateKey":"WO_ASSIGNED","channel":"IN_APP",
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "recipientRole":"NETWORK_DISPATCHER"},
                    {"triggerKey":"other","eventType":"workorder.cancelled",
                     "templateKey":"WO_CANCELLED","channel":"SMS",
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "recipientRole":"OPS"}
                  ]
                }
                """;
        var runtime = runtimeWith(definition);
        NotificationResolution resolution = runtime.resolveAndDispatch(new NotificationResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "default-notify",
                "workorder.assigned", "evt-1", context("BYD_OCEAN"),
                Map.of("NETWORK_DISPATCHER", List.of("u1", "u2")),
                Map.of("workOrderCode", "WO-1")));
        assertThat(resolution.attempts()).hasSize(2);
        assertThat(resolution.attempts()).allMatch(a -> "SENT".equals(a.outcome()));
        assertThat(resolution.attempts()).extracting(NotificationResolution.DeliveryAttempt::channel)
                .containsOnly("IN_APP");
        assertThat(resolution.requiresManualIntervention()).isFalse();
        assertThat(resolution.contentDigest()).hasSize(64);
    }

    @Test
    void smsWithoutCredentialsIsUnknownAndRequiresManualIntervention() {
        String definition = """
                {
                  "policyKey":"sms",
                  "version":"1.0.0",
                  "defaultChannel":"SMS",
                  "triggers":[
                    {"triggerKey":"sms-alert","eventType":"workorder.assigned",
                     "templateKey":"SMS_ALERT","channel":"SMS",
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "recipientRole":"OPS"}
                  ]
                }
                """;
        var runtime = runtimeWith(definition);
        NotificationResolution resolution = runtime.resolveAndDispatch(new NotificationResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "sms",
                "workorder.assigned", "evt-sms", context("BYD_OCEAN"),
                Map.of("OPS", List.of("ops1")), Map.of()));
        assertThat(resolution.attempts()).hasSize(1);
        assertThat(resolution.attempts().getFirst().outcome()).isEqualTo("UNKNOWN");
        assertThat(resolution.attempts().getFirst().detail()).isEqualTo("LOCAL_REFERENCE_NO_CREDENTIALS");
        assertThat(resolution.requiresManualIntervention()).isTrue();
    }

    @Test
    void idempotentReplayReturnsSentReplay() {
        String definition = """
                {
                  "policyKey":"idem",
                  "version":"1.0.0",
                  "defaultChannel":"IN_APP",
                  "triggers":[
                    {"triggerKey":"t1","eventType":"workorder.assigned",
                     "templateKey":"T","channel":"IN_APP",
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "recipientRole":"OPS"}
                  ]
                }
                """;
        var runtime = runtimeWith(definition);
        NotificationResolveCommand command = new NotificationResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "idem",
                "workorder.assigned", "evt-idem", context("BYD_OCEAN"),
                Map.of("OPS", List.of("ops1")), Map.of());
        NotificationResolution first = runtime.resolveAndDispatch(command);
        NotificationResolution second = runtime.resolveAndDispatch(command);
        assertThat(first.attempts().getFirst().outcome()).isEqualTo("SENT");
        assertThat(second.attempts().getFirst().outcome()).isEqualTo("SENT_REPLAY");
        assertThat(second.attempts().getFirst().detail())
                .isEqualTo(first.attempts().getFirst().detail());
    }

    @Test
    void emptyRecipientsRequiresManualIntervention() {
        String definition = """
                {
                  "policyKey":"empty-r",
                  "version":"1.0.0",
                  "defaultChannel":"IN_APP",
                  "triggers":[
                    {"triggerKey":"t1","eventType":"workorder.assigned",
                     "templateKey":"T","channel":"IN_APP",
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "recipientRole":"MISSING_ROLE"}
                  ]
                }
                """;
        var runtime = runtimeWith(definition);
        NotificationResolution resolution = runtime.resolveAndDispatch(new NotificationResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "empty-r",
                "workorder.assigned", "evt-empty", context("BYD_OCEAN"),
                Map.of(), Map.of()));
        assertThat(resolution.attempts()).isEmpty();
        assertThat(resolution.requiresManualIntervention()).isTrue();
    }

    @Test
    void failsClosedWhenPolicyMissing() {
        var runtime = runtimeWith("""
                {"policyKey":"exists","version":"1.0.0","defaultChannel":"IN_APP",
                 "triggers":[{"triggerKey":"t","eventType":"e","templateKey":"T","channel":"IN_APP",
                   "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                   "recipientRole":"OPS"}]}
                """);
        assertThatThrownBy(() -> runtime.resolveAndDispatch(new NotificationResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), "missing",
                "e", "evt", context("BYD_OCEAN"), Map.of(), Map.of())))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    private static ExpressionContext context(String brandCode) {
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext("BYD", brandCode, "HOME_CHARGING_SURVEY_INSTALL"),
                new ExpressionContext.RegionContext("370000", "370100", "370102"),
                new ExpressionContext.TaskContext("NOTIFY", "AUTO"));
    }

    private static DefaultNotificationRuntime runtimeWith(String definitionJson) {
        UUID versionId = UUID.randomUUID();
        String digest = Sha256.digest(definitionJson);
        ConfigurationAssetDefinition asset = new ConfigurationAssetDefinition(
                versionId, ConfigurationAssetType.NOTIFICATION, "default-notify",
                "1.0.0", "1.0.0", definitionJson, digest);
        ConfigurationService configurations = new ConfigurationService() {
            @Override
            public com.serviceos.configuration.api.ConfigurationAssetVersionReference publishAsset(
                    com.serviceos.configuration.api.PublishConfigurationAssetCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.serviceos.configuration.api.ConfigurationBundleReference publishBundle(
                    com.serviceos.configuration.api.PublishConfigurationBundleCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.serviceos.configuration.api.ConfigurationBundleReference resolve(
                    com.serviceos.configuration.api.ResolveConfigurationBundleQuery query) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ConfigurationAssetDefinition requireBundleAsset(
                    String tenantId, UUID bundleId, String expectedManifestDigest,
                    ConfigurationAssetType assetType) {
                return asset;
            }

            @Override
            public List<ConfigurationAssetDefinition> listBundleAssets(
                    String tenantId, UUID bundleId, String expectedManifestDigest,
                    ConfigurationAssetType assetType) {
                return List.of(asset);
            }

            @Override
            public ConfigurationAssetDefinition requireAssetVersion(
                    String tenantId, UUID versionIdArg, ConfigurationAssetType assetType,
                    String expectedContentDigest) {
                throw new UnsupportedOperationException();
            }
        };
        return new DefaultNotificationRuntime(
                configurations,
                new ServiceOsExprV1Evaluator(),
                List.of(new LocalReferenceNotificationChannelAdapter()),
                JsonMapper.builder().build());
    }
}
