package com.serviceos.evidence.infrastructure;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.ExpressionEvaluation;
import com.serviceos.configuration.api.ExpressionEvaluationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonEvidenceTemplateResolverTest {
    private final JsonEvidenceTemplateResolver resolver =
            new JsonEvidenceTemplateResolver(new ObjectMapper(), (expression, context) -> {
                if (!expression.source().startsWith("workOrder.brandCode == \"")) {
                    throw new ExpressionEvaluationException(
                            "表达式路径不在白名单中: " + expression.source());
                }
                String expected = expression.source()
                        .substring("workOrder.brandCode == \"".length(), expression.source().length() - 1);
                return new ExpressionEvaluation(
                        context.workOrder().brandCode().equals(expected),
                        java.util.Map.of("workOrder.brandCode", context.workOrder().brandCode()),
                        expression);
            });

    @Test
    void resolvesOnlyMatchingFixedStageAndDerivesSafeCountDefaults() {
        ConfigurationAssetDefinition template = asset("""
                {
                  "templateKey":"survey.site","version":"1.0.0","stage":"SURVEY",
                  "items":[
                    {"evidenceKey":"site.photo","name":"现场照","mediaType":"PHOTO","required":true},
                    {"evidenceKey":"site.note","name":"补充资料","mediaType":"DOCUMENT","required":false}
                  ]
                }
                """);

        assertThat(resolver.resolve(template, "INSTALLATION", JsonEvidenceTemplateResolverTest::unexpectedContext)
                .requirements())
                .isEmpty();
        assertThat(resolver.resolve(template, "SURVEY", JsonEvidenceTemplateResolverTest::unexpectedContext)
                .requirements())
                .extracting(requirement -> requirement.requirementCode() + ":"
                        + requirement.minCount() + ":" + requirement.maxCount())
                .containsExactly("site.photo:1:null", "site.note:0:null");
    }

    @Test
    void omitsConditionalSlotWhenExpressionIsFalse() {
        ConfigurationAssetDefinition template = asset("""
                {
                  "templateKey":"survey.conditional","version":"1.0.0","stage":"SURVEY",
                  "items":[
                    {"evidenceKey":"site.photo","name":"现场照","mediaType":"PHOTO","required":true},
                    {"evidenceKey":"pole.photo","name":"立柱照片","mediaType":"PHOTO","required":false,
                     "requiredWhen":{"language":"SERVICEOS_EXPR_V1",
                       "source":"workOrder.brandCode == \\\"OTHER\\\""}}
                  ]
                }
                """);

        var resolution = resolver.resolve(template, "SURVEY", () -> sampleContext("BYD_OCEAN"));
        assertThat(resolution.requirements())
                .extracting(requirement -> requirement.requirementCode())
                .containsExactly("site.photo");
        // 条件为 false 时没有槽位承载解释，因此必须由解析级决策事实保留该结果。
        assertThat(resolution.conditions()).singleElement().satisfies(condition -> {
            assertThat(condition.evidenceKey()).isEqualTo("pole.photo");
            assertThat(condition.result()).isFalse();
            assertThat(condition.bindings()).containsEntry("workOrder.brandCode", "BYD_OCEAN");
        });
    }

    @Test
    void createsConditionalSlotWhenExpressionIsTrue() {
        ConfigurationAssetDefinition template = asset("""
                {
                  "templateKey":"survey.conditional","version":"1.0.0","stage":"SURVEY",
                  "items":[{"evidenceKey":"pole.photo","name":"立柱照片","mediaType":"PHOTO",
                    "required":false,"requiredWhen":{"language":"SERVICEOS_EXPR_V1",
                      "source":"workOrder.brandCode == \\\"BYD_OCEAN\\\""}}]
                }
                """);

        assertThat(resolver.resolve(template, "SURVEY", () -> sampleContext("BYD_OCEAN")).requirements())
                .singleElement()
                .satisfies(requirement -> {
                    assertThat(requirement.requirementCode()).isEqualTo("pole.photo");
                    assertThat(requirement.required()).isTrue();
                    assertThat(requirement.minCount()).isEqualTo(1);
                    assertThat(requirement.resolutionExplanationJson()).contains("CONDITIONAL");
                });
    }

    @Test
    void rejectsIllegalExpressionsInsteadOfGuessingRuntimeSemantics() {
        assertThatThrownBy(() -> resolver.resolve(asset("""
                {
                  "templateKey":"survey.site","version":"1.0.0","stage":"SURVEY",
                  "items":[{"evidenceKey":"site.photo","name":"现场照","mediaType":"PHOTO",
                    "required":false,"requiredWhen":{"language":"SERVICEOS_EXPR_V1","source":"pole == true"}}]
                }
                """), "SURVEY", () -> sampleContext("BYD_OCEAN")))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("不在白名单");
        assertThatThrownBy(() -> resolver.resolve(asset("""
                {"templateKey":"global","version":"1.0.0","items":[
                  {"evidenceKey":"site.photo","name":"现场照","mediaType":"PHOTO","required":true}]}
                """), "SURVEY", () -> sampleContext("BYD_OCEAN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage");
    }

    @Test
    void rejectsExplicitZeroMinCountWhenConditionMakesRequirementMandatory() {
        assertThatThrownBy(() -> resolver.resolve(asset("""
                {
                  "templateKey":"survey.conditional","version":"1.0.0","stage":"SURVEY",
                  "items":[{"evidenceKey":"pole.photo","name":"立柱照片","mediaType":"PHOTO",
                    "required":false,"capture":{"minCount":0},
                    "requiredWhen":{"language":"SERVICEOS_EXPR_V1",
                      "source":"workOrder.brandCode == \\\"BYD_OCEAN\\\""}}]
                }
                """), "SURVEY", () -> sampleContext("BYD_OCEAN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minCount 必须大于零");
    }

    private static ExpressionContext sampleContext(String brandCode) {
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext("BYD", brandCode, "HOME_CHARGING"),
                new ExpressionContext.RegionContext("370000", "370100", "370102"),
                new ExpressionContext.TaskContext("SURVEY", "SITE_SURVEY"));
    }

    private static ExpressionContext unexpectedContext() {
        throw new AssertionError("固定模板不得加载条件表达式上下文");
    }

    private ConfigurationAssetDefinition asset(String definition) {
        return new ConfigurationAssetDefinition(
                UUID.randomUUID(), ConfigurationAssetType.EVIDENCE, "survey.site",
                "1.0.0", "1.0.0", definition.trim(), "a".repeat(64));
    }
}
