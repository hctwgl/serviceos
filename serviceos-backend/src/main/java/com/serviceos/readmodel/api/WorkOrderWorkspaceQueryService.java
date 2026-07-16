package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** 工单工作区只读组合查询。 */
public interface WorkOrderWorkspaceQueryService {
    WorkOrderWorkspace get(CurrentPrincipal principal, String correlationId, UUID workOrderId);
}
