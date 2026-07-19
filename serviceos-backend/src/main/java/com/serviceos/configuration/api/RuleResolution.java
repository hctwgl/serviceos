package com.serviceos.configuration.api;

import java.util.List;
import java.util.UUID;

/**
 * RULE 运行时输出。
 *
 * <p>{@code decision} 取值：PASS / PASS_WITH_WARNINGS / REQUIRE_APPROVAL / REQUIRE_MANUAL / BLOCK。</p>
 */
public record RuleResolution(
        String ruleKey,
        UUID assetVersionId,
        String contentDigest,
        String subjectType,
        String stage,
        String decision,
        String defaultAction,
        List<RuleHit> hits,
        List<String> explanations
) {
    public RuleResolution {
        hits = List.copyOf(hits);
        explanations = List.copyOf(explanations);
    }

    public record RuleHit(
            String ruleCode,
            String name,
            String severity,
            String rejectReasonCode,
            String message
    ) {
    }
}
