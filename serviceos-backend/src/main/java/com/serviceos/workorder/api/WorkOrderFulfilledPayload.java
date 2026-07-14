package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 必要履约流程到达 END 后产生的工单完成事实。 */
public record WorkOrderFulfilledPayload(
        UUID workOrderId,
        UUID workflowInstanceId,
        List<String> completedStageCodes,
        Instant fulfilledAt
) {
}
