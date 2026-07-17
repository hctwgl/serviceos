package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

/**
 * Admin 受控全局搜索：fan-in 既有授权查询，不授予额外数据能力。
 */
public interface ControlledSearchQueryService {
    ControlledSearchResult search(CurrentPrincipal actor, String correlationId, String q, String typesCsv);
}
