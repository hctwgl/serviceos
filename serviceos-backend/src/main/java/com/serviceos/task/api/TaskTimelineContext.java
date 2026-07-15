package com.serviceos.task.api;

import java.util.UUID;

/** 供跨模块时间线投影解析 Task 所属工单的最小非敏感上下文。 */
public record TaskTimelineContext(
        UUID taskId,
        UUID projectId,
        UUID workOrderId,
        String taskType,
        String taskKind
) {
}
