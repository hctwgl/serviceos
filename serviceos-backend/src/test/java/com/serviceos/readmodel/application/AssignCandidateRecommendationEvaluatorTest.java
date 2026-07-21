package com.serviceos.readmodel.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssignCandidateRecommendationEvaluatorTest {
    @Test
    void recommendedWhenSameCityCoverageApprovedAndAssignable() {
        var projection = AssignCandidateRecommendationEvaluator.evaluate(
                true, 2, 0, 0, false, 0,
                AssignCandidateDistanceEvaluator.TIER_SAME_CITY, true, 7);
        assertThat(projection.recommendationTier()).isEqualTo("RECOMMENDED");
        assertThat(projection.recommendationSummary()).startsWith("建议优先：");
        assertThat(projection.recommendationReasons()).anyMatch(r -> r.contains("同城"));
        assertThat(projection.recommendationReasons()).anyMatch(r -> r.contains("已通过资质"));
    }

    @Test
    void cautionWhenOutsideCoverage() {
        var projection = AssignCandidateRecommendationEvaluator.evaluate(
                true, 1, 0, 1, false, 0,
                AssignCandidateDistanceEvaluator.TIER_OUTSIDE_COVERAGE, false, 3);
        assertThat(projection.recommendationTier()).isEqualTo("CAUTION");
        assertThat(projection.recommendationSummary()).startsWith("谨慎：");
        assertThat(projection.recommendationReasons()).anyMatch(r -> r.contains("覆盖未命中"));
    }

    @Test
    void notAssignableWhenInactive() {
        var projection = AssignCandidateRecommendationEvaluator.evaluate(
                false, 0, 0, 0, false, 0,
                AssignCandidateDistanceEvaluator.TIER_UNKNOWN, false, null);
        assertThat(projection.recommendationTier()).isEqualTo("NOT_ASSIGNABLE");
        assertThat(projection.recommendationSummary()).contains("不可分配");
    }

    @Test
    void acceptableWhenProvinceOnlyCoverage() {
        var projection = AssignCandidateRecommendationEvaluator.evaluate(
                true, 1, 0, 0, false, 0,
                AssignCandidateDistanceEvaluator.TIER_SAME_PROVINCE, true, 5);
        assertThat(projection.recommendationTier()).isEqualTo("ACCEPTABLE");
        assertThat(projection.recommendationSummary()).startsWith("可用：");
    }
}
