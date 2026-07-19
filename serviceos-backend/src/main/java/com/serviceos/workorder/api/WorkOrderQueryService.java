package com.serviceos.workorder.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** WorkOrder 授权只读用例。 */
public interface WorkOrderQueryService {
    WorkOrderPage list(CurrentPrincipal principal, String correlationId, WorkOrderQuery query);
    WorkOrderDetail get(CurrentPrincipal principal, String correlationId, UUID workOrderId);

    /**
     * M351：返回服务端脱敏后的客户联系摘要；完整原文永不离开 workorder 模块边界。
     */
    WorkOrderMaskedContactView getMaskedContact(
            CurrentPrincipal principal, String correlationId, UUID workOrderId);
}
