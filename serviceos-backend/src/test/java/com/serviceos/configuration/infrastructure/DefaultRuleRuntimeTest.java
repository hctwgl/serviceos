package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.RuleResolution;
import com.serviceos.configuration.api.RuleResolveCommand;
import com.serviceos.configuration.application.DefaultRuleRuntime;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultRuleRuntimeTest {

    @Test
    void aggregatesBlockOverWarnings() {
        String definition = """
                {
                  "ruleKey":"evidence-review",
                  "version":"1.0.0",
                  "subjectType":"EVIDENCE_REVIEW",
                  "stage":"INTERNAL",
                  "defaultAction":"PASS",
                  "rules":[
                    {"ruleCode":"MISSING_PHOTO","name":"缺照片","severity":"WARN",
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "rejectReasonCode":"WARN_PHOTO","message":"建议补拍"},
                    {"ruleCode":"WRONG_BRAND","name":"品牌不符","severity":"BLOCK",
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "rejectReasonCode":"BRAND_MISMATCH","message":"阻断"}
                  ]
                }
                """;
        RuleResolution resolution = runtimeWith(definition).resolve(
                command("evidence-review", "EVIDENCE_REVIEW", "INTERNAL"));
        assertThat(resolution.decision()).isEqualTo("BLOCK");
        assertThat(resolution.hits()).hasSize(2);
        assertThat(resolution.hits()).extracting(RuleResolution.RuleHit::severity)
                .containsExactlyInAnyOrder("WARN", "BLOCK");
        assertThat(resolution.contentDigest()).hasSize(64);
    }

    @Test
    void returnsPassWithWarningsWhenOnlyWarnHits() {
        String definition = """
                {
                  "ruleKey":"evidence-review",
                  "version":"1.0.0",
                  "subjectType":"EVIDENCE_REVIEW",
                  "stage":"INTERNAL",
                  "defaultAction":"PASS",
                  "rules":[
                    {"ruleCode":"SOFT","name":"软提示","severity":"WARN",
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "rejectReasonCode":"SOFT_WARN","message":"注意"}
                  ]
                }
                """;
        RuleResolution resolution = runtimeWith(definition).resolve(
                command("evidence-review", "EVIDENCE_REVIEW", "INTERNAL"));
        assertThat(resolution.decision()).isEqualTo("PASS_WITH_WARNINGS");
        assertThat(resolution.hits()).hasSize(1);
    }

    @Test
    void returnsRequireApprovalWhenNoBlock() {
        String definition = """
                {
                  "ruleKey":"form-review",
                  "version":"1.0.0",
                  "subjectType":"FORM_REVIEW",
                  "stage":"CLIENT",
                  "defaultAction":"PASS",
                  "rules":[
                    {"ruleCode":"NEED_APPROVAL","name":"需审批","severity":"REQUIRE_APPROVAL",
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "rejectReasonCode":"NEED_APPR","message":"人工"}
                  ]
                }
                """;
        RuleResolution resolution = runtimeWith(definition).resolve(
                command("form-review", "FORM_REVIEW", "CLIENT"));
        assertThat(resolution.decision()).isEqualTo("REQUIRE_APPROVAL");
    }

    @Test
    void usesDefaultActionWhenNoRuleHits() {
        String definition = """
                {
                  "ruleKey":"evidence-review",
                  "version":"1.0.0",
                  "subjectType":"EVIDENCE_REVIEW",
                  "stage":"INTERNAL",
                  "defaultAction":"REQUIRE_MANUAL",
                  "rules":[
                    {"ruleCode":"NEVER","name":"永不","severity":"BLOCK",
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"OTHER\\""},
                     "rejectReasonCode":"X","message":"x"}
                  ]
                }
                """;
        RuleResolution resolution = runtimeWith(definition).resolve(
                command("evidence-review", "EVIDENCE_REVIEW", "INTERNAL"));
        assertThat(resolution.decision()).isEqualTo("REQUIRE_MANUAL");
        assertThat(resolution.hits()).isEmpty();
        assertThat(resolution.defaultAction()).isEqualTo("REQUIRE_MANUAL");
    }

    @Test
    void failsClosedOnSubjectStageMismatch() {
        String definition = """
                {
                  "ruleKey":"evidence-review",
                  "version":"1.0.0",
                  "subjectType":"EVIDENCE_REVIEW",
                  "stage":"INTERNAL",
                  "defaultAction":"PASS",
                  "rules":[
                    {"ruleCode":"R1","name":"r","severity":"WARN",
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "rejectReasonCode":"X","message":"x"}
                  ]
                }
                """;
        assertThatThrownBy(() -> runtimeWith(definition).resolve(
                command("evidence-review", "FORM_REVIEW", "INTERNAL")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void failsClosedWhenRuleKeyMissing() {
        String definition = """
                {
                  "ruleKey":"exists",
                  "version":"1.0.0",
                  "subjectType":"EVIDENCE_REVIEW",
                  "stage":"INTERNAL",
                  "defaultAction":"PASS",
                  "rules":[
                    {"ruleCode":"R1","name":"r","severity":"WARN",
                     "when":{"language":"SERVICEOS_EXPR_V1","source":"workOrder.brandCode == \\"BYD_OCEAN\\""},
                     "rejectReasonCode":"X","message":"x"}
                  ]
                }
                """;
        assertThatThrownBy(() -> runtimeWith(definition).resolve(
                command("missing", "EVIDENCE_REVIEW", "INTERNAL")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    private static RuleResolveCommand command(String ruleKey, String subjectType, String stage) {
        return new RuleResolveCommand(
                "tenant-a", UUID.randomUUID(), "a".repeat(64), ruleKey,
                subjectType, stage, context());
    }

    private static ExpressionContext context() {
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext("BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL"),
                new ExpressionContext.RegionContext("370000", "370100", "370102"),
                new ExpressionContext.TaskContext("REVIEW", "HUMAN"));
    }

    private static DefaultRuleRuntime runtimeWith(String definitionJson) {
        String ruleKey;
        if (definitionJson.contains("\"ruleKey\":\"form-review\"")) {
            ruleKey = "form-review";
        } else if (definitionJson.contains("\"ruleKey\":\"exists\"")) {
            ruleKey = "exists";
        } else {
            ruleKey = "evidence-review";
        }
        UUID versionId = UUID.randomUUID();
        String digest = Sha256.digest(definitionJson);
        ConfigurationAssetDefinition asset = new ConfigurationAssetDefinition(
                versionId, ConfigurationAssetType.RULE, ruleKey,
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
        return new DefaultRuleRuntime(
                configurations, new ServiceOsExprV1Evaluator(), JsonMapper.builder().build());
    }
}
