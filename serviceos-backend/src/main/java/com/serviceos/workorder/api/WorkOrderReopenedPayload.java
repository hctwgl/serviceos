package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

/** 重开载荷携带冻结 Bundle，供 Workflow 新建根实例。 */
public record WorkOrderReopenedPayload(
        UUID workOrderId,
        UUID projectId,
        WorkOrderReceivedPayload.ConfigurationBundleRef bundleRef,
        String approvalRef,
        Instant reopenedAt
) {
}
