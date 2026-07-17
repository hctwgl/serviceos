package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/** Network Portal 任务列表项。 */
public record NetworkPortalTaskItem(
        UUID taskId,
        UUID workOrderId,
        UUID projectId,
        String taskType,
        String taskKind,
        String stageCode,
        String status,
        String businessType,
        String technicianId,
        Instant effectiveFrom
) {
}
