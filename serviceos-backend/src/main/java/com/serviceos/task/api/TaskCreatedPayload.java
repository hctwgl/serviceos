package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

public record TaskCreatedPayload(
        UUID taskId,
        UUID projectId,
        UUID workOrderId,
        UUID workflowInstanceId,
        UUID stageInstanceId,
        UUID workflowNodeInstanceId,
        String workflowNodeId,
        String taskType,
        WorkflowTaskKind taskKind,
        String status,
        UUID workflowDefinitionVersionId,
        String workflowDefinitionDigest,
        Instant createdAt
) {
}
