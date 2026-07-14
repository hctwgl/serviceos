package com.serviceos.forms.application;

import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormValueValidatorTest {
    private final FormValueValidator validator = new FormValueValidator(JsonMapper.builder().build());

    @Test
    void expressionsAndValidatorParametersFailClosedUntilAdr018IsApproved() {
        String expressionDefinition = """
                {"sections":[{"fields":[{"fieldKey":"answer","dataType":"STRING",
                  "requiredWhen":{"language":"SERVICEOS_EXPR_V1","source":"task.required"}}]}]}
                """;
        String validatorDefinition = """
                {"sections":[{"fields":[{"fieldKey":"answer","dataType":"STRING",
                  "validators":[{"type":"REGEX","parameters":{"pattern":".*"}}]}]}]}
                """;

        assertUnsupported(expressionDefinition);
        assertUnsupported(validatorDefinition);
    }

    private void assertUnsupported(String definition) {
        assertThatThrownBy(() -> validator.validate(definition, "{}"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.FORM_RUNTIME_UNSUPPORTED));
    }
}
