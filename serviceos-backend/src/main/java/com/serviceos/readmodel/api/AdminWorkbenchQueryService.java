package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

/** Admin 首页页面级只读查询。 */
public interface AdminWorkbenchQueryService {
    AdminWorkbenchView get(CurrentPrincipal principal, String correlationId);
}
