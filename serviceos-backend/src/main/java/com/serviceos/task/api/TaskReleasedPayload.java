package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** 当前责任人释放任务后的稳定事实。 */
public record TaskReleasedPayload(UUID taskId, String actorId, String reasonCode, Instant releasedAt) {
}
