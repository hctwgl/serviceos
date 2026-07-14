package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** 人工任务责任正式归属的稳定事件载荷。 */
public record TaskClaimedPayload(UUID taskId, String actorId, Instant claimedAt) {
}
