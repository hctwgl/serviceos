package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** 人工任务命令提交后的权威状态。 */
public record HumanTaskCommandReceipt(
        UUID taskId, String status, String actorId, long version, Instant occurredAt
) {
}
