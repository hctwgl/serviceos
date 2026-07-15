package com.serviceos.operations.api;

import java.time.Instant;
import java.util.UUID;

/** Task 最终失败因可核验领域恢复事实而自动闭环的事件摘要。 */
public record TaskFailureExceptionResolvedPayload(
        UUID exceptionId,
        UUID sourceTaskId,
        String sourceTaskType,
        String recoveryType,
        String recoveryRef,
        UUID handlingTaskId,
        String handlingTaskStatus,
        String resolutionCode,
        String resolutionActionRef,
        UUID resolutionEventId,
        Instant resolvedAt
) {
}
