package com.serviceos.evidence.application;

import com.serviceos.configuration.api.ExpressionDefinition;

import java.util.Map;
import java.util.UUID;

/** 单个 requiredWhen 的确定性决策事实；无论 true/false 都进入解析级审计解释。 */
public record ResolvedEvidenceCondition(
        UUID templateVersionId,
        String templateKey,
        String templateVersion,
        String templateDigest,
        String evidenceKey,
        ExpressionDefinition expression,
        boolean result,
        Map<String, Object> bindings
) {
    public ResolvedEvidenceCondition {
        bindings = Map.copyOf(bindings);
    }
}
