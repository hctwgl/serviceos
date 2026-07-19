package com.serviceos.configuration.api;

import java.util.List;
import java.util.UUID;

/**
 * DISPATCH 运行时输出：过滤/评分排序、拒绝原因、Fallback 与解释。
 *
 * <p>确定性：同分按 candidateId 字典序；不使用随机/ML。</p>
 */
public record DispatchResolution(
        String policyKey,
        UUID assetVersionId,
        String contentDigest,
        List<RankedCandidate> rankedCandidates,
        List<RejectedCandidate> rejectedCandidates,
        FallbackDecision fallback,
        boolean requiresManualIntervention,
        List<String> explanations
) {
    public DispatchResolution {
        rankedCandidates = List.copyOf(rankedCandidates);
        rejectedCandidates = List.copyOf(rejectedCandidates);
        explanations = List.copyOf(explanations);
    }

    public record RankedCandidate(
            String candidateId,
            double score,
            int rank,
            List<String> scoreBreakdown
    ) {
        public RankedCandidate {
            scoreBreakdown = List.copyOf(scoreBreakdown);
        }
    }

    public record RejectedCandidate(
            String candidateId,
            String failureCode,
            String filterKey
    ) {
    }

    public record FallbackDecision(
            String onNoCandidate,
            String manualRole,
            int resolutionHours,
            boolean applied
    ) {
    }
}
