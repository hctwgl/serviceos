package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Network Portal 师傅列表项。
 *
 * <p>M421：附加本网点 ACTIVE 责任开放任务数与资质计数/摘要，口径对齐分配候选（M407），
 * 供师傅列表与工作区/目录 fan-in 摘要共用；不伪造技能 taxonomy 或服务区域。</p>
 */
public record NetworkPortalTechnicianItem(
        UUID membershipId,
        UUID technicianProfileId,
        UUID principalId,
        String displayName,
        String profileStatus,
        String membershipStatus,
        Instant validFrom,
        Instant validTo,
        long membershipVersion,
        int openTaskCount,
        int approvedQualificationCount,
        int pendingQualificationCount,
        String qualificationSummary
) {
    public NetworkPortalTechnicianItem {
        Objects.requireNonNull(membershipId, "membershipId");
        Objects.requireNonNull(technicianProfileId, "technicianProfileId");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(profileStatus, "profileStatus");
        Objects.requireNonNull(membershipStatus, "membershipStatus");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(qualificationSummary, "qualificationSummary");
        if (membershipVersion < 1) {
            throw new IllegalArgumentException("membershipVersion must be >= 1");
        }
        if (openTaskCount < 0
                || approvedQualificationCount < 0
                || pendingQualificationCount < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
    }
}
