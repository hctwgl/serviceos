package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** 工单工作区使用的最小 Task 执行摘要，不暴露载荷和结果引用。 */
public record WorkOrderTaskSummary(
        UUID id, UUID projectId, UUID workOrderId, UUID workflowInstanceId,
        UUID stageInstanceId, UUID workflowNodeInstanceId, String workflowNodeId,
        String stageCode, String taskType, String taskKind, int priority, String status,
        Instant nextRunAt, String claimedBy, Instant claimedAt, Instant startedAt,
        Instant completedAt, long version, Instant createdAt, Instant updatedAt) {
}
