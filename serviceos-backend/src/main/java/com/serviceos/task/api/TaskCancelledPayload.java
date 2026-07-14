package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** TaskCancelled 领域事件的最小非敏感载荷。 */
public record TaskCancelledPayload(
        UUID taskId,
        String taskType,
        String businessKey,
        String reasonCode,
        UUID sourceEventId,
        Instant cancelledAt
) {
}
