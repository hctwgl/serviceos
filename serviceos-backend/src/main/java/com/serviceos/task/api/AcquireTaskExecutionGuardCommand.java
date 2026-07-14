package com.serviceos.task.api;

import java.util.Objects;
import java.util.UUID;

/** 为跨模块责任切换建立不可执行保护窗；guardKey 应使用稳定 saga ID。 */
public record AcquireTaskExecutionGuardCommand(
        UUID taskId,
        long expectedVersion,
        String guardKey,
        String reasonCode
) {
    public AcquireTaskExecutionGuardCommand {
        taskId = Objects.requireNonNull(taskId, "taskId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        if (guardKey == null || guardKey.isBlank() || guardKey.length() > 160) {
            throw new IllegalArgumentException("guardKey must contain at most 160 characters");
        }
        if (reasonCode == null || !reasonCode.matches("[A-Z][A-Z0-9_]{1,99}")) {
            throw new IllegalArgumentException("reasonCode must be a stable uppercase code");
        }
    }
}
