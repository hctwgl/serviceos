package com.serviceos.task.api;

import java.util.UUID;

/** 已获授权的人工执行人以乐观锁领取 READY 任务；actorId 必须来自可信认证上下文。 */
public record ClaimHumanTaskCommand(UUID taskId, long expectedVersion) {
    public ClaimHumanTaskCommand {
        taskId = java.util.Objects.requireNonNull(taskId, "taskId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
    }
}
