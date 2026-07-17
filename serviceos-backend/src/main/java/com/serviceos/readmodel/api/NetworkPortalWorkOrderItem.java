package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Network Portal 工单列表项：按 ACTIVE NETWORK 责任聚合。 */
public record NetworkPortalWorkOrderItem(
        UUID workOrderId,
        UUID projectId,
        List<UUID> taskIds,
        String businessType,
        String technicianId,
        Instant effectiveFrom
) {
    public NetworkPortalWorkOrderItem {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
    }
}
