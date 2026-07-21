package com.serviceos.readmodel.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Network 分配候选「推荐解释」读模型：把可证明的运营事实汇总为档位与中文摘要。
 *
 * <p>不输出数值评分或内部公式；禁止把未知事实伪装成高分推荐。</p>
 */
final class AssignCandidateRecommendationEvaluator {
    static final String TIER_RECOMMENDED = "RECOMMENDED";
    static final String TIER_ACCEPTABLE = "ACCEPTABLE";
    static final String TIER_CAUTION = "CAUTION";
    static final String TIER_NOT_ASSIGNABLE = "NOT_ASSIGNABLE";

    static final String RANKING_EXPLANATION =
            "排序：可分配优先 → 推荐档位 → 行政区亲和 → 开放任务少 → 姓名；依据可见运营事实，不含内部评分公式。";
    static final String EMPTY_NO_TECHNICIANS =
            "本网点当前没有 ACTIVE 师傅成员，请先维护师傅关系与资质。";

    private AssignCandidateRecommendationEvaluator() {
    }

    record RecommendationProjection(
            String recommendationTier,
            String recommendationSummary,
            List<String> recommendationReasons
    ) {
        RecommendationProjection {
            Objects.requireNonNull(recommendationTier, "recommendationTier");
            Objects.requireNonNull(recommendationSummary, "recommendationSummary");
            recommendationReasons = List.copyOf(
                    Objects.requireNonNull(recommendationReasons, "recommendationReasons"));
        }
    }

    static int tierRank(String recommendationTier) {
        if (recommendationTier == null) {
            return 99;
        }
        return switch (recommendationTier) {
            case TIER_RECOMMENDED -> 0;
            case TIER_ACCEPTABLE -> 1;
            case TIER_CAUTION -> 2;
            case TIER_NOT_ASSIGNABLE -> 3;
            default -> 99;
        };
    }

    static RecommendationProjection evaluate(
            boolean assignable,
            int approvedQualificationCount,
            int pendingQualificationCount,
            int openTaskCount,
            boolean scheduleOverlap,
            int upcomingAppointmentCount,
            String distanceTier,
            boolean coverageMatched,
            Integer capacityAvailableUnits
    ) {
        List<String> reasons = new ArrayList<>();
        if (!assignable) {
            reasons.add("师傅关系或档案非 ACTIVE");
            return new RecommendationProjection(
                    TIER_NOT_ASSIGNABLE,
                    "不可分配：师傅关系或档案非 ACTIVE",
                    reasons);
        }

        boolean caution = false;
        if (scheduleOverlap) {
            reasons.add("预约窗口重叠");
            caution = true;
        }
        if (AssignCandidateDistanceEvaluator.TIER_OUTSIDE_COVERAGE.equals(distanceTier)) {
            reasons.add("覆盖未命中工单行政区");
            caution = true;
        } else if (AssignCandidateDistanceEvaluator.TIER_UNKNOWN.equals(distanceTier)) {
            reasons.add("服务区域未知");
            caution = true;
        } else if (coverageMatched) {
            reasons.add(distanceAffinityLabel(distanceTier));
        }

        if (approvedQualificationCount <= 0) {
            reasons.add(pendingQualificationCount > 0 ? "尚无已通过资质" : "无资质记录");
            caution = true;
        } else {
            reasons.add("已通过资质 " + approvedQualificationCount + " 项");
        }

        if (capacityAvailableUnits != null && capacityAvailableUnits <= 0) {
            reasons.add("网点产能已满");
            caution = true;
        } else if (capacityAvailableUnits != null) {
            reasons.add("网点产能可用");
        }

        if (!scheduleOverlap && upcomingAppointmentCount > 0) {
            reasons.add("另有 " + upcomingAppointmentCount + " 个未完成预约");
        }
        if (openTaskCount > 0) {
            reasons.add("开放任务 " + openTaskCount + " 个");
        } else {
            reasons.add("当前无开放任务");
        }

        if (caution) {
            return new RecommendationProjection(
                    TIER_CAUTION,
                    "谨慎：" + joinTop(reasons, 3),
                    reasons);
        }

        boolean recommended = coverageMatched
                && (AssignCandidateDistanceEvaluator.TIER_SAME_DISTRICT.equals(distanceTier)
                || AssignCandidateDistanceEvaluator.TIER_SAME_CITY.equals(distanceTier))
                && approvedQualificationCount > 0
                && !scheduleOverlap
                && (capacityAvailableUnits == null || capacityAvailableUnits > 0);
        if (recommended) {
            return new RecommendationProjection(
                    TIER_RECOMMENDED,
                    "建议优先：" + joinTop(reasons, 3),
                    reasons);
        }
        return new RecommendationProjection(
                TIER_ACCEPTABLE,
                "可用：" + joinTop(reasons, 3),
                reasons);
    }

    private static String distanceAffinityLabel(String distanceTier) {
        if (AssignCandidateDistanceEvaluator.TIER_SAME_DISTRICT.equals(distanceTier)) {
            return "同区覆盖";
        }
        if (AssignCandidateDistanceEvaluator.TIER_SAME_CITY.equals(distanceTier)) {
            return "同城覆盖";
        }
        if (AssignCandidateDistanceEvaluator.TIER_SAME_PROVINCE.equals(distanceTier)) {
            return "同省覆盖";
        }
        return "覆盖命中";
    }

    private static String joinTop(List<String> reasons, int limit) {
        if (reasons.isEmpty()) {
            return "暂无附加说明";
        }
        int end = Math.min(limit, reasons.size());
        return String.join(" + ", reasons.subList(0, end));
    }
}
