package com.serviceos.operations.api;

import java.time.Instant;
import java.util.UUID;

/** 以 ServiceAssignment 激活完成事件作为真实领域恢复证据解决超时异常。 */
public record ResolveServiceAssignmentTimeoutCommand(
        String tenantId,
        UUID eventId,
        int schemaVersion,
        String payloadDigest,
        UUID sagaId,
        UUID serviceAssignmentId,
        UUID workOrderId,
        UUID taskId,
        long sagaVersion,
        Instant completedAt,
        String correlationId
) {
}
