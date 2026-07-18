package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Network Portal 任务列表项。
 *
 * <p>M236：可选服务产品 / 区域 / 接收时间来自所属工单权威头（非 PII）。</p>
 */
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
        Instant effectiveFrom,
        String brandCode,
        String serviceProductCode,
        String provinceCode,
        String cityCode,
        String districtCode,
        Instant receivedAt
) {
    /** M194～M235 兼容：无工单头 enrichment。 */
    public NetworkPortalTaskItem(
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
        this(taskId, workOrderId, projectId, taskType, taskKind, stageCode, status,
                businessType, technicianId, effectiveFrom,
                null, null, null, null, null, null);
    }
}
