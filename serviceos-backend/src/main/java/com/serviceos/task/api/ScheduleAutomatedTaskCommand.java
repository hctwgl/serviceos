package com.serviceos.task.api;

import java.time.Instant;

/**
 * 调度自动任务的稳定输入。payloadRef 指向受控业务载荷，摘要用于阻止业务键复用时篡改输入。
 */
public record ScheduleAutomatedTaskCommand(
        String tenantId,
        String taskType,
        String businessKey,
        String payloadRef,
        String payloadDigest,
        int priority,
        Instant nextRunAt,
        int maxAttempts,
        String correlationId
) {
}
