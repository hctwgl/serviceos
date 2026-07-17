package com.serviceos.authorization.application;

import com.serviceos.authorization.api.AuthorizationRequest;

import java.time.Instant;
import java.util.List;

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

    /** 返回当前主体在指定 Portal 上下文范围内有效（ALLOW 且未被 DENY）的能力编码。 */
    List<String> listEffectiveCapabilityCodes(
            String tenantId,
            String principalId,
            String scopeType,
            String scopeRef,
            Instant evaluatedAt
    );

    String policyVersion(String tenantId);
}
