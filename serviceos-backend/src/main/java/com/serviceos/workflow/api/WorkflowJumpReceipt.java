package com.serviceos.workflow.api;

import java.util.UUID;

public record WorkflowJumpReceipt(
        UUID workflowInstanceId,
        UUID stageInstanceId,
        UUID nodeInstanceId,
        UUID taskId,
        String targetNodeId
) {
}
