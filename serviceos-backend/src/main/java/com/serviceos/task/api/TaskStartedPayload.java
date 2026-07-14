package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** 人工任务实际开始执行的稳定事件载荷。 */
public record TaskStartedPayload(UUID taskId, String actorId, Instant startedAt) {
}
