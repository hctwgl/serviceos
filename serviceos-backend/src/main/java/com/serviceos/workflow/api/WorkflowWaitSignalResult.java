package com.serviceos.workflow.api;

import java.util.UUID;

/** WAIT_EVENT 唤醒结果。 */
public record WorkflowWaitSignalResult(
        UUID waitSubscriptionId,
        UUID workflowInstanceId,
        UUID workOrderId,
        boolean replay,
        String status
) {
}
