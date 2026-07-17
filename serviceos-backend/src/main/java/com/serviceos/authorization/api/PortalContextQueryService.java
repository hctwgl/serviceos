package com.serviceos.authorization.api;

import com.serviceos.identity.api.CurrentPrincipal;

/**
 * 当前主体 Portal 上下文与导航查询。上下文只由有效 Persona、Membership、RoleGrant 与 feature gate 计算；
 * 导航不作为业务 API 授权凭证。
 */
public interface PortalContextQueryService {
    MeProfileView me(CurrentPrincipal actor, String correlationId);

    MeContextsView contexts(CurrentPrincipal actor, String correlationId);

    MeCapabilitiesView capabilities(
            CurrentPrincipal actor, String correlationId, String contextId, String expectedContextVersion);

    MeNavigationView navigation(
            CurrentPrincipal actor, String correlationId, String contextId, String expectedContextVersion);
}
