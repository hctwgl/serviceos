package com.serviceos.identity.api;

/**
 * identity 模块声明的授权端口，由 authorization 模块实现。
 *
 * <p>该方向保持 authorization 对 CurrentPrincipal 的既有依赖单向，避免 identity 为调用通用授权
 * API 反向依赖 authorization 并形成模块环。</p>
 */
public interface IdentityAuthorizationPort {
    IdentityAuthorizationEvidence requireTenantCapability(
            CurrentPrincipal principal, String capability, String resourceId, String correlationId);
}
