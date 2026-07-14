package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** 任务恢复取消结果；COMPLETED 表示人工已先完成，因此保留人工事实而不覆盖。 */
public record HandlingTaskCancellationReceipt(
        UUID taskId,
        String status,
        long taskVersion,
        UUID sourceEventId,
        Instant occurredAt
) {
}
