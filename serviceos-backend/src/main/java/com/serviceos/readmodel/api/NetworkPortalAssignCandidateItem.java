package com.serviceos.readmodel.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Network Portal 分配师傅候选摘要。
 *
 * <p>开放任务、资质、产能、预约日程冲突、行政区距离亲和与推荐解释均来自本网点可证明事实；
 * 不返回伪造经纬度路网距离或内部评分公式。</p>
 */
public record NetworkPortalAssignCandidateItem(
        UUID technicianProfileId,
        String displayName,
        String membershipStatus,
        String profileStatus,
        int openTaskCount,
        int approvedQualificationCount,
        int pendingQualificationCount,
        String qualificationSummary,
        int upcomingAppointmentCount,
        String scheduleConflictSummary,
        boolean scheduleOverlap,
        String distanceTier,
        String distanceSummary,
        boolean coverageMatched,
        Integer capacityAvailableUnits,
        Integer capacityMaxUnits,
        List<String> warnings,
        boolean assignable,
        String recommendationTier,
        String recommendationSummary,
        List<String> recommendationReasons
) {
    public NetworkPortalAssignCandidateItem {
        Objects.requireNonNull(technicianProfileId, "technicianProfileId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(membershipStatus, "membershipStatus");
        Objects.requireNonNull(profileStatus, "profileStatus");
        Objects.requireNonNull(qualificationSummary, "qualificationSummary");
        Objects.requireNonNull(scheduleConflictSummary, "scheduleConflictSummary");
        Objects.requireNonNull(distanceTier, "distanceTier");
        Objects.requireNonNull(distanceSummary, "distanceSummary");
        Objects.requireNonNull(recommendationTier, "recommendationTier");
        Objects.requireNonNull(recommendationSummary, "recommendationSummary");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        recommendationReasons = List.copyOf(
                Objects.requireNonNull(recommendationReasons, "recommendationReasons"));
        if (openTaskCount < 0
                || approvedQualificationCount < 0
                || pendingQualificationCount < 0
                || upcomingAppointmentCount < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
    }
}
