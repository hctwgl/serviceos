package com.serviceos.task.api;

import java.time.Instant;
import java.util.List;
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
        Instant completedAt,
        List<InputVersionRef> inputVersionRefs
) {
    public TaskCompletedPayload {
        inputVersionRefs = inputVersionRefs == null ? List.of() : List.copyOf(inputVersionRefs);
    }

    /** 兼容自动任务等无结构化输入引用的完成事件。 */
    public TaskCompletedPayload(
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
        this(taskId, projectId, workOrderId, workflowInstanceId, stageInstanceId,
                workflowNodeInstanceId, workflowNodeId, taskType, workflowDefinitionVersionId,
                workflowDefinitionDigest, resultRef, resultDigest, completedAt, List.of());
    }
}
