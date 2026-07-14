package com.serviceos.task.api;

import java.util.UUID;

/** 当前领取人启动人工任务。 */
public record StartHumanTaskCommand(UUID taskId, long expectedVersion) {
    public StartHumanTaskCommand {
        taskId = java.util.Objects.requireNonNull(taskId, "taskId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
    }
}
