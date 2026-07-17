package com.serviceos.network.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Network Portal 师傅列表项：ACTIVE 网点师傅关系 + 档案摘要。
 * 调用方负责 Portal 上下文与 capability 鉴权。
 */
public record NetworkPortalTechnicianView(
        UUID membershipId,
        UUID technicianProfileId,
        UUID principalId,
        String displayName,
        String profileStatus,
        String membershipStatus,
        Instant validFrom,
        Instant validTo,
        long membershipVersion
) {
}
