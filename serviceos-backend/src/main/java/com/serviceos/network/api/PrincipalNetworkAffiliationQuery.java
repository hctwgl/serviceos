package com.serviceos.network.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 主体有效网点成员/师傅关系只读端口，供 Portal 上下文计算；调用方必须只查询当前主体自身。
 */
public interface PrincipalNetworkAffiliationQuery {
    List<NetworkMembershipView> listActiveNetworkMemberships(String tenantId, UUID principalId, Instant at);

    Optional<TechnicianProfileView> findActiveTechnicianProfile(String tenantId, UUID principalId);

    List<NetworkTechnicianMembershipView> listActiveTechnicianMemberships(
            String tenantId, UUID technicianProfileId, Instant at);
}
