package com.serviceos.network.api;

import com.serviceos.identity.api.CurrentPrincipal;

/**
 * network 模块声明的授权端口，由 authorization 模块实现。
 */
public interface NetworkAuthorizationPort {
    NetworkAuthorizationEvidence requireTenantCapability(
            CurrentPrincipal principal, String capability, String resourceId, String correlationId);
}
