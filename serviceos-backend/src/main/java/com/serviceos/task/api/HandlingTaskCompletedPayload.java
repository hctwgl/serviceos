package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** 非工作流人工接管 Task 的完成事件；不伪造 workflow/project/workOrder 字段。 */
public record HandlingTaskCompletedPayload(
        UUID taskId,
        String taskType,
        String businessKey,
        String resultRef,
        String resultDigest,
        String completedBy,
        Instant completedAt
) {
}
