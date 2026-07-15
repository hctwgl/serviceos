package com.serviceos.configuration.api;

import java.util.Map;

/** 确定性布尔求值结果与命中绑定，供审计与 condition_input_digest。 */
public record ExpressionEvaluation(
        boolean result,
        Map<String, String> bindings,
        ExpressionDefinition expression
) {
}
