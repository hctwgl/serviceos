package com.serviceos.task.api;

import java.time.Instant;

/**
 * 为人工异常闭环创建的 HUMAN Task；同一 tenant/taskType/businessKey 必须幂等。
 */
public record CreateHandlingTaskCommand(
        String tenantId,
        String taskType,
        String businessKey,
        String payloadRef,
        String payloadDigest,
        int priority,
        Instant readyAt,
        String correlationId
) {
}
