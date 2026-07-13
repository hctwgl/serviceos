package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

public record ScheduledTaskView(
        UUID taskId,
        String tenantId,
        String taskType,
        String businessKey,
        String status,
        Instant nextRunAt,
        int attemptCount,
        int maxAttempts,
        long version
) {
}
