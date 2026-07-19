package com.serviceos.configuration.api;

import java.util.List;
import java.util.UUID;

/**
 * ASSIGNEE_POLICY 运行时输出：匹配策略、可交给 TaskAssignment 的 USER 候选、Fallback 与解释。
 *
 * <p>本结果本身不写 TaskAssignment；调用方必须以 {@code ASSIGNEE_POLICY} sourceType
 * 与 {@code assetVersionId} 调用既有 assignCandidates。</p>
 */
public record AssigneePolicyResolution(
        String policyKey,
        UUID assetVersionId,
        String contentDigest,
        List<MatchedStrategy> matchedStrategies,
        List<String> resolvedUserPrincipalIds,
        FallbackDecision fallback,
        boolean requiresManualIntervention,
        List<String> explanations
) {
    public AssigneePolicyResolution {
        matchedStrategies = List.copyOf(matchedStrategies);
        resolvedUserPrincipalIds = List.copyOf(resolvedUserPrincipalIds);
        explanations = List.copyOf(explanations);
    }

    public record MatchedStrategy(
            String strategyKey,
            String candidateType,
            int priority,
            String roleCode,
            int selectedCount
    ) {
    }

    public record FallbackDecision(
            String mode,
            String roleCode,
            boolean applied
    ) {
    }
}
