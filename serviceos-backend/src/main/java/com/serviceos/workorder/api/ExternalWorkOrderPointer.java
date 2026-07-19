package com.serviceos.workorder.api;

import java.util.UUID;

/** 按外部订单定位工单的最小指针；供集成管道取消/更新使用，不含 PII。 */
public record ExternalWorkOrderPointer(
        UUID workOrderId,
        UUID projectId,
        String status,
        long version
) {
}
