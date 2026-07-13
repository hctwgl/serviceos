package com.serviceos.workflow.api;

import java.time.Instant;
import java.util.UUID;

public record StageActivatedPayload(
        UUID stageInstanceId,
        UUID workflowInstanceId,
        UUID workOrderId,
        String stageCode,
        int sequenceNo,
        Instant activatedAt
) {
}
