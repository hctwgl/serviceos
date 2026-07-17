package com.serviceos.network.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 可接单判定公开 API，供 dispatch 后续候选过滤消费，不得复制资质规则。
 */
public interface TechnicianEligibilityQuery {
    boolean canAcceptAssignment(String tenantId, UUID technicianPrincipalId, UUID serviceNetworkId, Instant at);

    /** 不可接单时的中文原因；可接单时返回 null。 */
    String explainIneligibility(String tenantId, UUID technicianPrincipalId, UUID serviceNetworkId, Instant at);
}
