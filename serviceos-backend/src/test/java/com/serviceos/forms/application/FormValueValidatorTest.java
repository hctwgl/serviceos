package com.serviceos.forms.application;

import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.ExpressionEvaluation;
import com.serviceos.configuration.api.ExpressionEvaluationException;
import com.serviceos.configuration.api.ExpressionEvaluator;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormValueValidatorTest {
    private final ExpressionEvaluator expressions = (expression, context) -> {
        Object actual = context.formValues().get("needs-photo");
        if (actual == null) {
            throw new ExpressionEvaluationException("表达式字段缺失: needs-photo");
        }
        boolean result = Boolean.TRUE.equals(actual);
        return new ExpressionEvaluation(result, Map.of("formValues[\"needs-photo\"]", actual), expression);
    };
    private final FormValueValidator validator = new FormValueValidator(
            JsonMapper.builder().build(), expressions);
    private final ExpressionContext context = new ExpressionContext(
            new ExpressionContext.WorkOrderContext("CLIENT", "BRAND", "PRODUCT"),
            new ExpressionContext.RegionContext("11", "1101", "110101"),
            new ExpressionContext.TaskContext("INSTALL", "FIELD"));

    @Test
    void requiredWhenUsesCurrentSubmissionScalarValues() {
        String definition = """
                {"sections":[{"fields":[
                  {"fieldKey":"needs-photo","dataType":"BOOLEAN","required":true},
                  {"fieldKey":"photo-note","dataType":"STRING",
                   "requiredWhen":{"language":"SERVICEOS_EXPR_V1","source":"formValues[\\"needs-photo\\"] == true"}}
                ]}]}
                """;

        assertThat(validator.validate(definition, "{\"needs-photo\":false}", context).status())
                .isEqualTo("VALIDATED");
        FormValueValidator.ValidationResult required = validator.validate(
                definition, "{\"needs-photo\":true}", context);
        assertThat(required.status()).isEqualTo("INVALID");
        assertThat(required.errors()).extracting("code").contains("FIELD_REQUIRED");
    }

    @Test
    void missingConditionInputFailsClosedInsteadOfBecomingFalse() {
        String definition = """
                {"sections":[{"fields":[{"fieldKey":"photo-note","dataType":"STRING",
                  "requiredWhen":{"language":"SERVICEOS_EXPR_V1","source":"formValues[\\"needs-photo\\"] == true"}}]}]}
                """;

        FormValueValidator.ValidationResult result = validator.validate(definition, "{}", context);

        assertThat(result.status()).isEqualTo("INVALID");
        assertThat(result.errors()).extracting("code")
                .contains("FIELD_REQUIRED_CONDITION_INVALID");
    }

    @Test
    void unapprovedValidatorParametersRemainFailClosed() {
        String definition = """
                {"sections":[{"fields":[{"fieldKey":"answer","dataType":"STRING",
                  "validators":[{"type":"REGEX","parameters":{"pattern":".*"}}]}]}]}
                """;

        assertThatThrownBy(() -> validator.validate(definition, "{}", context))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.FORM_RUNTIME_UNSUPPORTED));
    }
}
