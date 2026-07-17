package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

/**
 * 个人最近访问读取。仅返回当前主体可见项；失权项省略，不整列表失败。
 */
public interface RecentResourceQueryService {
    RecentResourcePage list(CurrentPrincipal actor, String correlationId, String portal, Integer limit);
}
