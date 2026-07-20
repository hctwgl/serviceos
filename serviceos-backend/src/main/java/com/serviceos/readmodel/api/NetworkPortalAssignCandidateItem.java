package com.serviceos.readmodel.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Network Portal 分配师傅候选摘要。
 *
 * <p>仅包含可从本网点 ACTIVE 责任、资质与产能计数证明的事实；
 * 距离/日程冲突未建模时不返回伪造字段。</p>
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
        Integer capacityAvailableUnits,
        Integer capacityMaxUnits,
        List<String> warnings,
        boolean assignable
) {
    public NetworkPortalAssignCandidateItem {
        Objects.requireNonNull(technicianProfileId, "technicianProfileId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(membershipStatus, "membershipStatus");
        Objects.requireNonNull(profileStatus, "profileStatus");
        Objects.requireNonNull(qualificationSummary, "qualificationSummary");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        if (openTaskCount < 0 || approvedQualificationCount < 0 || pendingQualificationCount < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
    }
}
