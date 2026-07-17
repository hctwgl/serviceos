package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

/** 查询当前主体拥有的个人 SavedView；不授予页面访问能力。 */
public interface SavedViewQueryService {
    SavedViewPage list(CurrentPrincipal actor, String correlationId, String pageId);
}
