package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluation;
import com.serviceos.configuration.api.ExpressionEvaluationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceOsExprV1EvaluatorTest {
    private final ServiceOsExprV1Evaluator evaluator = new ServiceOsExprV1Evaluator();

    @Test
    void evaluatesBrandEqualityAndRecordsBindings() {
        ExpressionEvaluation evaluation = evaluator.evaluate(
                new ExpressionDefinition(
                        ExpressionDefinition.SERVICEOS_EXPR_V1,
                        "workOrder.brandCode == \"BYD_OCEAN\""),
                sampleContext("BYD_OCEAN"));

        assertThat(evaluation.result()).isTrue();
        assertThat(evaluation.bindings()).containsEntry("workOrder.brandCode", "BYD_OCEAN");
    }

    @Test
    void evaluatesFalseConditionWithoutBindingsForUnusedPaths() {
        ExpressionEvaluation evaluation = evaluator.evaluate(
                new ExpressionDefinition(
                        ExpressionDefinition.SERVICEOS_EXPR_V1,
                        "workOrder.brandCode == \"OTHER\""),
                sampleContext("BYD_OCEAN"));

        assertThat(evaluation.result()).isFalse();
        assertThat(evaluation.bindings()).containsEntry("workOrder.brandCode", "BYD_OCEAN");
    }

    @Test
    void supportsLogicalOperatorsAndGrouping() {
        ExpressionEvaluation evaluation = evaluator.evaluate(
                new ExpressionDefinition(
                        ExpressionDefinition.SERVICEOS_EXPR_V1,
                        "(task.stageCode == \"SURVEY\" && workOrder.brandCode == \"BYD_OCEAN\") || false"),
                sampleContext("BYD_OCEAN"));

        assertThat(evaluation.result()).isTrue();
        assertThat(evaluation.bindings()).containsKeys("task.stageCode", "workOrder.brandCode");
    }

    @Test
    void rejectsUnknownPathsAndIllegalSyntax() {
        assertThatThrownBy(() -> evaluator.evaluate(
                new ExpressionDefinition(
                        ExpressionDefinition.SERVICEOS_EXPR_V1,
                        "pole == true"),
                sampleContext("BYD_OCEAN")))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("不在白名单");

        assertThatThrownBy(() -> evaluator.evaluate(
                new ExpressionDefinition(
                        ExpressionDefinition.SERVICEOS_EXPR_V1,
                        "workOrder.brandCode =="),
                sampleContext("BYD_OCEAN")))
                .isInstanceOf(ExpressionEvaluationException.class);
    }

    @Test
    void rejectsExcessiveNestingAndOperatorCount() {
        String deeplyNested = "!".repeat(65) + "true";
        assertThatThrownBy(() -> evaluator.evaluate(
                new ExpressionDefinition(ExpressionDefinition.SERVICEOS_EXPR_V1, deeplyNested),
                sampleContext("BYD_OCEAN")))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("嵌套深度超过上限");

        String tooManyOperators = String.join("||", java.util.Collections.nCopies(258, "true"));
        assertThatThrownBy(() -> evaluator.evaluate(
                new ExpressionDefinition(ExpressionDefinition.SERVICEOS_EXPR_V1, tooManyOperators),
                sampleContext("BYD_OCEAN")))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("操作符数量超过上限");
    }

    @Test
    void evaluatesTypedFormFieldWithComplexStableKey() {
        ExpressionContext context = sampleContext("BYD_OCEAN").withFormValues(Map.of(
                "pole.height-mm", 1800L,
                "needs.photo", true));

        ExpressionEvaluation evaluation = evaluator.evaluate(
                new ExpressionDefinition(ExpressionDefinition.SERVICEOS_EXPR_V1,
                        "formValues[\"pole.height-mm\"] == 1800 && formValues[\"needs.photo\"] == true"),
                context);

        assertThat(evaluation.result()).isTrue();
        assertThat(evaluation.bindings())
                .containsEntry("formValues[\"pole.height-mm\"]", new BigDecimal("1800"))
                .containsEntry("formValues[\"needs.photo\"]", true);
    }

    @Test
    void strictPublicationRejectsUnknownOrIncompatibleFormField() {
        ExpressionDefinition unknown = new ExpressionDefinition(
                ExpressionDefinition.SERVICEOS_EXPR_V1,
                "formValues[\"missing\"] == true");
        ExpressionDefinition wrongType = new ExpressionDefinition(
                ExpressionDefinition.SERVICEOS_EXPR_V1,
                "formValues[\"count\"] == \"one\"");

        assertThatThrownBy(() -> evaluator.validate(unknown, Map.of("count", "INTEGER")))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("未声明");
        assertThatThrownBy(() -> evaluator.validate(wrongType, Map.of("count", "INTEGER")))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("类型");
    }

    private static ExpressionContext sampleContext(String brandCode) {
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext("BYD", brandCode, "HOME_CHARGING"),
                new ExpressionContext.RegionContext("370000", "370100", "370102"),
                new ExpressionContext.TaskContext("SURVEY", "SITE_SURVEY"));
    }
}
