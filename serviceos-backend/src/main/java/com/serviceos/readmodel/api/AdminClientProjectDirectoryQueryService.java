package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

/** Admin 客户与项目页面级查询入口。 */
public interface AdminClientProjectDirectoryQueryService {
    AdminClientProjectDirectoryView load(CurrentPrincipal actor, String correlationId);
}
