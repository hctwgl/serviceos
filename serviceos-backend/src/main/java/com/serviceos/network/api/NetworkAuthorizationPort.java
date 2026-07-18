package com.serviceos.network.api;

import com.serviceos.identity.api.CurrentPrincipal;

/**
 * network 模块声明的授权端口，由 authorization 模块实现。
 */
public interface NetworkAuthorizationPort {
    NetworkAuthorizationEvidence requireTenantCapability(
            CurrentPrincipal principal, String capability, String resourceId, String correlationId);

    /**
     * NETWORK scope 能力门禁。TENANT 级授予仍覆盖；供 Network Portal 收窄委托路径使用。
     */
    NetworkAuthorizationEvidence requireNetworkCapability(
            CurrentPrincipal principal, String capability, String networkId,
            String resourceId, String correlationId);
}
