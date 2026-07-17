package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Network Portal 本网点师傅服务关系安全摘要。
 * <p>
 * 字段对齐 {@code NetworkTechnicianMembershipView} 的非敏感子集；含真实 {@code version}
 * 供 terminate If-Match。
 */
public record NetworkPortalMembershipItem(
        UUID id,
        UUID serviceNetworkId,
        UUID technicianProfileId,
        String status,
        Instant validFrom,
        Instant validTo,
        long version,
        Instant createdAt,
        Instant terminatedAt,
        String terminateReason
) {}
