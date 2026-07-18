package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** 由权威业务终态完成人工接管 Task；调用方必须同时提供业务身份，不能仅凭 Task UUID。 */
public record CompleteHandlingTaskCommand(
        String tenantId,
        UUID taskId,
        String taskType,
        String businessKey,
        String resultRef,
        String resultDigest,
        String completedBy,
        Instant completedAt,
        String correlationId
) {
}
