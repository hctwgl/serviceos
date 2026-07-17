package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Network Portal 运营异常队列安全摘要。
 * <p>
 * 字段语义对齐 {@code OperationalExceptionItem} 的只读发现面；不含 acknowledgementNote、
 * aggregateVersion、操作者等敏感/动作字段。{@code allowedActions} 对 Portal 恒为空。
 */
public record NetworkPortalExceptionItem(
        UUID exceptionId,
        UUID projectId,
        String sourceType,
        String category,
        String severity,
        String errorCode,
        String status,
        UUID workOrderId,
        UUID taskId,
        UUID handlingTaskId,
        long occurrenceCount,
        Instant openedAt,
        Instant lastDetectedAt,
        Instant resolvedAt,
        String resolutionCode,
        List<String> allowedActions
) {
    public NetworkPortalExceptionItem {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }
}
