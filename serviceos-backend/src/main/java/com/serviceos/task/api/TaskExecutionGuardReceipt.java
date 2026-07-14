package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** guard 获取或解除的首次冻结结果。 */
public record TaskExecutionGuardReceipt(
        UUID guardId,
        UUID taskId,
        String guardKey,
        String status,
        long taskVersion,
        Instant occurredAt
) {
}
