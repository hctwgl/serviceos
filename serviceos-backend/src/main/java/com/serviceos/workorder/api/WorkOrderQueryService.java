package com.serviceos.workorder.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** WorkOrder 授权只读用例。 */
public interface WorkOrderQueryService {
    WorkOrderPage list(CurrentPrincipal principal, String correlationId, WorkOrderQuery query);
    WorkOrderDetail get(CurrentPrincipal principal, String correlationId, UUID workOrderId);
}
