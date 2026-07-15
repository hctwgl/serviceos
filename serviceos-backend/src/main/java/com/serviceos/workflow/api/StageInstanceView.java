package com.serviceos.workflow.api;

import java.time.Instant;
import java.util.UUID;

public record StageInstanceView(
        UUID id, UUID workflowInstanceId, UUID workOrderId, String stageCode,
        int sequenceNo, String status, long version, Instant activatedAt, Instant completedAt) {
}
