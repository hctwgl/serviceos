package com.serviceos.task.api;

import java.util.Objects;
import java.util.UUID;

/** 当前责任人在开始前释放已领取任务，使原候选人可再次领取。 */
public record ReleaseHumanTaskCommand(UUID taskId, long expectedVersion, String reasonCode) {
    public ReleaseHumanTaskCommand {
        taskId = Objects.requireNonNull(taskId, "taskId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        if (reasonCode == null || !reasonCode.matches("[A-Z][A-Z0-9_]{1,99}")) {
            throw new IllegalArgumentException("reasonCode must be a stable uppercase code");
        }
    }
}
