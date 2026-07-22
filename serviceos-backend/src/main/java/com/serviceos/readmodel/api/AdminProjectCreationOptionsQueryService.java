package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

/** Admin 新建项目抽屉所需的完整页面选项查询。 */
public interface AdminProjectCreationOptionsQueryService {
    AdminProjectCreationOptionsView load(CurrentPrincipal actor, String correlationId, String regionQuery);
}
