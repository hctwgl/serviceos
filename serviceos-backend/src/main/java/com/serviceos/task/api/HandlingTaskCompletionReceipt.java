package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

public record HandlingTaskCompletionReceipt(
        UUID taskId,
        String status,
        long version,
        String resultRef,
        Instant completedAt
) {
}
