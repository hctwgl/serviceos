package com.serviceos.authorization.application;

import java.time.Instant;

/**
 * authorization 模块内部权威授权查询端口。
 */
public interface AuthorizationPolicyStore {
    CapabilityGrantMatch findTenantCapabilityGrants(
            String tenantId,
            String principalId,
            String capability,
            Instant evaluatedAt
    );
}
