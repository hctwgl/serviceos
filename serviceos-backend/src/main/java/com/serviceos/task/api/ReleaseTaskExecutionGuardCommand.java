package com.serviceos.task.api;

import java.util.Objects;
import java.util.UUID;

/** 在跨模块责任已对齐或安全补偿后解除指定保护窗。 */
public record ReleaseTaskExecutionGuardCommand(
        UUID taskId,
        UUID guardId,
        long expectedVersion,
        String reasonCode
) {
    public ReleaseTaskExecutionGuardCommand {
        taskId = Objects.requireNonNull(taskId, "taskId");
        guardId = Objects.requireNonNull(guardId, "guardId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        if (reasonCode == null || !reasonCode.matches("[A-Z][A-Z0-9_]{1,99}")) {
            throw new IllegalArgumentException("reasonCode must be a stable uppercase code");
        }
    }
}
