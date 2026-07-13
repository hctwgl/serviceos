package com.serviceos.authorization.application;

import com.serviceos.authorization.api.AuthorizationRequest;

import java.time.Instant;

/**
 * authorization 模块内部权威授权查询端口。
 */
public interface AuthorizationPolicyStore {
    CapabilityGrantMatch findCapabilityGrants(
            String tenantId,
            String principalId,
            AuthorizationRequest request,
            Instant evaluatedAt
    );
}
