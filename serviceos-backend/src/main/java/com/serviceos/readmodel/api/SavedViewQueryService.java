package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

/**
 * 查询当前主体可见的 SavedView：本人视图 + 同租户 TENANT 共享 + 持有 RoleGrant 的 ROLE 共享。
 * 列出不授予页面访问能力。
 */
public interface SavedViewQueryService {
    SavedViewPage list(CurrentPrincipal actor, String correlationId, String pageId);
}
