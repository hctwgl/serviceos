package com.serviceos.workflow.api;

import java.time.Instant;
import java.util.UUID;

/** 阶段完成的稳定业务事实，不复制阶段内任务明细。 */
public record StageCompletedPayload(
        UUID stageInstanceId,
        UUID workflowInstanceId,
        UUID workOrderId,
        String stageCode,
        int sequenceNo,
        Instant completedAt
) {
}
