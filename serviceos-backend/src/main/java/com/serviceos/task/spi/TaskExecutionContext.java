package com.serviceos.task.spi;

import java.util.UUID;

/**
 * 一次已认领执行的不可变上下文。重认领会产生新 attemptId，但 taskId 与载荷摘要保持不变。
 */
public record TaskExecutionContext(
        UUID taskId,
        UUID attemptId,
        String tenantId,
        String taskType,
        String businessKey,
        String payloadRef,
        String payloadDigest,
        String correlationId,
        int attemptNo
) {
}
