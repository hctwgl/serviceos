package com.serviceos.operations.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 以一个已经落账的领域恢复事实，解决同一业务结果下失去处理必要性的 Task 失败异常。
 */
public record ResolveTaskFailureExceptionsCommand(
        String tenantId,
        UUID eventId,
        int schemaVersion,
        String payloadDigest,
        String sourceTaskType,
        List<UUID> sourceTaskIds,
        String recoveryType,
        String recoveryRef,
        Instant recoveredAt,
        String correlationId
) {
}
