package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** Admin 项目详情工作区页面查询。 */
public interface AdminProjectWorkspaceQueryService {
    AdminProjectWorkspaceView get(CurrentPrincipal actor, String correlationId, UUID projectId);
}
