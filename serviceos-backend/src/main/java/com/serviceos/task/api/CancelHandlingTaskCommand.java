package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 取消已经失去处理必要性的人工接管任务。
 *
 * <p>taskType 与 businessKey 是调用方掌握的业务身份，Task 模块必须同时校验，避免仅凭 UUID
 * 误取消无关任务。sourceEventId 是触发恢复的不可变领域事件，用于重放与审计。</p>
 */
public record CancelHandlingTaskCommand(
        String tenantId,
        UUID taskId,
        String taskType,
        String businessKey,
        String reasonCode,
        UUID sourceEventId,
        Instant cancelledAt,
        String correlationId
) {
}
