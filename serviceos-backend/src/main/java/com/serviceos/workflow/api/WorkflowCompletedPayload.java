package com.serviceos.workflow.api;

import java.time.Instant;
import java.util.UUID;

/** 冻结流程到达唯一 END 后产生的完成事实。 */
public record WorkflowCompletedPayload(
        UUID workflowInstanceId,
        UUID projectId,
        UUID workOrderId,
        UUID workflowDefinitionVersionId,
        String workflowDefinitionDigest,
        Instant completedAt
) {
}
