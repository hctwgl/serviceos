package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** Task 执行保护窗事实；下游只能据此推进 saga，不能直接修改 Task 表。 */
public record TaskExecutionGuardChangedPayload(
        UUID taskId,
        UUID guardId,
        String guardType,
        String guardKey,
        String status,
        String reasonCode,
        Instant occurredAt
) {
}
