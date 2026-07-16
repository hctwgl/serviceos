package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** 工单最近活动摘要查询边界。 */
public interface WorkOrderActivitySummaryQueryService {
    WorkOrderActivitySummary get(
            CurrentPrincipal principal, String correlationId, UUID workOrderId, int limit);
}
