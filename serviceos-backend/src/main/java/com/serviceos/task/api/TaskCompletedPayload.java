package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** 自动或人工任务达到业务完成条件后的稳定领域事实。 */
public record TaskCompletedPayload(
        UUID taskId,
        UUID projectId,
        UUID workOrderId,
        UUID workflowInstanceId,
        UUID stageInstanceId,
        UUID workflowNodeInstanceId,
        String workflowNodeId,
        String taskType,
        UUID workflowDefinitionVersionId,
        String workflowDefinitionDigest,
        String resultRef,
        String resultDigest,
        Instant completedAt
) {
}
