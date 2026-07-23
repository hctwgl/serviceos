package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.workorder.api.WorkOrderQuery;

import java.util.UUID;

/** Admin 工单黄金链路的页面级只读查询。 */
public interface AdminWorkOrderQueryService {
    AdminWorkOrderDirectoryView list(
            CurrentPrincipal principal,
            String correlationId,
            WorkOrderQuery query
    );

    AdminWorkOrderWorkspaceView getWorkspace(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId
    );
}
